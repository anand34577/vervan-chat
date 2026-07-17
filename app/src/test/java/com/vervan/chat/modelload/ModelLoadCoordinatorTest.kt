package com.vervan.chat.modelload

import com.vervan.chat.data.db.dao.ModelDao
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.llm.LlmEngine
import com.vervan.chat.retrieval.EmbeddingBackend
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ModelLoadCoordinator.doLoad] checks the model file actually exists on disk before loading —
 * a real (empty) temp file backs [filePath] so that check passes in these tests. */
private fun model(
    id: String,
    role: ModelRole = ModelRole.GENERATION,
    licenseAcknowledged: Boolean = true,
    lastLoadedAt: Long? = null,
    importedAt: Long = 0L
) = ModelInfo(
    id = id, displayName = id,
    filePath = java.io.File.createTempFile("model_$id", ".bin").apply { deleteOnExit() }.absolutePath,
    fileSizeBytes = 0, sha256 = "",
    role = role, licenseAcknowledged = licenseAcknowledged, lastLoadedAt = lastLoadedAt, importedAt = importedAt
)

class PickReassignmentCandidateTest {
    @Test
    fun prefersMostRecentlyLoaded() {
        val a = model("a", lastLoadedAt = 100, importedAt = 1)
        val b = model("b", lastLoadedAt = 200, importedAt = 2)
        val c = model("c", lastLoadedAt = null, importedAt = 3)
        assertEquals(b, pickReassignmentCandidate(listOf(a, b, c)))
    }

    @Test
    fun fallsBackToMostRecentImportWhenNeverLoaded() {
        val a = model("a", lastLoadedAt = null, importedAt = 1)
        val b = model("b", lastLoadedAt = null, importedAt = 5)
        assertEquals(b, pickReassignmentCandidate(listOf(a, b)))
    }

    @Test
    fun excludesUnacknowledgedModels() {
        val acknowledged = model("acknowledged", licenseAcknowledged = true, importedAt = 1)
        val unacknowledged = model("unacknowledged", licenseAcknowledged = false, importedAt = 100)
        // unacknowledged is "more eligible" by recency, but must never be silently handed
        // default status without the user ever having accepted its license.
        assertEquals(acknowledged, pickReassignmentCandidate(listOf(acknowledged, unacknowledged)))
    }

    @Test
    fun returnsNullWhenNoCandidateIsAcknowledged() {
        assertNull(pickReassignmentCandidate(listOf(model("a", licenseAcknowledged = false))))
    }

    @Test
    fun returnsNullForEmptyList() {
        assertNull(pickReassignmentCandidate(emptyList()))
    }
}

/** In-memory fake — real [ModelDao] is a Room-generated interface that needs a live database. */
private class FakeModelDao(initial: List<ModelInfo> = emptyList()) : ModelDao {
    val stateFlow = MutableStateFlow(initial)
    override fun observeModels(): Flow<List<ModelInfo>> = stateFlow
    override suspend fun getActiveModel(role: ModelRole): ModelInfo? = stateFlow.value.firstOrNull { it.role == role && it.isActive }
    override suspend fun get(id: String): ModelInfo? = stateFlow.value.firstOrNull { it.id == id }
    override fun observeActiveModel(role: ModelRole): Flow<ModelInfo?> = throw NotImplementedError()
    override suspend fun findByHash(sha256: String): ModelInfo? = null
    override suspend fun getOthersOfRole(role: ModelRole, excludeId: String): List<ModelInfo> =
        stateFlow.value.filter { it.role == role && it.id != excludeId }
    override suspend fun clearActive(role: ModelRole) {
        stateFlow.value = stateFlow.value.map { if (it.role == role) it.copy(isActive = false) else it }
    }
    override suspend fun findByCatalogEntry(modelId: String, version: String): ModelInfo? = null
    override suspend fun upsert(entity: ModelInfo) {
        stateFlow.value = stateFlow.value.filterNot { it.id == entity.id } + entity
    }
    override suspend fun update(entity: ModelInfo) = upsert(entity)
    override suspend fun delete(entity: ModelInfo) {
        stateFlow.value = stateFlow.value.filterNot { it.id == entity.id }
    }
}

private class FakeGenerationDefaults : GenerationDefaults {
    override suspend fun contextTokenLimit() = 4096
    override suspend fun maxNumImages() = 1
    override suspend fun preferredBackend() = "AUTO"
}

