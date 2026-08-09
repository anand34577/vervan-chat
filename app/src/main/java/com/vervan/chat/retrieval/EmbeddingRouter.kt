package com.vervan.chat.retrieval

import com.vervan.chat.data.db.entities.ModelEngine
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.llm.RemoteApiKeyStore
import com.vervan.chat.llm.RemoteOpenAiEngine
import com.vervan.chat.system.NetworkAuditLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single dispatch point for turning text into a vector — every embedding consumer (RAG retrieval,
 * memory recall, document indexing) routes through this instead of hand-rolling its own
 * on-device-vs-remote branch. Mirrors [com.vervan.chat.VervanApp.AppContainer.generate]'s
 * per-engine dispatch for chat.
 *
 * No mutex on the REMOTE_API branch, same reasoning as that `generate()` dispatch: a remote call is
 * a stateless HTTP request, not a native session fighting for exclusive access.
 */
suspend fun embedWith(
    model: ModelInfo,
    text: String,
    isQuery: Boolean = false,
    title: String? = null,
    embeddingEngine: EmbeddingEngine,
    embeddingMutex: Mutex,
    remoteOpenAiEngine: RemoteOpenAiEngine,
    remoteApiKeyStore: RemoteApiKeyStore,
    networkAuditLog: NetworkAuditLog
): FloatArray? = when (model.engine) {
    ModelEngine.REMOTE_API -> {
        val baseUrl = model.remoteBaseUrl?.takeIf { it.isNotBlank() }
        val remoteModelId = model.remoteApiModelId?.takeIf { it.isNotBlank() }
        if (baseUrl == null || remoteModelId == null) {
            null
        } else {
            val apiKey = remoteApiKeyStore.get(model.id).orEmpty()
            networkAuditLog.record("Remote API embedding: ${model.displayName}")
            remoteOpenAiEngine.embed(baseUrl, apiKey, remoteModelId, text).getOrNull()
        }
    }
    else -> embeddingMutex.withLock { embeddingEngine.embed(text, isQuery, title) }
}

/**
 * Batched counterpart of [embedWith] — for [ModelEngine.REMOTE_API] this becomes one HTTP
 * request carrying every text in [texts] instead of one request per item (see
 * [RemoteOpenAiEngine.embedBatch] and [com.vervan.chat.model.DocumentImportManager.persistChunks],
 * its only caller). A local engine has no such economy (one native inference call per text
 * either way), so it just loops [EmbeddingEngine.embed] under the same mutex discipline
 * [embedWith] uses. Returns one entry per input, in order; a `null` marks that item's embedding
 * as failed (a whole-batch remote failure fails every item in that batch, same "keyword-only
 * searchable" fallback contract [embedWith] already has for a single item).
 */
suspend fun embedBatchWith(
    model: ModelInfo,
    texts: List<String>,
    titles: List<String?>,
    embeddingEngine: EmbeddingEngine,
    embeddingMutex: Mutex,
    remoteOpenAiEngine: RemoteOpenAiEngine,
    remoteApiKeyStore: RemoteApiKeyStore,
    networkAuditLog: NetworkAuditLog
): List<FloatArray?> {
    if (texts.isEmpty()) return emptyList()
    return when (model.engine) {
        ModelEngine.REMOTE_API -> {
            val baseUrl = model.remoteBaseUrl?.takeIf { it.isNotBlank() }
            val remoteModelId = model.remoteApiModelId?.takeIf { it.isNotBlank() }
            if (baseUrl == null || remoteModelId == null) {
                texts.map { null }
            } else {
                val apiKey = remoteApiKeyStore.get(model.id).orEmpty()
                networkAuditLog.record("Remote API embedding: ${model.displayName} (${texts.size} chunks)")
                remoteOpenAiEngine.embedBatch(baseUrl, apiKey, remoteModelId, texts).getOrNull() ?: texts.map { null }
            }
        }
        else -> texts.mapIndexed { i, text -> embeddingMutex.withLock { embeddingEngine.embed(text, isQuery = false, titles.getOrNull(i)) } }
    }
}

/** Whether [model] is actually usable for embedding right now. A local engine has a real
 *  loaded-session check ([EmbeddingEngine.isLoaded]); a REMOTE_API model has no session to load
 *  (see [com.vervan.chat.modelload.ModelLoadCoordinator]'s REMOTE_API short-circuit) so "ready"
 *  just means it's configured — same on-device-vs-stateless asymmetry
 *  [com.vervan.chat.VervanApp.AppContainer.visionEnabled] already draws for REMOTE_API. */
fun embeddingReady(model: ModelInfo?, embeddingEngine: EmbeddingEngine): Boolean = when (model?.engine) {
    null -> false
    ModelEngine.REMOTE_API -> !model.remoteBaseUrl.isNullOrBlank() && !model.remoteApiModelId.isNullOrBlank()
    else -> embeddingEngine.isLoaded
}
