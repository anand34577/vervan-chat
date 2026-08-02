package com.vervan.chat.voice

import com.vervan.chat.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Resolves which [TtsEngine] is active right now, per
 * [SettingsRepository.ttsEnginePreference] + each engine's own readiness. Android's built-in
 * system TTS is deliberately never used — Piper is the only always-considered engine; Kokoro and
 * Supertonic are each only ever chosen by an explicit pin ("KOKORO"/"SUPERTONIC") — Kokoro for
 * its latency (2-3 minutes of compute per minute of audio on budget/mid-range devices), Supertonic
 * for its heavier 4-graph/8-step denoising pipeline (~400 MB of weights) — either would break the
 * realtime feel if picked automatically. [KokoroTtsEngine.isReady]/[SupertonicTtsEngine.isReady]
 * already gate on the voice actually being downloaded, so there's no separate "enabled" flag to
 * keep in sync with the pin.
 *
 * [resolve] returns null when no downloaded engine is ready (e.g. Piper hasn't been downloaded
 * yet) — callers must treat that as "no TTS available this turn" rather than falling back to a
 * device engine.
 */
class TtsEngineSelector(
    private val settingsRepository: SettingsRepository,
    private val piper: PiperTtsEngine,
    private val kokoro: KokoroTtsEngine,
    private val supertonic: SupertonicTtsEngine
) {
    /** The engine [resolve] picked last time, for the "TTS: {engineName}" UI badge — read
     * after a [resolve] call, not a live/reactive value. */
    var lastResolvedEngineName: String = "None"
        private set

    suspend fun resolve(): TtsEngine? {
        val preference = settingsRepository.ttsEnginePreference.first()

        val engine = when {
            preference == "SUPERTONIC" && supertonic.isReady() -> supertonic
            preference == "KOKORO" && kokoro.isReady() -> kokoro
            piper.isReady() -> piper
            else -> null
        }
        lastResolvedEngineName = engine?.engineName ?: "None"
        return engine
    }

    /** Releases every engine's native resources (ONNX/sherpa-onnx sessions), regardless of which
     * one was actually resolved/used this session — each engine's own [release] is a no-op if it
     * was never loaded. Callers must invoke this on teardown; nothing here does it implicitly. */
    fun releaseAll() {
        piper.release()
        kokoro.release()
        supertonic.release()
    }
}
