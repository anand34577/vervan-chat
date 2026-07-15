package com.vervan.chat.retrieval

import com.vervan.chat.retrieval.tokenizer.SentencePieceTokenizer
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * There's no real `.model` file available to test against (that's the whole reason
 * [SentencePieceTokenizer] exists — this app's real embedding model ships without one), so this
 * hand-builds a tiny synthetic SentencePiece protobuf to exercise the parser + greedy
 * longest-match + byte-fallback + BOS-prepend logic against known-correct output.
 */
class SentencePieceTokenizerTest {

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            val b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v != 0L) out.write(b or 0x80) else { out.write(b); break }
        }
    }

    private fun writeTag(out: ByteArrayOutputStream, field: Int, wireType: Int) = writeVarint(out, ((field shl 3) or wireType).toLong())

    private fun writeFixed32(out: ByteArrayOutputStream, bits: Int) {
        out.write(bits and 0xFF)
        out.write((bits ushr 8) and 0xFF)
        out.write((bits ushr 16) and 0xFF)
        out.write((bits ushr 24) and 0xFF)
    }

    private fun piece(text: String, score: Float, type: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val bytes = text.toByteArray(Charsets.UTF_8)
        writeTag(out, 1, 2); writeVarint(out, bytes.size.toLong()); out.write(bytes)
        writeTag(out, 2, 5); writeFixed32(out, java.lang.Float.floatToIntBits(score))
        writeTag(out, 3, 0); writeVarint(out, type.toLong())
        return out.toByteArray()
    }

    private fun modelProto(pieces: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for (p in pieces) {
            writeTag(out, 1, 2)
            writeVarint(out, p.size.toLong())
            out.write(p)
        }
        return out.toByteArray()
    }

    // Piece ids are assigned by list order, matching real SentencePiece semantics.
    private val vocab = modelProto(
        listOf(
            piece("<unk>", 0f, 2),      // 0
            piece("<s>", 0f, 3),        // 1 (bos)
            piece("</s>", 0f, 3),       // 2 (eos)
            piece("▁", -2.0f, 1),  // 3  "▁"
            piece("▁hello", -1.0f, 1), // 4
            piece("▁world", -1.5f, 1), // 5
            piece("<0xF0>", -5f, 6),    // 6
            piece("<0x9F>", -5f, 6),    // 7
            piece("<0x98>", -5f, 6),    // 8
            piece("<0x80>", -5f, 6)     // 9
        )
    )

    @Test fun prependsBosAndMatchesDummyPrefixAlone() {
        val tok = SentencePieceTokenizer(vocab)
        // Empty input still normalizes to the dummy-prefix "▁" alone, which should match the
        // "▁" piece (id 3) — not fall through to <unk> (id 0).
        assertArrayEquals(intArrayOf(1, 3), tok.encode(""))
    }

    @Test fun greedyLongestMatchPrefersWholeWordPieces() {
        val tok = SentencePieceTokenizer(vocab)
        // normalize("hello world") -> "▁hello▁world" -> greedy longest match should pick the
        // whole-word pieces (ids 4, 5), not the leftover "▁" piece (id 3) char by char.
        assertArrayEquals(intArrayOf(1, 4, 5), tok.encode("hello world"))
    }

    @Test fun byteFallbackForUncoveredCodepoint() {
        val tok = SentencePieceTokenizer(vocab)
        // U+1F600 (an emoji, a surrogate pair in a Java/Kotlin String) isn't in the vocab at
        // all, so it must fall back to the UTF-8 byte tokens (F0 9F 98 80) — this exercises
        // codePointAt-based iteration staying correct across a surrogate pair, not just
        // splitting by char index and corrupting it.
        val emoji = String(Character.toChars(0x1F600))
        assertArrayEquals(intArrayOf(1, 3, 6, 7, 8, 9), tok.encode(emoji))
    }
}
