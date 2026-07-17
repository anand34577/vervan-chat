package com.vervan.chat.retrieval

import com.vervan.chat.data.db.dao.ChunkDao
import com.vervan.chat.data.db.dao.DocumentDao
import com.vervan.chat.data.db.entities.Chunk
import com.vervan.chat.data.db.entities.toFloatArray

// EXACT_PHRASE added (Phase 2, spec §19) — the other two modes the spec calls out
// (current-document-only, recency-weighting) either need context this engine doesn't have
// (which document the user is currently viewing) or are folded into HYBRID directly below
// rather than becoming a whole separate mode.
enum class RetrievalMode { KEYWORD, SEMANTIC, HYBRID, EXACT_PHRASE }

data class SourcePassage(
    val chunkId: String,
    val documentId: String,
    val documentName: String,
    val sectionPath: String,
    val excerpt: String,
    val score: Float
)

/**
 * ponytail: brute-force over every chunk in scope for both keyword (term overlap) and
 * semantic (cosine) scoring — no FTS index, no ANN index. Fine for a personal knowledge
 * base (hundreds to low thousands of chunks); revisit if imports grow past that.
 */
class RetrievalEngine(
    private val chunkDao: ChunkDao,
    private val documentDao: DocumentDao,
    private val embeddingEngine: EmbeddingEngine
) {
    suspend fun retrieve(
        kbIds: List<String>,
        query: String,
        mode: RetrievalMode,
        topK: Int = 5
    ): List<SourcePassage> {
        if (kbIds.isEmpty() || query.isBlank()) return emptyList()
        var chunks = chunkDao.getForKnowledgeBases(kbIds)
        if (chunks.isEmpty()) return emptyList()
        if (chunks.size > MAX_CHUNKS_PER_QUERY) {
            // ponytail: brute-force scan has no size guard (B9) — cap rather than let a large
            // KB silently balloon memory/latency. Upgrade to an FTS/ANN index if imports
            // routinely exceed this in practice.
            android.util.Log.w("RetrievalEngine", "KB scope has ${chunks.size} chunks, capping scan to $MAX_CHUNKS_PER_QUERY")
            chunks = chunks.take(MAX_CHUNKS_PER_QUERY)
        }

        val keywordScores = if (mode == RetrievalMode.KEYWORD || mode == RetrievalMode.HYBRID) keywordScore(query, chunks) else emptyMap()
        val semanticScores = if ((mode == RetrievalMode.SEMANTIC || mode == RetrievalMode.HYBRID) && embeddingEngine.isLoaded) {
            semanticScore(query, chunks)
        } else emptyMap()
        val exactPhraseScores = if (mode == RetrievalMode.EXACT_PHRASE) exactPhraseScore(query, chunks) else emptyMap()
        // Recency-weighting folded directly into HYBRID (spec §19) rather than a standalone
        // mode — a small tie-breaker toward more recently imported documents, not a filter.
        val recencyScores = if (mode == RetrievalMode.HYBRID) recencyScore(chunks) else emptyMap()

        val combined = chunks.associateWith { chunk ->
            when (mode) {
                RetrievalMode.KEYWORD -> keywordScores[chunk.id] ?: 0f
                RetrievalMode.SEMANTIC -> semanticScores[chunk.id] ?: 0f
                RetrievalMode.EXACT_PHRASE -> exactPhraseScores[chunk.id] ?: 0f
                RetrievalMode.HYBRID -> {
                    val kw = keywordScores[chunk.id] ?: 0f
                    // No semantic score means this chunk has no embedding or a stale one from
                    // a different model (dimension mismatch, see semanticScore below) — score
                    // it on keyword alone instead of silently discounting it by 50% (B8).
                    val sem = semanticScores[chunk.id]
                    val base = if (sem != null) 0.5f * kw + 0.5f * sem else kw
                    // Recency only breaks ties among already-relevant chunks — a 0-relevance
                    // chunk shouldn't surface just because its document is new.
                    if (base > 0f) 0.9f * base + 0.1f * (recencyScores[chunk.id] ?: 0f) else 0f
                }
            }
        }

        // Below this, a match is noise (a single incidental keyword, or near-orthogonal
        // cosine similarity) rather than actual evidence — don't let it fill a topK slot
        // just because nothing better scored.
        val minScore = if (mode == RetrievalMode.EXACT_PHRASE) 1f else MIN_RELEVANCE_SCORE

        val docNames = mutableMapOf<String, String>()
        val perDocCount = mutableMapOf<String, Int>()
        return combined.entries
            .filter { it.value >= minScore }
            .sortedByDescending { it.value }
            // Cap chunks-per-document so one large/matching document can't fill every topK
            // slot with adjacent passages, leaving no room for other relevant documents.
            .filter { (chunk, _) ->
                val count = perDocCount.getOrDefault(chunk.documentId, 0)
                (count < MAX_CHUNKS_PER_DOCUMENT).also { if (it) perDocCount[chunk.documentId] = count + 1 }
            }
            .take(topK)
            .map { (chunk, score) ->
                val docName = docNames.getOrPut(chunk.documentId) { documentDao.get(chunk.documentId)?.displayName ?: "Unknown" }
                SourcePassage(chunk.id, chunk.documentId, docName, chunk.sectionPath, chunk.text, score)
            }
    }

    companion object {
        private const val MAX_CHUNKS_PER_QUERY = 4000
        private const val MIN_RELEVANCE_SCORE = 0.15f
        private const val MAX_CHUNKS_PER_DOCUMENT = 2
    }

    private fun keywordScore(query: String, chunks: List<Chunk>): Map<String, Float> {
        val terms = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        if (terms.isEmpty()) return emptyMap()
        return chunks.associate { chunk ->
            val lower = chunk.text.lowercase()
            val matched = terms.count { lower.contains(it) }
            chunk.id to (matched.toFloat() / terms.size)
        }
    }

    private fun exactPhraseScore(query: String, chunks: List<Chunk>): Map<String, Float> {
        val phrase = query.trim()
        if (phrase.isEmpty()) return emptyMap()
        return chunks.associate { chunk -> chunk.id to if (chunk.text.contains(phrase, ignoreCase = true)) 1f else 0f }
    }

    private suspend fun recencyScore(chunks: List<Chunk>): Map<String, Float> {
        val docIds = chunks.map { it.documentId }.distinct()
        val timestamps = docIds.associateWith { documentDao.get(it)?.importedAt ?: 0L }
        val minTs = timestamps.values.minOrNull() ?: 0L
        val maxTs = timestamps.values.maxOrNull() ?: 0L
        val range = (maxTs - minTs).coerceAtLeast(1L)
        return chunks.associate { chunk -> chunk.id to ((timestamps[chunk.documentId] ?: minTs) - minTs).toFloat() / range }
    }

    private suspend fun semanticScore(query: String, chunks: List<Chunk>): Map<String, Float> {
        val queryEmbedding = embeddingEngine.embed(query, isQuery = true) ?: return emptyMap()
        return chunks.mapNotNull { chunk ->
            val embedding = chunk.embedding?.toFloatArray() ?: return@mapNotNull null
            // Dimension mismatch means this chunk was embedded by a different model than the
            // one currently loaded (B8) — exclude it from semantic scoring entirely rather
            // than letting cosineSimilarity's built-in 0f-on-mismatch masquerade as "no match".
            if (embedding.size != queryEmbedding.size) return@mapNotNull null
            chunk.id to EmbeddingEngine.cosineSimilarity(queryEmbedding, embedding)
        }.toMap()
    }
}
