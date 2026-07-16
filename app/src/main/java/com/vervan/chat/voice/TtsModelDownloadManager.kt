package com.vervan.chat.voice

import android.content.Context
import com.vervan.chat.data.db.dao.JobDao
import com.vervan.chat.data.db.dao.TtsVoiceModelDao
import com.vervan.chat.data.db.entities.JobRecord
import com.vervan.chat.data.db.entities.JobState
import com.vervan.chat.data.db.entities.JobType
import com.vervan.chat.data.db.entities.TtsVoiceModel
import com.vervan.chat.system.NetworkAuditLog
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

sealed class TtsDownloadResult {
    data class Success(val model: TtsVoiceModel) : TtsDownloadResult()
    data class Failed(val reason: String) : TtsDownloadResult()
}

/**
 * Downloads and caches Piper/Kokoro voice files for the realtime voice pipeline (Supertonic
 * manages its own storage via the SDK's `autoDownload`, so it never goes through here).
 * Mirrors [com.vervan.chat.model.ModelImportManager]'s streaming-copy + SHA-256 + progress
 * shape almost verbatim, swapping a SAF `Uri` source for an HTTP source.
 *
 * This is the first real network-download call site in the app — [NetworkAuditLog] existed
 * with zero callers before this (see that class's own doc comment). [NetworkAuditLog.record]
 * is called once per voice download, at fetch start.
 *
 * Downloads every file for one voice into its own directory (`<voiceDir>/model.onnx`,
 * `tokens.txt`, etc.) so [PiperTtsEngine]/[KokoroTtsEngine] have one path to point their
 * engine config at. On failure, no partial [TtsVoiceModel] row is written and the partial
 * directory is deleted — the caller simply finds nothing for that engine/language and
 * [TtsEngineSelector] falls through to the next tier.
 */
