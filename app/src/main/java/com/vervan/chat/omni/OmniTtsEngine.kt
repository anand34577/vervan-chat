package com.vervan.chat.omni

import com.vervan.chat.VervanApp
import com.vervan.chat.omni.runtime.ChatPrompt
import com.vervan.chat.omni.runtime.Conversation
import com.vervan.chat.omni.runtime.Lfm3OmniEngine
import com.vervan.chat.voice.TtsAudio
import com.vervan.chat.voice.TtsEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Text-to-speech with LFM3 Omni's own voice (the model card's "Perform TTS in <language> using a
 * <gender> voice." prompt), as a [TtsEngine] for voice replies and the Text-to-speech tool. Chosen
 * only by an explicit "LFM3_OMNI" pin in [com.vervan.chat.voice.TtsEngineSelector]: it is far
 * heavier than Piper (a 2B model per sentence), so it is never picked automatically.
 *
 * Output is 24 kHz mono. The model starts each utterance with a short near-silent lead, which is
 * trimmed here so sentence-by-sentence playback does not stutter.
 */
class OmniTtsEngine(private val app: VervanApp) : TtsEngine {
    override val engineName = "LFM3 Omni voice"

    override suspend fun isReady(): Boolean =
        OmniRuntime.findBundle(app) != null && OmniRuntime.findSpeechDir(app) != null

    override suspend fun synthesize(text: String, lang: String): TtsAudio {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "Nothing to speak" }
        val settings = app.container.settingsRepository
        val gender = if (settings.omniTtsVoice.first() == "male") "male" else "female"
        val language = settings.omniTtsLanguage.first()
        val pcm = OmniRuntime.use(app, exclusive = false) { engine ->
            val ids = engine.tokenizer.encode(ChatPrompt.tts(clean, language, gender))
            Conversation(engine).answerIds(ids, maxNewTokens = maxFramesFor(clean)).pcm
        }
        return TtsAudio(toPcm16(trimLeadingSilence(pcm)), Lfm3OmniEngine.SAMPLE_RATE)
    }

    /** Frees the Omni runtime (it holds ~2 GB while open). Safe to call when it was never opened. */
    fun release() {
        app.applicationScope.launch { OmniRuntime.close() }
    }

    companion object {
        /** One speech frame is 80 ms; allow ~0.5 s per word plus a margin, capped by the context. */
        fun maxFramesFor(text: String): Int {
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
            return (words * 7 + 40).coerceIn(60, 1500)
        }

        fun toPcm16(pcm: FloatArray): ShortArray =
            ShortArray(pcm.size) { (pcm[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }

        /** Drops the quiet lead-in (below ~-45 dBFS), keeping 60 ms before the first sound. */
        fun trimLeadingSilence(pcm: FloatArray, rate: Int = Lfm3OmniEngine.SAMPLE_RATE): FloatArray {
            val threshold = 0.006f
            val first = pcm.indexOfFirst { kotlin.math.abs(it) > threshold }
            if (first <= 0) return pcm
            val start = (first - rate * 60 / 1000).coerceAtLeast(0)
            return pcm.copyOfRange(start, pcm.size)
        }
    }
}
