package com.vervan.chat.voice

/** One synthesized utterance — PCM16 mono samples plus the sample rate they were produced at.
 * Sample rate is per-result rather than a fixed property on [TtsEngine] because it genuinely
 * varies by engine and, for the Android system engine, by installed voice. */
data class TtsAudio(val samples: ShortArray, val sampleRateHz: Int)

/** One TTS backend in the tiered engine chain (Supertonic -> Piper -> Android system, plus
 * the optional Kokoro quality tier). [com.vervan.chat.voice.TtsEngineSelector] picks which
 * implementation is active; [com.vervan.chat.voice.RealtimeVoiceController] only ever talks
 * to this interface, never a concrete engine. */
interface TtsEngine {
    /** Shown in the UI badge (e.g. "TTS: Supertonic") — same pattern as the existing
     * "STT: ..." badge in [com.vervan.chat.ui.tools.VoiceChatScreen]. */
    val engineName: String

    /** Whether this engine's model files are downloaded/loaded and it can synthesize right
     * now. [TtsEngineSelector] skips engines that aren't ready rather than blocking on them. */
    suspend fun isReady(): Boolean

    /** Synthesizes one sentence. [lang] is a best-effort hint ("hi"/"en"); engines that
     * support auto-detection (Supertonic) may ignore it. Throws on synthesis failure — callers
     * (the playback queue's synth workers) catch and skip rather than crash the pipeline. */
    suspend fun synthesize(text: String, lang: String): TtsAudio
}
