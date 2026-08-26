package com.vervan.chat.voice

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vervan.chat.data.db.dao.JobDao
import com.vervan.chat.data.db.dao.TtsVoiceModelDao
import com.vervan.chat.data.db.entities.JobRecord
import com.vervan.chat.data.db.entities.JobState
import com.vervan.chat.data.db.entities.JobType
import com.vervan.chat.data.db.entities.TtsVoiceModel
import com.vervan.chat.model.ModelFileSniffer
import com.vervan.chat.model.copyToLimited
import com.vervan.chat.modeldownload.HttpRangeDownloader
import com.vervan.chat.modeldownload.ModelDownloadException
import com.vervan.chat.system.NetworkAuditLog
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import com.vervan.chat.validation.InputLimits

sealed class TtsDownloadResult {
    data class Success(val model: TtsVoiceModel) : TtsDownloadResult()
    data class Failed(val reason: String) : TtsDownloadResult()
}

/**
 * Downloads and caches the Kokoro voice archive for the realtime voice pipeline's opt-in
 * "higher quality" tier — the only voice still on this path. Hindi/English Piper voices moved to
 * the app's real model-download system ([com.vervan.chat.modeldownload.ModelDownloadRepository] +
 * [com.vervan.chat.modeldownload.ModelCatalog], category `TTS_VOICE`), which gives them proper
 * pause/resume/cancel/retry/delete UI instead of this class's simple download flow. Kokoro stays
 * here because it needs `.tar.bz2` archive extraction (`voices.bin` alongside the model, no
 * MMS/flat-file equivalent), which the generic multi-file download system doesn't support — not
 * worth building a whole second catalogue category for one optional secondary voice.
 *
 * The archive download itself still resumes via [HttpRangeDownloader] (the same Range-request
 * engine the real system uses) — [downloadArchiveVoice] keeps the partial `.tar.bz2` on a
 * deterministic per-(engine, language) path instead of deleting it on every interruption, so a
 * dropped connection or a user-cancelled retry continues from where it left off rather than
 * re-fetching the whole ~300 MB archive. Only extraction (post-download) stays custom.
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
     * already downloaded. The download step resumes via [HttpRangeDownloader] if a previous
     * attempt was interrupted (see the class doc); a corrupt/incomplete archive that still fails
     * extraction after a full download is discarded so a subsequent call starts clean rather than
     * retrying a bad file forever. On failure the voice directory is removed and nothing is
     * written, so [TtsEngineSelector] simply finds nothing for that engine/language and falls
     * through to the next tier. */
    suspend fun downloadArchiveVoice(
        engine: String,
        language: String,
        displayLabel: String,
        archiveUrl: String
    ): TtsDownloadResult = withContext(Dispatchers.IO) {
        voiceModelDao.getByEngine(engine, language)?.let { existing ->
            val existingDir = File(existing.filePath)
            if (isUsableVoiceDir(engine, existingDir)) return@withContext TtsDownloadResult.Success(existing)
            // A previous interrupted/old install can leave a row pointing at a directory that
            // lacks files the runtime requires. Remove the stale record so it can be repaired.
            existingDir.deleteRecursively()
            voiceModelDao.delete(existing)
        }

        val job = JobRecord(type = JobType.TTS_MODEL_DOWNLOAD, label = displayLabel, state = JobState.RUNNING)
        jobDao.upsert(job)
        networkAuditLog.record("Downloading TTS voice model: $displayLabel")

        val voiceDir = File(voicesDir, "${engine.lowercase()}_$language").apply { mkdirs() }
        // Deterministic (not timestamped) so a later call — after a dropped connection or a user
        // cancel/retry — finds the same partial file and resumes it via Range requests instead of
        // re-fetching the whole archive (Kokoro's is ~300 MB). Kokoro's archive URL is a pinned
        // GitHub release asset, which GitHub treats as immutable once published, so skipping
        // ETag/Last-Modified persistence across attempts (unlike the main downloader, which
        // additionally guards against a mutable upstream source) is an acceptable, deliberately
        // scoped-down risk here.
        val archiveFile = File(context.cacheDir, "tts_download_${engine.lowercase()}_$language.tar.bz2")
        try {
            var totalBytes: Long? = null
            HttpRangeDownloader().download(
                archiveUrl, archiveFile, knownEtag = null, knownLastModified = null, authToken = null,
                maxBytes = InputLimits.MAX_TTS_ARCHIVE_BYTES,
            ) { downloaded, total ->
                if (jobDao.get(job.id)?.state == JobState.CANCELLED) throw CancellationException("Stopped by user")
                if (downloaded > InputLimits.MAX_TTS_ARCHIVE_BYTES) throw IOException("TTS archive exceeds the 1 GB limit")
                totalBytes = total
                val progress = if (total != null && total > 0) ((downloaded * 90) / total).toInt() else 0
                jobDao.upsert(job.copy(progress = progress, detail = "Downloading…", updatedAt = System.currentTimeMillis()))
            }
            if (jobDao.get(job.id)?.state == JobState.CANCELLED) throw CancellationException("Stopped by user")
            require(archiveFile.length() <= InputLimits.MAX_TTS_ARCHIVE_BYTES) { "TTS archive exceeds the 1 GB limit" }
            jobDao.upsert(job.copy(progress = 90, detail = "Extracting…", updatedAt = System.currentTimeMillis()))
            extractTarBz2(archiveFile, voiceDir)
            if (!isUsableVoiceDir(engine, voiceDir)) {
                throw IOException("Archive is missing one or more required voice-model files")
            }

            val hash = sha256Of(archiveFile)
            val model = TtsVoiceModel(
                engine = engine, language = language, filePath = voiceDir.absolutePath,
                fileSizeBytes = totalBytes ?: archiveFile.length(), sha256 = hash
            )
            voiceModelDao.upsert(model)
            jobDao.upsert(job.copy(state = JobState.COMPLETED, progress = 100, updatedAt = System.currentTimeMillis()))
            archiveFile.delete()
            TtsDownloadResult.Success(model)
        } catch (cancelled: CancellationException) {
            // The archive file is deliberately kept (not deleted) so the next attempt resumes
            // instead of restarting — only extraction output is cleaned up, since extraction
            // hasn't necessarily run yet or may have partially written into voiceDir.
            voiceDir.listFiles()?.forEach { it.deleteRecursively() }
            jobDao.upsert(job.copy(state = JobState.CANCELLED, detail = "Stopped by user — resumes on retry", updatedAt = System.currentTimeMillis()))
            TtsDownloadResult.Failed("Download stopped")
        } catch (e: ModelDownloadException) {
            // A network/transport failure — the partial archive is still good, so keep it for a
            // resumed retry. Only a range/source-integrity failure invalidates it outright, and
            // HttpRangeDownloader already deletes/restarts that case internally before this can
            // observe it.
            voiceDir.listFiles()?.forEach { it.deleteRecursively() }
            jobDao.upsert(job.copy(state = JobState.FAILED, detail = e.message ?: "Download failed", updatedAt = System.currentTimeMillis()))
            TtsDownloadResult.Failed(e.message ?: "Download failed")
        } catch (t: Throwable) {
            com.vervan.chat.system.rethrowCancellation(t)
            // Extraction/validation failed on a *complete* download — the archive bytes
            // themselves are the problem, so discard it; resuming a known-bad file would just
            // fail the same way again.
            archiveFile.delete()
            voiceDir.listFiles()?.forEach { it.deleteRecursively() }
            jobDao.upsert(job.copy(state = JobState.FAILED, detail = t.message ?: "Download failed", updatedAt = System.currentTimeMillis()))
            TtsDownloadResult.Failed(t.message ?: "Download failed")
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
    }

    private fun isUsableVoiceDir(engine: String, dir: File): Boolean {
        if (!dir.isDirectory) return false
        val requiredFiles = buildList {
            add(File(dir, "model.onnx"))
            add(File(dir, "tokens.txt"))
            if (engine.equals("KOKORO", ignoreCase = true)) add(File(dir, "voices.bin"))
        }
        if (requiredFiles.any { !it.isFile || it.length() == 0L }) return false
        return !engine.equals("KOKORO", ignoreCase = true) || File(dir, "espeak-ng-data").isDirectory
    }

    /** Removes a downloaded voice's files and its [TtsVoiceModel] row. */
    suspend fun deleteVoice(engine: String, language: String) = withContext(Dispatchers.IO) {
        val existing = voiceModelDao.getByEngine(engine, language) ?: return@withContext
        File(existing.filePath).deleteRecursively()
        voiceModelDao.delete(existing)
    }

    /**
     * Imports a whisper.cpp ggml/GGUF model file already on-device (SAF-picked) — the
     * local-file counterpart to [downloadArchiveVoice], for bringing your own whisper.cpp model
     * (a different size/quantization, or simply without going through the catalog/network at
     * all) the same way [com.vervan.chat.model.ModelImportManager] lets you locally import a
     * generation/embedding model. Extension- and magic-byte-validated via [ModelFileSniffer] so
     * this can't silently accept, say, a renamed GGUF language model. Writes into the exact
     * directory [WhisperCppSttEngine] already reads
     * (`stt_models/whisper_cpp_multi/`, matching what a catalog `STT_MODEL` download produces —
     * see [ModelDownloadRepository][com.vervan.chat.modeldownload.ModelDownloadRepository]'s
     * `finalizeVoiceModel`), and replaces any existing whisper.cpp model in that slot, mirroring
     * what a fresh catalog re-download would do.
     */
    suspend fun importWhisperCppModel(
        context: Context,
        uri: Uri,
        onProgress: (String) -> Unit = {}
    ): TtsDownloadResult = withContext(Dispatchers.IO) {
        val rawName = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "whisper-model.bin"
        val safeName = rawName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifBlank { "whisper-model.bin" }
        if (!safeName.endsWith(".bin", ignoreCase = true) && !safeName.endsWith(".gguf", ignoreCase = true)) {
            return@withContext TtsDownloadResult.Failed("whisper.cpp models must be a .bin (ggml) or .gguf file.")
        }
        if (!ModelFileSniffer.looksLikeWhisperContainer(context, uri)) {
            return@withContext TtsDownloadResult.Failed("This doesn't look like a valid whisper.cpp model file (missing ggml/GGUF header).")
        }
        if (safeName.endsWith(".gguf", ignoreCase = true)) {
            // GGUF is also llama.cpp's language-model container — architecture disambiguates a
            // whisper GGUF from, say, a chat model someone picked here by mistake. A null
            // (unparseable/absent architecture key) is let through rather than rejected, since
            // that's a parser-coverage gap, not evidence the file is wrong.
            val architecture = ModelFileSniffer.ggufArchitecture(context, uri)
            if (architecture != null && architecture != "whisper") {
                return@withContext TtsDownloadResult.Failed(
                    "This GGUF file is a \"$architecture\" model, not whisper — import it from the matching option instead."
                )
            }
        }

        val job = JobRecord(type = JobType.TTS_MODEL_DOWNLOAD, label = "Whisper (imported)", state = JobState.RUNNING)
        jobDao.upsert(job)
        onProgress("Copying ${safeName}…")
        val voiceDir = File(context.filesDir, "stt_models/${WhisperCppSttEngine.ENGINE.lowercase()}_${WhisperCppSttEngine.MODEL_LANGUAGE_KEY}").apply { mkdirs() }
        val dest = File(voiceDir, safeName)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesCopied = 0L
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open selected file")
            input.use { src ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 20)
                    while (true) {
                        val read = src.read(buffer)
                        if (read == -1) break
                        if (bytesCopied > InputLimits.MAX_ADAPTER_BYTES - read) {
                            throw IOException("Whisper model exceeds the 4 GB limit")
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesCopied += read
                        onProgress("Copying… ${bytesCopied / (1024 * 1024)} MB")
                        jobDao.upsert(job.copy(detail = "Copying… ${bytesCopied / (1024 * 1024)} MB", updatedAt = System.currentTimeMillis()))
                    }
                }
            }
            if (bytesCopied == 0L) throw IOException("Selected file is empty")
            // Only one whisper.cpp model is ever "the" model for this language slot — drop any
            // other file left in the directory (a previous local import or catalog download) now
            // that the new one has copied successfully, so WhisperCppSttEngine's "largest file in
            // the directory" lookup can't pick up a stale leftover.
            voiceDir.listFiles()?.forEach { if (it != dest) it.delete() }
            val hash = digest.digest().joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
            // Reuse the existing (engine, language) row's id, if one exists (a prior catalog
            // download or import at this same slot), instead of always minting a fresh UUID —
            // TtsVoiceModel.id-keyed upsert() only replaces an exact id match, so a new id here
            // would leave the old row behind as a stale duplicate that other (engine, language)
            // keyed lookups (installedVoiceUi, WhisperCppSttEngine.ensureLoadedLocked) could
            // resolve to instead of this fresh import.
            val existingId = voiceModelDao.getByEngine(WhisperCppSttEngine.ENGINE, WhisperCppSttEngine.MODEL_LANGUAGE_KEY)?.id
            val model = TtsVoiceModel(
                id = existingId ?: java.util.UUID.randomUUID().toString(),
                engine = WhisperCppSttEngine.ENGINE,
                language = WhisperCppSttEngine.MODEL_LANGUAGE_KEY,
                filePath = voiceDir.absolutePath,
                fileSizeBytes = bytesCopied,
                sha256 = hash
            )
            voiceModelDao.upsert(model)
            jobDao.upsert(job.copy(state = JobState.COMPLETED, progress = 100, updatedAt = System.currentTimeMillis()))
            TtsDownloadResult.Success(model)
        } catch (cancelled: CancellationException) {
            dest.delete()
            jobDao.upsert(job.copy(state = JobState.CANCELLED, detail = "Stopped", updatedAt = System.currentTimeMillis()))
            throw cancelled
        } catch (t: Throwable) {
            com.vervan.chat.system.rethrowCancellation(t)
            dest.delete()
            jobDao.upsert(job.copy(state = JobState.FAILED, detail = t.message ?: "Import failed", updatedAt = System.currentTimeMillis()))
            TtsDownloadResult.Failed(t.message ?: "Import failed")
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
        }
        return null
    }

    /** Extracts a `.tar.bz2` into [destDir], stripping the archive's single top-level directory
     * component (e.g. `vits-piper-en_US-lessac-medium/model.onnx` -> `model.onnx`) so nested
     * voice files land flat where [PiperTtsEngine]/[KokoroTtsEngine] expect them. */
    private fun extractTarBz2(archiveFile: File, destDir: File) {
        val canonicalDest = destDir.canonicalFile
        BZip2CompressorInputStream(archiveFile.inputStream().buffered()).use { bz2 ->
            TarArchiveInputStream(bz2).use { tar ->
                var entryCount = 0
                var extractedBytes = 0L
                var entry = tar.nextEntry
                while (entry != null) {
                    if (++entryCount > InputLimits.MAX_TTS_ARCHIVE_ENTRIES) throw IOException("TTS archive contains too many files")
                    val relative = entry.name.substringAfter('/', missingDelimiterValue = entry.name)
                    if (relative.isNotBlank() && !entry.isDirectory) {
                        // Resolve and validate before mkdirs: creating the parent first let a
                        // traversal entry create directories outside the voice root even though
                        // the subsequent file write was skipped.
                        val outFile = File(canonicalDest, relative).canonicalFile
                        if (!outFile.path.startsWith(canonicalDest.path + File.separator)) {
                            throw IOException("TTS archive contains an unsafe path")
                        }
                        outFile.parentFile?.mkdirs()
                        if (entry.size > InputLimits.MAX_TTS_EXTRACTED_BYTES - extractedBytes) {
                            throw IOException("Extracted TTS files exceed the 2 GB limit")
                        }
                        val remaining = InputLimits.MAX_TTS_EXTRACTED_BYTES - extractedBytes
                        val copied = outFile.outputStream().use { out -> tar.copyToLimited(out, remaining) }
                        extractedBytes += copied
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }

}

/** The one voice still offered outside the real model-download system — see the class doc above
 * for why Kokoro stays here. filename is best-effort against sherpa-onnx's release
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
