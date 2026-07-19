package com.vervan.chat.retrieval.tokenizer

import java.text.Normalizer

/**
 * Minimal SentencePiece unigram-model tokenizer, for embedding models distributed as a raw
 * TFLite graph (no bundled tokenizer) alongside a companion `.model`/`.spm` file — e.g.
 * `litert-community/embeddinggemma-300m`'s `.tflite` + `tokenizer.model` pair. There's no
 * SentencePiece Java binding available offline for this project, so this parses just the
 * pieces this app needs directly out of the `.model` file's protobuf bytes (a `ModelProto`
 * with a `repeated SentencePiece pieces = 1` field — see sentencepiece_model.proto) rather
 * than pulling in a full protobuf runtime for one message type.
 *
 * segmentation is greedy longest-prefix-match with byte-fallback, not the reference
 * implementation's optimal Viterbi unigram search. SentencePiece vocabs are built so greedy
 * longest-match closely tracks the optimal segmentation for ordinary text, and what matters
 * for embedding quality is using the model's real vocab ids consistently between indexing and
 * querying — not bit-exact parity with Google's tokenizer. Upgrade to full Viterbi if retrieval
 * quality on real content turns out to need it.
 */
class SentencePieceTokenizer(modelBytes: ByteArray) {

    private data class Piece(val id: Int, val score: Float, val type: Int)

    private val pieceIndex = HashMap<String, Piece>()
    private var maxPieceLen = 1
    var unkId: Int = 0
        private set
    var bosId: Int? = null
        private set
    var eosId: Int? = null
        private set
    var padId: Int? = null
        private set

    init {
        val reader = ProtoReader(modelBytes)
        var index = 0
        while (reader.hasNext()) {
            val (field, wireType) = reader.readTag()
            if (field == 1 && wireType == WIRE_LEN) {
                val piece = parsePiece(ProtoReader(reader.readLengthDelimited()), index)
                pieceIndex[piece.first] = piece.second
                if (piece.first.length > maxPieceLen) maxPieceLen = piece.first.length
                when (piece.first) {
                    "<unk>" -> unkId = piece.second.id
                    "<s>", "<bos>" -> bosId = piece.second.id
                    "</s>", "<eos>" -> eosId = piece.second.id
                    "<pad>" -> padId = piece.second.id
                }
                if (piece.second.type == TYPE_UNKNOWN) unkId = piece.second.id
                index++
            } else {
                reader.skip(wireType)
            }
        }
        require(pieceIndex.isNotEmpty()) { "No pieces found in tokenizer model — not a valid SentencePiece .model file" }
    }

    private fun parsePiece(reader: ProtoReader, index: Int): Pair<String, Piece> {
        var text = ""
        var score = 0f
        var type = TYPE_NORMAL
        while (reader.hasNext()) {
            val (field, wireType) = reader.readTag()
            when {
                field == 1 && wireType == WIRE_LEN -> text = String(reader.readLengthDelimited(), Charsets.UTF_8)
                field == 2 && wireType == WIRE_FIXED32 -> score = Float.fromBits(reader.readFixed32())
                field == 3 && wireType == WIRE_VARINT -> type = reader.readVarint().toInt()
                else -> reader.skip(wireType)
            }
        }
        return text to Piece(index, score, type)
    }

    /** Normalizes then greedily segments [text] into vocab ids, with a leading `<bos>` if the
     * vocab defines one (standard for Gemma-family tokenizers) and UTF-8 byte-fallback
     * (`<0xXX>` pieces) for any character not directly covered by the vocab. */
    fun encode(text: String): IntArray {
        val normalized = normalize(text)
        val ids = ArrayList<Int>(normalized.length / 3 + 2)
        bosId?.let { ids += it }
        var i = 0
        val n = normalized.length
        while (i < n) {
            var matchLen = 0
            var matchId = -1
            val upper = minOf(maxPieceLen, n - i)
            for (len in upper downTo 1) {
                val piece = pieceIndex[normalized.substring(i, i + len)]
                if (piece != null) {
                    matchLen = len
                    matchId = piece.id
                    break
                }
            }
            if (matchId >= 0) {
                ids += matchId
                i += matchLen
            } else {
                val codePoint = normalized.codePointAt(i)
                val charCount = Character.charCount(codePoint)
                for (b in String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)) {
                    val token = "<0x%02X>".format(b.toInt() and 0xFF)
                    ids += pieceIndex[token]?.id ?: unkId
                }
                i += charCount
            }
        }
        return ids.toIntArray()
    }

    private fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        // SentencePiece's default normalization: collapse whitespace to the meta-symbol "▁"
        // and add a dummy leading one, so "hello world" -> "▁hello▁world".
        val collapsed = nfkc.trim().replace(Regex("\\s+"), " ").replace(" ", META_SYMBOL)
        return META_SYMBOL + collapsed
    }

    private class ProtoReader(private val data: ByteArray) {
        var pos = 0
        fun hasNext() = pos < data.size

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = data[pos++].toInt() and 0xFF
                result = result or ((b.toLong() and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            return result
        }

        fun readTag(): Pair<Int, Int> {
            val tag = readVarint()
            return (tag ushr 3).toInt() to (tag and 0x7L).toInt()
        }

        fun readLengthDelimited(): ByteArray {
            val len = readVarint().toInt()
            val slice = data.copyOfRange(pos, pos + len)
            pos += len
            return slice
        }

        fun readFixed32(): Int {
            val b0 = data[pos].toInt() and 0xFF
            val b1 = data[pos + 1].toInt() and 0xFF
            val b2 = data[pos + 2].toInt() and 0xFF
            val b3 = data[pos + 3].toInt() and 0xFF
            pos += 4
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        fun skip(wireType: Int) {
            when (wireType) {
                WIRE_VARINT -> readVarint()
                WIRE_FIXED64 -> pos += 8
                WIRE_LEN -> { val len = readVarint().toInt(); pos += len }
                WIRE_FIXED32 -> pos += 4
                else -> throw IllegalStateException("Unknown protobuf wire type $wireType at offset $pos")
            }
        }
    }

    companion object {
        private const val META_SYMBOL = "▁" // "▁"
        private const val WIRE_VARINT = 0
        private const val WIRE_FIXED64 = 1
        private const val WIRE_LEN = 2
        private const val WIRE_FIXED32 = 5
        private const val TYPE_NORMAL = 1
        private const val TYPE_UNKNOWN = 2
    }
}
