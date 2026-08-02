package com.vervan.chat.retrieval

import com.vervan.chat.data.db.dao.ChunkDao
import com.vervan.chat.data.db.dao.DocumentDao
import com.vervan.chat.data.db.dao.ModelDao
import com.vervan.chat.data.db.entities.Chunk
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.toFloatArray

// EXACT_PHRASE added — the other two modes the spec calls out
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
    val score: Float,
    val pageNumber: Int? = null
)

/**
 * Keyword matching goes through [ChunkDao]'s FTS4 index ([com.vervan.chat.data.db.entities.ChunkFts])
 * instead of a per-chunk Kotlin scan — real tokenized, indexed lookup, so a KEYWORD/EXACT_PHRASE
 * query no longer has to fetch every chunk in scope just to find the handful that match. SEMANTIC
 * still brute-forces cosine similarity over every chunk with an embedding (no ANN index — fine
 * for a personal knowledge base of a few thousand chunks; revisit if imports grow past that), and
 * HYBRID's semantic half inherits that same ceiling since it needs the full candidate set either
 * way.
 */
class RetrievalEngine(
    private val chunkDao: ChunkDao,
    private val documentDao: DocumentDao,
    private val embeddingEngine: EmbeddingEngine,
    private val modelDao: ModelDao
) {
    suspend fun retrieve(
        kbIds: List<String>,
        query: String,
        mode: RetrievalMode,
        topK: Int = 5
    ): List<SourcePassage> {
        if (kbIds.isEmpty() || query.isBlank()) return emptyList()

        val chunks: List<Chunk>
        val keywordScores: Map<String, Float>
        val exactPhraseScores: Map<String, Float>

        when (mode) {
            RetrievalMode.KEYWORD -> {
                val terms = extractTerms(query)
                if (terms.isEmpty()) return emptyList()
                val ids = chunkDao.matchFts(orMatchQuery(terms), kbIds, MAX_CHUNKS_PER_QUERY)
                chunks = if (ids.isEmpty()) emptyList() else chunkDao.getByIds(ids)
                keywordScores = keywordScore(terms, chunks)
                exactPhraseScores = emptyMap()
            }
            RetrievalMode.EXACT_PHRASE -> {
                val phrase = query.trim()
                if (phrase.isEmpty()) return emptyList()
                val ids = chunkDao.matchFts(phraseMatchQuery(phrase), kbIds, MAX_CHUNKS_PER_QUERY)
                chunks = if (ids.isEmpty()) emptyList() else chunkDao.getByIds(ids)
                keywordScores = emptyMap()
                // FTS already only returned chunks containing the phrase — every candidate here
                // is a hit, so score is uniformly 1 (matches the old contains()-based scoring).
                exactPhraseScores = chunks.associate { it.id to 1f }
            }
            RetrievalMode.SEMANTIC, RetrievalMode.HYBRID -> {
                // Fetch one past the cap so an oversized KB is still detectable/loggable below,
                // without pulling the full unbounded result set into memory first.
                var fetched = chunkDao.getForKnowledgeBases(kbIds, MAX_CHUNKS_PER_QUERY + 1)
                if (fetched.size > MAX_CHUNKS_PER_QUERY) {
                    android.util.Log.w("RetrievalEngine", "KB scope has more than $MAX_CHUNKS_PER_QUERY chunks, capping scan to $MAX_CHUNKS_PER_QUERY")
                    fetched = fetched.take(MAX_CHUNKS_PER_QUERY)
                }
                chunks = fetched
                keywordScores = if (mode == RetrievalMode.HYBRID) keywordScore(extractTerms(query), chunks) else emptyMap()
                exactPhraseScores = emptyMap()
            }
        }
        if (chunks.isEmpty()) return emptyList()

        val semanticScores = if ((mode == RetrievalMode.SEMANTIC || mode == RetrievalMode.HYBRID) && embeddingEngine.isLoaded) {
            val activeModelId = modelDao.getActiveModel(ModelRole.EMBEDDING)?.id
            semanticScore(query, chunks, activeModelId)
        } else emptyMap()
        // Recency-weighting folded directly into HYBRID rather than a standalone
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
                SourcePassage(chunk.id, chunk.documentId, docName, chunk.sectionPath, chunk.text, score, chunk.pageNumber)
            }
    }

    companion object {
        private const val MAX_CHUNKS_PER_QUERY = 4000
        private const val MIN_RELEVANCE_SCORE = 0.15f
        private const val MAX_CHUNKS_PER_DOCUMENT = 2
    }

    private fun extractTerms(query: String): Set<String> =
        query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()

    /** FTS4 MATCH expression: any term hits (OR), each quoted so punctuation-free tokens are
     * matched literally rather than as FTS query-syntax operators. */
    private fun orMatchQuery(terms: Set<String>): String = terms.joinToString(" OR ") { "\"$it\"" }

    /** FTS4 phrase query — a quoted string matches only chunks containing that exact contiguous
     * token sequence, the FTS equivalent of the old raw `String.contains()` check. */
    private fun phraseMatchQuery(phrase: String): String = "\"${phrase.replace("\"", " ")}\""

    // Word-boundary term matching (real tokenization via split, not a substring `.contains()`),
    // so a term like "cat" no longer false-positives inside an unrelated word like "category".
    private fun keywordScore(terms: Set<String>, chunks: List<Chunk>): Map<String, Float> {
        if (terms.isEmpty()) return emptyMap()
        return chunks.associate { chunk ->
            val words = chunk.text.lowercase().split(Regex("\\W+")).toSet()
            val matched = terms.count { it in words }
            chunk.id to (matched.toFloat() / terms.size)
        }
    }

    private suspend fun recencyScore(chunks: List<Chunk>): Map<String, Float> {
        val docIds = chunks.map { it.documentId }.distinct()
        val timestamps = docIds.associateWith { documentDao.get(it)?.importedAt ?: 0L }
        val minTs = timestamps.values.minOrNull() ?: 0L
        val maxTs = timestamps.values.maxOrNull() ?: 0L
        val range = (maxTs - minTs).coerceAtLeast(1L)
        return chunks.associate { chunk -> chunk.id to ((timestamps[chunk.documentId] ?: minTs) - minTs).toFloat() / range }
    }

    private suspend fun semanticScore(query: String, chunks: List<Chunk>, activeModelId: String?): Map<String, Float> {
        val queryEmbedding = embeddingEngine.embed(query, isQuery = true) ?: return emptyMap()
        return chunks.mapNotNull { chunk ->
            val embedding = chunk.embedding?.toFloatArray() ?: return@mapNotNull null
            // Exact model-id mismatch (B8) is the authoritative staleness check — two different
            // embedding models can coincidentally share an output dimension, which would let a
            // dimension-only check silently cosine-compare vectors from different embedding
            // spaces. Chunks embedded before embeddingModelId existed carry null, so they fall
            // back to the dimension check alone rather than being treated as a hard mismatch.
            if (chunk.embeddingModelId != null && activeModelId != null && chunk.embeddingModelId != activeModelId) return@mapNotNull null
            if (embedding.size != queryEmbedding.size) return@mapNotNull null
            chunk.id to EmbeddingEngine.cosineSimilarity(queryEmbedding, embedding)
        }.toMap()
    }
}
