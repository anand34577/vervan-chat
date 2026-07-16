package com.vervan.chat.audio

import android.content.Context

/**
 * Voice activity detection for the realtime voice pipeline — reuses sherpa-onnx's bundled
 * Silero-VAD (MIT-licensed, ~1-2MB) rather than pulling in a separate VAD dependency, since
 * sherpa-onnx is already part of this app for the Piper/Kokoro TTS engines (see
 * [com.vervan.chat.voice.PiperTtsEngine]). Used for both STT endpointing (replacing "record
 * until button release" with "record until trailing silence" in
 * [com.vervan.chat.voice.RealtimeVoiceController]) and barge-in detection (classifying the
 * live mic stream while TTS is playing).
 *
 * Requires `assets/vad/silero_vad.onnx` to be present in the APK (small enough — ~1-2MB — to
 * ship in-APK rather than downloaded). Loaded directly from the AssetManager rather than
 * copied out to app storage first: sherpa-onnx's [com.k2fsa.sherpa.onnx.Vad] constructor takes
 * an `AssetManager` specifically so asset-bundled models don't need that extra copy step —
 * passing `null` there instead (as [com.vervan.chat.voice.PiperTtsEngine] does for its
 * downloaded, non-asset voice files) tells it to treat the config's `model` path as an
 * absolute filesystem path instead.
 */
class VoiceActivityDetector(private val context: Context) {
    private var vad: com.k2fsa.sherpa.onnx.Vad? = null

    val isReady: Boolean get() = vad != null

    fun load() {
        if (vad != null) return
        val config = com.k2fsa.sherpa.onnx.VadModelConfig(
            sileroVadModelConfig = com.k2fsa.sherpa.onnx.SileroVadModelConfig(
                model = "vad/silero_vad.onnx",
                threshold = 0.5f,
                minSilenceDuration = 0.3f,
                minSpeechDuration = 0.15f,
                maxSpeechDuration = 20f
            ),
            sampleRate = SAMPLE_RATE_HZ,
            numThreads = 1,
            provider = "cpu"
        )
        // Positional: real Kotlin parameter names for this constructor weren't visible via
        // javap on the compiled AAR. context.assets, not null, since the model ships in APK.
        vad = com.k2fsa.sherpa.onnx.Vad(context.assets, config)
    }

    fun release() {
        vad?.release()
        vad = null
    }

    /** Speech/silence classification for one ~20-30ms 16kHz mono PCM16 frame. Returns false
     * (never speech) if the model hasn't loaded — callers should treat that as "can't do
     * barge-in / VAD-based endpointing right now" rather than crash. */
    fun isSpeech(frame: ShortArray): Boolean {
        val v = vad ?: return false
        v.acceptWaveform(frame.toFloatSamples())
        return v.isSpeechDetected()
    }

    fun reset() { vad?.reset() }

    private fun ShortArray.toFloatSamples(): FloatArray = FloatArray(size) { this[it] / 32768f }

    companion object {
        const val SAMPLE_RATE_HZ = 16000
    }
}
