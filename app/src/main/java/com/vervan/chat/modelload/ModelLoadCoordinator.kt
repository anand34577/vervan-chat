package com.vervan.chat.modelload

import android.util.Log
import com.vervan.chat.data.db.dao.ModelDao
import com.vervan.chat.data.db.entities.BackendChoice
import com.vervan.chat.data.db.entities.ModelBackend
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.reconcileCapabilities
import com.vervan.chat.llm.LlmEngine
import com.vervan.chat.retrieval.EmbeddingBackend
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Picks the replacement default when a role's current default is deleted (or when a role has
 * models but none is active yet) — spec §4.3's priority order collapses to this single
 * comparison in practice: "currently loaded" only matters when it's the *deleted* model itself
 * (which is about to be gone anyway), so what's left is most-recently-loaded, then
 * most-recently-imported. Reassignment reuses whatever license acknowledgment the candidate
 * already has — it must not silently hand default status to a model the user never accepted,
 * so unacknowledged models are excluded rather than re-prompted for automatically (a manual
 * Load/Activate in Model Manager still raises the acknowledgment dialog as before). Extracted
 * as a standalone function so it's unit-testable without any DAO/engine/coroutine setup. */
fun pickReassignmentCandidate(candidates: List<ModelInfo>): ModelInfo? =
    candidates.filter { it.licenseAcknowledged }
        .maxWithOrNull(compareBy<ModelInfo> { it.lastLoadedAt ?: -1L }.thenBy { it.importedAt })

/**
 * Single entry point for loading/unloading/defaulting GENERATION and EMBEDDING models, replacing
 * the hand-rolled "check loadedModelPath, load if needed" logic that used to live separately in
 * ModelManagerViewModel, ChatViewModel, and RealtimeVoiceController. Wraps the existing
 * [LlmEngine]/[com.vervan.chat.retrieval.EmbeddingEngine] singletons and their
 * [AppContainer][com.vervan.chat.VervanApp.AppContainer]-owned mutexes rather than replacing them
 * — callers that need the raw engine for `generate()`/`embed()` still use
 * `AppContainer.withLlm`/`withEmbedding` directly, same as before; this coordinator only owns
 * *loading*.
 *
 * Concurrency: [inFlight] holds at most one [Deferred] per role, guarded by [coordinatorMutex].
 * That mutex is only ever held long enough to read-or-insert the map entry (the native call
 * itself runs inside [scope]'s own `async`, never while [coordinatorMutex] is held) — concurrent
 * callers for the same role all observe and await the same [Deferred], so only the first caller
 * actually starts a load. [generationMutex]/[embeddingMutex] are acquired later, independently,
 * inside [doLoad] — since [coordinatorMutex] is always fully released before either of those is
 * touched, there is no lock-order cycle. Callers must invoke [ensureLoaded]/[loadManually]
 * *outside* any existing `withLlm{}`/`withEmbedding{}` block: [Mutex] isn't reentrant, so nesting
 * would deadlock.
 */
