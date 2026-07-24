package com.vervan.chat.store.install

import com.vervan.chat.store.model.ArtifactRole
import java.io.File
import java.io.RandomAccessFile

/**
 * Cheap header inspection run before an artifact is accepted.
 *
 * This is *not* a substitute for the SHA-256 check and does not try to be — a matching hash
 * already proves the bytes are the ones the catalogue signed. The probe exists for the case the
 * hash cannot catch: the catalogue itself being wrong, because a publishing-pipeline mistake
 * pinned the right commit but the wrong path, and hashed whatever was there. A `weights` role
 * pointing at a README would pass every hash check and fail here.
 *
 * ### Deliberate limits
 * Only formats with a real, documented magic number are checked. ONNX is protocol-buffer encoded
 * with no magic bytes, and `.litertlm` has no published container signature, so for those the
 * probe verifies the file is non-trivial and defers to the runtime's own load-time validation
 * rather than inventing a heuristic that would reject valid files. [probe] returning true means
 * "nothing disqualifying found", not "verified loadable" — the honest guarantee, and callers
 * should not read more into it.
 */
class ArtifactFormatProbe {

    fun probe(file: File, role: ArtifactRole): ProbeResult {
        if (!file.isFile) return ProbeResult.Rejected("file is missing")
        if (file.length() == 0L) return ProbeResult.Rejected("file is empty")

        val magic = readMagic(file) ?: return ProbeResult.Rejected("file is too short to identify")

        return when (role) {
            // GGUF is llama.cpp's container for both weights and projectors, and is strictly
            // magic-prefixed — a mismatch here is unambiguous.
            ArtifactRole.WEIGHTS, ArtifactRole.DRAFT_WEIGHTS, ArtifactRole.MULTIMODAL_PROJECTOR ->
                if (magic.startsWithAny(GGUF_MAGIC, GGML_MAGIC, ONNX_LIKELY_PREFIX)) {
                    ProbeResult.Accepted
                } else {
                    // Not every weights artifact is GGUF — sherpa-onnx variants carry ONNX here.
                    // Accept anything that is at least plausibly binary rather than text, so a
                    // README or an HTML error page lands in Rejected.
                    if (looksLikeText(magic)) {
                        ProbeResult.Rejected("weights artifact looks like a text file")
                    } else {
                        ProbeResult.Accepted
                    }
                }

            // whisper.cpp models are ggml containers with their own magic.
            ArtifactRole.ASR_MODEL ->
                if (magic.startsWithAny(GGML_MAGIC, ONNX_LIKELY_PREFIX)) ProbeResult.Accepted
                else if (looksLikeText(magic)) ProbeResult.Rejected("ASR model looks like a text file")
                else ProbeResult.Accepted

            // These are genuinely text: a tokens list, a lexicon, a chat template.
            ArtifactRole.TOKENS, ArtifactRole.LEXICON, ArtifactRole.CHAT_TEMPLATE ->
                ProbeResult.Accepted

            else -> ProbeResult.Accepted
        }
    }

    private fun readMagic(file: File): ByteArray? = try {
        RandomAccessFile(file, "r").use { raf ->
            val buffer = ByteArray(MAGIC_BYTES.coerceAtMost(file.length().toInt()))
            raf.readFully(buffer)
            buffer.takeIf { it.size >= 4 }
        }
    } catch (t: Throwable) {
        null
    }

    /** Detects an HTML error page or a Markdown README served where binary weights were expected —
     * the common shape of a mis-pinned catalogue path. */
    private fun looksLikeText(magic: ByteArray): Boolean {
        val prefix = String(magic, Charsets.US_ASCII).trimStart().lowercase()
        if (TEXT_PREFIXES.any { prefix.startsWith(it) }) return true
        // All-printable-ASCII in the first bytes is a strong signal for a text file; real model
        // containers have non-ASCII bytes in their headers essentially immediately.
        return magic.all { it in 0x09..0x7E }
    }

    private fun ByteArray.startsWithAny(vararg prefixes: ByteArray): Boolean =
        prefixes.any { prefix -> size >= prefix.size && prefix.indices.all { this[it] == prefix[it] } }

    sealed interface ProbeResult {
        data object Accepted : ProbeResult
        data class Rejected(val reason: String) : ProbeResult
    }

    private companion object {
        const val MAGIC_BYTES = 16
        val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46) // "GGUF"
        val GGML_MAGIC = byteArrayOf(0x6C, 0x6D, 0x67, 0x67) // "lmgg" — ggml little-endian
        /** ONNX has no magic number; its protobuf almost always opens with field 1 (ir_version). */
        val ONNX_LIKELY_PREFIX = byteArrayOf(0x08)
        val TEXT_PREFIXES = listOf("<!doctype", "<html", "# ", "version https://git-lfs")
    }
}
