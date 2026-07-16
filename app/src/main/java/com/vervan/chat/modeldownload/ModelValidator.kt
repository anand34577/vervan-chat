package com.vervan.chat.modeldownload

import android.util.Log
import com.google.ai.edge.litertlm.Capabilities
import com.vervan.chat.data.db.entities.ModelErrorCode
import com.vervan.chat.retrieval.tokenizer.SentencePieceTokenizer
import java.io.File
import java.security.MessageDigest
import org.tensorflow.lite.Interpreter

/**
 * Mandatory post-download checks before a package is allowed into the import pipeline — size,
 * checksum (when the catalogue provides one), and a lightweight format-specific open that
 * detects a truncated/corrupt file without fully loading the model for inference. All of this
 * runs against files still in the staging directory; nothing here touches the installed path.
 */
class ModelValidator {

    fun validateFile(file: File, spec: ModelFileSpec) {
        if (!file.isFile || file.length() == 0L) {
            throw ModelDownloadException(ModelErrorCode.INVALID_MODEL_FILE, "${spec.fileName} is missing or empty")
        }
        if (spec.expectedBytes != null && file.length() != spec.expectedBytes) {
            throw ModelDownloadException(
                ModelErrorCode.INVALID_MODEL_FILE,
                "${spec.fileName}: expected ${spec.expectedBytes} bytes, got ${file.length()}"
            )
        }
        if (spec.sha256 != null) {
            val actual = sha256Of(file)
            if (!actual.equals(spec.sha256, ignoreCase = true)) {
                file.delete()
                throw ModelDownloadException(ModelErrorCode.CHECKSUM_MISMATCH, "${spec.fileName} failed checksum verification")
            }
        }
    }

    /** Opens the model's metadata via LiteRT-LM's [Capabilities] API without loading it for
     * inference — the same safe-probe [com.vervan.chat.llm.LlmEngine] already uses to detect
     * MTP support, reused here purely to confirm the file isn't truncated/corrupt. */
    fun validateLitertlm(file: File) {
        try {
            Capabilities(file.absolutePath).use { }
        } catch (t: Throwable) {
            Log.w(TAG, "validateLitertlm() failed for ${file.name}: ${t.message}")
            throw ModelDownloadException(ModelErrorCode.INVALID_MODEL_FILE, "${file.name} could not be opened as a LiteRT model", t)
        }
    }

    /** Opens the TFLite flatbuffer via the plain Interpreter (same API
     * [com.vervan.chat.retrieval.RawTfliteEmbedder] uses to load real embedding graphs) just
     * long enough to confirm it parses and has at least one input/output tensor, then closes it
     * — never runs inference. */
    fun validateTflite(file: File) {
        try {
            Interpreter(file, Interpreter.Options()).use { interpreter ->
                if (interpreter.inputTensorCount < 1 || interpreter.outputTensorCount < 1) {
                    throw ModelDownloadException(ModelErrorCode.INVALID_MODEL_FILE, "${file.name} has no input/output tensors")
                }
            }
        } catch (e: ModelDownloadException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "validateTflite() failed for ${file.name}: ${t.message}")
            throw ModelDownloadException(ModelErrorCode.INVALID_MODEL_FILE, "${file.name} could not be opened as a TFLite model", t)
        }
    }

    /** EmbeddingGemma's bare-TFLite path needs its SentencePiece tokenizer to actually parse,
     * not just exist — mirrors what [com.vervan.chat.retrieval.RawTfliteEmbedder] does at real
     * load time so a corrupt tokenizer fails here instead of surfacing later as a load crash. */
    fun validateSentencePieceTokenizer(file: File) {
        try {
            SentencePieceTokenizer(file.readBytes())
        } catch (t: Throwable) {
            Log.w(TAG, "validateSentencePieceTokenizer() failed for ${file.name}: ${t.message}")
            throw ModelDownloadException(ModelErrorCode.TOKENIZER_MISSING, "${file.name} could not be parsed as a SentencePiece tokenizer", t)
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
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "ModelValidator"
    }
}
