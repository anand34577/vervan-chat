package com.vervan.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.settings.AccentTheme
import com.vervan.chat.data.settings.ThemeMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val app: VervanApp) : ViewModel() {
    private val settings = app.container.settingsRepository

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)
    val defaultRetrievalMode: StateFlow<String> = settings.defaultRetrievalMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "HYBRID")
    val queryExpansionEnabled: StateFlow<Boolean> = settings.queryExpansionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val includePastThinkingInContext: StateFlow<Boolean> = settings.includePastThinkingInContext
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoReadAloud: StateFlow<Boolean> = settings.autoReadAloud
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val ttsEnginePreference: StateFlow<String> = settings.ttsEnginePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")
    val bargeInEnabled: StateFlow<Boolean> = settings.bargeInEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val inbuiltSttEnabled: StateFlow<Boolean> = settings.inbuiltSttEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val modelAudioSttEnabled = settings.modelAudioSttEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val androidSttEnabled = settings.androidSttEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val sttEnginePreference = settings.sttEnginePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")
    val sttFallbackEnabled = settings.sttFallbackEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val voiceQualityPreset: StateFlow<String> = settings.voiceQualityPreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "BALANCED")

    /** Collapses the six TTS/STT engines behind one dial. FAST skips whisper.cpp's heavier compute;
     * BALANCED is today's engine defaults; BEST prefers the highest-quality downloaded TTS voice
     * (Kokoro, else Supertonic, else Piper) and drops the lower-accuracy Android STT fallback. Each
     * underlying toggle stays visible and overridable in the Advanced section below. */
    fun setVoiceQualityPreset(preset: String) = viewModelScope.launch {
        settings.setVoiceQualityPreset(preset)
        val voices = downloadedVoiceModels.value
        val bestTts = when {
            voices.any { it.engine == "KOKORO" && it.isReady } -> "KOKORO"
            voices.any { it.engine == "SUPERTONIC" && it.isReady } -> "SUPERTONIC"
            else -> "AUTO"
        }
        when (preset) {
            "FAST" -> {
                settings.setTtsEnginePreference("AUTO")
                settings.setInbuiltSttEnabled(false)
                settings.setModelAudioSttEnabled(true)
                settings.setAndroidSttEnabled(true)
                settings.setSttEnginePreference("AUTO")
                settings.setSttFallbackEnabled(true)
            }
            "BALANCED" -> {
                settings.setTtsEnginePreference("AUTO")
                settings.setInbuiltSttEnabled(true)
                settings.setModelAudioSttEnabled(true)
                settings.setAndroidSttEnabled(true)
                settings.setSttEnginePreference("AUTO")
                settings.setSttFallbackEnabled(true)
            }
            "BEST" -> {
                settings.setTtsEnginePreference(bestTts)
                settings.setInbuiltSttEnabled(true)
                settings.setModelAudioSttEnabled(true)
                settings.setAndroidSttEnabled(false)
                settings.setSttEnginePreference("AUTO")
                settings.setSttFallbackEnabled(true)
            }
        }
    }
    val whisperGpuEnabled: StateFlow<Boolean> = settings.whisperGpuEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val supertonicVoiceVariant: StateFlow<String> = settings.supertonicVoiceVariant
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "multi")
    fun setSupertonicVoiceVariant(v: String) = viewModelScope.launch { settings.setSupertonicVoiceVariant(v) }
    val whisperModelVariant: StateFlow<String> = settings.whisperModelVariant
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "multi")
    fun setWhisperModelVariant(v: String) = viewModelScope.launch { settings.setWhisperModelVariant(v) }
    val speechInputEnabled = settings.speechInputEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val voiceReplyMode = settings.voiceReplyMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MANUAL")
    val voiceInputMethod = settings.voiceInputMethod.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DICTATION")
    val transcriptReviewEnabled = settings.transcriptReviewEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val handsFreeAutoSend = settings.handsFreeAutoSend.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val continueListening = settings.continueListening.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val headphonesOnlyPlayback = settings.headphonesOnlyPlayback.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val headphonePrivacyPause = settings.headphonePrivacyPause.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val voiceInputLanguage = settings.voiceInputLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")
    val vadSensitivity = settings.vadSensitivity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.5f)
    val voiceSilenceDurationMs = settings.voiceSilenceDurationMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 600)
    val maxUtteranceSeconds = settings.maxUtteranceSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)
    val storeVoiceRecordings = settings.storeVoiceRecordings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val voiceSpeechRate = settings.voiceSpeechRate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)
    val voiceSpeechPitch = settings.voiceSpeechPitch.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)
    val readCodeMode = settings.readCodeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SUMMARY")
    val readTableMode = settings.readTableMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SUMMARY")
    val longResponseVoiceMode = settings.longResponseVoiceMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ASK")
    val backgroundVoiceEnabled = settings.backgroundVoiceEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val voiceBatterySaver = settings.voiceBatterySaver.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val transcriptRetentionEnabled = settings.transcriptRetentionEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val recordingRetentionMode = settings.recordingRetentionMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "TEMPORARY")

    fun setSpeechInputEnabled(v: Boolean) = viewModelScope.launch { settings.setSpeechInputEnabled(v) }
    fun setModelAudioSttEnabled(v: Boolean) = viewModelScope.launch { settings.setModelAudioSttEnabled(v) }
    fun setAndroidSttEnabled(v: Boolean) = viewModelScope.launch { settings.setAndroidSttEnabled(v) }
    fun setSttEnginePreference(v: String) = viewModelScope.launch { settings.setSttEnginePreference(v) }
    fun setSttFallbackEnabled(v: Boolean) = viewModelScope.launch { settings.setSttFallbackEnabled(v) }
    fun setVoiceReplyMode(v: String) = viewModelScope.launch {
        settings.setVoiceReplyMode(v)
        settings.setAutoReadAloud(v == "AUTOMATIC")
    }
    fun setVoiceInputMethod(v: String) = viewModelScope.launch { settings.setVoiceInputMethod(v) }
    fun setTranscriptReviewEnabled(v: Boolean) = viewModelScope.launch { settings.setTranscriptReviewEnabled(v) }
    fun setHandsFreeAutoSend(v: Boolean) = viewModelScope.launch { settings.setHandsFreeAutoSend(v) }
    fun setContinueListening(v: Boolean) = viewModelScope.launch { settings.setContinueListening(v) }
    fun setHeadphonesOnlyPlayback(v: Boolean) = viewModelScope.launch { settings.setHeadphonesOnlyPlayback(v) }
    fun setHeadphonePrivacyPause(v: Boolean) = viewModelScope.launch { settings.setHeadphonePrivacyPause(v) }
    fun setVoiceInputLanguage(v: String) = viewModelScope.launch { settings.setVoiceInputLanguage(v) }
    fun setVadSensitivity(v: Float) = viewModelScope.launch { settings.setVadSensitivity(v) }
    fun setVoiceSilenceDurationMs(v: Int) = viewModelScope.launch { settings.setVoiceSilenceDurationMs(v) }
    fun setMaxUtteranceSeconds(v: Int) = viewModelScope.launch { settings.setMaxUtteranceSeconds(v) }
    fun setStoreVoiceRecordings(v: Boolean) = viewModelScope.launch {
        settings.setStoreVoiceRecordings(v)
        settings.setRecordingRetentionMode(if (v) "KEEP" else "NONE")
    }
    fun setVoiceSpeechRate(v: Float) = viewModelScope.launch { settings.setVoiceSpeechRate(v) }
    fun setVoiceSpeechPitch(v: Float) = viewModelScope.launch { settings.setVoiceSpeechPitch(v) }
    fun setReadCodeMode(v: String) = viewModelScope.launch { settings.setReadCodeMode(v) }
    fun setReadTableMode(v: String) = viewModelScope.launch { settings.setReadTableMode(v) }
    fun setLongResponseVoiceMode(v: String) = viewModelScope.launch { settings.setLongResponseVoiceMode(v) }
    fun setBackgroundVoiceEnabled(v: Boolean) = viewModelScope.launch { settings.setBackgroundVoiceEnabled(v) }
    fun setVoiceBatterySaver(v: Boolean) = viewModelScope.launch { settings.setVoiceBatterySaver(v) }
    fun setTranscriptRetentionEnabled(v: Boolean) = viewModelScope.launch { settings.setTranscriptRetentionEnabled(v) }
    fun setRecordingRetentionMode(v: String) = viewModelScope.launch {
        settings.setRecordingRetentionMode(v)
        settings.setStoreVoiceRecordings(v == "KEEP")
    }

    // ---- Realtime voice — Piper/Kokoro voice model downloads ----
    val downloadedVoiceModels: StateFlow<List<com.vervan.chat.data.db.entities.TtsVoiceModel>> =
        app.container.db.ttsVoiceModelDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeGenerationModel = app.container.db.modelDao()
        .observeActiveModel(com.vervan.chat.data.db.entities.ModelRole.GENERATION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val activeVoiceDownloadJobs: StateFlow<List<com.vervan.chat.data.db.entities.JobRecord>> =
        app.container.db.jobDao().observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun downloadVoiceModel(entry: com.vervan.chat.voice.TtsVoiceCatalogEntry) {
        viewModelScope.launch {
            app.container.ttsModelDownloadManager.downloadArchiveVoice(entry.engine, entry.language, entry.label, entry.archiveUrl)
        }
    }
    fun deleteVoiceModel(entry: com.vervan.chat.voice.TtsVoiceCatalogEntry) {
        viewModelScope.launch { app.container.ttsModelDownloadManager.deleteVoice(entry.engine, entry.language) }
    }
    val fontScale: StateFlow<Float> = settings.fontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
    val contextTokenLimit: StateFlow<Int> = settings.contextTokenLimit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4096)
    val autoContextSummarization: StateFlow<Boolean> = settings.autoContextSummarization
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setAutoContextSummarization(v: Boolean) { viewModelScope.launch { settings.setAutoContextSummarization(v) } }
    val responseLength: StateFlow<String> = settings.responseLength
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "BALANCED")
    val responseTone: StateFlow<String> = settings.responseTone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "NEUTRAL")
    val temperature = settings.temperature.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.8f)
    val topP = settings.topP.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.95f)
    val topK = settings.topK.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 40)
    val preferredBackend = settings.preferredBackend.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")
    val allowLowMemoryModelLoads = settings.allowLowMemoryModelLoads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showGenerationStats = settings.showGenerationStats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val maxNumImages = settings.maxNumImages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)
    val randomSeed = settings.randomSeed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)
    val minP = settings.minP.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.05f)
    val repetitionPenalty = settings.repetitionPenalty.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.1f)
    val maxOutputTokens = settings.maxOutputTokens.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 512)
    val cpuThreads = settings.cpuThreads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val nBatch = settings.nBatch.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2048)
    val nUbatch = settings.nUbatch.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 512)
    val useMlock = settings.useMlock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val flashAttentionMode = settings.flashAttentionMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")
    val kvCacheType = settings.kvCacheType.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "f16")
    val vulkanDeviceIndex = settings.vulkanDeviceIndex.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val oledTrueBlack: StateFlow<Boolean> = settings.oledTrueBlack
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val accentTheme: StateFlow<AccentTheme> = settings.accentTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccentTheme.GREEN)
    val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val expertMode: StateFlow<Boolean> = settings.expertMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoModelSelectionEnabled: StateFlow<Boolean> = settings.autoModelSelectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val fastCapableRoutingEnabled: StateFlow<Boolean> = settings.fastCapableRoutingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val deviceAwarePerformance: StateFlow<Boolean> = settings.deviceAwarePerformance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val largeTouchTargets: StateFlow<Boolean> = settings.largeTouchTargets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val highContrast: StateFlow<Boolean> = settings.highContrast
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ---- App lock ----
    private val appLockManager = app.container.appLockManager
    val appLockEnabled: StateFlow<Boolean> = settings.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val appLockMethod: StateFlow<String> = settings.appLockMethod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "BIOMETRIC")
    val autoLockTimeoutSeconds: StateFlow<Int> = settings.autoLockTimeoutSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)
    val hasPin: Boolean get() = appLockManager.hasPin()

    /** Fails (returns false, doesn't enable) if the chosen method needs a PIN and none is set
     * yet — the security settings screen must call [setPin] first in that case. */
    fun setAppLockEnabled(enabled: Boolean) {
        if (enabled && appLockMethod.value != "BIOMETRIC" && !appLockManager.hasPin()) return
        viewModelScope.launch { settings.setAppLockEnabled(enabled) }
        if (!enabled) appLockManager.unlock()
    }
    fun setAppLockMethod(value: String) { viewModelScope.launch { settings.setAppLockMethod(value) } }
    fun setAutoLockTimeoutSeconds(value: Int) { viewModelScope.launch { settings.setAutoLockTimeoutSeconds(value) } }
    fun setPin(pin: String) { appLockManager.setPin(pin) }
    fun clearPin() { appLockManager.clearPin() }

    // ---- Quick-action bubble ----
    val quickActionBubbleEnabled: StateFlow<Boolean> = settings.quickActionBubbleEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setQuickActionBubbleEnabled(v: Boolean) {
        viewModelScope.launch { settings.setQuickActionBubbleEnabled(v) }
        // Start while Settings is visible. Android 12+ can reject foreground-service starts
        // after the app has already moved to the background.
        if (v) com.vervan.chat.overlay.BubbleService.start(app)
        else com.vervan.chat.overlay.BubbleService.stop(app)
    }

    // ---- Local API server ----
    val apiServerEnabled: StateFlow<Boolean> = settings.apiServerEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val apiServerPort: StateFlow<Int> = settings.apiServerPort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8080)
    val apiServerRequireAuth: StateFlow<Boolean> = settings.apiServerRequireAuth.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val apiServerFullMode: StateFlow<Boolean> = settings.apiServerFullMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val apiServerAutoStart: StateFlow<Boolean> = settings.apiServerAutoStart.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val apiModelTtlSeconds: StateFlow<Int> = settings.apiModelTtlSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 300)
    val apiServerToken: String get() = app.container.apiServerAuth.tokenOrGenerate()
    fun regenerateApiServerToken(): String = app.container.apiServerAuth.regenerate()

    /** Any change here — including port while already running — needs a restart to take
     * effect, since NanoHTTPD binds host/port at construction. Simplest correct behavior:
     * always restart the service on any settings change made while it's on, rather than
     * silently leaving it serving the old configuration. */
    fun setApiServerEnabled(v: Boolean) {
        viewModelScope.launch {
            settings.setApiServerEnabled(v)
            if (v) com.vervan.chat.server.ApiServerService.start(app)
            else com.vervan.chat.server.ApiServerService.stop(app)
        }
    }
    private fun updateApiServerSetting(update: suspend () -> Unit) {
        viewModelScope.launch {
            update()
            if (settings.apiServerEnabled.first()) com.vervan.chat.server.ApiServerService.restart(app)
        }
    }
    fun setApiServerPort(v: Int) = updateApiServerSetting { settings.setApiServerPort(v) }
    fun setApiServerRequireAuth(v: Boolean) = updateApiServerSetting { settings.setApiServerRequireAuth(v) }
    fun setApiServerFullMode(v: Boolean) = updateApiServerSetting { settings.setApiServerFullMode(v) }
    /** Not routed through [updateApiServerSetting]: this changes what happens at the *next* app
     * start, so restarting the running server (and dropping any in-flight stream) would achieve
     * nothing. Turning it on while the server is off also starts it now, because otherwise
     * "start automatically" would appear to do nothing until the app was restarted. */
    fun setApiServerAutoStart(v: Boolean) {
        viewModelScope.launch {
            settings.setApiServerAutoStart(v)
            if (v && !settings.apiServerEnabled.first()) {
                settings.setApiServerEnabled(true)
                com.vervan.chat.server.ApiServerService.start(app)
            }
        }
    }
    // Deliberately NOT routed through updateApiServerSetting: the TTL is read live by the load
    // coordinator on every arm/touch, so it takes effect immediately — restarting the HTTP server
    // (and dropping any in-flight stream with it) to apply it would be pure collateral damage.
    fun setApiModelTtlSeconds(v: Int) { viewModelScope.launch { settings.setApiModelTtlSeconds(v) } }
    val apiServerAppTools: StateFlow<Boolean> = settings.apiServerAppTools.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setApiServerAppTools(v: Boolean) { viewModelScope.launch { settings.setApiServerAppTools(v) } }
    val apiServerAllowWriteTools: StateFlow<Boolean> = settings.apiServerAllowWriteTools.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setApiServerAllowWriteTools(v: Boolean) { viewModelScope.launch { settings.setApiServerAllowWriteTools(v) } }

    // ---- On-device data sources ----
    val calendarToolEnabled: StateFlow<Boolean> = settings.calendarToolEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val deviceStatusToolEnabled: StateFlow<Boolean> = settings.deviceStatusToolEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setCalendarToolEnabled(v: Boolean) { viewModelScope.launch { settings.setCalendarToolEnabled(v) } }
    fun setDeviceStatusToolEnabled(v: Boolean) { viewModelScope.launch { settings.setDeviceStatusToolEnabled(v) } }

    // ---- Tool catalog (Settings → Tools) ----
    val disabledToolIds: StateFlow<Set<String>> = settings.disabledToolIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val alwaysIncludeDateTime: StateFlow<Boolean> = settings.alwaysIncludeDateTime.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setToolEnabled(toolId: String, enabled: Boolean) { viewModelScope.launch { settings.setToolEnabled(toolId, enabled) } }
    fun setAlwaysIncludeDateTime(v: Boolean) { viewModelScope.launch { settings.setAlwaysIncludeDateTime(v) } }

    // ---- App-wide screenshot/screen-recording block ----
    val screenshotBlockingEnabled: StateFlow<Boolean> = settings.screenshotBlockingEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setScreenshotBlockingEnabled(v: Boolean) { viewModelScope.launch { settings.setScreenshotBlockingEnabled(v) } }

    // ---- Retention policy ----
    val autoDeleteAfterDays: StateFlow<Int> = settings.autoDeleteAfterDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    fun setAutoDeleteAfterDays(days: Int) { viewModelScope.launch { settings.setAutoDeleteAfterDays(days) } }

    /**
     * Panic wipe — best-effort secure-deletes every document/model file, clears every
     * preference and the app-lock PIN store, then closes and deletes the Room database, and
     * finally kills the process outright (there is deliberately no "restart cleanly" step —
     * this mirrors the abrupt, no-lingering-state behavior a panic wipe is supposed to have;
     * the next launch starts from a genuinely fresh install).
     */
    suspend fun panicWipe() {
        withContext(Dispatchers.IO) {
            settings.wipeAll()
            appLockManager.clearPin()
            app.container.db.documentDao().observeAll().first().forEach {
                com.vervan.chat.data.SecureDelete.overwriteAndDelete(File(it.filePath))
            }
            app.container.db.modelDao().observeModels().first().forEach {
                com.vervan.chat.data.SecureDelete.overwriteAndDelete(File(it.filePath))
            }
            app.container.db.close()
            app.deleteDatabase("vervan.db")
            app.cacheDir.deleteRecursively()
        }
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes

    init {
        refreshCacheSize()
    }

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }
    fun setDefaultRetrievalMode(mode: String) { viewModelScope.launch { settings.setDefaultRetrievalMode(mode) } }
    fun setQueryExpansionEnabled(enabled: Boolean) { viewModelScope.launch { settings.setQueryExpansionEnabled(enabled) } }
    fun setIncludePastThinkingInContext(enabled: Boolean) { viewModelScope.launch { settings.setIncludePastThinkingInContext(enabled) } }
    fun setAutoReadAloud(enabled: Boolean) { viewModelScope.launch { settings.setAutoReadAloud(enabled) } }
    fun setTtsEnginePreference(value: String) { viewModelScope.launch { settings.setTtsEnginePreference(value) } }
    fun setBargeInEnabled(v: Boolean) { viewModelScope.launch { settings.setBargeInEnabled(v) } }
    fun setInbuiltSttEnabled(v: Boolean) { viewModelScope.launch { settings.setInbuiltSttEnabled(v) } }
    fun setWhisperGpuEnabled(v: Boolean) { viewModelScope.launch { settings.setWhisperGpuEnabled(v) } }

    /** Best-effort, non-live status for the Voice Settings screen — see
     *  [com.vervan.chat.voice.WhisperCppSttEngine]'s companion helpers. Read fresh each call
     *  rather than cached/reactive: it only changes as a side effect of an actual voice session
     *  loading whisper.cpp, which this screen doesn't run. */
    fun whisperLastKnownBackend(): String? = com.vervan.chat.voice.WhisperCppSttEngine.lastKnownBackendLabel(app)
    fun whisperGpuDisabledAfterCrash(): Boolean = com.vervan.chat.voice.WhisperCppSttEngine.isGpuDisabledAfterCrash(app)
    fun setFontScale(scale: Float) { viewModelScope.launch { settings.setFontScale(scale) } }
    fun setOledTrueBlack(enabled: Boolean) { viewModelScope.launch { settings.setOledTrueBlack(enabled) } }
    fun setAccentTheme(theme: AccentTheme) { viewModelScope.launch { settings.setAccentTheme(theme) } }
    fun setHapticsEnabled(enabled: Boolean) { viewModelScope.launch { settings.setHapticsEnabled(enabled) } }
    fun setShowGenerationStats(enabled: Boolean) { viewModelScope.launch { settings.setShowGenerationStats(enabled) } }
    fun setExpertMode(enabled: Boolean) { viewModelScope.launch { settings.setExpertMode(enabled) } }
    fun setDeviceAwarePerformance(enabled: Boolean) { viewModelScope.launch { settings.setDeviceAwarePerformance(enabled) } }
    fun setAutoModelSelectionEnabled(enabled: Boolean) { viewModelScope.launch { settings.setAutoModelSelectionEnabled(enabled) } }
    fun setFastCapableRoutingEnabled(enabled: Boolean) { viewModelScope.launch { settings.setFastCapableRoutingEnabled(enabled) } }
    fun setLargeTouchTargets(enabled: Boolean) { viewModelScope.launch { settings.setLargeTouchTargets(enabled) } }
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { settings.setDynamicColor(enabled) } }
    fun setHighContrast(enabled: Boolean) { viewModelScope.launch { settings.setHighContrast(enabled) } }
    fun setContextTokenLimit(limit: Int) { viewModelScope.launch { settings.setContextTokenLimit(limit) } }
    fun setResponseLength(value: String) { viewModelScope.launch { settings.setResponseLength(value) } }
    fun setResponseTone(value: String) { viewModelScope.launch { settings.setResponseTone(value) } }
    fun setTemperature(value: Float) { viewModelScope.launch { settings.setTemperature(value) } }
    fun setTopP(value: Float) { viewModelScope.launch { settings.setTopP(value) } }
    fun setTopK(value: Int) { viewModelScope.launch { settings.setTopK(value) } }
    fun setPreferredBackend(value: String) { viewModelScope.launch { settings.setPreferredBackend(value) } }
    fun setAllowLowMemoryModelLoads(value: Boolean) { viewModelScope.launch { settings.setAllowLowMemoryModelLoads(value) } }
    fun setMaxNumImages(value: Int) { viewModelScope.launch { settings.setMaxNumImages(value) } }
    fun setRandomSeed(value: Int) { viewModelScope.launch { settings.setRandomSeed(value) } }
    fun setMinP(value: Float) { viewModelScope.launch { settings.setMinP(value) } }
    fun setRepetitionPenalty(value: Float) { viewModelScope.launch { settings.setRepetitionPenalty(value) } }
    fun setMaxOutputTokens(value: Int) { viewModelScope.launch { settings.setMaxOutputTokens(value) } }
    fun setCpuThreads(value: Int) { viewModelScope.launch { settings.setCpuThreads(value) } }
    fun setNBatch(value: Int) { viewModelScope.launch { settings.setNBatch(value) } }
    fun setNUbatch(value: Int) { viewModelScope.launch { settings.setNUbatch(value) } }
    fun setUseMlock(value: Boolean) { viewModelScope.launch { settings.setUseMlock(value) } }
    fun setFlashAttentionMode(value: String) { viewModelScope.launch { settings.setFlashAttentionMode(value) } }
    fun setKvCacheType(value: String) { viewModelScope.launch { settings.setKvCacheType(value) } }
    fun setVulkanDeviceIndex(value: Int) { viewModelScope.launch { settings.setVulkanDeviceIndex(value) } }

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSizeBytes.value = withContext(Dispatchers.IO) { dirSize(app.cacheDir) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
            refreshCacheSize()
        }
    }

    private fun dirSize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
