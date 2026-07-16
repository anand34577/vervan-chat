package com.vervan.chat.voice

import com.vervan.chat.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Resolves which [TtsEngine] is active right now, per
 * [SettingsRepository.ttsEnginePreference] + each engine's own readiness. `AUTO` tries
 * Piper -> Android system in order; an explicit pin uses that engine if it's ready, falling
 * back to the same chain (never silently failing) if it isn't. Kokoro is only ever chosen by
 * an explicit "KOKORO" pin with [SettingsRepository.kokoroQualityEnabled] on — `AUTO` never
 * selects it, since its latency would break the realtime feel.
 *
 * Supertonic is not wired in — see `SupertonicTtsEngine.kt.disabled` — its Android SDK isn't
 * publicly Maven-resolvable or documented, unlike Piper/Kokoro (both routed through
 * sherpa-onnx). Re-add a `supertonic` engine parameter here once that's resolved.
 */
class TtsEngineSelector(
    private val settingsRepository: SettingsRepository,
    private val piper: PiperTtsEngine,
    private val system: AndroidSystemTtsEngine,
    private val kokoro: KokoroTtsEngine
) {
    /** The engine [resolve] picked last time, for the "TTS: {engineName}" UI badge — read
     * after a [resolve] call, not a live/reactive value. */
    var lastResolvedEngineName: String = system.engineName
        private set

    suspend fun resolve(): TtsEngine {
        val preference = settingsRepository.ttsEnginePreference.first()
        val kokoroEnabled = settingsRepository.kokoroQualityEnabled.first()

        val engine = when {
            preference == "KOKORO" && kokoroEnabled && kokoro.isReady() -> kokoro
            preference == "PIPER" && piper.isReady() -> piper
            preference == "SYSTEM" -> system
            piper.isReady() -> piper
            else -> system
        }
        lastResolvedEngineName = engine.engineName
        return engine
    }
}
