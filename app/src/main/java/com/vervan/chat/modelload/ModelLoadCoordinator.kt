package com.vervan.chat.modelload

import android.util.Log
import com.vervan.chat.data.db.dao.ModelDao
import com.vervan.chat.data.db.entities.BackendChoice
import com.vervan.chat.data.db.entities.ModelBackend
import com.vervan.chat.data.db.entities.ModelEngine
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.reconcileCapabilities
import com.vervan.chat.llm.LlamaCppEngine
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
 * actually starts a load. The per-engine mutexes are acquired later, independently, inside
 * [doLoad] — since [coordinatorMutex] is always fully released before any of those is touched,
 * there is no lock-order cycle. Callers must invoke [ensureLoaded]/[loadManually] *outside* any
 * existing `withLlm{}`/`withLlamaCpp{}`/`withEmbedding{}` block: [Mutex] isn't reentrant, so
 * nesting would deadlock.
 *
 * GENERATION spans **two** engines (LiteRT-LM and llama.cpp — see [ModelEngine]), routed per
 * `ModelInfo.engine`, but "one loaded GENERATION model" is still an app-wide invariant: loading
 * model X on one engine force-closes whatever the *other* engine currently has loaded, so a
 * LiteRT-LM model and a GGUF model can never both be resident at once. EMBEDDING stays
 * single-engine (GGUF isn't an embedding format in this app).
 */
class ModelLoadCoordinator(
    private val modelDao: ModelDao,
    private val litertEngine: GenerationLoadable,
    private val llamaCppEngine: GenerationLoadable,
    private val embeddingEngine: EmbeddingLoadable,
    private val litertMutex: Mutex,
    private val llamaCppMutex: Mutex,
    private val embeddingMutex: Mutex,
    private val defaults: GenerationDefaults,
    private val scope: CoroutineScope,
    // Defaults to "unconstrained" rather than being required, so every existing call site
    // (including the coordinator's own unit tests, which predate §13) keeps compiling and
    // behaving exactly as before unless it deliberately opts into a real memory check.
    private val resourceMonitor: ResourceMonitor = UnconstrainedResourceMonitor
) {
    private val coordinatorMutex = Mutex()

    // §9.2 follow-up: once a load's watchdog timeout fires, the blocking native call underneath
    // keeps running on nativeLoadDispatcher with no way to cancel it — releasing the engine mutex
    // (withLock's normal finally behavior) would let a retried load call close()/loadModel()
    // concurrently with that still-running orphaned call on the very same engine instance. These
    // flags make that engine permanently off-limits for the rest of the process once it happens,
    // rather than racing with an orphaned call we have no way to detect the completion of.
    @Volatile private var litertPoisoned = false
    @Volatile private var llamaCppPoisoned = false
    @Volatile private var embeddingPoisoned = false

    private fun isPoisoned(engine: GenerationLoadable): Boolean =
        if (engine === llamaCppEngine) llamaCppPoisoned else litertPoisoned

    private fun poison(engine: GenerationLoadable) {
        if (engine === llamaCppEngine) llamaCppPoisoned = true else litertPoisoned = true
    }

    private fun engineUnavailableResult(role: ModelRole, model: ModelInfo): EnsureLoadResult {
        val result = EnsureLoadResult(
            role, success = false, loadedModelId = model.id,
            errorCategory = ModelLoadErrorCategory.ENGINE_UNAVAILABLE,
            errorMessage = "${model.displayName}'s runtime is stuck after a previous load timed out and can't be used again safely — restart the app to continue.",
            retryable = false
        )
        publishFailure(role, result)
        return result
    }
    private data class LoadRequest(
        val role: ModelRole,
        val modelId: String?,
        val filePath: String?,
        val engine: ModelEngine?,
        val configHash: Int?,
        val contextTokensOverride: Int?
    )
    private data class InFlightLoad(val request: LoadRequest, val deferred: Deferred<EnsureLoadResult>)
    // §10 priority — everything needed to actually start [request] as the next in-flight load,
    // captured at registration time since the coroutine that ends up starting it (whichever one
    // wins the race right after the current in-flight load completes) is very often *not* the
    // same coroutine that originally wanted it.
    private data class PendingWaiter(
        val request: LoadRequest,
        val trigger: LoadTrigger,
        val model: ModelInfo?,
        val contextTokensOverride: Int?
    )
    private val pendingWaiters = mutableMapOf<ModelRole, MutableList<PendingWaiter>>()

    private val inFlight = mutableMapOf<ModelRole, InFlightLoad>()
    private val lastSuccessfulRequest = mutableMapOf<ModelRole, LoadRequest>()

    private val _state = MutableStateFlow(
        mapOf(
            ModelRole.GENERATION to ModelLoadInfo(ModelRole.GENERATION),
            ModelRole.EMBEDDING to ModelLoadInfo(ModelRole.EMBEDDING)
        )
    )

    // §23 "Memory pressure changed" event — a plain StateFlow rather than a one-shot signal so a
    // screen that starts observing mid-pressure-event (e.g. navigates in right after a trim
    // callback) still sees the current level instead of missing it.
    private val _memoryPressure = MutableStateFlow(MemoryPressureLevel.NORMAL)
    val memoryPressure: StateFlow<MemoryPressureLevel> = _memoryPressure
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
        return requestLoad(role, modelDao.getActiveModel(role), trigger, contextTokensOverride)
    }

    /** Loads the exact model selected by a chat/tool/API caller. A role-only load resolves the
     * current app default, while this overload preserves per-chat and explicit API selections. */
    suspend fun ensureLoaded(model: ModelInfo, trigger: LoadTrigger, contextTokensOverride: Int? = null): EnsureLoadResult =
        requestLoad(model.role, model, trigger, contextTokensOverride)

    private suspend fun requestLoad(
        role: ModelRole,
        model: ModelInfo?,
        trigger: LoadTrigger,
        contextTokensOverride: Int?
    ): EnsureLoadResult {
        val request = LoadRequest(
            role, model?.id, model?.filePath, model?.engine,
            model?.let { loadConfigHash(it, contextTokensOverride) }, contextTokensOverride
        )
        coordinatorMutex.withLock {
            if (lastSuccessfulRequest[role] == request && isResident(request)) {
                publishReady(role, model!!.id)
                return EnsureLoadResult(role, success = true, loadedModelId = model.id, loadRequired = false)
            }
        }
        var joinedAny = false
        while (true) {
            val (entry, joined) = coordinatorMutex.withLock {
                val existing = inFlight[role]
                if (existing != null) {
                    // §10 priority: a load for a *different* request is already running and can't
                    // be interrupted (it's a blocking native call — see the watchdog dispatcher
                    // comment above for why that's a hard constraint, not an oversight). Register
                    // this request so that once the current one finishes, the highest-priority
                    // *waiting* request goes next — not just whichever of possibly several waiting
                    // coroutines happens to win the race to re-acquire this mutex first.
                    if (existing.request != request) {
                        val waiters = pendingWaiters.getOrPut(role) { mutableListOf() }
                        if (waiters.none { it.request == request }) {
                            waiters += PendingWaiter(request, trigger, model, contextTokensOverride)
                        }
                    }
                    existing to true
                } else {
                    // Nothing in flight right now. If other requests are already queued up for
                    // this role (this coroutine is simply the first to reach this point after the
                    // previous in-flight load finished — not necessarily the highest-priority
                    // waiter itself), start the highest-priority one instead of blindly running
                    // with this call's own `request`/`trigger`/`model`. Manual/validation always
                    // outranks anything automatic, matching §10's stated priority order; ties keep
                    // FIFO order (List.minByOrNull returns the first minimum).
                    val waiters = pendingWaiters[role]
                    val winner = waiters?.minByOrNull { triggerPriority(it.trigger) }
                    val (useRequest, useTrigger, useModel, useContext) = if (winner != null) {
                        waiters.remove(winner)
                        winner
                    } else PendingWaiter(request, trigger, model, contextTokensOverride)
                    val started = InFlightLoad(
                        useRequest,
                        scope.async { doLoad(role, explicitModel = useModel, useTrigger, useContext) }
                    )
                    inFlight[role] = started
                    started to false
                }
            }
            val result = try {
                entry.deferred.await()
            } finally {
                coordinatorMutex.withLock {
                    if (inFlight[role] === entry && entry.deferred.isCompleted) inFlight.remove(role)
                }
            }
            if (entry.request != request) {
                // A different explicit model/config was already loading. Let it finish safely,
                // then perform this request instead of returning the wrong model to the caller.
                joinedAny = true
                continue
            }
            if (result.success) {
                val persisted = result.loadedModelId?.let { modelDao.get(it) }
                val successfulRequest = if (persisted != null) request.copy(
                    filePath = persisted.filePath,
                    engine = persisted.engine,
                    configHash = loadConfigHash(persisted, contextTokensOverride)
                ) else request
                coordinatorMutex.withLock { lastSuccessfulRequest[role] = successfulRequest }
            }
            return if (joined || joinedAny) result.copy(joinedInFlight = true) else result
        }
    }

    /** §10's stated priority order: explicit manual selection > active foreground user operation
     * > automatic screen-entry request > background/preloading request. Lower number = higher
     * priority (wins the race to start next — see the `pendingWaiters` election in [requestLoad]).
     * VALIDATION sits alongside MANUAL_MODEL_MANAGER — both represent the user (or the import flow
     * acting on their behalf) dealing with one specific, deliberately-chosen model instance. */
    private fun triggerPriority(trigger: LoadTrigger): Int = when (trigger) {
        LoadTrigger.MANUAL_MODEL_MANAGER, LoadTrigger.VALIDATION -> 0
        LoadTrigger.CHAT_SEND, LoadTrigger.VOICE_SESSION -> 1
        LoadTrigger.CHAT_AUTOLOAD -> 2
        LoadTrigger.RAG_RETRIEVAL -> 3
    }

    private fun isResident(request: LoadRequest): Boolean {
        val path = request.filePath ?: return false
        return when (request.role) {
            ModelRole.GENERATION -> when (request.engine) {
                ModelEngine.LLAMA_CPP -> llamaCppEngine.loadedModelPath == path
                ModelEngine.LITERT_LM -> litertEngine.loadedModelPath == path
                else -> false
            }
            ModelRole.EMBEDDING -> embeddingEngine.loadedModelPath == path
            else -> false
        }
    }

    /** Resolves the context/maxTokens to load [model] with. An explicit [contextTokensOverride]
     * always wins. Otherwise, if [model] is already the one resident on its engine, reuses
     * whatever context is actually loaded — recomputing from settings/model fields instead (as
     * this used to) can land on a different number than what's resident (e.g. a profile-scaled
     * autoload context vs. the raw default), which looks like a config change and forces a
     * pointless full unload+reload right as generation is about to start. Only falls back to a
     * fresh computation when nothing matching is actually loaded yet. */
    private suspend fun resolveContextTokens(model: ModelInfo, contextTokensOverride: Int?): Int {
        if (contextTokensOverride != null) return contextTokensOverride
        val engine = generationEngineFor(model)
        if (engine.loadedModelPath == model.filePath) {
            engine.loadedContextTokens?.let { return it }
        }
        return model.contextTokens ?: defaults.contextTokenLimit()
    }

    private suspend fun loadConfigHash(model: ModelInfo, contextTokensOverride: Int?): Int = if (model.role == ModelRole.EMBEDDING) {
        listOf(model.tokenizerPath).hashCode()
    } else {
        listOf(
            resolveContextTokens(model, contextTokensOverride),
            model.maxNumImages ?: defaults.maxNumImages(),
            model.preferredBackend,
            if (model.preferredBackend == BackendChoice.AUTO) defaults.preferredBackend() else null,
            model.mtpEnabled, model.mmprojPath, model.gpuLayerCount, model.cpuThreads,
            model.nBatch, model.nUbatch, model.useMlock, model.flashAttention,
            model.kvCacheType, model.vulkanDeviceIndex, model.ropeFreqBase,
            model.ropeFreqScale, model.loraPath, model.loraScale,
            if (model.engine == ModelEngine.LLAMA_CPP) defaults.cpuThreads() else null,
            if (model.engine == ModelEngine.LLAMA_CPP) defaults.nBatch() else null,
            if (model.engine == ModelEngine.LLAMA_CPP) defaults.nUbatch() else null,
            if (model.engine == ModelEngine.LLAMA_CPP) defaults.useMlock() else null,
            if (model.engine == ModelEngine.LLAMA_CPP) defaults.flashAttentionMode() else null,
            if (model.engine == ModelEngine.LLAMA_CPP) defaults.kvCacheType() else null,
            if (model.engine == ModelEngine.LLAMA_CPP) defaults.vulkanDeviceIndex() else null
        ).hashCode()
    }

    /** Explicit Model Manager pick — does NOT change the default; combine with [setDefault] if
     * the caller wants that too. If a load is already in-flight for this role, waits for it to
     * finish first, then loads [model] on top (double-load cost, accepted tradeoff — avoids
     * cancelling a coroutine that may be holding the engine mutex mid-native-call). */
    suspend fun loadManually(model: ModelInfo): EnsureLoadResult {
        return requestLoad(model.role, model, LoadTrigger.MANUAL_MODEL_MANAGER, null)
    }

    suspend fun unload(role: ModelRole) {
        when (role) {
            // No specific model here (unlike forceUnloadIfLoaded) — close whichever of the two
            // generation engines actually has something loaded; normally only one ever does,
            // thanks to doLoad's cross-engine close-on-load, but this stays symmetric/defensive
            // rather than assuming that invariant always held.
            ModelRole.GENERATION -> {
                // Interrupt any in-flight response first — generate() holds {litert,llamaCpp}Mutex
                // for its whole streaming duration, so without this, unload() would just suspend
                // on withLock until generation finished on its own (looking like Unload does
                // nothing while a response is streaming).
                litertEngine.cancelActiveGeneration()
                llamaCppEngine.cancelActiveGeneration()
                litertMutex.withLock { if (litertEngine.loadedModelPath != null) litertEngine.close() }
                llamaCppMutex.withLock { if (llamaCppEngine.loadedModelPath != null) llamaCppEngine.close() }
            }
            ModelRole.EMBEDDING -> embeddingMutex.withLock { embeddingEngine.close() }
            else -> {}
        }
        publishUnloaded(role)
    }

    /** Synchronous (`tryLock`-based, not suspend) variant for `Application.onLowMemory()`, which
     * cannot suspend. Returns the roles that actually got unloaded. */
    fun unloadUnderMemoryPressure(): List<ModelRole> {
        val unloaded = mutableListOf<ModelRole>()
        var generationUnloaded = false
        if (litertMutex.tryLock()) {
            try {
                if (litertEngine.loadedModelPath != null) { litertEngine.close(); generationUnloaded = true }
            } finally { litertMutex.unlock() }
        }
        if (llamaCppMutex.tryLock()) {
            try {
                if (llamaCppEngine.loadedModelPath != null) { llamaCppEngine.close(); generationUnloaded = true }
            } finally { llamaCppMutex.unlock() }
        }
        if (generationUnloaded) unloaded += ModelRole.GENERATION
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

    /** §6.2 entry point for `Application.onTrimMemory`/`onLowMemory` — both are synchronous
     * `ComponentCallbacks`/`ComponentCallbacks2` methods that cannot suspend, so this can't
     * either. [MemoryPressureLevel.MODERATE] only updates [memoryPressure] for any observer that
     * cares (§23); this app has no speculative/background preload of a non-default model to
     * cancel, so there's nothing else to *do* at that tier yet. [MemoryPressureLevel.CRITICAL]
     * additionally proactively unloads via [unloadUnderMemoryPressure] — returns the roles that
     * got unloaded so the caller can decide whether/how to notify the user (VervanApp does, for
     * GENERATION specifically, since that's the one the user is likely mid-conversation with). */
    fun onMemoryPressure(level: MemoryPressureLevel): List<ModelRole> {
        _memoryPressure.value = level
        return if (level == MemoryPressureLevel.CRITICAL) unloadUnderMemoryPressure() else emptyList()
    }

    suspend fun setDefault(model: ModelInfo) {
        modelDao.clearActive(model.role)
        modelDao.upsert(model.copy(isActive = true))
        publishDefault(model.role, model.id)
    }

    /** Closes [model]'s specific engine if [model] is the one currently resident — used before
     * deleting a model file so a resident model is never yanked out from under itself. */
    suspend fun forceUnloadIfLoaded(model: ModelInfo) {
        // Interrupt any in-flight response first, same reasoning as unload() above — generate()
        // holds this engine's mutex for its whole streaming duration, so without this, a delete/
        // update on a model that's actively generating would silently hang on withLock until
        // generation finished on its own instead of the cancel-in-flight-operations behavior
        // Model Loading Strategy §4.4 step 2 requires for this exact combined case.
        if (model.role == ModelRole.GENERATION) generationEngineFor(model).cancelActiveGeneration()
        val wasLoaded = when (model.role) {
            ModelRole.GENERATION -> {
                val engine = generationEngineFor(model)
                generationMutexFor(model).withLock {
                    if (engine.loadedModelPath == model.filePath) { engine.close(); true } else false
                }
            }
            ModelRole.EMBEDDING -> embeddingMutex.withLock {
                if (embeddingEngine.loadedModelPath == model.filePath) { embeddingEngine.close(); true } else false
            }
            else -> false
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
        contextTokensOverride: Int?,
        // §11.2 loop guard — true only on the one automatic retry below, so a device with
        // every model of a role simultaneously missing its file fails cleanly on the second
        // attempt instead of recursing through every remaining candidate.
        retriedAfterUnavailable: Boolean = false
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
            // §11.2: a *default* model whose file vanished outside the app (moved, deleted, SD
            // card ejected) is a non-temporary unavailability, not a one-off glitch — reassign
            // default away from it and retry once with whatever's next, rather than leaving the
            // app failing to auto-load anything on every future chat until the user happens to
            // notice and fix it manually in Model Manager.
            //
            // Gated on trigger, not on explicitModel being null — every automatic-loading call
            // site (ChatViewModel's own CHAT_AUTOLOAD/CHAT_SEND, RAG_RETRIEVAL, VOICE_SESSION)
            // already resolves a concrete ModelInfo itself before calling ensureLoaded, so
            // explicitModel is non-null there too; MANUAL_MODEL_MANAGER is the one trigger that
            // means "the user explicitly picked this exact model instance" (Model Manager's Load
            // button, and the validate/benchmark flows that route through it) — silently
            // substituting a different model than the one they asked for would violate §10's
            // "explicit selection must not be silently replaced".
            if (trigger != LoadTrigger.MANUAL_MODEL_MANAGER && model.isActive && !retriedAfterUnavailable) {
                Log.w(TAG, "doLoad() default ${model.displayName} (role=$role) file is missing; reassigning default and retrying once")
                reassignDefaultAfterDelete(role, model.id)
                return doLoad(role, explicitModel = null, trigger, contextTokensOverride, retriedAfterUnavailable = true)
            }
            val result = EnsureLoadResult(
                role, success = false, loadedModelId = model.id,
                errorCategory = ModelLoadErrorCategory.FILE_MISSING,
                errorMessage = "${model.displayName}'s file could not be found — it may have been moved or deleted outside the app.",
                retryable = false
            )
            publishFailure(role, result)
            return result
        }
        if (role == ModelRole.GENERATION && isPoisoned(generationEngineFor(model))) {
            return engineUnavailableResult(role, model)
        }
        if (role == ModelRole.EMBEDDING && embeddingPoisoned) {
            return engineUnavailableResult(role, model)
        }
        if (role == ModelRole.GENERATION || role == ModelRole.EMBEDDING) {
            val contextTokens = if (role == ModelRole.GENERATION) resolveContextTokens(model, contextTokensOverride) else 0
            checkResourceBudget(role, model, contextTokens)?.let { insufficient ->
                publishFailure(role, insufficient)
                return insufficient
            }
        }
        publishLoading(role, model.id)
        return try {
            val (updatedModel, delegateFallback) = when (role) {
                ModelRole.GENERATION -> {
                    // "One loaded GENERATION model" is an app-wide invariant spanning both
                    // engines — if the *other* engine currently has something resident, close
                    // it first (each engine only ever frees its own previous model internally).
                    val (otherEngine, otherMutex) = otherGenerationEngineAndMutex(model)
                    if (otherEngine.loadedModelPath != null) {
                        // Same reasoning as unload()/forceUnloadIfLoaded(): generate() holds this
                        // mutex for its whole streaming duration, so without cancelling first, a
                        // load that switches generation engines would silently hang on withLock
                        // until whatever's mid-response on the other engine finishes on its own.
                        otherEngine.cancelActiveGeneration()
                        otherMutex.withLock { otherEngine.close() }
                    }
                    val engine = generationEngineFor(model)
                    generationMutexFor(model).withLock {
                        try {
                            kotlinx.coroutines.withTimeout(LOAD_WATCHDOG_TIMEOUT_MS) {
                                withContext(nativeLoadDispatcher) { doLoadGeneration(model, contextTokensOverride) }
                            }
                        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                            // Still holding this engine's mutex here — poison it before releasing
                            // so no later caller can race the orphaned native call underneath.
                            poison(engine)
                            throw timeout
                        }
                    }
                }
                ModelRole.EMBEDDING -> embeddingMutex.withLock {
                    try {
                        kotlinx.coroutines.withTimeout(LOAD_WATCHDOG_TIMEOUT_MS) {
                            withContext(nativeLoadDispatcher) { doLoadEmbedding(model) }
                        }
                    } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                        embeddingPoisoned = true
                        throw timeout
                    }
                }
                else -> model to false
            }
            modelDao.upsert(updatedModel.copy(lastLoadedAt = System.currentTimeMillis()))
            publishReady(role, updatedModel.id)
            EnsureLoadResult(role, success = true, loadedModelId = updatedModel.id, loadRequired = true, delegateFallback = delegateFallback)
        } catch (t: Throwable) {
            Log.e(TAG, "doLoad() FAILED for ${model.displayName} (role=$role, trigger=$trigger) — ${diagnosticContext(model, trigger)}", t)
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

    private suspend fun doLoadGeneration(model: ModelInfo, contextTokensOverride: Int?): Pair<ModelInfo, Boolean> {
        val engine = generationEngineFor(model)
        // supportsVision/supportsAudio go from null ("never tried") to false the first time a
        // real load attempt proves the modality isn't in this model's package (see
        // reconcileCapabilities below) — once that's known, every later load can skip straight
        // past the doomed FULL/NO_AUDIO attempts instead of re-discovering the same failure.
        if (engine is LlmEngine) {
            engine.pendingKnownAudioSupported = model.supportsAudio
            engine.pendingKnownVisionSupported = model.supportsVision
        }
        // Only llama.cpp needs the mmproj path, and it's not part of GenerationLoadable's
        // shared signature (LiteRT-LM has no equivalent concept — its vision encoder is bundled
        // in the single .task/.litertlm package) — set as a side channel immediately before the
        // call, safe because this whole block already runs under that engine's own mutex.
        if (engine is LlamaCppEngine) {
            engine.pendingMmprojPath = model.mmprojPath
            engine.pendingGpuLayerCount = model.gpuLayerCount
            engine.pendingLayerCount = model.layerCount
            engine.pendingOptions = com.vervan.chat.llm.LlamaLoadOptions(
                cpuThreads = model.cpuThreads ?: defaults.cpuThreads(),
                nBatch = model.nBatch ?: defaults.nBatch(),
                nUbatch = model.nUbatch ?: defaults.nUbatch(),
                useMlock = model.useMlock ?: defaults.useMlock(),
                flashAttention = model.flashAttention ?: when (defaults.flashAttentionMode()) {
                    "ON" -> true
                    "OFF" -> false
                    else -> null
                },
                kvCacheType = model.kvCacheType ?: defaults.kvCacheType(),
                vulkanDeviceIndex = model.vulkanDeviceIndex ?: defaults.vulkanDeviceIndex(),
                ropeFreqBase = model.ropeFreqBase ?: 0f,
                ropeFreqScale = model.ropeFreqScale ?: 0f,
                loraPath = model.loraPath,
                loraScale = model.loraScale ?: 1.0f
            )
        }
        // Falls back to the app-wide Settings defaults exactly like the ChatViewModel logic
        // this replaces did — a model's own contextTokens/maxNumImages/preferredBackend fields
        // (set via Configure) take priority; the global setting only matters for a field the
        // model never overrode.
        val requestedContext = resolveContextTokens(model, contextTokensOverride)
        val maxNumImages = model.maxNumImages ?: defaults.maxNumImages()
        val backendPreference = when (model.preferredBackend) {
            BackendChoice.GPU -> LlmEngine.BackendPreference.GPU_ONLY
            BackendChoice.CPU -> LlmEngine.BackendPreference.CPU_ONLY
            // llama.cpp has no NPU backend — NPU_ONLY is only ever set on a LiteRT-LM model
            // (the UI hides that chip for llama.cpp models, see ModelManagerScreen).
            BackendChoice.NPU -> if (model.engine == ModelEngine.LLAMA_CPP) {
                LlmEngine.BackendPreference.AUTO
            } else LlmEngine.BackendPreference.NPU_ONLY
            BackendChoice.AUTO -> when (defaults.preferredBackend()) {
                "GPU" -> LlmEngine.BackendPreference.GPU_ONLY
                "CPU" -> LlmEngine.BackendPreference.CPU_ONLY
                "NPU" -> if (model.engine == ModelEngine.LLAMA_CPP) {
                    LlmEngine.BackendPreference.AUTO
                } else LlmEngine.BackendPreference.NPU_ONLY
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
            engine.loadModel(model.filePath, requestedContext, maxNumImages, backendPreference, backendHint, useMtp)
        } catch (first: Throwable) {
            if (requestedContext <= MIN_CONTEXT_RETRY_TOKENS) throw first
            engine.loadModel(model.filePath, MIN_CONTEXT_RETRY_TOKENS, maxNumImages, backendPreference, backendHint, useMtp)
        }
        val dbBackend = when (result.backend) {
            LlmEngine.ModelBackend.GPU -> ModelBackend.GPU
            LlmEngine.ModelBackend.NPU -> ModelBackend.NPU
            else -> ModelBackend.CPU
        }
        val (reconciled, _) = model.reconcileCapabilities(
            dbBackend, engine.visionEnabled, engine.audioEnabled,
            mtpAttempted = useMtp, mtpActive = engine.speculativeDecodingActive
        )
        return reconciled to result.fellBackToCpu
    }

    private fun doLoadEmbedding(model: ModelInfo): Pair<ModelInfo, Boolean> {
        embeddingEngine.loadModel(model.filePath, model.tokenizerPath)
        val dbBackend = if (embeddingEngine.activeBackend == EmbeddingBackend.GPU) ModelBackend.GPU else ModelBackend.CPU
        // No GPU→CPU auto-fallback ladder on the embedding path today, so this is always false —
        // kept as a Pair for symmetry with doLoadGeneration so doLoad's `when` stays uniform.
        return model.copy(lastWorkingBackend = dbBackend) to false
    }

    private fun generationEngineFor(model: ModelInfo): GenerationLoadable =
        if (model.engine == ModelEngine.LLAMA_CPP) llamaCppEngine else litertEngine

    private fun generationMutexFor(model: ModelInfo): Mutex =
        if (model.engine == ModelEngine.LLAMA_CPP) llamaCppMutex else litertMutex

    private fun otherGenerationEngineAndMutex(model: ModelInfo): Pair<GenerationLoadable, Mutex> =
        if (model.engine == ModelEngine.LLAMA_CPP) litertEngine to litertMutex else llamaCppEngine to llamaCppMutex

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
        // Must precede the plain CancellationException branch below — TimeoutCancellationException
        // is a subtype of it, and a `when` takes the first matching arm.
        is kotlinx.coroutines.TimeoutCancellationException -> ModelLoadErrorCategory.TIMED_OUT
        is OutOfMemoryError -> ModelLoadErrorCategory.INSUFFICIENT_MEMORY
        is SecurityException -> ModelLoadErrorCategory.PERMISSION_DENIED
        is kotlinx.coroutines.CancellationException -> ModelLoadErrorCategory.CANCELLED
        is IllegalArgumentException -> ModelLoadErrorCategory.FILE_CORRUPTED
        is IllegalStateException -> ModelLoadErrorCategory.INCOMPATIBLE_BACKEND
        else -> ModelLoadErrorCategory.UNKNOWN
    }

    /** §13 pre-load resource estimate. Neither native runtime reports its actual working-set
     * size to this layer, so weight memory is proxied from the file itself (dominant cost either
     * way, and per §13.1 an mmap'd llama.cpp weight still counts toward headroom even though it's
     * OS-reclaimable — reclaiming it mid-inference costs latency, not correctness, but it still
     * has to fit to be mapped in the first place). KV-cache is estimated per-token, then scaled by
     * context length — still a heuristic either way (no per-model hidden-dim/head-count is
     * available at this layer to compute the textbook `2 × layers × heads × head_dim × bytes`
     * formula exactly), but two tiers of it:
     *   - [ModelInfo.layerCount] known (llama.cpp models get this from `readModelInfo()` during
     *     Configure's validate-then-close pass, §18.4): scales off actual per-model layer count,
     *     a real measured property of *this* model rather than a guess.
     *   - Unknown (LiteRT-LM models — no native metadata surface for this today, or a llama.cpp
     *     model never yet validated): falls back to scaling off the weight file size as a proxy
     *     for model size class (bigger checkpoint implies more layers/wider heads).
     * Either way this exists to catch "this obviously will not fit" before burning 10-30s on a
     * doomed native load, not to precisely budget memory — deliberately erring toward
     * overestimating (better to occasionally block a load that would've just barely fit than
     * undercount and let an OOM through).
     */
    private fun estimateRequiredBytes(model: ModelInfo, contextTokens: Int): Long {
        val weightBytes = File(model.filePath).takeIf { it.isFile }?.length()?.takeIf { it > 0 } ?: model.fileSizeBytes
        val kvCacheBytes = model.layerCount?.let { layers ->
            layers.toLong() * KV_CACHE_BYTES_PER_LAYER_PER_TOKEN * contextTokens
        } ?: ((weightBytes / KV_CACHE_FILE_SIZE_SCALE_DIVISOR) * contextTokens)
        return weightBytes + kvCacheBytes + RUNTIME_OVERHEAD_BYTES
    }

    /** §13.3 combined budget — GENERATION and EMBEDDING can be resident simultaneously (§3), so a
     * check for one role must still count whatever's already loaded in the *other* one. Proxies
     * that resident model's footprint from its own file size too, for the same reason as above. */
    private fun residentWeightBytes(excludingRole: ModelRole): Long {
        var total = 0L
        if (excludingRole != ModelRole.GENERATION) {
            (litertEngine.loadedModelPath ?: llamaCppEngine.loadedModelPath)?.let { total += File(it).length() }
        }
        if (excludingRole != ModelRole.EMBEDDING) {
            embeddingEngine.loadedModelPath?.let { total += File(it).length() }
        }
        return total
    }

    /** Returns a failure result when [model] clearly will not fit, or null to proceed. Named the
     * specific numbers in the message per §13.4's example format ("needs ~X; ~Y is available") —
     * a bare "insufficient memory" tells the user nothing they can act on. */
    private fun checkResourceBudget(role: ModelRole, model: ModelInfo, contextTokens: Int): EnsureLoadResult? {
        val required = estimateRequiredBytes(model, contextTokens) + residentWeightBytes(excludingRole = role)
        val available = resourceMonitor.availableMemoryBytes()
        if (required <= available) return null
        fun gb(bytes: Long) = "%.1f".format(bytes / 1_073_741_824.0)
        return EnsureLoadResult(
            role, success = false, loadedModelId = model.id,
            errorCategory = ModelLoadErrorCategory.INSUFFICIENT_MEMORY,
            errorMessage = "${model.displayName} needs ~${gb(required)}GB of memory; ~${gb(available)}GB is available.",
            retryable = true
        )
    }

    /** §9.3 crash/timeout diagnostics — deliberately independent of the failed call itself (it
     * may have hung or died without writing anything), built purely from what the coordinator
     * already knows: the model's own identity/backend/engine fields plus static device info. Not
     * gated to timeouts only — cheap enough to attach to every load failure, and useful for the
     * genuinely-crashed-JNI-call case too (§9.4's "capture what it safely can"). */
    private fun diagnosticContext(model: ModelInfo, trigger: LoadTrigger): String =
        "model=${model.displayName} engine=${model.engine} backend=${model.preferredBackend} " +
            "lastWorkingBackend=${model.lastWorkingBackend} trigger=$trigger " +
            "device=${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}"

    companion object {
        private const val TAG = "ModelLoadCoordinator"
        private val ROLES = listOf(ModelRole.GENERATION, ModelRole.EMBEDDING)
        private const val MIN_CONTEXT_RETRY_TOKENS = 2048
        // §9.2 watchdog — generous on purpose. Observed real loads (worst case: GPU shader
        // recompilation across a multi-attempt capability probe) taking up to ~30s; this exists
        // to catch a genuinely stuck native call (corrupted file, wedged delegate), not to
        // pressure a slow-but-progressing one. A model that legitimately needs longer than this
        // to load is a product decision to raise, not silently wait on forever.
        private const val LOAD_WATCHDOG_TIMEOUT_MS = 180_000L
        // Dedicated, not Dispatchers.Default — withTimeout only stops *waiting* for a blocking
        // JNI call, it can't interrupt one already running underneath (Kotlin coroutines can't
        // preempt native/blocking code). A load that genuinely hangs forever therefore strands
        // one thread permanently; isolating that to its own small pool means it can never
        // eventually starve Default's CPU-bound pool that the rest of the app's coroutines share.
        private val nativeLoadDispatcher = Dispatchers.IO.limitedParallelism(2)
        // §13 KV-cache proxy, tier 1 (model.layerCount known) — bytes per transformer layer per
        // context token, f16 KV cache. Real hidden-dim/head-count metadata isn't available at
        // this layer, so this is a single constant tuned to the middle of the range typical
        // dense 1B-13B architectures (Llama/Mistral/Qwen-style) land in, not a per-architecture
        // computation — deliberately on the high side so the estimate errs toward overestimating.
        private const val KV_CACHE_BYTES_PER_LAYER_PER_TOKEN = 20_000L
        // §13 KV-cache proxy, tier 2 (layerCount unknown — LiteRT-LM, or a llama.cpp model never
        // validated) — divides the weight file size by this to get a rough per-token byte cost
        // instead, then multiplies by context length. Tuned only to be in the right order of
        // magnitude so the check catches genuinely-oversized requests without false-positiving on
        // normal ones.
        private const val KV_CACHE_FILE_SIZE_SCALE_DIVISOR = 50_000L
        // Fixed allowance for runtime/delegate/framework overhead beyond the raw weights+KV-cache
        // (OpenCL/Vulkan context, tokenizer, activation buffers) — a flat estimate rather than
        // trying to model it precisely.
        private const val RUNTIME_OVERHEAD_BYTES = 300L * 1024 * 1024
    }
}