class TtsModelDownloadManager(
    private val context: Context,
    private val voiceModelDao: TtsVoiceModelDao,
    private val jobDao: JobDao,
    private val networkAuditLog: NetworkAuditLog
) {
    private val voicesDir: File get() = File(context.filesDir, "tts_voices").apply { mkdirs() }

    /** Downloads every file in [sourceUrls] (relative filename -> full URL) into one voice
     * directory, tracked as a [JobRecord] with real incremental progress — the first job type
     * in the app to actually drive [JobRecord.progress] rather than just toggling
     * RUNNING->COMPLETED/FAILED. No-ops (returns the existing row) if already downloaded. */
    suspend fun downloadVoice(
        engine: String,
        language: String,
        displayLabel: String,
        sourceUrls: Map<String, String>
    ): TtsDownloadResult = withContext(Dispatchers.IO) {
        voiceModelDao.getByEngine(engine, language)?.let { existing ->
            if (File(existing.filePath).isDirectory) return@withContext TtsDownloadResult.Success(existing)
        }

        val job = JobRecord(type = JobType.TTS_MODEL_DOWNLOAD, label = displayLabel, state = JobState.RUNNING)
        jobDao.upsert(job)
        networkAuditLog.record("Downloading TTS voice model: $displayLabel")

        val voiceDir = File(voicesDir, "${engine.lowercase()}_$language").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        try {
            val totalFiles = sourceUrls.size.coerceAtLeast(1)
            sourceUrls.entries.forEachIndexed { index, (fileName, url) ->
                val dest = File(voiceDir, fileName)
                dest.parentFile?.mkdirs()
                totalBytes += downloadFile(url, dest, digest) { fileFraction ->
                    val overall = (((index + fileFraction) / totalFiles) * 100).toInt().coerceIn(0, 100)
                    jobDao.upsert(job.copy(progress = overall, detail = "Downloading $fileName…", updatedAt = System.currentTimeMillis()))
                }
            }
        } catch (t: Throwable) {
            voiceDir.deleteRecursively()
            jobDao.upsert(job.copy(state = JobState.FAILED, detail = t.message ?: "Download failed", updatedAt = System.currentTimeMillis()))
            return@withContext TtsDownloadResult.Failed(t.message ?: "Download failed")
        }

        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val model = TtsVoiceModel(
            engine = engine, language = language, filePath = voiceDir.absolutePath,
            fileSizeBytes = totalBytes, sha256 = hash
        )
        voiceModelDao.upsert(model)
        jobDao.upsert(job.copy(state = JobState.COMPLETED, progress = 100, updatedAt = System.currentTimeMillis()))
        TtsDownloadResult.Success(model)
    }

    /** Downloads and extracts one of sherpa-onnx's `.tar.bz2` voice release assets (each
     * contains `model.onnx` + `tokens.txt` + a shared `espeak-ng-data/` directory, all under one
     * top-level folder matching the archive name) directly into a flat voice directory — the
     * layout [PiperTtsEngine]/[KokoroTtsEngine] expect. No-ops (returns the existing row) if
     * already downloaded. On any failure (bad URL, truncated download, corrupt archive, missing
     * `model.onnx` after extraction) the partial directory is removed and nothing is written,
     * so [TtsEngineSelector] simply finds nothing for that engine/language and falls through to
     * the next tier — same failure contract as [downloadVoice]. */
    suspend fun downloadArchiveVoice(
        engine: String,
        language: String,
        displayLabel: String,
        archiveUrl: String
    ): TtsDownloadResult = withContext(Dispatchers.IO) {
        voiceModelDao.getByEngine(engine, language)?.let { existing ->
            if (File(existing.filePath).isDirectory) return@withContext TtsDownloadResult.Success(existing)
        }

        val job = JobRecord(type = JobType.TTS_MODEL_DOWNLOAD, label = displayLabel, state = JobState.RUNNING)
        jobDao.upsert(job)
        networkAuditLog.record("Downloading TTS voice model: $displayLabel")

        val voiceDir = File(voicesDir, "${engine.lowercase()}_$language").apply { mkdirs() }
        val archiveFile = File(context.cacheDir, "tts_download_${System.currentTimeMillis()}.tar.bz2")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val totalBytes = downloadFile(archiveUrl, archiveFile, digest) { fraction ->
                jobDao.upsert(job.copy(progress = (fraction * 90).toInt(), detail = "Downloading…", updatedAt = System.currentTimeMillis()))
            }
            jobDao.upsert(job.copy(progress = 90, detail = "Extracting…", updatedAt = System.currentTimeMillis()))
            extractTarBz2(archiveFile, voiceDir)
            if (!File(voiceDir, "model.onnx").isFile) {
                throw IOException("Archive did not contain model.onnx")
            }

            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val model = TtsVoiceModel(
                engine = engine, language = language, filePath = voiceDir.absolutePath,
                fileSizeBytes = totalBytes, sha256 = hash
            )
            voiceModelDao.upsert(model)
            jobDao.upsert(job.copy(state = JobState.COMPLETED, progress = 100, updatedAt = System.currentTimeMillis()))
            TtsDownloadResult.Success(model)
        } catch (t: Throwable) {
            voiceDir.deleteRecursively()
            jobDao.upsert(job.copy(state = JobState.FAILED, detail = t.message ?: "Download failed", updatedAt = System.currentTimeMillis()))
            TtsDownloadResult.Failed(t.message ?: "Download failed")
        } finally {
            archiveFile.delete()
        }
    }

    /** Extracts a `.tar.bz2` into [destDir], stripping the archive's single top-level directory
     * component (e.g. `vits-piper-en_US-lessac-medium/model.onnx` -> `model.onnx`) so nested
     * voice files land flat where [PiperTtsEngine]/[KokoroTtsEngine] expect them. */
    private fun extractTarBz2(archiveFile: File, destDir: File) {
        BZip2CompressorInputStream(archiveFile.inputStream().buffered()).use { bz2 ->
            TarArchiveInputStream(bz2).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val relative = entry.name.substringAfter('/', missingDelimiterValue = "")
                    if (relative.isNotBlank() && !entry.isDirectory) {
                        val outFile = File(destDir, relative)
                        outFile.parentFile?.mkdirs()
                        // Guard against a malicious archive escaping destDir via "../" segments.
                        if (outFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                            outFile.outputStream().use { out -> tar.copyTo(out) }
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }

    private suspend fun downloadFile(url: String, dest: File, digest: MessageDigest, onProgress: suspend (Float) -> Unit): Long {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.connect()
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP ${connection.responseCode} fetching $url")
        }
        val contentLength = connection.contentLengthLong.takeIf { it > 0 }
        var bytesCopied = 0L
        connection.inputStream.use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    bytesCopied += read
                    if (contentLength != null) onProgress((bytesCopied.toFloat() / contentLength).coerceIn(0f, 1f))
                }
            }
        }
        connection.disconnect()
        if (bytesCopied == 0L) {
            dest.delete()
            throw IOException("Downloaded file was empty: $url")
        }
        return bytesCopied
    }

    companion object {
        /** ponytail: exact source URLs need confirming against the active mirror at
         * implementation/ship time — Piper's original hosting (rhasspy/piper) moved to
         * OHF-Voice/piper1-gpl release assets after the source repo was archived. Wire these
         * once confirmed; [downloadVoice] itself is source-agnostic. */
        const val VOICE_SOURCE_TODO = "Confirm active piper-voices / kokoro-multi-lang mirror before shipping"
    }
}

/** A single downloadable voice offered from Settings. Points at sherpa-onnx's own GitHub
 * release assets (`tts-models` tag) — confirmed pattern:
 * `.../releases/download/tts-models/vits-piper-<voice>.tar.bz2`, each a self-contained
 * `model.onnx` + `tokens.txt` + `espeak-ng-data/` archive. ponytail: Hindi/Kokoro entries here
 * are best-effort — if a given asset name has moved or doesn't exist, [downloadArchiveVoice]
 * fails cleanly (surfaced as JobState.FAILED, no partial voice written) rather than crashing;
 * confirm the exact live filenames against the release page before treating this as final.
 */
data class TtsVoiceCatalogEntry(val engine: String, val language: String, val label: String, val archiveUrl: String)

object TtsVoiceCatalog {
    private const val RELEASE_BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

    val entries = listOf(
        TtsVoiceCatalogEntry("PIPER", "hi", "Hindi (Piper, pratham)", "$RELEASE_BASE/vits-piper-hi_IN-pratham-medium.tar.bz2"),
        TtsVoiceCatalogEntry("PIPER", "en", "English (Piper, lessac)", "$RELEASE_BASE/vits-piper-en_US-lessac-medium.tar.bz2"),
        TtsVoiceCatalogEntry("KOKORO", "multi", "Multilingual (Kokoro, higher quality)", "$RELEASE_BASE/kokoro-multi-lang-v1_1.tar.bz2")
    )
}