/** Fake whose [loadModel] suspends-in-spirit by blocking on a [CompletableDeferred] the test
 * controls, so concurrent coordinator calls can be proven to share one underlying load. Blocking
 * (not suspending) is intentional: [ModelLoadCoordinator.doLoad] calls [loadModel] from inside a
 * plain (non-suspend) code path dispatched onto [Dispatchers.Default], mirroring how the real
 * [LlmEngine.load] is itself a blocking native call, not a suspend function. */
private class FakeGenerationEngine(private val gate: CompletableDeferred<Unit>) : GenerationLoadable {
    val loadCallCount = AtomicInteger(0)
    override var loadedModelPath: String? = null
    override val activeBackend = LlmEngine.ModelBackend.CPU
    override val visionEnabled = false
    override val audioEnabled = false
    override val speculativeDecodingActive = false
    override fun loadModel(
        modelPath: String, maxTokens: Int, maxNumImages: Int,
        backendPreference: LlmEngine.BackendPreference, preferredBackendHint: LlmEngine.ModelBackend?,
        enableSpeculativeDecoding: Boolean
    ): LlmEngine.LoadResult {
        loadCallCount.incrementAndGet()
        runBlocking { gate.await() }
        loadedModelPath = modelPath
        return LlmEngine.LoadResult(LlmEngine.ModelBackend.CPU, fellBackToCpu = false)
    }
    override fun close() { loadedModelPath = null }
}

private class FakeEmbeddingEngine : EmbeddingLoadable {
    override var loadedModelPath: String? = null
    override val activeBackend = EmbeddingBackend.CPU
    override fun loadModel(modelPath: String, tokenizerPath: String?) { loadedModelPath = modelPath }
    override fun close() { loadedModelPath = null }
}

class ModelLoadCoordinatorConcurrencyTest {
    @Test
    fun concurrentEnsureLoadedCallsShareOneUnderlyingLoad() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val generationEngine = FakeGenerationEngine(gate)
        val dao = FakeModelDao(listOf(model("m1").copy(isActive = true)))
        val coordinator = ModelLoadCoordinator(
            dao, generationEngine, FakeEmbeddingEngine(),
            kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(),
            FakeGenerationDefaults(), CoroutineScope(Dispatchers.Default)
        )

        val callerCount = 5
        val callers = CoroutineScope(Dispatchers.Default).let { scope ->
            (1..callerCount).map { scope.async { coordinator.ensureLoaded(ModelRole.GENERATION, LoadTrigger.CHAT_SEND) } }
        }
        // Give every caller a chance to reach the in-flight-map check before releasing the gate.
        kotlinx.coroutines.delay(100)
        gate.complete(Unit)
        val results = callers.map { it.await() }

        assertEquals(1, generationEngine.loadCallCount.get())
        assertTrue(results.all { it.success })
        assertEquals(1, results.count { !it.joinedInFlight })
        assertEquals(callerCount - 1, results.count { it.joinedInFlight })
    }

    @Test
    fun alreadyLoadedModelSkipsAnotherNativeLoad() = runBlocking {
        val gate = CompletableDeferred<Unit>().apply { complete(Unit) }
        val generationEngine = FakeGenerationEngine(gate)
        val m = model("m1").copy(isActive = true)
        val dao = FakeModelDao(listOf(m))
        val coordinator = ModelLoadCoordinator(
            dao, generationEngine, FakeEmbeddingEngine(),
            kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(),
            FakeGenerationDefaults(), CoroutineScope(Dispatchers.Default)
        )

        val first = coordinator.ensureLoaded(ModelRole.GENERATION, LoadTrigger.CHAT_SEND)
        val second = coordinator.ensureLoaded(ModelRole.GENERATION, LoadTrigger.CHAT_SEND)

        assertTrue(first.success)
        assertTrue(second.success)
        assertEquals(1, generationEngine.loadCallCount.get())
        assertEquals(m.id, second.loadedModelId)
    }

    @Test
    fun noModelInstalledReturnsFailureWithoutTouchingEngine() = runBlocking {
        val generationEngine = FakeGenerationEngine(CompletableDeferred())
        val coordinator = ModelLoadCoordinator(
            FakeModelDao(), generationEngine, FakeEmbeddingEngine(),
            kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(),
            FakeGenerationDefaults(), CoroutineScope(Dispatchers.Default)
        )

        val result = coordinator.ensureLoaded(ModelRole.GENERATION, LoadTrigger.CHAT_SEND)

        assertEquals(false, result.success)
        assertEquals(ModelLoadErrorCategory.NO_MODEL_INSTALLED, result.errorCategory)
        assertEquals(0, generationEngine.loadCallCount.get())
    }
}
