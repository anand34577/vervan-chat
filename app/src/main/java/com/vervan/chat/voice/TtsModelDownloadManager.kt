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
 * Downloads and caches the Kokoro voice archive for the realtime voice pipeline's opt-in
 * "higher quality" tier — the only voice still on this path. Hindi/English Piper voices moved to
 * the app's real model-download system ([com.vervan.chat.modeldownload.ModelDownloadRepository] +
 * [com.vervan.chat.modeldownload.ModelCatalog], category `TTS_VOICE`), which gives them proper
 * pause/resume/cancel/retry/delete UI instead of this class's simple download-only flow. Kokoro
 * stays here because it needs `.tar.bz2` archive extraction (`voices.bin` alongside the model,
 * no MMS/flat-file equivalent), which the generic multi-file download system doesn't support —
 * not worth building for one optional secondary voice.
 */
class TtsModelDownloadManager(
    private val context: Context,
    private val voiceModelDao: TtsVoiceModelDao,
    private val jobDao: JobDao,
    private val networkAuditLog: NetworkAuditLog
) {
    private val voicesDir: File get() = File(context.filesDir, "tts_voices").apply { mkdirs() }

    /** Downloads and extracts one of sherpa-onnx's `.tar.bz2` voice release assets (each
     * contains `model.onnx` + `tokens.txt` + a shared `espeak-ng-data/` directory, all under one
     * top-level folder matching the archive name) directly into a flat voice directory — the
     * layout [PiperTtsEngine]/[KokoroTtsEngine] expect. No-ops (returns the existing row) if
     * already downloaded. On any failure (bad URL, truncated download, corrupt archive, missing
     * `model.onnx` after extraction) the partial directory is removed and nothing is written, so
     * [TtsEngineSelector] simply finds nothing for that engine/language and falls through to the
     * next tier. */
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

    /** Removes a downloaded voice's files and its [TtsVoiceModel] row. */
    suspend fun deleteVoice(engine: String, language: String) = withContext(Dispatchers.IO) {
        val existing = voiceModelDao.getByEngine(engine, language) ?: return@withContext
        File(existing.filePath).deleteRecursively()
        voiceModelDao.delete(existing)
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
}

/** The one voice still offered outside the real model-download system — see the class doc above
 * for why Kokoro stays here. ponytail: filename is best-effort against sherpa-onnx's release
 * naming convention, not independently confirmed the way the MMS-backed ModelCatalog entries
 * are — downloadArchiveVoice fails cleanly (JobState.FAILED, no partial voice written) if it's
 * ever moved/renamed upstream. */
data class TtsVoiceCatalogEntry(val engine: String, val language: String, val label: String, val archiveUrl: String)

object TtsVoiceCatalog {
    private const val RELEASE_BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

    val entries = listOf(
        TtsVoiceCatalogEntry("KOKORO", "multi", "Multilingual (Kokoro, higher quality)", "$RELEASE_BASE/kokoro-multi-lang-v1_1.tar.bz2")
    )
}
