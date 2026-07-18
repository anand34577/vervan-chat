package com.vervan.chat.retrieval

import android.content.Context
import android.util.Log
import com.vervan.chat.retrieval.tokenizer.SentencePieceTokenizer
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File

/**
 * Loads a bare (non-Task-Bundle) TFLite embedding graph directly via the plain TFLite
 * Interpreter, with its own SentencePiece tokenizer file (see [SentencePieceTokenizer]) —
 * this is what MediaPipe's [com.google.mediapipe.tasks.text.textembedder.TextEmbedder] can't
 * load, since it only accepts Task Bundles with embedded tokenizer metadata.
 *
 * ponytail: this model's exported graph has no named input/output tensors (confirmed by
 * inspecting the raw flatbuffer — no "input_ids"/"attention_mask" strings anywhere in it), so
 * there's no way to bind by name. Wiring follows the near-universal encoder-embedding export
 * convention — input 0 = token ids, input 1 = attention mask, optional input 2 = token type
 * ids (zeros), last output = the final pooled embedding — logged in full at load time so a
 * wrong guess is a one-line fix instead of a silent wrong answer.
 */
class RawTfliteEmbedder(context: Context, modelPath: String, tokenizerModelPath: String) : AutoCloseable {

    val backend: EmbeddingBackend
    private val interpreter: Interpreter
    private val tokenizer = SentencePieceTokenizer(File(tokenizerModelPath).readBytes())
    private val seqLen: Int
    private val inputCount: Int
    private val outputIndex: Int
    private val embeddingDim: Int

    init {
        interpreter = Interpreter(File(modelPath), Interpreter.Options())
        backend = EmbeddingBackend.CPU

        inputCount = interpreter.inputTensorCount
        for (i in 0 until inputCount) {
            val t = interpreter.getInputTensor(i)
            Log.i(TAG, "load() input[$i]: shape=${t.shape().toList()} dtype=${t.dataType()}")
        }
        val outputCount = interpreter.outputTensorCount
        for (i in 0 until outputCount) {
            val t = interpreter.getOutputTensor(i)
            Log.i(TAG, "load() output[$i]: shape=${t.shape().toList()} dtype=${t.dataType()}")
        }
        seqLen = interpreter.getInputTensor(0).shape().last()
        outputIndex = outputCount - 1
        embeddingDim = interpreter.getOutputTensor(outputIndex).shape().last()
        Log.i(TAG, "load() SUCCESS: ${File(modelPath).name} on $backend, seqLen=$seqLen, embeddingDim=$embeddingDim, inputs=$inputCount")
    }

    /**
     * EmbeddingGemma is an asymmetric dual-encoder: query and document text must be embedded
     * with different task prefixes (per Google's model card) or the two vector spaces aren't
     * actually comparable — cosine similarity comes back weak/noisy even for an obviously
     * relevant passage. Raw exports (this class's target) don't bake this in like a Task
     * Bundle's bundled preprocessing might, so it has to happen here.
     */
    fun embed(text: String, isQuery: Boolean = false, title: String? = null): FloatArray {
        val prefixed = if (isQuery) "task: search result | query: $text" else "title: ${title?.takeIf { it.isNotBlank() } ?: "none"} | text: $text"
        val ids = tokenizer.encode(prefixed)
        if (ids.size > seqLen) {
            // Silent truncation here means the tail of whatever text the caller is embedding
            // (often a document chunk shown to the user as a cited source) never reaches the
            // model at all — logged so a chunk that's still oversized despite Chunker's own
            // splitting is diagnosable instead of just quietly under-retrieving.
            Log.w(TAG, "embed() truncating ${ids.size} tokens to seqLen=$seqLen (isQuery=$isQuery)")
        }
        val n = minOf(ids.size, seqLen)
        val idsBuf = IntArray(seqLen)
        val maskBuf = IntArray(seqLen)
        for (i in 0 until n) {
            idsBuf[i] = ids[i]
            maskBuf[i] = 1
        }

        val inputs = arrayOfNulls<Any>(inputCount)
        inputs[0] = asTensorArray(0, idsBuf)
        if (inputCount >= 2) inputs[1] = asTensorArray(1, maskBuf)
        if (inputCount >= 3) inputs[2] = asTensorArray(2, IntArray(seqLen))

        val outputBuffer = Array(1) { FloatArray(embeddingDim) }
        val outputs = HashMap<Int, Any>()
        outputs[outputIndex] = outputBuffer
        interpreter.runForMultipleInputsOutputs(inputs, outputs)
        return outputBuffer[0]
    }

    /** Matches the tensor's actual declared dtype (int32/int64/float32) instead of assuming
     * int32 everywhere — a mismatch here throws instead of quietly running on garbage data. */
    private fun asTensorArray(tensorIndex: Int, values: IntArray): Any {
        return when (interpreter.getInputTensor(tensorIndex).dataType()) {
            DataType.INT64 -> arrayOf(LongArray(values.size) { values[it].toLong() })
            DataType.FLOAT32 -> arrayOf(FloatArray(values.size) { values[it].toFloat() })
            else -> arrayOf(values)
        }
    }

    /** Token count under this model's actual SentencePiece vocab — used by [Chunker] to size
     * chunks against this model's real window instead of a word-count proxy. Deliberately not
     * the LLM's own tokenizer: EmbeddingGemma ships its own SentencePiece vocab, separate from
     * whatever tokenizer the generation model uses. */
    fun tokenCount(text: String): Int = tokenizer.encode(text).size

    override fun close() {
        interpreter.close()
    }

    companion object {
        private const val TAG = "RawTfliteEmbedder"
    }
}
