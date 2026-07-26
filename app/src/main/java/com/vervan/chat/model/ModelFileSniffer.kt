package com.vervan.chat.model

import android.content.Context
import android.net.Uri
import java.io.InputStream

/**
 * Best-effort content sniffing for model files picked via SAF, layered on top of the extension
 * checks [ModelImportManager] already does — a renamed file (someone's .gguf saved as .task, or
 * vice versa) would otherwise sail through on extension alone. GGUF is also one container format
 * shared by both llama.cpp language models and whisper.cpp speech models, so magic bytes alone
 * can't tell those apart — [ggufArchitecture] parses the "general.architecture" metadata key to
 * do that.
 *
 * Everything here is best-effort and never throws: any read/parse failure degrades to "unknown"
 * (false/null) rather than blocking an import the pre-existing extension check already allowed
 * through, so a format quirk this (intentionally non-exhaustive) parser doesn't handle can't make
 * importing worse than it was before this existed.
 */
object ModelFileSniffer {
    private val GGUF_MAGIC = byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte())
    // ggml's classic container magic is the little-endian uint32 0x67676d6c ("ggml" is just the
    // hex constant's mnemonic name in ggml.h, not its on-disk byte order) — written out via
    // fwrite on a little-endian target, the actual bytes are 0x6c,0x6d,0x67,0x67, i.e. "lmgg".
    // Confirmed against a real ggml-tiny.bin's first 4 bytes. Checking for literal ASCII "ggml"
    // here rejected every genuine ggml model file.
    private val GGML_MAGIC = byteArrayOf(0x6c, 0x6d, 0x67, 0x67)
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun looksLikeGguf(context: Context, uri: Uri): Boolean = magicMatches(context, uri, GGUF_MAGIC)

    /** whisper.cpp accepts either the classic ggml container or GGUF. */
    fun looksLikeWhisperContainer(context: Context, uri: Uri): Boolean =
        magicMatches(context, uri, GGML_MAGIC) || magicMatches(context, uri, GGUF_MAGIC)

    /** The MediaPipe Task Bundle format (.task) is a documented ZIP container. The newer
     * LiteRT-LM format (.litertlm/.litert) has no published magic number — see
     * ArtifactFormatProbe's same finding — so callers must not sniff those against this. */
    fun looksLikeZipBundle(context: Context, uri: Uri): Boolean = magicMatches(context, uri, ZIP_MAGIC)

    private fun magicMatches(context: Context, uri: Uri, magic: ByteArray): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(magic.size)
            val read = input.read(header)
            read == magic.size && header.contentEquals(magic)
        } ?: false
    }.getOrDefault(false)

    /**
     * Reads a GGUF file's "general.architecture" metadata string (e.g. "llama", "qwen2",
     * "whisper") without loading the file — GGUF is magic + version + tensor_count +
     * metadata_kv_count, then that many key/typed-value pairs; tensor data (the bulk of the
     * file) comes after all metadata, so this rarely needs to read more than a few hundred KB.
     * Returns null if the file isn't GGUF, the key isn't present, or anything here doesn't match
     * this parser's understanding of the format — never throws.
     */
    fun ggufArchitecture(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { parseGgufArchitecture(it) }
    }.getOrNull()

    private fun parseGgufArchitecture(raw: InputStream): String? {
        val input = LimitedLeReader(raw, MAX_METADATA_SCAN_BYTES)
        if (!input.readExact(4).contentEquals(GGUF_MAGIC)) return null
        input.readU32() // version, unused
        val tensorCount = input.readU64()
        val kvCount = input.readU64()
        if (tensorCount < 0 || kvCount !in 0..MAX_KV_ENTRIES) return null
        repeat(kvCount.toInt()) {
            val key = input.readGgufString()
            val type = input.readU32()
            if (key == "general.architecture" && type == GGUF_TYPE_STRING.toLong()) {
                return input.readGgufString()
            }
            input.skipValue(type)
        }
        return null
    }

    private const val MAX_METADATA_SCAN_BYTES = 16L * 1024 * 1024
    private const val MAX_KV_ENTRIES = 100_000L
    private const val MAX_STRING_BYTES = 16L * 1024 * 1024
    private const val GGUF_TYPE_UINT8 = 0
    private const val GGUF_TYPE_INT8 = 1
    private const val GGUF_TYPE_UINT16 = 2
    private const val GGUF_TYPE_INT16 = 3
    private const val GGUF_TYPE_UINT32 = 4
    private const val GGUF_TYPE_INT32 = 5
    private const val GGUF_TYPE_FLOAT32 = 6
    private const val GGUF_TYPE_BOOL = 7
    private const val GGUF_TYPE_STRING = 8
    private const val GGUF_TYPE_ARRAY = 9
    private const val GGUF_TYPE_UINT64 = 10
    private const val GGUF_TYPE_INT64 = 11
    private const val GGUF_TYPE_FLOAT64 = 12

    /** Little-endian primitive reader over an [InputStream], with a hard byte-count cap so a
     * malformed or huge metadata section can't turn a quick sniff into scanning gigabytes. */
    private class LimitedLeReader(private val src: InputStream, private val limit: Long) {
        private var consumed = 0L

        fun readExact(n: Int): ByteArray {
            check(consumed + n <= limit) { "metadata scan limit exceeded" }
            val buf = ByteArray(n)
            var off = 0
            while (off < n) {
                val r = src.read(buf, off, n - off)
                check(r != -1) { "unexpected EOF" }
                off += r
            }
            consumed += n
            return buf
        }

        fun skip(n: Long) {
            check(consumed + n <= limit) { "metadata scan limit exceeded" }
            var remaining = n
            while (remaining > 0) {
                val s = src.skip(remaining)
                if (s <= 0) {
                    // Some stream implementations no-op skip() near EOF — fall back to a
                    // byte-at-a-time discard rather than spinning forever.
                    check(src.read() != -1) { "unexpected EOF" }
                    remaining -= 1
                } else {
                    remaining -= s
                }
            }
            consumed += n
        }

        fun readU32(): Long = readExact(4).let { b ->
            (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
                ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
        }

        fun readU64(): Long {
            val b = readExact(8)
            var v = 0L
            for (i in 0 until 8) v = v or ((b[i].toLong() and 0xFF) shl (8 * i))
            return v
        }

        fun readGgufString(): String {
            val len = readU64()
            check(len in 0..MAX_STRING_BYTES) { "implausible gguf string length" }
            return String(readExact(len.toInt()), Charsets.UTF_8)
        }

        fun skipValue(type: Long) {
            when (type.toInt()) {
                GGUF_TYPE_UINT8, GGUF_TYPE_INT8, GGUF_TYPE_BOOL -> skip(1)
                GGUF_TYPE_UINT16, GGUF_TYPE_INT16 -> skip(2)
                GGUF_TYPE_UINT32, GGUF_TYPE_INT32, GGUF_TYPE_FLOAT32 -> skip(4)
                GGUF_TYPE_UINT64, GGUF_TYPE_INT64, GGUF_TYPE_FLOAT64 -> skip(8)
                GGUF_TYPE_STRING -> readGgufString()
                GGUF_TYPE_ARRAY -> {
                    val elemType = readU32()
                    val count = readU64()
                    check(count in 0..MAX_KV_ENTRIES) { "implausible gguf array length" }
                    repeat(count.toInt()) { skipValue(elemType) }
                }
                else -> error("unknown gguf value type $type")
            }
        }
    }
}
