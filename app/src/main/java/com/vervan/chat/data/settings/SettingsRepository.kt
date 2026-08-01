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
 * Real user-facing settings, DataStore-backed. one flat preferences
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
        val DEVICE_AWARE_PERFORMANCE = booleanPreferencesKey("device_aware_performance")
        val AUTO_MODEL_SELECTION = booleanPreferencesKey("auto_model_selection")
        val FAST_CAPABLE_ROUTING = booleanPreferencesKey("fast_capable_routing")
        val EXPERT_MODE = booleanPreferencesKey("expert_mode")
        val LARGE_TOUCH_TARGETS = booleanPreferencesKey("large_touch_targets")
        val DEFAULT_RETRIEVAL_MODE = stringPreferencesKey("default_retrieval_mode")
        val QUERY_EXPANSION_ENABLED = booleanPreferencesKey("query_expansion_enabled")
        val AUTO_READ_ALOUD = booleanPreferencesKey("auto_read_aloud")
        val TTS_ENGINE_PREFERENCE = stringPreferencesKey("tts_engine_preference")
        val BARGE_IN_ENABLED = booleanPreferencesKey("barge_in_enabled")
        val INBUILT_STT_ENABLED = booleanPreferencesKey("inbuilt_stt_enabled")
        val MODEL_AUDIO_STT_ENABLED = booleanPreferencesKey("model_audio_stt_enabled")
        val ANDROID_STT_ENABLED = booleanPreferencesKey("android_stt_enabled")
        val STT_ENGINE_PREFERENCE = stringPreferencesKey("stt_engine_preference")
        val VOICE_QUALITY_PRESET = stringPreferencesKey("voice_quality_preset")
        val STT_FALLBACK_ENABLED = booleanPreferencesKey("stt_fallback_enabled")
        val WHISPER_GPU_ENABLED = booleanPreferencesKey("whisper_gpu_enabled")
        val SUPERTONIC_VOICE_VARIANT = stringPreferencesKey("supertonic_voice_variant")
        val WHISPER_MODEL_VARIANT = stringPreferencesKey("whisper_model_variant")
        val SPEECH_INPUT_ENABLED = booleanPreferencesKey("speech_input_enabled")
        val VOICE_REPLY_MODE = stringPreferencesKey("voice_reply_mode")
        val VOICE_INPUT_METHOD = stringPreferencesKey("voice_input_method")
        val TRANSCRIPT_REVIEW_ENABLED = booleanPreferencesKey("transcript_review_enabled")
        val HANDS_FREE_AUTO_SEND = booleanPreferencesKey("hands_free_auto_send")
        val CONTINUE_LISTENING = booleanPreferencesKey("continue_listening")
        val HEADPHONES_ONLY_PLAYBACK = booleanPreferencesKey("headphones_only_playback")
        val HEADPHONE_PRIVACY_PAUSE = booleanPreferencesKey("headphone_privacy_pause")
        val VOICE_INPUT_LANGUAGE = stringPreferencesKey("voice_input_language")
        val VAD_SENSITIVITY = floatPreferencesKey("vad_sensitivity")
        val VOICE_SILENCE_DURATION_MS = intPreferencesKey("voice_silence_duration_ms")
        val MAX_UTTERANCE_SECONDS = intPreferencesKey("max_utterance_seconds")
        val STORE_VOICE_RECORDINGS = booleanPreferencesKey("store_voice_recordings")
        val VOICE_SPEECH_RATE = floatPreferencesKey("voice_speech_rate")
        val VOICE_SPEECH_PITCH = floatPreferencesKey("voice_speech_pitch")
        val READ_CODE_MODE = stringPreferencesKey("read_code_mode")
        val READ_TABLE_MODE = stringPreferencesKey("read_table_mode")
        val LONG_RESPONSE_VOICE_MODE = stringPreferencesKey("long_response_voice_mode")
        val BACKGROUND_VOICE_ENABLED = booleanPreferencesKey("background_voice_enabled")
        val VOICE_BATTERY_SAVER = booleanPreferencesKey("voice_battery_saver")
        val TRANSCRIPT_RETENTION_ENABLED = booleanPreferencesKey("transcript_retention_enabled")
        val RECORDING_RETENTION_MODE = stringPreferencesKey("recording_retention_mode")
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
        // Workspace System — the single active workspace, global to the app (not a
        // per-workspace field). Null until the first cold-start seed sets it to "default".
        val ACTIVE_WORKSPACE_ID = stringPreferencesKey("active_workspace_id")
        // User profile fields — optional, declared by the user.
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_OCCUPATION = stringPreferencesKey("user_occupation")
        val USER_EXPERTISE = stringPreferencesKey("user_expertise")
        val USER_INTERESTS = stringPreferencesKey("user_interests")
        val USER_LANGUAGES = stringSetPreferencesKey("user_languages")
        val USER_CODING_LANGUAGES = stringSetPreferencesKey("user_coding_languages")
        val USER_UNITS = stringPreferencesKey("user_units")
        val USER_TOPICS_AVOID = stringPreferencesKey("user_topics_avoid")
        val USER_GOALS = stringPreferencesKey("user_goals")
        // memory-suggestion keys the user opted out of via "Never suggest this type".
        val BLOCKED_MEMORY_SUGGESTION_KEYS = stringSetPreferencesKey("blocked_memory_suggestion_keys")
        // Privacy hardening — app-lock configuration. The PIN itself is never stored
        // here: it lives in AppLockManager's EncryptedSharedPreferences, not plain DataStore.
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_METHOD = stringPreferencesKey("app_lock_method")
        val AUTO_LOCK_TIMEOUT_SECONDS = intPreferencesKey("auto_lock_timeout_seconds")
        // retention policy. 0 means "off" (no auto-delete).
        val AUTO_DELETE_AFTER_DAYS = intPreferencesKey("auto_delete_after_days")
        // on-device data sources. Each is a separate app-level opt-in, independent
        // of (and in addition to) the OS runtime permission — granting the Android permission
        // doesn't mean the model should always be allowed to query it.
        val CALENDAR_TOOL_ENABLED = booleanPreferencesKey("calendar_tool_enabled")
        val DEVICE_STATUS_TOOL_ENABLED = booleanPreferencesKey("device_status_tool_enabled")
        val LOCATION_TOOL_ENABLED = booleanPreferencesKey("location_tool_enabled")
        // floating quick-action bubble, off by default (the one feature in this app
        // that needs an overlay permission).
        val QUICK_ACTION_BUBBLE_ENABLED = booleanPreferencesKey("quick_action_bubble_enabled")
        // local OpenAI-compatible API server. The bearer token itself is NOT here —
        // see ApiServerAuth's EncryptedSharedPreferences, same reasoning as the app-lock PIN.
        val API_SERVER_ENABLED = booleanPreferencesKey("api_server_enabled")
        val LAN_API_SERVER_ENABLED = booleanPreferencesKey("lan_api_server_enabled")
        val API_SERVER_PORT = intPreferencesKey("api_server_port")
        val API_SERVER_REQUIRE_AUTH = booleanPreferencesKey("api_server_require_auth")
        // Tool catalog — globally disabled tool ids (see ToolRegistry). Storing "what's off"
        // instead of "what's on" means a newly added tool is enabled by default without needing
        // a migration or a new key every time one is added.
        val DISABLED_TOOL_IDS = stringSetPreferencesKey("disabled_tool_ids")
        val TOOL_FAVORITES = stringSetPreferencesKey("tool_favorites")
        val ONBOARDED = booleanPreferencesKey("onboarded")
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

    // OLED true-black dark variant and a haptics on/off switch
    // (the app already respects the system's reduced-motion/animation-scale setting directly
    // at the point animations happen, so that doesn't need its own preference).
    val oledTrueBlack: Flow<Boolean> = store.data.map { it[Keys.OLED_TRUE_BLACK] ?: false }
    suspend fun setOledTrueBlack(enabled: Boolean) { store.edit { it[Keys.OLED_TRUE_BLACK] = enabled } }

    // Material You dynamic color and a high-contrast mode independent of accent/theme.
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

    val deviceAwarePerformance: Flow<Boolean> = store.data.map { it[Keys.DEVICE_AWARE_PERFORMANCE] ?: true }
    suspend fun setDeviceAwarePerformance(enabled: Boolean) { store.edit { it[Keys.DEVICE_AWARE_PERFORMANCE] = enabled } }

    // When on (default) and more than one GENERATION model is installed, a chat/folder that
    // hasn't explicitly pinned a model picks one automatically per turn instead of always using
    // whatever's currently loaded — see com.vervan.chat.llm.AutoModelSelector. An explicit pin
    // (chat or folder default) always wins regardless of this setting; "Advanced" model choice
    // stays exactly as manual as it is today by turning this off.
    val autoModelSelectionEnabled: Flow<Boolean> = store.data.map { it[Keys.AUTO_MODEL_SELECTION] ?: true }
    suspend fun setAutoModelSelectionEnabled(enabled: Boolean) { store.edit { it[Keys.AUTO_MODEL_SELECTION] = enabled } }

    // Off by default — a per-turn switch between installed models (see
    // AutoModelSelector.complexityProfileHint) still costs a full unload+reload swap whenever it
    // picks differently than what's already resident, which is a real latency hit a user didn't
    // necessarily choose to pay just by leaving auto model selection on. Only takes effect when
    // autoModelSelectionEnabled is also on and a chat is left on BALANCED.
    val fastCapableRoutingEnabled: Flow<Boolean> = store.data.map { it[Keys.FAST_CAPABLE_ROUTING] ?: false }
    suspend fun setFastCapableRoutingEnabled(enabled: Boolean) { store.edit { it[Keys.FAST_CAPABLE_ROUTING] = enabled } }

    val expertMode: Flow<Boolean> = store.data.map { it[Keys.EXPERT_MODE] ?: false }
    suspend fun setExpertMode(enabled: Boolean) { store.edit { it[Keys.EXPERT_MODE] = enabled } }

    val largeTouchTargets: Flow<Boolean> = store.data.map { it[Keys.LARGE_TOUCH_TARGETS] ?: false }
    suspend fun setLargeTouchTargets(enabled: Boolean) { store.edit { it[Keys.LARGE_TOUCH_TARGETS] = enabled } }

    /** One of RetrievalMode's names ("KEYWORD"/"SEMANTIC"/"HYBRID") — kept as a string here
     * so this module doesn't need to depend on the retrieval package. */
    val defaultRetrievalMode: Flow<String> = store.data.map { it[Keys.DEFAULT_RETRIEVAL_MODE] ?: "HYBRID" }
    suspend fun setDefaultRetrievalMode(mode: String) { store.edit { it[Keys.DEFAULT_RETRIEVAL_MODE] = mode } }

    // Off by default — rewriting the query first costs a whole extra generation round-trip
    // before retrieval even starts, real latency on a phone-class model. Users who want the
    // recall improvement enough to pay for it turn it on explicitly.
    val queryExpansionEnabled: Flow<Boolean> = store.data.map { it[Keys.QUERY_EXPANSION_ENABLED] ?: false }
    suspend fun setQueryExpansionEnabled(enabled: Boolean) { store.edit { it[Keys.QUERY_EXPANSION_ENABLED] = enabled } }

    val autoReadAloud: Flow<Boolean> = store.data.map { it[Keys.AUTO_READ_ALOUD] ?: false }
    suspend fun setAutoReadAloud(enabled: Boolean) { store.edit { it[Keys.AUTO_READ_ALOUD] = enabled } }

    /** "AUTO" (Piper, once its voice is downloaded), or the explicit "KOKORO" pin. Android's
     * system TTS engine is deliberately never used. Realtime voice pipeline engine choice — see
     * [com.vervan.chat.voice.TtsEngineSelector]. */
    val ttsEnginePreference: Flow<String> = store.data.map { it[Keys.TTS_ENGINE_PREFERENCE] ?: "AUTO" }
    suspend fun setTtsEnginePreference(value: String) { store.edit { it[Keys.TTS_ENGINE_PREFERENCE] = value } }

    /** Whether the realtime voice pipeline listens for interrupting speech while TTS is
     * playing. Best-effort (needs hardware echo cancellation) — off automatically falls back
     * to a tap-to-interrupt button, see [com.vervan.chat.audio.ContinuousAudioCapture]. */
    val bargeInEnabled: Flow<Boolean> = store.data.map { it[Keys.BARGE_IN_ENABLED] ?: true }
    suspend fun setBargeInEnabled(v: Boolean) { store.edit { it[Keys.BARGE_IN_ENABLED] = v } }

    /** Realtime voice pipeline's speech-to-text policy (see
     * [com.vervan.chat.voice.RealtimeVoiceController]): the active generation model is tried
     * first when it supports audio input; this toggle only controls whether the downloaded
     * on-device whisper.cpp model — the only offline STT engine — is used as the fallback tier
     * (default on). There is no device speech-recognizer fallback tier — Android's system STT is
     * deliberately never used, so with this off (or no model downloaded) voice chat only works
     * via the active model's own audio input. Has no effect until the whisper.cpp model is
     * actually downloaded via Model Manager, or on a build with no whisper.cpp native library
     * (see [com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE]). */
    val inbuiltSttEnabled: Flow<Boolean> = store.data.map { it[Keys.INBUILT_STT_ENABLED] ?: true }
    suspend fun setInbuiltSttEnabled(v: Boolean) { store.edit { it[Keys.INBUILT_STT_ENABLED] = v } }

    /** Speech recognition is independently configurable from the active chat model. AUTO tries
     * enabled engines in the privacy-first order: model audio, whisper.cpp, Android on-device
     * recognition. An explicit preference stays strict unless fallback is enabled. */
    val modelAudioSttEnabled: Flow<Boolean> = store.data.map { it[Keys.MODEL_AUDIO_STT_ENABLED] ?: true }
    suspend fun setModelAudioSttEnabled(v: Boolean) { store.edit { it[Keys.MODEL_AUDIO_STT_ENABLED] = v } }
    val androidSttEnabled: Flow<Boolean> = store.data.map { it[Keys.ANDROID_STT_ENABLED] ?: true }
    suspend fun setAndroidSttEnabled(v: Boolean) { store.edit { it[Keys.ANDROID_STT_ENABLED] = v } }
    val sttEnginePreference: Flow<String> = store.data.map { it[Keys.STT_ENGINE_PREFERENCE] ?: "AUTO" }
    suspend fun setSttEnginePreference(v: String) { store.edit { it[Keys.STT_ENGINE_PREFERENCE] = v } }
    val sttFallbackEnabled: Flow<Boolean> = store.data.map { it[Keys.STT_FALLBACK_ENABLED] ?: true }
    suspend fun setSttFallbackEnabled(v: Boolean) { store.edit { it[Keys.STT_FALLBACK_ENABLED] = v } }

    /** One-dial voice quality ("FAST"/"BALANCED"/"BEST") shown as the primary control in Voice
     * settings — see [com.vervan.chat.ui.settings.SettingsViewModel.setVoiceQualityPreset] for
     * what each level actually applies to the granular engine settings above. Applying a preset
     * is one-way: it writes sensible values into those settings, which the Advanced section below
     * can still override individually without changing this label back. */
    val voiceQualityPreset: Flow<String> = store.data.map { it[Keys.VOICE_QUALITY_PRESET] ?: "BALANCED" }
    suspend fun setVoiceQualityPreset(v: String) { store.edit { it[Keys.VOICE_QUALITY_PRESET] = v } }

    /** Whether [com.vervan.chat.voice.WhisperCppSttEngine] is allowed to try its Vulkan GPU
     * backend. OFF by default: GPU init has been observed to crash the whole process with a
     * native SIGSEGV during device/pipeline setup on at least one real device instead of failing
     * gracefully, whereas the CPU backend has been reliable — so this app never risks that crash
     * without the user explicitly opting in here. Even opted in, a crash-loop breaker (see that
     * class's doc) permanently falls back to CPU for this install after the first crash, so
     * turning this on costs at most one crash, never a repeat one. */
    val whisperGpuEnabled: Flow<Boolean> = store.data.map { it[Keys.WHISPER_GPU_ENABLED] ?: false }
    suspend fun setWhisperGpuEnabled(v: Boolean) { store.edit { it[Keys.WHISPER_GPU_ENABLED] = v } }

    /** Which installed Supertonic voice is used — a [com.vervan.chat.modeldownload.CatalogModel]
     * `ttsLanguage` key ("multi" for the original bundled M1 voice, or "F1"/"M2"/etc. for one of
     * the other 9 installable voice styles — see [com.vervan.chat.voice.SupertonicTtsEngine]).
     * Defaults to "multi" so existing installs keep using the same voice they already have,
     * unchanged. Every voice other than "multi" is just a ~290 KB style file that reuses the
     * shared ~400 MB acoustic model from the "multi" package, so that package must stay
     * installed regardless of which voice is selected here. */
    val supertonicVoiceVariant: Flow<String> = store.data.map { it[Keys.SUPERTONIC_VOICE_VARIANT] ?: "multi" }
    suspend fun setSupertonicVoiceVariant(v: String) { store.edit { it[Keys.SUPERTONIC_VOICE_VARIANT] = v } }

    /** Which installed whisper.cpp model is loaded — a catalog `ttsLanguage` key ("multi" for the
     * original bundled Tiny model, or "base"/"small"). Defaults to "multi" so existing installs
     * keep working unchanged. See [com.vervan.chat.voice.WhisperCppSttEngine]. */
    val whisperModelVariant: Flow<String> = store.data.map { it[Keys.WHISPER_MODEL_VARIANT] ?: "multi" }
    suspend fun setWhisperModelVariant(v: String) { store.edit { it[Keys.WHISPER_MODEL_VARIANT] = v } }

    // ---- Unified multimodal voice UX ----
    val speechInputEnabled: Flow<Boolean> = store.data.map { it[Keys.SPEECH_INPUT_ENABLED] ?: true }
    suspend fun setSpeechInputEnabled(v: Boolean) { store.edit { it[Keys.SPEECH_INPUT_ENABLED] = v } }
    val voiceReplyMode: Flow<String> = store.data.map { it[Keys.VOICE_REPLY_MODE] ?: "MANUAL" }
    suspend fun setVoiceReplyMode(v: String) { store.edit { it[Keys.VOICE_REPLY_MODE] = v } }
    val voiceInputMethod: Flow<String> = store.data.map { it[Keys.VOICE_INPUT_METHOD] ?: "DICTATION" }
    suspend fun setVoiceInputMethod(v: String) { store.edit { it[Keys.VOICE_INPUT_METHOD] = v } }
    val transcriptReviewEnabled: Flow<Boolean> = store.data.map { it[Keys.TRANSCRIPT_REVIEW_ENABLED] ?: true }
    suspend fun setTranscriptReviewEnabled(v: Boolean) { store.edit { it[Keys.TRANSCRIPT_REVIEW_ENABLED] = v } }
    val handsFreeAutoSend: Flow<Boolean> = store.data.map { it[Keys.HANDS_FREE_AUTO_SEND] ?: true }
    suspend fun setHandsFreeAutoSend(v: Boolean) { store.edit { it[Keys.HANDS_FREE_AUTO_SEND] = v } }
    val continueListening: Flow<Boolean> = store.data.map { it[Keys.CONTINUE_LISTENING] ?: true }
    suspend fun setContinueListening(v: Boolean) { store.edit { it[Keys.CONTINUE_LISTENING] = v } }
    val headphonesOnlyPlayback: Flow<Boolean> = store.data.map { it[Keys.HEADPHONES_ONLY_PLAYBACK] ?: false }
    suspend fun setHeadphonesOnlyPlayback(v: Boolean) { store.edit { it[Keys.HEADPHONES_ONLY_PLAYBACK] = v } }
    val headphonePrivacyPause: Flow<Boolean> = store.data.map { it[Keys.HEADPHONE_PRIVACY_PAUSE] ?: true }
    suspend fun setHeadphonePrivacyPause(v: Boolean) { store.edit { it[Keys.HEADPHONE_PRIVACY_PAUSE] = v } }
    val voiceInputLanguage: Flow<String> = store.data.map { it[Keys.VOICE_INPUT_LANGUAGE] ?: "AUTO" }
    suspend fun setVoiceInputLanguage(v: String) { store.edit { it[Keys.VOICE_INPUT_LANGUAGE] = v } }
    val vadSensitivity: Flow<Float> = store.data.map { it[Keys.VAD_SENSITIVITY] ?: 0.5f }
    suspend fun setVadSensitivity(v: Float) { store.edit { it[Keys.VAD_SENSITIVITY] = v.coerceIn(0f, 1f) } }
    val voiceSilenceDurationMs: Flow<Int> = store.data.map { it[Keys.VOICE_SILENCE_DURATION_MS] ?: 600 }
    suspend fun setVoiceSilenceDurationMs(v: Int) { store.edit { it[Keys.VOICE_SILENCE_DURATION_MS] = v.coerceIn(300, 3000) } }
    val maxUtteranceSeconds: Flow<Int> = store.data.map { it[Keys.MAX_UTTERANCE_SECONDS] ?: 30 }
    suspend fun setMaxUtteranceSeconds(v: Int) { store.edit { it[Keys.MAX_UTTERANCE_SECONDS] = v.coerceIn(10, 180) } }
    val storeVoiceRecordings: Flow<Boolean> = store.data.map { it[Keys.STORE_VOICE_RECORDINGS] ?: false }
    suspend fun setStoreVoiceRecordings(v: Boolean) { store.edit { it[Keys.STORE_VOICE_RECORDINGS] = v } }
    val voiceSpeechRate: Flow<Float> = store.data.map { it[Keys.VOICE_SPEECH_RATE] ?: 1f }
    suspend fun setVoiceSpeechRate(v: Float) { store.edit { it[Keys.VOICE_SPEECH_RATE] = v.coerceIn(0.6f, 1.6f) } }
    val voiceSpeechPitch: Flow<Float> = store.data.map { it[Keys.VOICE_SPEECH_PITCH] ?: 1f }
    suspend fun setVoiceSpeechPitch(v: Float) { store.edit { it[Keys.VOICE_SPEECH_PITCH] = v.coerceIn(0.7f, 1.3f) } }
    val readCodeMode: Flow<String> = store.data.map { it[Keys.READ_CODE_MODE] ?: "SUMMARY" }
    suspend fun setReadCodeMode(v: String) { store.edit { it[Keys.READ_CODE_MODE] = v } }
    val readTableMode: Flow<String> = store.data.map { it[Keys.READ_TABLE_MODE] ?: "SUMMARY" }
    suspend fun setReadTableMode(v: String) { store.edit { it[Keys.READ_TABLE_MODE] = v } }
    val longResponseVoiceMode: Flow<String> = store.data.map { it[Keys.LONG_RESPONSE_VOICE_MODE] ?: "ASK" }
    suspend fun setLongResponseVoiceMode(v: String) { store.edit { it[Keys.LONG_RESPONSE_VOICE_MODE] = v } }
    val backgroundVoiceEnabled: Flow<Boolean> = store.data.map { it[Keys.BACKGROUND_VOICE_ENABLED] ?: false }
    suspend fun setBackgroundVoiceEnabled(v: Boolean) { store.edit { it[Keys.BACKGROUND_VOICE_ENABLED] = v } }
    val voiceBatterySaver: Flow<Boolean> = store.data.map { it[Keys.VOICE_BATTERY_SAVER] ?: true }
    suspend fun setVoiceBatterySaver(v: Boolean) { store.edit { it[Keys.VOICE_BATTERY_SAVER] = v } }
    val transcriptRetentionEnabled: Flow<Boolean> = store.data.map { it[Keys.TRANSCRIPT_RETENTION_ENABLED] ?: true }
    suspend fun setTranscriptRetentionEnabled(v: Boolean) { store.edit { it[Keys.TRANSCRIPT_RETENTION_ENABLED] = v } }
    val recordingRetentionMode: Flow<String> = store.data.map { it[Keys.RECORDING_RETENTION_MODE] ?: "TEMPORARY" }
    suspend fun setRecordingRetentionMode(v: String) { store.edit { it[Keys.RECORDING_RETENTION_MODE] = v } }

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

    /** UI text scale multiplier, 0.85x-1.3x — font-scale accessibility setting. */
    val fontScale: Flow<Float> = store.data.map { it[Keys.FONT_SCALE] ?: 1.0f }
    suspend fun setFontScale(scale: Float) { store.edit { it[Keys.FONT_SCALE] = scale } }

    val contextTokenLimit: Flow<Int> = store.data.map { it[Keys.CONTEXT_TOKEN_LIMIT] ?: 4096 }
    suspend fun setContextTokenLimit(limit: Int) { store.edit { it[Keys.CONTEXT_TOKEN_LIMIT] = limit } }

    /**
     * Declared, not inferred — the user picks these explicitly in Settings, the
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
     * or swap despite the conservative pre-load estimate. On by default so users aren't blocked
     * from loading models that the conservative estimate rejects; users who want the strict
     * guard can turn it off. */
    val allowLowMemoryModelLoads: Flow<Boolean> = store.data.map { it[Keys.ALLOW_LOW_MEMORY_MODEL_LOADS] ?: true }
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

    /** Default model profile for new chats. One of ModelProfileType.id. */
    val defaultProfile: Flow<String> = store.data.map { it[Keys.DEFAULT_PROFILE] ?: "BALANCED" }
    suspend fun setDefaultProfile(value: String) { store.edit { it[Keys.DEFAULT_PROFILE] = value } }

    /** Falls back to the Default Workspace id (Workspace System) until the user
     * switches — new chats always have somewhere valid to land. */
    val activeWorkspaceId: Flow<String> = store.data.map {
        it[Keys.ACTIVE_WORKSPACE_ID] ?: com.vervan.chat.data.db.entities.Workspace.DEFAULT_WORKSPACE_ID
    }
    suspend fun setActiveWorkspaceId(id: String) { store.edit { it[Keys.ACTIVE_WORKSPACE_ID] = id } }

    // ---- User profile ----
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
     *). Empty when nothing is filled in, so a user who never opens this screen pays
     * zero prompt cost.
     */
    // ---- App lock ----
    val appLockEnabled: Flow<Boolean> = store.data.map { it[Keys.APP_LOCK_ENABLED] ?: false }
    suspend fun setAppLockEnabled(enabled: Boolean) { store.edit { it[Keys.APP_LOCK_ENABLED] = enabled } }

    /** One of AppLockMethod's names ("BIOMETRIC"/"PIN"/"BOTH"). */
    val appLockMethod: Flow<String> = store.data.map { it[Keys.APP_LOCK_METHOD] ?: "BIOMETRIC" }
    suspend fun setAppLockMethod(value: String) { store.edit { it[Keys.APP_LOCK_METHOD] = value } }

    val autoLockTimeoutSeconds: Flow<Int> = store.data.map { it[Keys.AUTO_LOCK_TIMEOUT_SECONDS] ?: 60 }
    suspend fun setAutoLockTimeoutSeconds(value: Int) { store.edit { it[Keys.AUTO_LOCK_TIMEOUT_SECONDS] = value } }

    val quickActionBubbleEnabled: Flow<Boolean> = store.data.map { it[Keys.QUICK_ACTION_BUBBLE_ENABLED] ?: false }
    suspend fun setQuickActionBubbleEnabled(v: Boolean) { store.edit { it[Keys.QUICK_ACTION_BUBBLE_ENABLED] = v } }

    // ---- Local API server ----
    val apiServerEnabled: Flow<Boolean> = store.data.map { it[Keys.API_SERVER_ENABLED] ?: false }
    suspend fun setApiServerEnabled(v: Boolean) { store.edit { it[Keys.API_SERVER_ENABLED] = v } }
    val lanApiServerEnabled: Flow<Boolean> = store.data.map { it[Keys.LAN_API_SERVER_ENABLED] ?: false }
    suspend fun setLanApiServerEnabled(v: Boolean) { store.edit { it[Keys.LAN_API_SERVER_ENABLED] = v } }
    val apiServerPort: Flow<Int> = store.data.map { it[Keys.API_SERVER_PORT] ?: 8080 }
    suspend fun setApiServerPort(v: Int) { store.edit { it[Keys.API_SERVER_PORT] = v.coerceIn(1024, 65535) } }
    val apiServerRequireAuth: Flow<Boolean> = store.data.map { it[Keys.API_SERVER_REQUIRE_AUTH] ?: true }
    suspend fun setApiServerRequireAuth(v: Boolean) { store.edit { it[Keys.API_SERVER_REQUIRE_AUTH] = v } }

    // ---- Retention policy ----
    val autoDeleteAfterDays: Flow<Int> = store.data.map { it[Keys.AUTO_DELETE_AFTER_DAYS] ?: 0 }
    suspend fun setAutoDeleteAfterDays(value: Int) { store.edit { it[Keys.AUTO_DELETE_AFTER_DAYS] = value.coerceAtLeast(0) } }

    // ---- On-device data sources — all off by default ----
    val calendarToolEnabled: Flow<Boolean> = store.data.map { it[Keys.CALENDAR_TOOL_ENABLED] ?: false }
    suspend fun setCalendarToolEnabled(v: Boolean) { store.edit { it[Keys.CALENDAR_TOOL_ENABLED] = v } }
    val deviceStatusToolEnabled: Flow<Boolean> = store.data.map { it[Keys.DEVICE_STATUS_TOOL_ENABLED] ?: false }
    suspend fun setDeviceStatusToolEnabled(v: Boolean) { store.edit { it[Keys.DEVICE_STATUS_TOOL_ENABLED] = v } }
    val locationToolEnabled: Flow<Boolean> = store.data.map { it[Keys.LOCATION_TOOL_ENABLED] ?: false }
    suspend fun setLocationToolEnabled(v: Boolean) { store.edit { it[Keys.LOCATION_TOOL_ENABLED] = v } }

    // ---- Tool catalog (Settings → Tools) ----
    val disabledToolIds: Flow<Set<String>> = store.data.map { it[Keys.DISABLED_TOOL_IDS] ?: emptySet() }
    suspend fun setToolEnabled(toolId: String, enabled: Boolean) {
        store.edit { prefs ->
            val current = prefs[Keys.DISABLED_TOOL_IDS] ?: emptySet()
            prefs[Keys.DISABLED_TOOL_IDS] = if (enabled) current - toolId else current + toolId
        }
    }
    val toolFavorites: Flow<Set<String>> = store.data.map { it[Keys.TOOL_FAVORITES] ?: emptySet() }
    suspend fun setToolFavorites(routes: Set<String>) {
        store.edit { it[Keys.TOOL_FAVORITES] = routes }
    }
    val onboarded: Flow<Boolean> = store.data.map { it[Keys.ONBOARDED] ?: false }
    suspend fun setOnboarded(value: Boolean) {
        store.edit { it[Keys.ONBOARDED] = value }
    }
    val alwaysIncludeDateTime: Flow<Boolean> = store.data.map { it[Keys.ALWAYS_INCLUDE_DATETIME] ?: true }
    suspend fun setAlwaysIncludeDateTime(v: Boolean) { store.edit { it[Keys.ALWAYS_INCLUDE_DATETIME] = v } }

    val screenshotBlockingEnabled: Flow<Boolean> = store.data.map { it[Keys.SCREENSHOT_BLOCKING_ENABLED] ?: false }
    suspend fun setScreenshotBlockingEnabled(v: Boolean) { store.edit { it[Keys.SCREENSHOT_BLOCKING_ENABLED] = v } }

    /** Panic wipe — clears every preference back to defaults. Does not touch the
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
