package com.vervan.chat.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vervan.chat.BuildConfig
import com.vervan.chat.data.db.dao.ModelDao
import com.vervan.chat.data.db.entities.ModelEngine
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
 * validation here is "does it look like a model file" (extension + non-empty +
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
        if (name.endsWith(".gguf", ignoreCase = true) && !BuildConfig.LLAMA_CPP_AVAILABLE) {
            return@withContext ImportResult.Rejected(
                "This app build does not include the llama.cpp Android/Vulkan runtime required for GGUF models."
            )
        }
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
        // Resolve the input stream outside the `?.use` so a null return (cloud providers, revoked
        // SAF grants, content:// schemes we can't read) still cleans up the empty destination file
        // we just allocated on disk — otherwise the leak accumulates one model-sized file per
        // failed import attempt, with no user-visible signal.
        val input = context.contentResolver.openInputStream(uri)
        if (input == null) {
            dest.delete()
            return@withContext ImportResult.Rejected("Could not open selected file")
        }
        try {
            input.use { src ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 20)
                    while (true) {
                        val read = src.read(buffer)
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
            }
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
            role = role,
            engine = if (name.endsWith(".gguf", ignoreCase = true)) ModelEngine.LLAMA_CPP else ModelEngine.LITERT_LM
        )
        modelDao.upsert(model)
        ImportResult.Success(model)
    }

    /** GGUF vision models need a second file, the mtmd projector — unlike the embedding
     * model+tokenizer pair, both files share the same .gguf extension, so there's no way to
     * auto-tell them apart by name; the caller (import dialog) asks for them via two distinct
     * pickers instead. [mmprojUri] is optional — omit for a text-only GGUF model. */
    suspend fun importLlamaCppModel(
        modelUri: Uri,
        mmprojUri: Uri? = null,
        onProgress: (String) -> Unit = {}
    ): ImportResult = withContext(Dispatchers.IO) {
        if (!BuildConfig.LLAMA_CPP_AVAILABLE) {
            return@withContext ImportResult.Rejected("This app build does not include GGUF/llama.cpp support.")
        }
        val modelName = safeFileName(queryDisplayName(modelUri) ?: modelUri.lastPathSegment ?: "model")
        if (!modelName.endsWith(".gguf", ignoreCase = true)) {
            return@withContext ImportResult.Rejected("llama.cpp generation models must be GGUF files.")
        }
        if (mmprojUri != null && !BuildConfig.LLAMA_CPP_VISION_AVAILABLE) {
            return@withContext ImportResult.Rejected(
                "This llama.cpp build has no mtmd vision support. Rebuild llama.cpp with LLAMA_BUILD_MTMD=ON first."
            )
        }
        val modelResult = import(modelUri, ModelRole.GENERATION, onProgress)
        val model = when (modelResult) {
            is ImportResult.Success -> modelResult.model
            is ImportResult.Duplicate -> modelResult.existing
            is ImportResult.Rejected -> return@withContext modelResult
        }
        if (model.role != ModelRole.GENERATION || model.engine != ModelEngine.LLAMA_CPP) {
            return@withContext ImportResult.Rejected("This file was already imported for a different runtime or role.")
        }
        if (mmprojUri == null) return@withContext ImportResult.Success(model)

        onProgress("Copying vision projector…")
        val mmprojName = safeFileName(queryDisplayName(mmprojUri) ?: mmprojUri.lastPathSegment ?: "mmproj.gguf")
        if (!mmprojName.endsWith(".gguf", ignoreCase = true)) {
            return@withContext ImportResult.Rejected("Vision projectors must be GGUF files.")
        }
        val mmprojDest = File(modelsDir, "${System.currentTimeMillis()}_$mmprojName")
        val mmprojInput = context.contentResolver.openInputStream(mmprojUri)
        if (mmprojInput == null) {
            mmprojDest.delete()
            return@withContext ImportResult.Rejected("Could not open selected projector file")
        }
        try {
            mmprojInput.use { src ->
                mmprojDest.outputStream().use { output -> src.copyTo(output) }
            }
        } catch (e: java.io.IOException) {
            mmprojDest.delete()
            return@withContext ImportResult.Rejected("Ran out of storage while copying the projector (${e.message})")
        }
        if (mmprojDest.length() == 0L) {
            mmprojDest.delete()
            return@withContext ImportResult.Rejected("Selected projector file is empty")
        }
        val updated = model.copy(mmprojPath = mmprojDest.absolutePath, supportsVision = true)
        modelDao.upsert(updated)
        model.mmprojPath?.takeIf { it != updated.mmprojPath }?.let { File(it).delete() }
        ImportResult.Success(updated)
    }

    /** Attaches (or replaces) a LoRA adapter on an already-imported llama.cpp model, copying it
     * into internal storage the same way [importLlamaCppModel]'s mmproj does — a picked
     * `content://` `Uri` isn't a real filesystem path the native loader can `fopen`, and isn't
     * guaranteed to stay readable after this process exits. */
    suspend fun importLoraAdapter(model: ModelInfo, loraUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        if (model.engine != ModelEngine.LLAMA_CPP) {
            return@withContext ImportResult.Rejected("LoRA adapters can only be attached to llama.cpp models.")
        }
        val loraName = safeFileName(queryDisplayName(loraUri) ?: loraUri.lastPathSegment ?: "lora.gguf")
        if (!loraName.endsWith(".gguf", ignoreCase = true)) {
            return@withContext ImportResult.Rejected("LoRA adapters must be GGUF files.")
        }
        val loraDest = File(modelsDir, "${System.currentTimeMillis()}_$loraName")
        val loraInput = context.contentResolver.openInputStream(loraUri)
        if (loraInput == null) {
            loraDest.delete()
            return@withContext ImportResult.Rejected("Could not open selected LoRA file")
        }
        try {
            loraInput.use { src ->
                loraDest.outputStream().use { output -> src.copyTo(output) }
            }
        } catch (e: java.io.IOException) {
            loraDest.delete()
            return@withContext ImportResult.Rejected("Ran out of storage while copying the LoRA adapter (${e.message})")
        }
        if (loraDest.length() == 0L) {
            loraDest.delete()
            return@withContext ImportResult.Rejected("Selected LoRA file is empty")
        }
        val updated = model.copy(loraPath = loraDest.absolutePath)
        modelDao.upsert(updated)
        model.loraPath?.takeIf { it != updated.loraPath }?.let { File(it).delete() }
        ImportResult.Success(updated)
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
        val tokenizerInput = context.contentResolver.openInputStream(tokenizerUri)
        if (tokenizerInput == null) {
            tokenizerDest.delete()
            return@withContext ImportResult.Rejected("Could not open selected tokenizer file")
        }
        try {
            tokenizerInput.use { src ->
                tokenizerDest.outputStream().use { output -> src.copyTo(output) }
            }
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
        private val GENERATION_EXTENSIONS = listOf(".task", ".litertlm", ".litert", ".gguf")
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
