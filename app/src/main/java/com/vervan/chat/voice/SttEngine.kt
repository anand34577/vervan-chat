package com.vervan.chat.voice

/**
 * A pluggable offline speech-to-text backend for the realtime voice pipeline (see
 * [RealtimeVoiceController]'s 3-tier STT policy). Each implementation transcribes one
 * already-captured, VAD-endpointed PCM16 utterance.
 *
 * Note the two STT paths that are deliberately NOT [SttEngine]s: the active generation model's
 * own audio-direct transcription (it takes a WAV file, not a raw buffer, and is tied to the
 * loaded chat model) and Android's live [android.speech.SpeechRecognizer] (it captures its own
 * audio and does its own endpointing, so it never sees a finished PCM buffer). An [SttEngine] is
 * specifically the "downloaded, self-contained decoder fed a finished utterance" tier —
 * [WhisperSttEngine] today, and the extension point for any future lightweight offline decoder
 * (e.g. a Vosk or Moonshine model) that slots into exactly the same call site.
 *
 * Thread-safety contract: implementations MUST be safe to [release] concurrently with an
 * in-flight [transcribe]. The voice session decodes on a background thread while the UI can call
 * [RealtimeVoiceController.stop] (hence [release]) at any instant — a naive implementation that
 * frees native memory out from under a running decode crashes the whole process with an
 * uncatchable native fault.
 */
interface SttEngine {
    /** Short human label for the "STT: …" badge in [com.vervan.chat.ui.tools.VoiceChatScreen]. */
    val label: String

    /** True once the model is downloaded, loaded, and a transcription can actually be attempted.
     * Cheap to call repeatedly and safe to poll — it triggers a one-time lazy load and simply
     * returns false (without latching) while the model isn't downloaded yet, so a download that
     * finishes mid-session is picked up on the next call rather than never. */
    suspend fun isReady(): Boolean

    /** Transcribes one utterance. [pcm] is 16-bit signed PCM at [sampleRateHz]. Returns null on
     * ANY failure (not ready, native decode error, blank output) so the caller can fall through
     * to the next STT tier instead of surfacing an error. */
    suspend fun transcribe(pcm: ShortArray, sampleRateHz: Int): String?

    /** Frees native resources. Safe to call while a [transcribe] is running — it must not free
     * anything out from under an in-flight decode. Idempotent, and leaves the engine reusable:
     * a subsequent [isReady]/[transcribe] reloads lazily. */
    fun release()
}
