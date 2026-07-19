package com.vervan.chat.modelload

import com.vervan.chat.data.db.entities.ModelRole

/** Load lifecycle for one role's native engine. Deliberately not named `ModelStatus` (that name
 * is already taken by [com.vervan.chat.data.db.entities.ModelStatus], the download-lifecycle
 * enum) or `ModelBackend` (already used twice, for hardware backend). */
enum class ModelLoadPhase { UNLOADED, LOADING, READY, FAILED }

/** What actually went wrong on a failed `ensureLoaded`/`loadManually` call — mirrors the
 * categories from the Model Loading Strategy spec that are reachable for GENERATION/EMBEDDING. */
enum class ModelLoadErrorCategory {
    NO_MODEL_INSTALLED,
    NO_DEFAULT_SET,
    FILE_MISSING,
    FILE_CORRUPTED,
    INCOMPATIBLE_BACKEND,
    INSUFFICIENT_MEMORY,
    PERMISSION_DENIED,
    CANCELLED,
    SWITCH_CONFLICT,
    // §9.2 — a native call that hung past the watchdog timeout. Distinct from CANCELLED (user-
    // initiated) even though a timeout is technically implemented as cancelling the wrapping
    // coroutine — the caller didn't ask for this, the runtime did.
    TIMED_OUT,
    // A prior TIMED_OUT left this engine's native call still running with no way to cancel it
    // (coroutines can't preempt blocking JNI) — touching the engine again (close()/loadModel())
    // while that orphaned call may still be executing would race on the same native instance, so
    // the coordinator refuses to reuse it at all rather than risk that. Deliberately not
    // `retryable`: retrying won't help until the process restarts.
    ENGINE_UNAVAILABLE,
    UNKNOWN
}

/** Immutable outcome of any coordinator load attempt. [joinedInFlight] means this result came
 * from a load another caller already had running, not one this call started itself. */
data class EnsureLoadResult(
    val role: ModelRole,
    val success: Boolean,
    val loadedModelId: String? = null,
    val loadRequired: Boolean = false,
    val joinedInFlight: Boolean = false,
    val errorCategory: ModelLoadErrorCategory? = null,
    val errorMessage: String? = null,
    val retryable: Boolean = false,
    // §11.3 — true when this load only succeeded by falling back to a lower-capability delegate
    // than requested (GPU asked for, CPU actually used). The underlying engines already detected
    // this correctly; it just never reached callers before, so the UI had no way to disclose it.
    val delegateFallback: Boolean = false
)

/** Per-role snapshot the UI observes. [currentModelId]/[defaultModelId]/[lastSuccessfulModelId]
 * are [com.vervan.chat.data.db.entities.ModelInfo.id] values, not full rows — observers already
 * hold a `models: StateFlow<List<ModelInfo>>` of their own and can resolve display info from it
 * without the coordinator doing its own redundant DB reads on every emission. */
data class ModelLoadInfo(
    val role: ModelRole,
    val phase: ModelLoadPhase = ModelLoadPhase.UNLOADED,
    val currentModelId: String? = null,
    val defaultModelId: String? = null,
    val lastSuccessfulModelId: String? = null,
    val loadingModelId: String? = null,
    val error: EnsureLoadResult? = null
)

/** Why an `ensureLoaded` call is happening — descriptive only today (logging), kept as a real
 * parameter so a future UI distinction has a hook without another signature change. */
enum class LoadTrigger { CHAT_SEND, CHAT_AUTOLOAD, RAG_RETRIEVAL, VOICE_SESSION, MANUAL_MODEL_MANAGER, VALIDATION }

/** Model Loading Strategy §13 — abstracts the real `ActivityManager` memory query so tests can
 * supply fixed values, same reasoning as [GenerationDefaults] below. Neither native runtime
 * exposes its actual working-set size to this Kotlin layer, so the coordinator's estimate is
 * necessarily a proxy (file size + a context-scaled KV-cache guess) rather than exact accounting
 * — this interface only supplies the "how much room is there" half of that comparison. */
interface ResourceMonitor {
    /** Bytes the OS currently reports as available for this process to grow into. */
    fun availableMemoryBytes(): Long
}

/** Default used when no real [ResourceMonitor] is wired in (e.g. existing coordinator unit tests
 * that predate §13 and don't exercise it) — always reports abundant memory, so §13's pre-load
 * check never blocks a load unless a caller deliberately supplies a constrained monitor. */
object UnconstrainedResourceMonitor : ResourceMonitor {
    override fun availableMemoryBytes(): Long = Long.MAX_VALUE
}

