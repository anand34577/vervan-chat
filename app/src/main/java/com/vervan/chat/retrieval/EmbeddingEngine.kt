package com.vervan.chat.retrieval

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

enum class EmbeddingBackend { GPU, CPU }

/**
 * Wraps a single loaded EmbeddingGemma (or compatible) text-embedding model. One instance =
 * one loaded model, mirroring [com.vervan.chat.llm.LlmEngine]'s lifecycle.
 *
 * Two on-device runtimes are supported, auto-selected by what the file actually is:
 * - A MediaPipe **Task Bundle** (zip container with bundled tokenizer/metadata) loads via
 *   [TextEmbedder] — MediaPipe's own runtime.
 * - A bare TFLite graph exported straight from a converter (this app's primary target,
 *   `litert-community/…` style releases) has no bundled tokenizer at all, so it loads via
 *   [RawTfliteEmbedder] instead, using a required companion SentencePiece `tokenizer.model`
 *   file imported alongside it.
 */
class EmbeddingEngine(private val context: Context) {

    private var mediaPipeEmbedder: TextEmbedder? = null
    private var rawEmbedder: RawTfliteEmbedder? = null
    private val lock = Any()
    var loadedModelPath: String? = null
        private set
    var activeBackend: EmbeddingBackend = EmbeddingBackend.CPU
        private set

    /** [tokenizerPath] is only required (and only used) for a raw, non-Task-Bundle graph. */
    fun load(modelPath: String, tokenizerPath: String? = null) {
        synchronized(lock) {
            closeLocked()
            if (isLikelyTaskBundle(modelPath)) {
                loadMediaPipe(modelPath)
            } else {
                if (tokenizerPath.isNullOrBlank()) {
                    throw IllegalArgumentException(
                        "'${File(modelPath).name}' is a bare TFLite graph (no bundled tokenizer), not a " +
                            "MediaPipe Task Bundle — it needs its companion SentencePiece tokenizer file " +
                            "(e.g. tokenizer.model) imported alongside it before it can be loaded."
                    )
                }
                rawEmbedder = RawTfliteEmbedder(context, modelPath, tokenizerPath)
                activeBackend = rawEmbedder!!.backend
            }
            loadedModelPath = modelPath
        }
    }

    private fun loadMediaPipe(modelPath: String) {
        // GPU first, CPU fallback — same ladder LlmEngine uses for generation, so a
        // supported-but-currently-unavailable GPU delegate doesn't hard-fail the load.
        var lastError: Throwable? = null
        for (delegate in listOf(Delegate.GPU, Delegate.CPU)) {
            try {
                val options = TextEmbedderOptions.builder().setBaseOptions(
                    com.google.mediapipe.tasks.core.BaseOptions.builder()
                        .setModelAssetPath(modelPath)
                        .setDelegate(delegate)
                        .build()
                ).build()
                mediaPipeEmbedder = TextEmbedder.createFromOptions(context, options)
                activeBackend = if (delegate == Delegate.GPU) EmbeddingBackend.GPU else EmbeddingBackend.CPU
                Log.i(TAG, "loadMediaPipe() SUCCESS: ${File(modelPath).name} on $activeBackend")
                return
            } catch (e: Throwable) {
                lastError = e
                Log.w(TAG, "loadMediaPipe() FAILED on $delegate for ${File(modelPath).name}: ${e.message}", e)
            }
        }
        throw IllegalStateException("Could not load '${File(modelPath).name}': ${lastError?.message}", lastError)
    }

    val isLoaded: Boolean get() = synchronized(lock) { mediaPipeEmbedder != null || rawEmbedder != null }

    /** [isQuery] and [title] only affect [RawTfliteEmbedder] (see its doc comment on why) — a
     * MediaPipe Task Bundle embeds with whatever preprocessing it was exported with. */
    suspend fun embed(text: String, isQuery: Boolean = false, title: String? = null): FloatArray? = withContext(Dispatchers.Default) {
        synchronized(lock) {
            try {
                mediaPipeEmbedder?.let { return@withContext it.embed(text).embeddingResult().embeddings().firstOrNull()?.floatEmbedding() }
                rawEmbedder?.let { return@withContext it.embed(text, isQuery, title) }
                null
            } catch (ex: Exception) {
                Log.w(TAG, "Embedding failed", ex)
                null
            }
        }
    }

    /** Real token count under the loaded raw model's SentencePiece vocab, or null when no raw
     * model is loaded (a MediaPipe Task Bundle has no exposed tokenizer to count with — callers
     * fall back to a word-count proxy in that case). */
    fun countTokens(text: String): Int? = synchronized(lock) { rawEmbedder?.tokenCount(text) }

    fun close() = synchronized(lock) {
        closeLocked()
    }

    private fun closeLocked() {
        mediaPipeEmbedder?.close()
        mediaPipeEmbedder = null
        rawEmbedder?.close()
        rawEmbedder = null
        loadedModelPath = null
    }

    companion object {
        private const val TAG = "EmbeddingEngine"

        /** MediaPipe Task Bundles are zip archives (local-file-header signature `PK\3\4`); a
         * bare TFLite graph is a flatbuffer and starts with its own 4-byte identifier instead. */
        fun isLikelyTaskBundle(modelPath: String): Boolean {
            return try {
                RandomAccessFile(modelPath, "r").use { raf ->
                    val header = ByteArray(4)
                    if (raf.read(header) != 4) return false
                    header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
                        header[2] == 3.toByte() && header[3] == 4.toByte()
                }
            } catch (e: Exception) {
                Log.w(TAG, "isLikelyTaskBundle() couldn't read $modelPath", e)
                true // don't block on a read hiccup; the real load will surface any issue
            }
        }

        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f
            var dot = 0f; var normA = 0f; var normB = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            if (normA == 0f || normB == 0f) return 0f
            return dot / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())).toFloat()
        }
    }
}
