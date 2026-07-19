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

private class FakeGenerationDefaults(private val allowLowMemoryLoads: Boolean = false) : GenerationDefaults {
    override suspend fun contextTokenLimit() = 4096
    override suspend fun maxNumImages() = 1
    override suspend fun preferredBackend() = "AUTO"
    override suspend fun allowLowMemoryModelLoads() = allowLowMemoryLoads
    override suspend fun cpuThreads() = 0
    override suspend fun nBatch() = 2048
    override suspend fun nUbatch() = 512
    override suspend fun useMlock() = false
    override suspend fun flashAttentionMode() = "AUTO"
    override suspend fun kvCacheType() = "f16"
    override suspend fun vulkanDeviceIndex() = 0
}

/** Fake whose [loadModel] suspends-in-spirit by blocking on a [CompletableDeferred] the test
 * controls, so concurrent coordinator calls can be proven to share one underlying load. Blocking
 * (not suspending) is intentional: [ModelLoadCoordinator.doLoad] calls [loadModel] from inside a
 * plain (non-suspend) code path dispatched onto [Dispatchers.Default], mirroring how the real
 * [LlmEngine.load] is itself a blocking native call, not a suspend function. */
private class FakeGenerationEngine(private val gate: CompletableDeferred<Unit>) : GenerationLoadable {
    val loadCallCount = AtomicInteger(0)
    // §10 priority tests need the *order* calls happened in, not just the count — the
    // coordinator already serializes loadModel() calls per role, so a plain list is safe.
    val loadedPathsInOrder = java.util.Collections.synchronizedList(mutableListOf<String>())
    override var loadedModelPath: String? = null
    override val activeBackend = LlmEngine.ModelBackend.CPU
    override val visionEnabled = false
    override val audioEnabled = false
    override val speculativeDecodingActive = false
    override val loadedContextTokens: Int? = null
    override fun loadModel(
        modelPath: String, maxTokens: Int, maxNumImages: Int,
        backendPreference: LlmEngine.BackendPreference, preferredBackendHint: LlmEngine.ModelBackend?,
        enableSpeculativeDecoding: Boolean
    ): LlmEngine.LoadResult {
        loadCallCount.incrementAndGet()
        runBlocking { gate.await() }
        loadedModelPath = modelPath
        loadedPathsInOrder += modelPath
        return LlmEngine.LoadResult(LlmEngine.ModelBackend.CPU, fellBackToCpu = false)
    }
    override fun close() { loadedModelPath = null }
    override fun cancelActiveGeneration() {}
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
            dao, generationEngine, FakeGenerationEngine(CompletableDeferred(Unit)), FakeEmbeddingEngine(),
            kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(),
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
            dao, generationEngine, FakeGenerationEngine(CompletableDeferred(Unit)), FakeEmbeddingEngine(),
            kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(),
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
            FakeModelDao(), generationEngine, FakeGenerationEngine(CompletableDeferred(Unit)), FakeEmbeddingEngine(),
            kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(),
            FakeGenerationDefaults(), CoroutineScope(Dispatchers.Default)
        )

        val result = coordinator.ensureLoaded(ModelRole.GENERATION, LoadTrigger.CHAT_SEND)

        assertEquals(false, result.success)
        assertEquals(ModelLoadErrorCategory.NO_MODEL_INSTALLED, result.errorCategory)
        assertEquals(0, generationEngine.loadCallCount.get())
    }

    /** §10 — "explicit manual selection" must jump ahead of a lower-priority automatic request
     * that queued for the same role at the same time, even though the coordinator can't interrupt
     * whatever's *already* running (native loads are blocking calls with no cancellation hook —
     * see the watchdog dispatcher comment in ModelLoadCoordinator). What it *can* control is who
     * goes next once the current load finishes, and that's what this proves. */
    @Test
    fun manualRequestJumpsAheadOfQueuedAutomaticRequestForTheSameRole() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val generationEngine = FakeGenerationEngine(gate)
        val a = model("a", lastLoadedAt = 1)
        val b = model("b", lastLoadedAt = 2)
        val c = model("c", lastLoadedAt = 3)
        val dao = FakeModelDao(listOf(a, b, c))
        val coordinator = ModelLoadCoordinator(
            dao, generationEngine, FakeGenerationEngine(CompletableDeferred(Unit)), FakeEmbeddingEngine(),
            kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(),
            FakeGenerationDefaults(), CoroutineScope(Dispatchers.Default)
        )
        val scope = CoroutineScope(Dispatchers.Default)

        // a's load starts and blocks on the gate — the "currently in flight" load.
        // Generous settling delays: on a loaded machine 100ms was occasionally not enough for the
        // async launches on Dispatchers.Default to reach their queue registration, which let two
        // requests race past dedupe and produced a flaky fourth engine load.
        val aResult = scope.async { coordinator.ensureLoaded(a, LoadTrigger.CHAT_AUTOLOAD) }
        kotlinx.coroutines.delay(300) // let a actually become the in-flight load first

        // While a is in flight, b (low-priority automatic) and c (high-priority manual) both
        // queue up for the same role — b first, to prove priority beats arrival order.
        val bResult = scope.async { coordinator.ensureLoaded(b, LoadTrigger.CHAT_AUTOLOAD) }
        kotlinx.coroutines.delay(150)
        val cResult = scope.async { coordinator.loadManually(c) }
        kotlinx.coroutines.delay(300) // let both b and c actually register as waiters

        gate.complete(Unit) // let a finish; b and c now race to go next

        assertTrue(aResult.await().success)
        assertTrue(bResult.await().success)
        assertTrue(cResult.await().success)

        assertEquals(3, generationEngine.loadCallCount.get())
        assertEquals(listOf(a.filePath, c.filePath, b.filePath), generationEngine.loadedPathsInOrder)
    }
}

