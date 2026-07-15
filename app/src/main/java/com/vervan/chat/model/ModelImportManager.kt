package com.vervan.chat.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vervan.chat.data.db.dao.ModelDao
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ImportResult {
    data class Success(val model: ModelInfo) : ImportResult()
    data class Duplicate(val existing: ModelInfo) : ImportResult()
    data class Rejected(val reason: String) : ImportResult()
}

/**
 * Copies a user-picked model file (via SAF) into app-managed storage and registers it.
 * ponytail: validation here is "does it look like a model file" (extension + non-empty +
 * enough free space) — real architecture/tokenizer/shard validation happens when
 * LlmEngine.load() actually tries to initialize it. Add a real pre-flight parser when
 * bad imports become a real support burden.
 */
class ModelImportManager(private val context: Context, private val modelDao: ModelDao) {

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    suspend fun import(
        uri: Uri,
        role: ModelRole = ModelRole.GENERATION,
        onProgress: (String) -> Unit = {}
    ): ImportResult = withContext(Dispatchers.IO) {
        val name = safeFileName(queryDisplayName(uri) ?: uri.lastPathSegment ?: "model")
        val supportedExtensions = supportedExtensions(role)
        if (!supportedExtensions.any { name.endsWith(it, ignoreCase = true) }) {
            return@withContext ImportResult.Rejected(
                "Unsupported file type \"${name.substringAfterLast('.', "(no extension)")}\". " +
                    "Expected one of: ${supportedExtensions.joinToString()}"
            )
        }

        val sourceSize = queryFileSize(uri)
        val freeBytes = modelsDir.usableSpace
        if (sourceSize != null && freeBytes < sourceSize + STORAGE_SAFETY_MARGIN_BYTES) {
            return@withContext ImportResult.Rejected(
                "Not enough storage — this model needs ~${formatBytes(sourceSize)}, only ${formatBytes(freeBytes)} free"
            )
        }

        val dest = File(modelsDir, "${System.currentTimeMillis()}_$name")
        val digest = MessageDigest.getInstance("SHA-256")
        var bytesCopied = 0L
        var lastProgressBytes = 0L
        onProgress("Copying ${name.substringBeforeLast('.')}…")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 20)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesCopied += read
                        if (bytesCopied - lastProgressBytes >= PROGRESS_STEP_BYTES) {
                            lastProgressBytes = bytesCopied
                            onProgress("Copying model… ${formatBytes(bytesCopied)}${sourceSize?.let { " / ${formatBytes(it)}" }.orEmpty()}")
                        }
                    }
                }
            } ?: return@withContext ImportResult.Rejected("Could not open selected file")
        } catch (e: java.io.IOException) {
            dest.delete()
            return@withContext ImportResult.Rejected("Ran out of storage while copying the model (${e.message})")
        }

        if (bytesCopied == 0L) {
            dest.delete()
            return@withContext ImportResult.Rejected("Selected file is empty")
        }

        onProgress("Registering model…")
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        modelDao.findByHash(hash)?.let { existing ->
            dest.delete()
            return@withContext ImportResult.Duplicate(existing)
        }

        val model = ModelInfo(
            displayName = name.substringBeforeLast('.'),
            filePath = dest.absolutePath,
            fileSizeBytes = bytesCopied,
            sha256 = hash,
            role = role
        )
        modelDao.upsert(model)
        ImportResult.Success(model)
    }

    /**
     * Embedding models always need two files: the model itself, and its SentencePiece
     * tokenizer file — a bare TFLite graph (this app's primary embedding target) has no
     * tokenizer bundled in, unlike a MediaPipe Task Bundle (see EmbeddingEngine). Which of
     * [fileA]/[fileB] is which is worked out by extension rather than requiring the user to
     * pick them in a specific order.
     */
    suspend fun importEmbeddingModel(
        fileA: Uri,
        fileB: Uri,
        onProgress: (String) -> Unit = {}
    ): ImportResult = withContext(Dispatchers.IO) {
        val nameA = safeFileName(queryDisplayName(fileA) ?: fileA.lastPathSegment ?: "file")
        val nameB = safeFileName(queryDisplayName(fileB) ?: fileB.lastPathSegment ?: "file")
        val modelExtensions = supportedExtensions(ModelRole.EMBEDDING)
        val aIsModel = modelExtensions.any { nameA.endsWith(it, ignoreCase = true) }
        val bIsModel = modelExtensions.any { nameB.endsWith(it, ignoreCase = true) }
        val modelUri: Uri
        val tokenizerUri: Uri
        val tokenizerName: String
        when {
            aIsModel && !bIsModel -> { modelUri = fileA; tokenizerUri = fileB; tokenizerName = nameB }
            bIsModel && !aIsModel -> { modelUri = fileB; tokenizerUri = fileA; tokenizerName = nameA }
            else -> return@withContext ImportResult.Rejected(
                "Couldn't tell which file is the model and which is the tokenizer (\"$nameA\" and \"$nameB\") — " +
                    "the model should end in one of ${modelExtensions.joinToString()}, the tokenizer file " +
                    "(e.g. tokenizer.model) shouldn't."
            )
        }

        val modelResult = import(modelUri, ModelRole.EMBEDDING, onProgress)
        val model = when (modelResult) {
            is ImportResult.Success -> modelResult.model
            is ImportResult.Duplicate -> modelResult.existing
            is ImportResult.Rejected -> return@withContext modelResult
        }
        if (model.role != ModelRole.EMBEDDING) {
            return@withContext ImportResult.Rejected("This file was already imported as a generation model.")
        }

        onProgress("Copying tokenizer…")
        val tokenizerDest = File(modelsDir, "${System.currentTimeMillis()}_$tokenizerName")
        try {
            context.contentResolver.openInputStream(tokenizerUri)?.use { input ->
                tokenizerDest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext ImportResult.Rejected("Could not open selected tokenizer file")
        } catch (e: java.io.IOException) {
            tokenizerDest.delete()
            return@withContext ImportResult.Rejected("Ran out of storage while copying the tokenizer (${e.message})")
        }
        if (tokenizerDest.length() == 0L) {
            tokenizerDest.delete()
            return@withContext ImportResult.Rejected("Selected tokenizer file is empty")
        }

        val updated = model.copy(tokenizerPath = tokenizerDest.absolutePath)
        modelDao.upsert(updated)
        ImportResult.Success(updated)
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
        }
        return null
    }

    /** Null when the content provider doesn't report a size — some don't, in which case the
     * pre-flight storage check is skipped and only the mid-copy [java.io.IOException] guard applies. */
    private fun queryFileSize(uri: Uri): Long? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) return cursor.getLong(sizeIndex)
        }
        return null
    }

    private fun safeFileName(value: String): String =
        value.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .ifBlank { "model" }

    companion object {
        private val GENERATION_EXTENSIONS = listOf(".task", ".tflite", ".litertlm", ".litert", ".bin")
        private val EMBEDDING_EXTENSIONS = listOf(".task", ".tflite", ".bin", ".litert")
        fun supportedExtensions(role: ModelRole) =
            if (role == ModelRole.GENERATION) GENERATION_EXTENSIONS else EMBEDDING_EXTENSIONS

        private const val STORAGE_SAFETY_MARGIN_BYTES = 100L * 1024 * 1024 // 100MB headroom
        private const val PROGRESS_STEP_BYTES = 16L * 1024 * 1024
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
