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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)
    val defaultRetrievalMode: StateFlow<String> = settings.defaultRetrievalMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "HYBRID")
    val ttsRate: StateFlow<Float> = settings.ttsRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
    val autoReadAloud: StateFlow<Boolean> = settings.autoReadAloud
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val ttsEnginePreference: StateFlow<String> = settings.ttsEnginePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")
    val kokoroQualityEnabled: StateFlow<Boolean> = settings.kokoroQualityEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val bargeInEnabled: StateFlow<Boolean> = settings.bargeInEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val inbuiltSttEnabled: StateFlow<Boolean> = settings.inbuiltSttEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val sttEnginePreference: StateFlow<String> = settings.sttEnginePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")

    // ---- Realtime voice — Piper/Kokoro voice model downloads ----
    val downloadedVoiceModels: StateFlow<List<com.vervan.chat.data.db.entities.TtsVoiceModel>> =
        app.container.db.ttsVoiceModelDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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
    val showGenerationStats = settings.showGenerationStats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccentTheme.AMBER)
    val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val expertMode: StateFlow<Boolean> = settings.expertMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoModelSelectionEnabled: StateFlow<Boolean> = settings.autoModelSelectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val deviceAwarePerformance: StateFlow<Boolean> = settings.deviceAwarePerformance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val largeTouchTargets: StateFlow<Boolean> = settings.largeTouchTargets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val highContrast: StateFlow<Boolean> = settings.highContrast
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ---- App lock (Phase A) ----
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

    // ---- Quick-action bubble (Phase I) ----
    val quickActionBubbleEnabled: StateFlow<Boolean> = settings.quickActionBubbleEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setQuickActionBubbleEnabled(v: Boolean) {
        viewModelScope.launch { settings.setQuickActionBubbleEnabled(v) }
        // Start while Settings is visible. Android 12+ can reject foreground-service starts
        // after the app has already moved to the background.
        if (v) com.vervan.chat.overlay.BubbleService.start(app)
        else com.vervan.chat.overlay.BubbleService.stop(app)
    }

    // ---- Local API server (Phase J) ----
    val apiServerEnabled: StateFlow<Boolean> = settings.apiServerEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val lanApiServerEnabled: StateFlow<Boolean> = settings.lanApiServerEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val apiServerPort: StateFlow<Int> = settings.apiServerPort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8080)
    val apiServerRequireAuth: StateFlow<Boolean> = settings.apiServerRequireAuth.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val apiServerToken: String get() = app.container.apiServerAuth.tokenOrGenerate()
    fun regenerateApiServerToken(): String = app.container.apiServerAuth.regenerate()

    /** Any change here — including LAN/port while already running — needs a restart to take
     * effect, since NanoHTTPD binds host/port at construction. Simplest correct behavior:
     * always restart the service on any settings change made while it's on, rather than
     * silently leaving it serving the old configuration. */
    fun setApiServerEnabled(v: Boolean) {
        viewModelScope.launch { settings.setApiServerEnabled(v) }
        if (v) com.vervan.chat.server.ApiServerService.start(app) else com.vervan.chat.server.ApiServerService.stop(app)
    }
    private fun restartIfRunning() {
        // ApiServerService.onStartCommand() no-ops if a server instance already exists, so a
        // config change while running needs an explicit stop before the restart actually picks
        // up the new host/port/auth settings.
        if (apiServerEnabled.value) {
            com.vervan.chat.server.ApiServerService.stop(app)
            com.vervan.chat.server.ApiServerService.start(app)
        }
    }
    fun setLanApiServerEnabled(v: Boolean) { viewModelScope.launch { settings.setLanApiServerEnabled(v) }; restartIfRunning() }
    fun setApiServerPort(v: Int) { viewModelScope.launch { settings.setApiServerPort(v) }; restartIfRunning() }
    fun setApiServerRequireAuth(v: Boolean) { viewModelScope.launch { settings.setApiServerRequireAuth(v) }; restartIfRunning() }

    // ---- On-device data sources (Phase G) ----
    val calendarToolEnabled: StateFlow<Boolean> = settings.calendarToolEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val deviceStatusToolEnabled: StateFlow<Boolean> = settings.deviceStatusToolEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val filesToolEnabled: StateFlow<Boolean> = settings.filesToolEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val locationToolEnabled: StateFlow<Boolean> = settings.locationToolEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val screenTimeToolEnabled: StateFlow<Boolean> = settings.screenTimeToolEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setCalendarToolEnabled(v: Boolean) { viewModelScope.launch { settings.setCalendarToolEnabled(v) } }
    fun setDeviceStatusToolEnabled(v: Boolean) { viewModelScope.launch { settings.setDeviceStatusToolEnabled(v) } }
    fun setFilesToolEnabled(v: Boolean) { viewModelScope.launch { settings.setFilesToolEnabled(v) } }
    fun setLocationToolEnabled(v: Boolean) { viewModelScope.launch { settings.setLocationToolEnabled(v) } }
    fun setScreenTimeToolEnabled(v: Boolean) { viewModelScope.launch { settings.setScreenTimeToolEnabled(v) } }

    // ---- Web search (model-initiated outbound-network tool) ----
    val webSearchToolEnabled: StateFlow<Boolean> = settings.webSearchToolEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setWebSearchToolEnabled(v: Boolean) { viewModelScope.launch { settings.setWebSearchToolEnabled(v) } }
    /** True only when both the toggle is on AND an API key has been configured, since either
     * being false leaves the tool unable to actually run — surfaces honestly in the Settings
     * UI instead of showing "on" for a switch the model call will still reject. */
    val webSearchConfigured: Boolean get() = webSearchToolEnabled.value && app.container.knowledgeGraphStore.get()?.isNotBlank() == true
    fun webSearchApiKey(): String = app.container.knowledgeGraphStore.get().orEmpty()
    fun setWebSearchApiKey(value: String) {
        // Blank clears the key entirely (KnowledgeGraphStore.set treats null/blank as "remove"),
        // so the user can wipe what's stored without having to dig through app data.
        app.container.knowledgeGraphStore.set(value.ifBlank { null })
    }

    // ---- Tool catalog (Settings → Tools) ----
    val disabledToolIds: StateFlow<Set<String>> = settings.disabledToolIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val alwaysIncludeDateTime: StateFlow<Boolean> = settings.alwaysIncludeDateTime.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setToolEnabled(toolId: String, enabled: Boolean) { viewModelScope.launch { settings.setToolEnabled(toolId, enabled) } }
    fun setAlwaysIncludeDateTime(v: Boolean) { viewModelScope.launch { settings.setAlwaysIncludeDateTime(v) } }

    // ---- App-wide screenshot/screen-recording block ----
    val screenshotBlockingEnabled: StateFlow<Boolean> = settings.screenshotBlockingEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setScreenshotBlockingEnabled(v: Boolean) { viewModelScope.launch { settings.setScreenshotBlockingEnabled(v) } }

    // ---- Retention policy (Phase C) ----
    val autoDeleteAfterDays: StateFlow<Int> = settings.autoDeleteAfterDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    fun setAutoDeleteAfterDays(days: Int) { viewModelScope.launch { settings.setAutoDeleteAfterDays(days) } }

    /**
     * Panic wipe (Phase C) — best-effort secure-deletes every document/model file, clears every
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
    fun setTtsRate(rate: Float) { viewModelScope.launch { settings.setTtsRate(rate) } }
    fun setAutoReadAloud(enabled: Boolean) { viewModelScope.launch { settings.setAutoReadAloud(enabled) } }
    fun setTtsEnginePreference(value: String) { viewModelScope.launch { settings.setTtsEnginePreference(value) } }
    fun setKokoroQualityEnabled(v: Boolean) { viewModelScope.launch { settings.setKokoroQualityEnabled(v) } }
    fun setBargeInEnabled(v: Boolean) { viewModelScope.launch { settings.setBargeInEnabled(v) } }
    fun setInbuiltSttEnabled(v: Boolean) { viewModelScope.launch { settings.setInbuiltSttEnabled(v) } }
    fun setSttEnginePreference(v: String) { viewModelScope.launch { settings.setSttEnginePreference(v) } }
    fun setFontScale(scale: Float) { viewModelScope.launch { settings.setFontScale(scale) } }
    fun setOledTrueBlack(enabled: Boolean) { viewModelScope.launch { settings.setOledTrueBlack(enabled) } }
    fun setAccentTheme(theme: AccentTheme) { viewModelScope.launch { settings.setAccentTheme(theme) } }
    fun setHapticsEnabled(enabled: Boolean) { viewModelScope.launch { settings.setHapticsEnabled(enabled) } }
    fun setShowGenerationStats(enabled: Boolean) { viewModelScope.launch { settings.setShowGenerationStats(enabled) } }
    fun setExpertMode(enabled: Boolean) { viewModelScope.launch { settings.setExpertMode(enabled) } }
    fun setDeviceAwarePerformance(enabled: Boolean) { viewModelScope.launch { settings.setDeviceAwarePerformance(enabled) } }
    fun setAutoModelSelectionEnabled(enabled: Boolean) { viewModelScope.launch { settings.setAutoModelSelectionEnabled(enabled) } }
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