class ModelLoadCoordinator(
    private val modelDao: ModelDao,
    private val generationEngine: GenerationLoadable,
    private val embeddingEngine: EmbeddingLoadable,
    private val generationMutex: Mutex,
    private val embeddingMutex: Mutex,
    private val defaults: GenerationDefaults,
    private val scope: CoroutineScope
) {
    private val coordinatorMutex = Mutex()
    private val inFlight = mutableMapOf<ModelRole, Deferred<EnsureLoadResult>>()

    private val _state = MutableStateFlow(
        mapOf(
            ModelRole.GENERATION to ModelLoadInfo(ModelRole.GENERATION),
            ModelRole.EMBEDDING to ModelLoadInfo(ModelRole.EMBEDDING)
        )
    )
    val state: StateFlow<Map<ModelRole, ModelLoadInfo>> = _state

    fun observeState(role: ModelRole): Flow<ModelLoadInfo> = state.map { it.getValue(role) }

    init {
        // Keeps ModelLoadInfo.defaultModelId mirrored from ModelInfo.isActive — this collector
        // does NOT decide *whether* to auto-activate anything (that decision requires the
        // license-acknowledgment gate, which stays in ModelManagerViewModel.setActive so the
        // acknowledgment dialog keeps popping up for a newly-sole/newly-defaultless model,
        // same as before this coordinator existed).
        scope.launch {
            modelDao.observeModels().collect { list ->
                for (role in ROLES) {
                    val activeId = list.firstOrNull { it.role == role && it.isActive }?.id
                    publishDefault(role, activeId)
                }
            }
        }
    }

    suspend fun ensureLoaded(role: ModelRole, trigger: LoadTrigger, contextTokensOverride: Int? = null): EnsureLoadResult {
        val (deferred, joined) = coordinatorMutex.withLock {
            val existing = inFlight[role]
            if (existing != null) {
                existing to true
            } else {
                val started = scope.async { doLoad(role, explicitModel = null, trigger, contextTokensOverride) }
                inFlight[role] = started
                started to false
            }
        }
        val result = deferred.await()
        coordinatorMutex.withLock { if (inFlight[role] === deferred) inFlight.remove(role) }
        return if (joined) result.copy(joinedInFlight = true) else result
    }

    /** Explicit Model Manager pick — does NOT change the default; combine with [setDefault] if
     * the caller wants that too. If a load is already in-flight for this role, waits for it to
     * finish first, then loads [model] on top (double-load cost, accepted tradeoff — avoids
     * cancelling a coroutine that may be holding the engine mutex mid-native-call). */
    suspend fun loadManually(model: ModelInfo): EnsureLoadResult {
        coordinatorMutex.withLock { inFlight[model.role] }?.await()
        val deferred = coordinatorMutex.withLock {
            val started = scope.async { doLoad(model.role, explicitModel = model, LoadTrigger.MANUAL_MODEL_MANAGER, null) }
            inFlight[model.role] = started
            started
        }
        val result = deferred.await()
        coordinatorMutex.withLock { if (inFlight[model.role] === deferred) inFlight.remove(model.role) }
        return result
    }

    suspend fun unload(role: ModelRole) {
        mutexFor(role).withLock { closeEngine(role) }
        publishUnloaded(role)
    }

    /** Synchronous (`tryLock`-based, not suspend) variant for `Application.onLowMemory()`, which
     * cannot suspend. Returns the roles that actually got unloaded. */
    fun unloadUnderMemoryPressure(): List<ModelRole> {
        val unloaded = mutableListOf<ModelRole>()
        if (generationMutex.tryLock()) {
            try {
                if (generationEngine.loadedModelPath != null) {
                    generationEngine.close()
                    unloaded += ModelRole.GENERATION
                }
            } finally { generationMutex.unlock() }
        }
        if (embeddingMutex.tryLock()) {
            try {
                if (embeddingEngine.loadedModelPath != null) {
                    embeddingEngine.close()
                    unloaded += ModelRole.EMBEDDING
                }
            } finally { embeddingMutex.unlock() }
        }
        unloaded.forEach { publishUnloaded(it) }
        return unloaded
    }

    suspend fun setDefault(model: ModelInfo) {
        modelDao.clearActive(model.role)
        modelDao.upsert(model.copy(isActive = true))
        publishDefault(model.role, model.id)
    }

    /** Closes the role's engine if [model] is the one currently resident — used before deleting
     * a model file so a resident model is never yanked out from under itself. */
    suspend fun forceUnloadIfLoaded(model: ModelInfo) {
        val wasLoaded = mutexFor(model.role).withLock {
            val loadedPath = when (model.role) {
                ModelRole.GENERATION -> generationEngine.loadedModelPath
                ModelRole.EMBEDDING -> embeddingEngine.loadedModelPath
                else -> null
            }
            if (loadedPath == model.filePath) { closeEngine(model.role); true } else false
        }
        if (wasLoaded) publishUnloaded(model.role)
    }

    /** Called after deleting [excludeId] when it was the role's default — picks the next
     * eligible candidate (see [pickReassignmentCandidate]) or leaves the role defaultless. */
    suspend fun reassignDefaultAfterDelete(role: ModelRole, excludeId: String) {
        val candidates = modelDao.getOthersOfRole(role, excludeId)
        val next = pickReassignmentCandidate(candidates)
        if (next != null) setDefault(next) else publishDefault(role, null)
    }

    private suspend fun doLoad(
        role: ModelRole,
        explicitModel: ModelInfo?,
        trigger: LoadTrigger,
        contextTokensOverride: Int?
    ): EnsureLoadResult {
        val model = explicitModel ?: modelDao.getActiveModel(role)
        if (model == null) {
            val anyOfRole = modelDao.observeModels().first().any { it.role == role }
            val result = EnsureLoadResult(
                role, success = false,
                errorCategory = if (anyOfRole) ModelLoadErrorCategory.NO_DEFAULT_SET else ModelLoadErrorCategory.NO_MODEL_INSTALLED,
                errorMessage = if (anyOfRole) "No default model selected for this type." else "No model installed for this type.",
                retryable = false
            )
            publishFailure(role, result)
            return result
        }
        if (!File(model.filePath).exists()) {
            val result = EnsureLoadResult(
                role, success = false, loadedModelId = model.id,
                errorCategory = ModelLoadErrorCategory.FILE_MISSING,
                errorMessage = "${model.displayName}'s file could not be found — it may have been moved or deleted outside the app.",
                retryable = false
            )
            publishFailure(role, result)
            return result
        }
        val alreadyLoadedPath = when (role) {
            ModelRole.GENERATION -> generationEngine.loadedModelPath
            ModelRole.EMBEDDING -> embeddingEngine.loadedModelPath
            else -> null
        }
        if (alreadyLoadedPath == model.filePath) {
            publishReady(role, model.id)
            return EnsureLoadResult(role, success = true, loadedModelId = model.id, loadRequired = false)
        }
        publishLoading(role, model.id)
        return try {
            val updatedModel = mutexFor(role).withLock {
                withContext(Dispatchers.Default) {
                    when (role) {
                        ModelRole.GENERATION -> doLoadGeneration(model, contextTokensOverride)
                        ModelRole.EMBEDDING -> doLoadEmbedding(model)
                        else -> model
                    }
                }
            }
            modelDao.upsert(updatedModel.copy(lastLoadedAt = System.currentTimeMillis()))
            publishReady(role, updatedModel.id)
            EnsureLoadResult(role, success = true, loadedModelId = updatedModel.id, loadRequired = true)
        } catch (t: Throwable) {
            Log.e(TAG, "doLoad() FAILED for ${model.displayName} (role=$role, trigger=$trigger)", t)
            val category = classifyError(t)
            val result = EnsureLoadResult(
                role, success = false, loadedModelId = model.id, loadRequired = true,
                errorCategory = category,
                errorMessage = t.message ?: t::class.simpleName ?: "Unknown error",
                retryable = category != ModelLoadErrorCategory.FILE_MISSING
            )
            publishFailure(role, result)
            result
        }
    }

    private suspend fun doLoadGeneration(model: ModelInfo, contextTokensOverride: Int?): ModelInfo {
        // Falls back to the app-wide Settings defaults exactly like the ChatViewModel logic
        // this replaces did — a model's own contextTokens/maxNumImages/preferredBackend fields
        // (set via Configure) take priority; the global setting only matters for a field the
        // model never overrode.
        val requestedContext = contextTokensOverride ?: model.contextTokens ?: defaults.contextTokenLimit()
        val maxNumImages = model.maxNumImages ?: defaults.maxNumImages()
        val backendPreference = when (model.preferredBackend) {
            BackendChoice.GPU -> LlmEngine.BackendPreference.GPU_ONLY
            BackendChoice.CPU -> LlmEngine.BackendPreference.CPU_ONLY
            BackendChoice.NPU -> LlmEngine.BackendPreference.NPU_ONLY
            BackendChoice.AUTO -> when (defaults.preferredBackend()) {
                "GPU" -> LlmEngine.BackendPreference.GPU_ONLY
                "CPU" -> LlmEngine.BackendPreference.CPU_ONLY
                "NPU" -> LlmEngine.BackendPreference.NPU_ONLY
                else -> LlmEngine.BackendPreference.AUTO
            }
        }
        val backendHint = when (model.lastWorkingBackend) {
            ModelBackend.GPU -> LlmEngine.ModelBackend.GPU
            ModelBackend.CPU -> LlmEngine.ModelBackend.CPU
            ModelBackend.NPU -> LlmEngine.ModelBackend.NPU
            else -> null
        }
        val useMtp = model.mtpEnabled
        val result = try {
            generationEngine.loadModel(model.filePath, requestedContext, maxNumImages, backendPreference, backendHint, useMtp)
        } catch (first: Throwable) {
            if (requestedContext <= MIN_CONTEXT_RETRY_TOKENS) throw first
            generationEngine.loadModel(model.filePath, MIN_CONTEXT_RETRY_TOKENS, maxNumImages, backendPreference, backendHint, useMtp)
        }
        val dbBackend = when (result.backend) {
            LlmEngine.ModelBackend.GPU -> ModelBackend.GPU
            LlmEngine.ModelBackend.NPU -> ModelBackend.NPU
            else -> ModelBackend.CPU
        }
        val (reconciled, _) = model.reconcileCapabilities(
            dbBackend, generationEngine.visionEnabled, generationEngine.audioEnabled,
            mtpAttempted = useMtp, mtpActive = generationEngine.speculativeDecodingActive
        )
        return reconciled
    }

    private fun doLoadEmbedding(model: ModelInfo): ModelInfo {
        embeddingEngine.loadModel(model.filePath, model.tokenizerPath)
        val dbBackend = if (embeddingEngine.activeBackend == EmbeddingBackend.GPU) ModelBackend.GPU else ModelBackend.CPU
        return model.copy(lastWorkingBackend = dbBackend)
    }

    private fun closeEngine(role: ModelRole) {
        when (role) {
            ModelRole.GENERATION -> generationEngine.close()
            ModelRole.EMBEDDING -> embeddingEngine.close()
            else -> {}
        }
    }

    private fun mutexFor(role: ModelRole): Mutex = if (role == ModelRole.GENERATION) generationMutex else embeddingMutex

    private fun publishDefault(role: ModelRole, modelId: String?) {
        _state.update { it + (role to it.getValue(role).copy(defaultModelId = modelId)) }
    }

    private fun publishLoading(role: ModelRole, modelId: String) {
        _state.update { it + (role to it.getValue(role).copy(phase = ModelLoadPhase.LOADING, loadingModelId = modelId, error = null)) }
    }

    private fun publishReady(role: ModelRole, modelId: String) {
        _state.update {
            it + (role to it.getValue(role).copy(
                phase = ModelLoadPhase.READY, currentModelId = modelId, loadingModelId = null,
                lastSuccessfulModelId = modelId, error = null
            ))
        }
    }

    private fun publishFailure(role: ModelRole, result: EnsureLoadResult) {
        // Both LlmEngine.load() and EmbeddingEngine.load() close() any previous session before
        // attempting the new one, so a failed attempt always leaves the engine unloaded — never
        // a stale "previous model still resident" state.
        _state.update {
            it + (role to it.getValue(role).copy(
                phase = ModelLoadPhase.FAILED, currentModelId = null, loadingModelId = null, error = result
            ))
        }
    }

    private fun publishUnloaded(role: ModelRole) {
        _state.update { it + (role to it.getValue(role).copy(phase = ModelLoadPhase.UNLOADED, currentModelId = null, loadingModelId = null)) }
    }

    private fun classifyError(t: Throwable): ModelLoadErrorCategory = when (t) {
        is OutOfMemoryError -> ModelLoadErrorCategory.INSUFFICIENT_MEMORY
        is SecurityException -> ModelLoadErrorCategory.PERMISSION_DENIED
        is kotlinx.coroutines.CancellationException -> ModelLoadErrorCategory.CANCELLED
        is IllegalArgumentException -> ModelLoadErrorCategory.FILE_CORRUPTED
        is IllegalStateException -> ModelLoadErrorCategory.INCOMPATIBLE_BACKEND
        else -> ModelLoadErrorCategory.UNKNOWN
    }

    companion object {
        private const val TAG = "ModelLoadCoordinator"
        private val ROLES = listOf(ModelRole.GENERATION, ModelRole.EMBEDDING)
        private const val MIN_CONTEXT_RETRY_TOKENS = 2048
    }
}
