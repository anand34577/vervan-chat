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
    val retryable: Boolean = false
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
    fun loadModel(
        modelPath: String,
        maxTokens: Int,
        maxNumImages: Int,
        backendPreference: com.vervan.chat.llm.LlmEngine.BackendPreference,
        preferredBackendHint: com.vervan.chat.llm.LlmEngine.ModelBackend?,
        enableSpeculativeDecoding: Boolean
    ): com.vervan.chat.llm.LlmEngine.LoadResult
    fun close()
}

/** The subset of [com.vervan.chat.retrieval.EmbeddingEngine] that [ModelLoadCoordinator] depends
 * on — same reasoning as [GenerationLoadable] for the `loadModel` naming. */
interface EmbeddingLoadable {
    val loadedModelPath: String?
    val activeBackend: com.vervan.chat.retrieval.EmbeddingBackend
    fun loadModel(modelPath: String, tokenizerPath: String?)
    fun close()
}

/** The 3 app-wide Settings fallbacks [ModelLoadCoordinator] needs when a model's own
 * contextTokens/maxNumImages/preferredBackend fields are unset — extracted from
 * [com.vervan.chat.data.settings.SettingsRepository] (which requires a real Android `Context`
 * and can't be constructed in a plain JVM unit test) purely so tests can supply fixed values
 * instead. [preferredBackend] returns one of "GPU"/"CPU"/"NPU"/"AUTO", matching
 * SettingsRepository.preferredBackend's raw stored string. */
interface GenerationDefaults {
    suspend fun contextTokenLimit(): Int
    suspend fun maxNumImages(): Int
    suspend fun preferredBackend(): String
}