/** §11.2 — a default model whose file vanished outside the app should self-heal by reassigning
 * default status and retrying once, but only for automatic loading; an explicit Model Manager
 * pick of that exact (now-missing) model must not be silently swapped for something else. */
class ModelLoadCoordinatorMissingDefaultTest {
    @Test
    fun automaticLoadReassignsDefaultAndRetriesOnceOnMissingFile() = runBlocking {
        val broken = model("broken", lastLoadedAt = 1).copy(isActive = true)
        java.io.File(broken.filePath).delete() // simulate the file having vanished outside the app
        val replacement = model("replacement", lastLoadedAt = 2)
        val generationEngine = FakeGenerationEngine(CompletableDeferred(Unit))
        val dao = FakeModelDao(listOf(broken, replacement))
        val coordinator = ModelLoadCoordinator(
            dao, generationEngine, FakeGenerationEngine(CompletableDeferred(Unit)), FakeEmbeddingEngine(),
            kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(),
            FakeGenerationDefaults(), CoroutineScope(Dispatchers.Default)
        )

        val result = coordinator.ensureLoaded(broken, LoadTrigger.CHAT_AUTOLOAD)

        assertTrue(result.success)
        assertEquals(replacement.id, result.loadedModelId)
        assertEquals(1, generationEngine.loadCallCount.get())
        assertTrue(dao.stateFlow.value.first { it.id == replacement.id }.isActive)
        assertTrue(!dao.stateFlow.value.first { it.id == broken.id }.isActive)
    }