/** Model Loading Strategy §6.2 — Android's [android.content.ComponentCallbacks2.onTrimMemory]
 * levels collapsed to the two the spec actually distinguishes: [MODERATE] (stop any speculative/
 * background preloading — informational today, since nothing in this app preloads a non-default
 * model) and [CRITICAL] (the OS may reclaim the loaded model soon; the coordinator proactively
 * unloads first rather than letting a native `SIGKILL` take the whole process with it). */
enum class MemoryPressureLevel { NORMAL, MODERATE, CRITICAL }

/** The subset of [com.vervan.chat.llm.LlmEngine] that [ModelLoadCoordinator] depends on —
 * extracted purely so unit tests can supply a fake instead of a real native engine.
 *
 * Named `loadModel`, not `load` — [com.vervan.chat.llm.LlmEngine.load] is called from a couple
 * dozen call sites across the app relying on its default parameter values (Kotlin overrides
 * can't redeclare defaults, and can't coexist with a same-signature non-override overload), so
 * satisfying this interface via that exact method would force every one of those unrelated call
 * sites to start passing every argument explicitly. A distinctly-named method that just delegates
 * avoids touching any of them. */
interface GenerationLoadable {
    val loadedModelPath: String?
    val activeBackend: com.vervan.chat.llm.LlmEngine.ModelBackend
    val visionEnabled: Boolean
    val audioEnabled: Boolean
    val speculativeDecodingActive: Boolean
    // The context/maxTokens actually in effect for [loadedModelPath], or null if nothing is
    // loaded. Lets a caller that wants "keep whatever's currently loaded" (no explicit context
    // override) reuse this live value instead of recomputing one from settings/model fields —
    // recomputing can land on a different number than what's actually resident (e.g. a
    // profile-scaled context at autoload vs. the raw default at send time), which looks like a
    // config change to ModelLoadCoordinator and forces a pointless full unload+reload.
    val loadedContextTokens: Int?
    fun loadModel(
        modelPath: String,
        maxTokens: Int,
        maxNumImages: Int,
        backendPreference: com.vervan.chat.llm.LlmEngine.BackendPreference,
        preferredBackendHint: com.vervan.chat.llm.LlmEngine.ModelBackend?,
        enableSpeculativeDecoding: Boolean
    ): com.vervan.chat.llm.LlmEngine.LoadResult
    fun close()
    /** Signals any in-flight [close]-independent generation to stop as soon as possible, without
     * requiring the caller to hold whichever mutex normally guards a `generate()` call for this
     * engine. Safe to call from any thread/coroutine, and a no-op if nothing is generating —
     * lets [com.vervan.chat.modelload.ModelLoadCoordinator.unload] interrupt an in-progress
     * response instead of silently blocking on that mutex until generation finishes on its own. */
    fun cancelActiveGeneration()
}

/** The subset of [com.vervan.chat.retrieval.EmbeddingEngine] that [ModelLoadCoordinator] depends
 * on — same reasoning as [GenerationLoadable] for the `loadModel` naming. */
interface EmbeddingLoadable {
    val loadedModelPath: String?
    val activeBackend: com.vervan.chat.retrieval.EmbeddingBackend
    fun loadModel(modelPath: String, tokenizerPath: String?)
    fun close()
}

/** The app-wide Settings fallbacks [ModelLoadCoordinator] needs when a model's own
 * contextTokens/maxNumImages/preferredBackend fields are unset — extracted from
 * [com.vervan.chat.data.settings.SettingsRepository] (which requires a real Android `Context`
 * and can't be constructed in a plain JVM unit test) purely so tests can supply fixed values
 * instead. [preferredBackend] returns one of "GPU"/"CPU"/"NPU"/"AUTO", matching
 * SettingsRepository.preferredBackend's raw stored string; [allowLowMemoryModelLoads]
 * controls the conservative system-memory preflight. */
interface GenerationDefaults {
    suspend fun contextTokenLimit(): Int
    suspend fun maxNumImages(): Int
    suspend fun preferredBackend(): String
    suspend fun allowLowMemoryModelLoads(): Boolean = false
    // llama.cpp-only load-time fallbacks — irrelevant for a LiteRT-LM model, only read when
    // routing a load through LlamaCppEngine (see ModelLoadCoordinator.doLoadGeneration).
    suspend fun cpuThreads(): Int
    suspend fun nBatch(): Int
    suspend fun nUbatch(): Int
    suspend fun useMlock(): Boolean
    suspend fun flashAttentionMode(): String
    suspend fun kvCacheType(): String
    suspend fun vulkanDeviceIndex(): Int
}
