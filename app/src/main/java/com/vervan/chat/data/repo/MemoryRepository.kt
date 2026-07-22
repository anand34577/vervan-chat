package com.vervan.chat.data.repo

import com.vervan.chat.data.db.dao.MemoryDao
import com.vervan.chat.data.db.dao.ModelDao
import com.vervan.chat.data.db.entities.Memory
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.toBytes
import com.vervan.chat.data.db.entities.toFloatArray
import com.vervan.chat.modelload.LoadTrigger
import com.vervan.chat.modelload.ModelLoadCoordinator
import com.vervan.chat.retrieval.EmbeddingEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class MemoryRecallMode { SEMANTIC, TEXT }

data class MemoryMatch(val memory: Memory, val score: Float)
data class MemoryRecall(val matches: List<MemoryMatch>, val mode: MemoryRecallMode)
data class MemorySaveResult(val memory: Memory, val indexed: Boolean)

/** One path for memory writes and recalls so chat, tools, and the Memory screens stay consistent. */
class MemoryRepository(
    private val memoryDao: MemoryDao,
    private val modelDao: ModelDao,
    private val coordinator: ModelLoadCoordinator,
    private val embeddingEngine: EmbeddingEngine,
    private val embeddingMutex: Mutex
) {
    suspend fun upsert(memory: Memory): MemorySaveResult {
        val base = memory.copy(embedding = null, embeddingModelId = null)
        val model = modelDao.getActiveModel(ModelRole.EMBEDDING)
        val vector = if (model != null && coordinator.ensureLoaded(model, LoadTrigger.RAG_RETRIEVAL).success) {
            embeddingMutex.withLock { embeddingEngine.embed(base.text, title = "Memory") }
        } else null
        val saved = if (vector != null) base.copy(embedding = vector.toBytes(), embeddingModelId = model?.id) else base
        memoryDao.upsert(saved)
        return MemorySaveResult(saved, vector != null)
    }

    /** Universal recall: memory is shared across every persona, project, and model — anyone can
     *  save a memory and anyone can recall it. Ranks against *all* enabled memories, never scoped
     *  by the chat's persona/project. */
    suspend fun recall(query: String, topK: Int = DEFAULT_TOP_K): MemoryRecall =
        rank(memoryDao.getEnabled(), query, topK)

    suspend fun search(query: String, topK: Int = 10): MemoryRecall = rank(memoryDao.getEnabled(), query, topK)

    private suspend fun rank(candidates: List<Memory>, query: String, topK: Int): MemoryRecall {
        if (candidates.isEmpty() || topK <= 0) return MemoryRecall(emptyList(), MemoryRecallMode.TEXT)
        if (query.isBlank()) return textFallback(candidates, query, topK)
        val model = modelDao.getActiveModel(ModelRole.EMBEDDING) ?: return textFallback(candidates, query, topK)
        if (!coordinator.ensureLoaded(model, LoadTrigger.RAG_RETRIEVAL).success) return textFallback(candidates, query, topK)

        val queryVector = embeddingMutex.withLock { embeddingEngine.embed(query, isQuery = true) }
            ?: return textFallback(candidates, query, topK)
        val vectors = mutableMapOf<String, FloatArray>()
        val refreshed = mutableListOf<Memory>()
        embeddingMutex.withLock {
            candidates.forEach { memory ->
                val cached = memory.embedding
                    ?.takeIf { memory.embeddingModelId == model.id }
                    ?.toFloatArray()
                    ?.takeIf { it.size == queryVector.size }
                val vector = cached ?: embeddingEngine.embed(memory.text, title = "Memory")
                if (vector != null && vector.size == queryVector.size) {
                    vectors[memory.id] = vector
                    if (cached == null) refreshed += memory.copy(embedding = vector.toBytes(), embeddingModelId = model.id)
                }
            }
        }
        refreshed.forEach { memoryDao.update(it) }
        val ranked = rankMemoryCandidates(query, queryVector, candidates, vectors, topK)
        return if (ranked.isEmpty()) textFallback(candidates, query, topK) else MemoryRecall(ranked, MemoryRecallMode.SEMANTIC)
    }

    private fun textFallback(candidates: List<Memory>, query: String, topK: Int) = MemoryRecall(
        rankTextMemoryCandidates(query, candidates, topK),
        MemoryRecallMode.TEXT
    )

    companion object { const val DEFAULT_TOP_K = 6 }
}

internal fun rankMemoryCandidates(
    query: String,
    queryVector: FloatArray,
    candidates: List<Memory>,
    vectors: Map<String, FloatArray>,
    topK: Int
): List<MemoryMatch> {
    val terms = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
    return candidates.mapNotNull { memory ->
        val vector = vectors[memory.id] ?: return@mapNotNull null
        val semantic = EmbeddingEngine.cosineSimilarity(queryVector, vector)
        val lexical = if (terms.isEmpty()) 0f else {
            val lower = memory.text.lowercase()
            terms.count { it in lower }.toFloat() / terms.size
        }
        MemoryMatch(memory, semantic + lexical * 0.15f)
    }.sortedByDescending { it.score }.take(topK)
}

internal fun rankTextMemoryCandidates(query: String, candidates: List<Memory>, topK: Int): List<MemoryMatch> {
    val terms = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
    return candidates.map { memory ->
        val lower = memory.text.lowercase()
        val score = if (terms.isEmpty()) 0f else terms.count { it in lower }.toFloat() / terms.size
        MemoryMatch(memory, score)
    }.sortedByDescending { it.score }.take(topK)
}