    @Test
    fun explicitManualPickOfMissingModelFailsCleanlyWithoutSubstituting() = runBlocking {
        val broken = model("broken").copy(isActive = true)
        java.io.File(broken.filePath).delete()
        val generationEngine = FakeGenerationEngine(CompletableDeferred(Unit))
        val dao = FakeModelDao(listOf(broken))
        val coordinator = ModelLoadCoordinator(
            dao, generationEngine, FakeGenerationEngine(CompletableDeferred(Unit)), FakeEmbeddingEngine(),
            kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(),
            FakeGenerationDefaults(), CoroutineScope(Dispatchers.Default)
        )

        val result = coordinator.loadManually(broken)

        assertEquals(false, result.success)
        assertEquals(ModelLoadErrorCategory.FILE_MISSING, result.errorCategory)
        assertEquals(0, generationEngine.loadCallCount.get())
        assertTrue(dao.stateFlow.value.first { it.id == broken.id }.isActive)
    }
}

private class FakeResourceMonitor(private val bytes: Long) : ResourceMonitor {
    override fun availableMemoryBytes(): Long = bytes
}

/** §13 resource estimation — the model() helper's temp file is empty, so estimateRequiredBytes
 * falls back to ModelInfo.fileSizeBytes, which is exactly what these tests drive. */
class ModelLoadCoordinatorResourceBudgetTest {
    @Test
    fun blocksLoadThatObviouslyWontFitAndNeverTouchesTheEngine() = runBlocking {
        val generationEngine = FakeGenerationEngine(CompletableDeferred(Unit))
        val huge = model("huge").copy(isActive = true, fileSizeBytes = 20_000_000_000L) // 20GB
        val coordinator = ModelLoadCoordinator(
            FakeModelDao(listOf(huge)), generationEngine, FakeGenerationEngine(CompletableDeferred(Unit)), FakeEmbeddingEngine(),
            kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(),
            FakeGenerationDefaults(), CoroutineScope(Dispatchers.Default),
            FakeResourceMonitor(200L * 1024 * 1024) // 200MB available
        )

        val result = coordinator.ensureLoaded(ModelRole.GENERATION, LoadTrigger.CHAT_SEND)

        assertEquals(false, result.success)
        assertEquals(ModelLoadErrorCategory.INSUFFICIENT_MEMORY, result.errorCategory)
        assertTrue(result.errorMessage!!.contains("GB"))
        assertEquals(0, generationEngine.loadCallCount.get())
    }

    @Test
    fun allowsLoadThatFitsWithinAvailableMemory() = runBlocking {
        val generationEngine = FakeGenerationEngine(CompletableDeferred(Unit))
        val small = model("small").copy(isActive = true, fileSizeBytes = 500L * 1024 * 1024) // 500MB
        val coordinator = ModelLoadCoordinator(
            FakeModelDao(listOf(small)), generationEngine, FakeGenerationEngine(CompletableDeferred(Unit)), FakeEmbeddingEngine(),
            kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(),
            FakeGenerationDefaults(), CoroutineScope(Dispatchers.Default),
            FakeResourceMonitor(8L * 1024 * 1024 * 1024) // 8GB available
        )

        val result = coordinator.ensureLoaded(ModelRole.GENERATION, LoadTrigger.CHAT_SEND)

        assertTrue(result.success)
        assertEquals(1, generationEngine.loadCallCount.get())
    }

    @Test
    fun lowMemoryOverrideLetsTheNativeEngineTryTheLoad() = runBlocking {
        val generationEngine = FakeGenerationEngine(CompletableDeferred(Unit))
        val huge = model("huge_override").copy(isActive = true, fileSizeBytes = 20_000_000_000L)
        val coordinator = ModelLoadCoordinator(
            FakeModelDao(listOf(huge)), generationEngine, FakeGenerationEngine(CompletableDeferred(Unit)), FakeEmbeddingEngine(),
            kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(), kotlinx.coroutines.sync.Mutex(),
            FakeGenerationDefaults(allowLowMemoryLoads = true), CoroutineScope(Dispatchers.Default),
            FakeResourceMonitor(200L * 1024 * 1024)
        )

        val result = coordinator.ensureLoaded(ModelRole.GENERATION, LoadTrigger.CHAT_SEND)

        assertTrue(result.success)
        assertEquals(1, generationEngine.loadCallCount.get())
    }
}
