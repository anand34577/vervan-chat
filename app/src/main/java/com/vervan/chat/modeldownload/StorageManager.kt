package com.vervan.chat.modeldownload

import android.content.Context
import com.vervan.chat.data.db.entities.ModelErrorCode
import java.io.File

/** Staging/installed directory layout and free-space accounting for the model downloader.
 * Staging files are never exposed to a loader — only [StorageManager.finalizeIntoInstalled]
 * (an atomic rename where possible) moves files where [com.vervan.chat.llm.LlmEngine]/
 * [com.vervan.chat.retrieval.EmbeddingEngine] can see them, and only after validation passes. */
class StorageManager(context: Context) {
    private val appContext = context.applicationContext

    val stagingRoot: File get() = File(appContext.filesDir, "models/downloads").apply { mkdirs() }

    fun stagingDirFor(packageId: String): File = File(stagingRoot, packageId).apply { mkdirs() }

    fun partFileFor(stagingDir: File, fileName: String): File = File(stagingDir, "$fileName.part")

    /** [remainingDownloadBytes] + a flat overhead margin for temp/copy overhead during import
     * (ModelImportManager streams a second copy into its own storage — see
     * ModelInstallationRepository's import step) + a fixed safety margin, per Storage
     * can still be exhausted mid-write after this check passes; callers must handle that as
     * [ModelErrorCode.STORAGE_WRITE_FAILED], not just preflight. */
    fun checkAvailable(remainingDownloadBytes: Long, requiresImportCopy: Boolean) {
        val required = remainingDownloadBytes +
            (if (requiresImportCopy) remainingDownloadBytes else 0L) +
            SAFETY_MARGIN_BYTES
        val usable = runCatching { appContext.filesDir.usableSpace }.getOrDefault(-1L)
        if (usable < 0) throw ModelDownloadException(ModelErrorCode.STORAGE_UNAVAILABLE, "Could not determine free storage")
        if (usable < required) {
            throw ModelDownloadException(
                ModelErrorCode.INSUFFICIENT_STORAGE,
                "Required: ${formatBytes(required)}, available: ${formatBytes(usable)}"
            )
        }
    }

    fun deleteStaging(packageId: String) {
        stagingDirFor(packageId).deleteRecursively()
    }

    companion object {
        private const val SAFETY_MARGIN_BYTES = 256L * 1024 * 1024

        fun formatBytes(bytes: Long): String {
            val gib = bytes / (1024.0 * 1024 * 1024)
            if (gib >= 1) return "%.2f GiB".format(gib)
            val mib = bytes / (1024.0 * 1024)
            if (mib >= 1) return "%.1f MiB".format(mib)
            val kib = bytes / 1024.0
            return if (kib >= 1) "%.0f KiB".format(kib) else "$bytes B"
        }
    }
}
