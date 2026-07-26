package com.vervan.chat.voice

import com.vervan.chat.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Resolves which [TtsEngine] is active right now, per
 * [SettingsRepository.ttsEnginePreference] + each engine's own readiness. Android's built-in
 * system TTS is deliberately never used — Piper is the only always-considered engine, and Kokoro
 * is only ever chosen by an explicit "KOKORO" pin (its latency, 2-3 minutes of compute per minute
 * of audio on budget/mid-range devices, would break the realtime feel if it were picked
 * automatically). [KokoroTtsEngine.isReady] already gates on the voice actually being downloaded,
 * so there's no separate "enabled" flag to keep in sync with the pin.
 *
 * [resolve] returns null when no downloaded engine is ready (e.g. Piper hasn't been downloaded
 * yet) — callers must treat that as "no TTS available this turn" rather than falling back to a
 * device engine.
 *
 * Supertonic is not wired in — see `SupertonicTtsEngine.kt.disabled` — its Android SDK isn't
 * publicly Maven-resolvable or documented, unlike Piper/Kokoro (both routed through
 * sherpa-onnx). Re-add a `supertonic` engine parameter here once that's resolved.
 */
class TtsEngineSelector(
    private val settingsRepository: SettingsRepository,
    private val piper: PiperTtsEngine,
    private val kokoro: KokoroTtsEngine
) {
    /** The engine [resolve] picked last time, for the "TTS: {engineName}" UI badge — read
     * after a [resolve] call, not a live/reactive value. */
    var lastResolvedEngineName: String = "None"
        private set

    suspend fun resolve(): TtsEngine? {
        val preference = settingsRepository.ttsEnginePreference.first()

        val engine = when {
            preference == "KOKORO" && kokoro.isReady() -> kokoro
            piper.isReady() -> piper
            else -> null
        }
        lastResolvedEngineName = engine?.engineName ?: "None"
        return engine
    }
}
