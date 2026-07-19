package com.vervan.chat.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vervan_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Selectable accent color palette (spec ask: "add more themes") — layered on top of the
 * existing light/dark/OLED axis, not a replacement for it. */
enum class AccentTheme { AMBER, BLUE, GREEN, VIOLET, ROSE }

/**
 * Real user-facing settings (spec §41), DataStore-backed. one flat preferences
 * file covering the settings screens actually built today, not placeholder keys for the
 * spec's unbuilt groups (retrieval-mode picker per source type, per-tool timeouts, etc).
 */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_THEME = stringPreferencesKey("accent_theme")
        val OLED_TRUE_BLACK = booleanPreferencesKey("oled_true_black")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val SHOW_GENERATION_STATS = booleanPreferencesKey("show_generation_stats")
        val EXPERT_MODE = booleanPreferencesKey("expert_mode")
        val LARGE_TOUCH_TARGETS = booleanPreferencesKey("large_touch_targets")
        val DEFAULT_RETRIEVAL_MODE = stringPreferencesKey("default_retrieval_mode")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val AUTO_READ_ALOUD = booleanPreferencesKey("auto_read_aloud")
        val TTS_ENGINE_PREFERENCE = stringPreferencesKey("tts_engine_preference")
        val KOKORO_QUALITY_ENABLED = booleanPreferencesKey("kokoro_quality_enabled")
        val BARGE_IN_ENABLED = booleanPreferencesKey("barge_in_enabled")
        val INBUILT_STT_ENABLED = booleanPreferencesKey("inbuilt_stt_enabled")
        val WIFI_ONLY_MODEL_DOWNLOADS = booleanPreferencesKey("wifi_only_model_downloads")
        val AUTO_CONTEXT_SUMMARIZATION = booleanPreferencesKey("auto_context_summarization")
        val AUTO_RESUME_MODEL_DOWNLOADS = booleanPreferencesKey("auto_resume_model_downloads")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val CONTEXT_TOKEN_LIMIT = intPreferencesKey("context_token_limit")
        val RESPONSE_LENGTH = stringPreferencesKey("response_length")
        val RESPONSE_TONE = stringPreferencesKey("response_tone")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_P = floatPreferencesKey("top_p")
        val TOP_K = intPreferencesKey("top_k")
        val PREFERRED_BACKEND = stringPreferencesKey("preferred_backend")
        val ALLOW_LOW_MEMORY_MODEL_LOADS = booleanPreferencesKey("allow_low_memory_model_loads")
        val MAX_NUM_IMAGES = intPreferencesKey("max_num_images")
        val RANDOM_SEED = intPreferencesKey("random_seed")
        // Full LLM config exposure — global fallbacks for fields with a real app-wide default
        // (chatTemplateOverride/loraPath/gpuLayerCount are per-model-only, no global concept,
        // same precedent as mmprojPath having no global counterpart).
        val MIN_P = floatPreferencesKey("min_p")
        val REPETITION_PENALTY = floatPreferencesKey("repetition_penalty")
        val MAX_OUTPUT_TOKENS = intPreferencesKey("max_output_tokens")
        val CPU_THREADS = intPreferencesKey("cpu_threads")
        val N_BATCH = intPreferencesKey("n_batch")
        val N_UBATCH = intPreferencesKey("n_ubatch")
        val USE_MLOCK = booleanPreferencesKey("use_mlock")
        val FLASH_ATTENTION_MODE = stringPreferencesKey("flash_attention_mode")
        val KV_CACHE_TYPE = stringPreferencesKey("kv_cache_type")
        val VULKAN_DEVICE_INDEX = intPreferencesKey("vulkan_device_index")
        val DEFAULT_PROFILE = stringPreferencesKey("default_profile")
        val DEFAULT_START_SCREEN = stringPreferencesKey("default_start_screen")
        val CONFIRM_DESTRUCTIVE = booleanPreferencesKey("confirm_destructive")
        // Workspace System spec §5 — the single active workspace, global to the app (not a
        // per-workspace field). Null until the first cold-start seed sets it to "default".
        val ACTIVE_WORKSPACE_ID = stringPreferencesKey("active_workspace_id")
        // User profile fields (spec §26.1) — optional, declared by the user.
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_OCCUPATION = stringPreferencesKey("user_occupation")
        val USER_EXPERTISE = stringPreferencesKey("user_expertise")
        val USER_INTERESTS = stringPreferencesKey("user_interests")
        val USER_LANGUAGES = stringSetPreferencesKey("user_languages")
        val USER_CODING_LANGUAGES = stringSetPreferencesKey("user_coding_languages")
        val USER_UNITS = stringPreferencesKey("user_units")
        val USER_TOPICS_AVOID = stringPreferencesKey("user_topics_avoid")
        val USER_GOALS = stringPreferencesKey("user_goals")
        // §27.3 — memory-suggestion keys the user opted out of via "Never suggest this type".
        val BLOCKED_MEMORY_SUGGESTION_KEYS = stringSetPreferencesKey("blocked_memory_suggestion_keys")
        // Privacy hardening (Phase A) — app-lock configuration. The PIN itself is never stored
        // here: it lives in AppLockManager's EncryptedSharedPreferences, not plain DataStore.
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_METHOD = stringPreferencesKey("app_lock_method")
        val AUTO_LOCK_TIMEOUT_SECONDS = intPreferencesKey("auto_lock_timeout_seconds")
        // Phase C — retention policy. 0 means "off" (no auto-delete).
        val AUTO_DELETE_AFTER_DAYS = intPreferencesKey("auto_delete_after_days")
        // Phase G — on-device data sources. Each is a separate app-level opt-in, independent
        // of (and in addition to) the OS runtime permission — granting the Android permission
        // doesn't mean the model should always be allowed to query it.
        val CALENDAR_TOOL_ENABLED = booleanPreferencesKey("calendar_tool_enabled")
        val DEVICE_STATUS_TOOL_ENABLED = booleanPreferencesKey("device_status_tool_enabled")
        val FILES_TOOL_ENABLED = booleanPreferencesKey("files_tool_enabled")
        val LOCATION_TOOL_ENABLED = booleanPreferencesKey("location_tool_enabled")
        val SCREEN_TIME_TOOL_ENABLED = booleanPreferencesKey("screen_time_tool_enabled")
        // Phase I — floating quick-action bubble, off by default (the one feature in this app
        // that needs an overlay permission).
        val QUICK_ACTION_BUBBLE_ENABLED = booleanPreferencesKey("quick_action_bubble_enabled")
        // Phase J — local OpenAI-compatible API server. The bearer token itself is NOT here —
        // see ApiServerAuth's EncryptedSharedPreferences, same reasoning as the app-lock PIN.
        val API_SERVER_ENABLED = booleanPreferencesKey("api_server_enabled")
        val LAN_API_SERVER_ENABLED = booleanPreferencesKey("lan_api_server_enabled")
        val API_SERVER_PORT = intPreferencesKey("api_server_port")
        val API_SERVER_REQUIRE_AUTH = booleanPreferencesKey("api_server_require_auth")
        // Tool catalog — globally disabled tool ids (see ToolRegistry). Storing "what's off"
        // instead of "what's on" means a newly added tool is enabled by default without needing
        // a migration or a new key every time one is added.
        val DISABLED_TOOL_IDS = stringSetPreferencesKey("disabled_tool_ids")
        // Current date/time is injected into every prompt directly (not gated behind the
        // tool-calling loop at all) so the model always has it, even in chats with tools off —
        // this is the one thing "always enabled ... so it can refer to it for anything" needs.
        val ALWAYS_INCLUDE_DATETIME = booleanPreferencesKey("always_include_datetime")
        // App-wide screenshot/screen-recording block (moved from a per-chat toggle — a privacy
        // setting a user reaches for should protect every chat, not just whichever one they
        // remembered to flip it on for).
        val SCREENSHOT_BLOCKING_ENABLED = booleanPreferencesKey("screenshot_blocking_enabled")
    }

    val blockedMemorySuggestionKeys: Flow<Set<String>> = store.data.map { it[Keys.BLOCKED_MEMORY_SUGGESTION_KEYS] ?: emptySet() }
    suspend fun blockMemorySuggestionKey(key: String) {
        store.edit { it[Keys.BLOCKED_MEMORY_SUGGESTION_KEYS] = (it[Keys.BLOCKED_MEMORY_SUGGESTION_KEYS] ?: emptySet()) + key }
    }

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }
    suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[Keys.THEME_MODE] = mode.name } }

    val accentTheme: Flow<AccentTheme> = store.data.map { prefs ->
        prefs[Keys.ACCENT_THEME]?.let { runCatching { AccentTheme.valueOf(it) }.getOrNull() } ?: AccentTheme.AMBER
    }
    suspend fun setAccentTheme(theme: AccentTheme) { store.edit { it[Keys.ACCENT_THEME] = theme.name } }

    // Phase 7 polish (spec §35-36) — OLED true-black dark variant and a haptics on/off switch
    // (the app already respects the system's reduced-motion/animation-scale setting directly
    // at the point animations happen, so that doesn't need its own preference).
    val oledTrueBlack: Flow<Boolean> = store.data.map { it[Keys.OLED_TRUE_BLACK] ?: false }
    suspend fun setOledTrueBlack(enabled: Boolean) { store.edit { it[Keys.OLED_TRUE_BLACK] = enabled } }

    // §3.2 — Material You dynamic color and a high-contrast mode independent of accent/theme.
    val dynamicColor: Flow<Boolean> = store.data.map { it[Keys.DYNAMIC_COLOR] ?: false }
    suspend fun setDynamicColor(enabled: Boolean) { store.edit { it[Keys.DYNAMIC_COLOR] = enabled } }

    val highContrast: Flow<Boolean> = store.data.map { it[Keys.HIGH_CONTRAST] ?: false }
    suspend fun setHighContrast(enabled: Boolean) { store.edit { it[Keys.HIGH_CONTRAST] = enabled } }

    val hapticsEnabled: Flow<Boolean> = store.data.map { it[Keys.HAPTICS_ENABLED] ?: true }
    suspend fun setHapticsEnabled(enabled: Boolean) { store.edit { it[Keys.HAPTICS_ENABLED] = enabled } }

    // Per-message generation stats (time/tokens/tok-per-sec) shown when an assistant bubble is
    // expanded — optional since it's noise for anyone who doesn't care about performance.
    val showGenerationStats: Flow<Boolean> = store.data.map { it[Keys.SHOW_GENERATION_STATS] ?: false }
    suspend fun setShowGenerationStats(enabled: Boolean) { store.edit { it[Keys.SHOW_GENERATION_STATS] = enabled } }

    val expertMode: Flow<Boolean> = store.data.map { it[Keys.EXPERT_MODE] ?: false }
    suspend fun setExpertMode(enabled: Boolean) { store.edit { it[Keys.EXPERT_MODE] = enabled } }

    val largeTouchTargets: Flow<Boolean> = store.data.map { it[Keys.LARGE_TOUCH_TARGETS] ?: false }
    suspend fun setLargeTouchTargets(enabled: Boolean) { store.edit { it[Keys.LARGE_TOUCH_TARGETS] = enabled } }

    /** One of RetrievalMode's names ("KEYWORD"/"SEMANTIC"/"HYBRID") — kept as a string here
     * so this module doesn't need to depend on the retrieval package. */
    val defaultRetrievalMode: Flow<String> = store.data.map { it[Keys.DEFAULT_RETRIEVAL_MODE] ?: "HYBRID" }
    suspend fun setDefaultRetrievalMode(mode: String) { store.edit { it[Keys.DEFAULT_RETRIEVAL_MODE] = mode } }

    val ttsRate: Flow<Float> = store.data.map { it[Keys.TTS_RATE] ?: 1.0f }
    suspend fun setTtsRate(rate: Float) { store.edit { it[Keys.TTS_RATE] = rate } }

    val autoReadAloud: Flow<Boolean> = store.data.map { it[Keys.AUTO_READ_ALOUD] ?: false }
    suspend fun setAutoReadAloud(enabled: Boolean) { store.edit { it[Keys.AUTO_READ_ALOUD] = enabled } }

    /** "AUTO" (Supertonic, falling back to Piper then the Android system engine), or an
     * explicit pin: "SUPERTONIC", "PIPER", "SYSTEM". Realtime voice pipeline engine choice —
     * see [com.vervan.chat.voice.TtsEngineSelector]. */
    val ttsEnginePreference: Flow<String> = store.data.map { it[Keys.TTS_ENGINE_PREFERENCE] ?: "AUTO" }
    suspend fun setTtsEnginePreference(value: String) { store.edit { it[Keys.TTS_ENGINE_PREFERENCE] = value } }

    /** Opt-in "higher quality voice (slower)" tier — Kokoro is noticeably higher quality than
     * Piper but can be 2-3 minutes of compute per minute of audio on budget devices, so it's
     * never selected by AUTO even when enabled here. */
    val kokoroQualityEnabled: Flow<Boolean> = store.data.map { it[Keys.KOKORO_QUALITY_ENABLED] ?: false }
    suspend fun setKokoroQualityEnabled(v: Boolean) { store.edit { it[Keys.KOKORO_QUALITY_ENABLED] = v } }

    /** Whether the realtime voice pipeline listens for interrupting speech while TTS is
     * playing. Best-effort (needs hardware echo cancellation) — off automatically falls back
     * to a tap-to-interrupt button, see [com.vervan.chat.audio.ContinuousAudioCapture]. */
    val bargeInEnabled: Flow<Boolean> = store.data.map { it[Keys.BARGE_IN_ENABLED] ?: true }
    suspend fun setBargeInEnabled(v: Boolean) { store.edit { it[Keys.BARGE_IN_ENABLED] = v } }

    /** Realtime voice pipeline's speech-to-text policy (see
     * [com.vervan.chat.voice.RealtimeVoiceController]): the active generation model is tried
     * first when it supports audio input; this toggle only controls whether the downloaded
     * on-device Whisper model is used as the fallback tier (default on) or skipped straight to
     * Android's system speech recognizer (off). Has no effect until the Whisper model is
     * actually downloaded via Model Manager. */
    val inbuiltSttEnabled: Flow<Boolean> = store.data.map { it[Keys.INBUILT_STT_ENABLED] ?: true }
    suspend fun setInbuiltSttEnabled(v: Boolean) { store.edit { it[Keys.INBUILT_STT_ENABLED] = v } }

    /** Model downloader (see com.vervan.chat.modeldownload) network settings. Off by default —
     * a large model download simply waits for Wi-Fi instead of silently spending mobile data
     * when on. */
    val wifiOnlyModelDownloads: Flow<Boolean> = store.data.map { it[Keys.WIFI_ONLY_MODEL_DOWNLOADS] ?: false }
    suspend fun setWifiOnlyModelDownloads(v: Boolean) { store.edit { it[Keys.WIFI_ONLY_MODEL_DOWNLOADS] = v } }
    val autoResumeModelDownloads: Flow<Boolean> = store.data.map { it[Keys.AUTO_RESUME_MODEL_DOWNLOADS] ?: true }
    suspend fun setAutoResumeModelDownloads(v: Boolean) { store.edit { it[Keys.AUTO_RESUME_MODEL_DOWNLOADS] = v } }

    /** Long-chat context management (ChatViewModel.summarizeOlderHistoryIfNeeded) — folds turns
     * that are about to be dropped by context eviction into a running per-chat summary instead
     * of just discarding them, at the cost of one extra background generation call on the
     * already-loaded model. On by default since the alternative (silent truncation) is worse
     * for small-context models; off is for users who'd rather not pay the extra generation. */
    val autoContextSummarization: Flow<Boolean> = store.data.map { it[Keys.AUTO_CONTEXT_SUMMARIZATION] ?: true }
    suspend fun setAutoContextSummarization(v: Boolean) { store.edit { it[Keys.AUTO_CONTEXT_SUMMARIZATION] = v } }

    /** UI text scale multiplier, 0.85x-1.3x — spec §38's font-scale accessibility setting. */
    val fontScale: Flow<Float> = store.data.map { it[Keys.FONT_SCALE] ?: 1.0f }
    suspend fun setFontScale(scale: Float) { store.edit { it[Keys.FONT_SCALE] = scale } }

    val contextTokenLimit: Flow<Int> = store.data.map { it[Keys.CONTEXT_TOKEN_LIMIT] ?: 4096 }
    suspend fun setContextTokenLimit(limit: Int) { store.edit { it[Keys.CONTEXT_TOKEN_LIMIT] = limit } }

    /**
     * Declared, not inferred (spec §26) — the user picks these explicitly in Settings, the
     * app never learns them from conversation history. "BALANCED"/"NEUTRAL" are the no-op
     * defaults, in which case no style section is added to the prompt at all (see
     * ChatViewModel.buildPromptSections) rather than spending tokens saying nothing useful.
     */
    val responseLength: Flow<String> = store.data.map { it[Keys.RESPONSE_LENGTH] ?: "BALANCED" }
    suspend fun setResponseLength(value: String) { store.edit { it[Keys.RESPONSE_LENGTH] = value } }

    val responseTone: Flow<String> = store.data.map { it[Keys.RESPONSE_TONE] ?: "NEUTRAL" }
    suspend fun setResponseTone(value: String) { store.edit { it[Keys.RESPONSE_TONE] = value } }

    val temperature: Flow<Float> = store.data.map { it[Keys.TEMPERATURE] ?: 0.8f }
    suspend fun setTemperature(value: Float) { store.edit { it[Keys.TEMPERATURE] = value.coerceIn(0f, 2f) } }

    val topP: Flow<Float> = store.data.map { it[Keys.TOP_P] ?: 0.95f }
    suspend fun setTopP(value: Float) { store.edit { it[Keys.TOP_P] = value.coerceIn(0.1f, 1f) } }

    val topK: Flow<Int> = store.data.map { it[Keys.TOP_K] ?: 40 }
    suspend fun setTopK(value: Int) { store.edit { it[Keys.TOP_K] = value.coerceIn(1, 64) } }

    /** "AUTO" (GPU, falling back to CPU if unavailable — the default), "GPU", or "CPU" —
     * user's explicit engine choice from model config. No "NPU": tasks-genai doesn't expose
     * an NPU delegate to pick. */
    val preferredBackend: Flow<String> = store.data.map { it[Keys.PREFERRED_BACKEND] ?: "AUTO" }
    suspend fun setPreferredBackend(value: String) { store.edit { it[Keys.PREFERRED_BACKEND] = value } }

    /** Opt-in escape hatch for devices that can successfully lean on Android's compressed RAM
     * or swap despite the conservative pre-load estimate. Off keeps the current strict guard. */
    val allowLowMemoryModelLoads: Flow<Boolean> = store.data.map { it[Keys.ALLOW_LOW_MEMORY_MODEL_LOADS] ?: false }
    suspend fun setAllowLowMemoryModelLoads(value: Boolean) { store.edit { it[Keys.ALLOW_LOW_MEMORY_MODEL_LOADS] = value } }

    /** Vision token budget: how many images a single prompt can attach, for models loaded
     * with vision support. */
    val maxNumImages: Flow<Int> = store.data.map { it[Keys.MAX_NUM_IMAGES] ?: 1 }
    suspend fun setMaxNumImages(value: Int) { store.edit { it[Keys.MAX_NUM_IMAGES] = value.coerceIn(1, 4) } }

    /** -1 means "no fixed seed" (each generation samples fresh); any other value is passed to
     * the engine for reproducible output. */
    val randomSeed: Flow<Int> = store.data.map { it[Keys.RANDOM_SEED] ?: -1 }
    suspend fun setRandomSeed(value: Int) { store.edit { it[Keys.RANDOM_SEED] = value } }

    val minP: Flow<Float> = store.data.map { it[Keys.MIN_P] ?: 0.05f }
    suspend fun setMinP(value: Float) { store.edit { it[Keys.MIN_P] = value.coerceIn(0f, 1f) } }

    val repetitionPenalty: Flow<Float> = store.data.map { it[Keys.REPETITION_PENALTY] ?: 1.1f }
    suspend fun setRepetitionPenalty(value: Float) { store.edit { it[Keys.REPETITION_PENALTY] = value.coerceIn(1f, 2f) } }

    val maxOutputTokens: Flow<Int> = store.data.map { it[Keys.MAX_OUTPUT_TOKENS] ?: 512 }
    suspend fun setMaxOutputTokens(value: Int) { store.edit { it[Keys.MAX_OUTPUT_TOKENS] = value } }

    /** 0/null means "auto" (`Runtime.getRuntime().availableProcessors()`), llama.cpp-only. */
    val cpuThreads: Flow<Int> = store.data.map { it[Keys.CPU_THREADS] ?: 0 }
    suspend fun setCpuThreads(value: Int) { store.edit { it[Keys.CPU_THREADS] = value } }

    val nBatch: Flow<Int> = store.data.map { it[Keys.N_BATCH] ?: 2048 }
    suspend fun setNBatch(value: Int) { store.edit { it[Keys.N_BATCH] = value } }

    val nUbatch: Flow<Int> = store.data.map { it[Keys.N_UBATCH] ?: 512 }
    suspend fun setNUbatch(value: Int) { store.edit { it[Keys.N_UBATCH] = value } }

    val useMlock: Flow<Boolean> = store.data.map { it[Keys.USE_MLOCK] ?: false }
    suspend fun setUseMlock(value: Boolean) { store.edit { it[Keys.USE_MLOCK] = value } }

    /** "AUTO" (default — degrades safely if unsupported), "ON", or "OFF". */
    val flashAttentionMode: Flow<String> = store.data.map { it[Keys.FLASH_ATTENTION_MODE] ?: "AUTO" }
    suspend fun setFlashAttentionMode(value: String) { store.edit { it[Keys.FLASH_ATTENTION_MODE] = value } }

    /** "f16" (default), "q8_0", or "q4_0" — llama.cpp KV cache quantization. */
    val kvCacheType: Flow<String> = store.data.map { it[Keys.KV_CACHE_TYPE] ?: "f16" }
    suspend fun setKvCacheType(value: String) { store.edit { it[Keys.KV_CACHE_TYPE] = value } }

    val vulkanDeviceIndex: Flow<Int> = store.data.map { it[Keys.VULKAN_DEVICE_INDEX] ?: 0 }
    suspend fun setVulkanDeviceIndex(value: Int) { store.edit { it[Keys.VULKAN_DEVICE_INDEX] = value } }

    /** Default model profile for new chats (spec §11.9). One of ModelProfileType.id. */
    val defaultProfile: Flow<String> = store.data.map { it[Keys.DEFAULT_PROFILE] ?: "BALANCED" }
    suspend fun setDefaultProfile(value: String) { store.edit { it[Keys.DEFAULT_PROFILE] = value } }

    /** Default start screen (spec §41.1) — "home"/"chats"/"knowledge"/"library". */
    val defaultStartScreen: Flow<String> = store.data.map { it[Keys.DEFAULT_START_SCREEN] ?: "home" }
    suspend fun setDefaultStartScreen(value: String) { store.edit { it[Keys.DEFAULT_START_SCREEN] = value } }

    /** Whether destructive actions require an extra confirmation (spec §41.1). */
    val confirmDestructive: Flow<Boolean> = store.data.map { it[Keys.CONFIRM_DESTRUCTIVE] ?: true }
    suspend fun setConfirmDestructive(value: Boolean) { store.edit { it[Keys.CONFIRM_DESTRUCTIVE] = value } }

    /** Falls back to the Default Workspace id (Workspace System spec §2, §5) until the user
     * switches — new chats always have somewhere valid to land. */
    val activeWorkspaceId: Flow<String> = store.data.map {
        it[Keys.ACTIVE_WORKSPACE_ID] ?: com.vervan.chat.data.db.entities.Workspace.DEFAULT_WORKSPACE_ID
    }
    suspend fun setActiveWorkspaceId(id: String) { store.edit { it[Keys.ACTIVE_WORKSPACE_ID] = id } }

    // ---- User profile (spec §26.1) ----
    val userName: Flow<String> = store.data.map { it[Keys.USER_NAME] ?: "" }
    suspend fun setUserName(v: String) { store.edit { it[Keys.USER_NAME] = v } }

    val userOccupation: Flow<String> = store.data.map { it[Keys.USER_OCCUPATION] ?: "" }
    suspend fun setUserOccupation(v: String) { store.edit { it[Keys.USER_OCCUPATION] = v } }

    val userExpertise: Flow<String> = store.data.map { it[Keys.USER_EXPERTISE] ?: "" }
    suspend fun setUserExpertise(v: String) { store.edit { it[Keys.USER_EXPERTISE] = v } }

    val userInterests: Flow<String> = store.data.map { it[Keys.USER_INTERESTS] ?: "" }
    suspend fun setUserInterests(v: String) { store.edit { it[Keys.USER_INTERESTS] = v } }

    val userLanguages: Flow<Set<String>> = store.data.map { it[Keys.USER_LANGUAGES] ?: emptySet() }
    suspend fun setUserLanguages(v: Set<String>) { store.edit { it[Keys.USER_LANGUAGES] = v } }

    val userCodingLanguages: Flow<Set<String>> = store.data.map { it[Keys.USER_CODING_LANGUAGES] ?: emptySet() }
    suspend fun setUserCodingLanguages(v: Set<String>) { store.edit { it[Keys.USER_CODING_LANGUAGES] = v } }

    val userUnits: Flow<String> = store.data.map { it[Keys.USER_UNITS] ?: "metric" }
    suspend fun setUserUnits(v: String) { store.edit { it[Keys.USER_UNITS] = v } }

    val userTopicsAvoid: Flow<String> = store.data.map { it[Keys.USER_TOPICS_AVOID] ?: "" }
    suspend fun setUserTopicsAvoid(v: String) { store.edit { it[Keys.USER_TOPICS_AVOID] = v } }

    val userGoals: Flow<String> = store.data.map { it[Keys.USER_GOALS] ?: "" }
    suspend fun setUserGoals(v: String) { store.edit { it[Keys.USER_GOALS] = v } }

    /**
     * Renders the user-profile fields that are actually set into a prompt instruction (spec
     * §26.1). Empty when nothing is filled in, so a user who never opens this screen pays
     * zero prompt cost.
     */
    // ---- App lock (Phase A) ----
    val appLockEnabled: Flow<Boolean> = store.data.map { it[Keys.APP_LOCK_ENABLED] ?: false }
    suspend fun setAppLockEnabled(enabled: Boolean) { store.edit { it[Keys.APP_LOCK_ENABLED] = enabled } }

    /** One of AppLockMethod's names ("BIOMETRIC"/"PIN"/"BOTH"). */
    val appLockMethod: Flow<String> = store.data.map { it[Keys.APP_LOCK_METHOD] ?: "BIOMETRIC" }
    suspend fun setAppLockMethod(value: String) { store.edit { it[Keys.APP_LOCK_METHOD] = value } }

    val autoLockTimeoutSeconds: Flow<Int> = store.data.map { it[Keys.AUTO_LOCK_TIMEOUT_SECONDS] ?: 60 }
    suspend fun setAutoLockTimeoutSeconds(value: Int) { store.edit { it[Keys.AUTO_LOCK_TIMEOUT_SECONDS] = value } }

    val quickActionBubbleEnabled: Flow<Boolean> = store.data.map { it[Keys.QUICK_ACTION_BUBBLE_ENABLED] ?: false }
    suspend fun setQuickActionBubbleEnabled(v: Boolean) { store.edit { it[Keys.QUICK_ACTION_BUBBLE_ENABLED] = v } }

    // ---- Local API server (Phase J) ----
    val apiServerEnabled: Flow<Boolean> = store.data.map { it[Keys.API_SERVER_ENABLED] ?: false }
    suspend fun setApiServerEnabled(v: Boolean) { store.edit { it[Keys.API_SERVER_ENABLED] = v } }
    val lanApiServerEnabled: Flow<Boolean> = store.data.map { it[Keys.LAN_API_SERVER_ENABLED] ?: false }
    suspend fun setLanApiServerEnabled(v: Boolean) { store.edit { it[Keys.LAN_API_SERVER_ENABLED] = v } }
    val apiServerPort: Flow<Int> = store.data.map { it[Keys.API_SERVER_PORT] ?: 8080 }
    suspend fun setApiServerPort(v: Int) { store.edit { it[Keys.API_SERVER_PORT] = v.coerceIn(1024, 65535) } }
    val apiServerRequireAuth: Flow<Boolean> = store.data.map { it[Keys.API_SERVER_REQUIRE_AUTH] ?: true }
    suspend fun setApiServerRequireAuth(v: Boolean) { store.edit { it[Keys.API_SERVER_REQUIRE_AUTH] = v } }

    // ---- Retention policy (Phase C) ----
    val autoDeleteAfterDays: Flow<Int> = store.data.map { it[Keys.AUTO_DELETE_AFTER_DAYS] ?: 0 }
    suspend fun setAutoDeleteAfterDays(value: Int) { store.edit { it[Keys.AUTO_DELETE_AFTER_DAYS] = value.coerceAtLeast(0) } }

    // ---- On-device data sources (Phase G) — all off by default ----
    val calendarToolEnabled: Flow<Boolean> = store.data.map { it[Keys.CALENDAR_TOOL_ENABLED] ?: false }
    suspend fun setCalendarToolEnabled(v: Boolean) { store.edit { it[Keys.CALENDAR_TOOL_ENABLED] = v } }
    val deviceStatusToolEnabled: Flow<Boolean> = store.data.map { it[Keys.DEVICE_STATUS_TOOL_ENABLED] ?: false }
    suspend fun setDeviceStatusToolEnabled(v: Boolean) { store.edit { it[Keys.DEVICE_STATUS_TOOL_ENABLED] = v } }
    val filesToolEnabled: Flow<Boolean> = store.data.map { it[Keys.FILES_TOOL_ENABLED] ?: false }
    suspend fun setFilesToolEnabled(v: Boolean) { store.edit { it[Keys.FILES_TOOL_ENABLED] = v } }
    val locationToolEnabled: Flow<Boolean> = store.data.map { it[Keys.LOCATION_TOOL_ENABLED] ?: false }
    suspend fun setLocationToolEnabled(v: Boolean) { store.edit { it[Keys.LOCATION_TOOL_ENABLED] = v } }
    val screenTimeToolEnabled: Flow<Boolean> = store.data.map { it[Keys.SCREEN_TIME_TOOL_ENABLED] ?: false }
    suspend fun setScreenTimeToolEnabled(v: Boolean) { store.edit { it[Keys.SCREEN_TIME_TOOL_ENABLED] = v } }

    // ---- Tool catalog (Settings → Tools) ----
    val disabledToolIds: Flow<Set<String>> = store.data.map { it[Keys.DISABLED_TOOL_IDS] ?: emptySet() }
    suspend fun setToolEnabled(toolId: String, enabled: Boolean) {
        store.edit { prefs ->
            val current = prefs[Keys.DISABLED_TOOL_IDS] ?: emptySet()
            prefs[Keys.DISABLED_TOOL_IDS] = if (enabled) current - toolId else current + toolId
        }
    }
    val alwaysIncludeDateTime: Flow<Boolean> = store.data.map { it[Keys.ALWAYS_INCLUDE_DATETIME] ?: true }
    suspend fun setAlwaysIncludeDateTime(v: Boolean) { store.edit { it[Keys.ALWAYS_INCLUDE_DATETIME] = v } }

    val screenshotBlockingEnabled: Flow<Boolean> = store.data.map { it[Keys.SCREENSHOT_BLOCKING_ENABLED] ?: false }
    suspend fun setScreenshotBlockingEnabled(v: Boolean) { store.edit { it[Keys.SCREENSHOT_BLOCKING_ENABLED] = v } }

    /** Panic wipe (Phase C) — clears every preference back to defaults. Does not touch the
     * Room database, model/document files, or the app-lock PIN store; those are separate
     * storage the caller (SettingsViewModel.panicWipe) clears independently. */
    suspend fun wipeAll() { store.edit { it.clear() } }

    suspend fun userProfilePrompt(): String {
        val parts = mutableListOf<String>()
        val name = userName.first()
        if (name.isNotBlank()) parts += "The user's name is $name."
        val occupation = userOccupation.first()
        if (occupation.isNotBlank()) parts += "The user works as $occupation."
        val expertise = userExpertise.first()
        if (expertise.isNotBlank()) parts += "The user's expertise level is $expertise."
        val langs = userLanguages.first()
        if (langs.isNotEmpty()) parts += "The user speaks ${langs.joinToString(", ")}."
        val units = userUnits.first()
        if (units == "imperial") parts += "The user prefers imperial units."
        val avoid = userTopicsAvoid.first()
        if (avoid.isNotBlank()) parts += "Avoid: $avoid."
        val goals = userGoals.first()
        if (goals.isNotBlank()) parts += "The user's current goals: $goals."
        return if (parts.isEmpty()) "" else parts.joinToString(" ")
    }
}
