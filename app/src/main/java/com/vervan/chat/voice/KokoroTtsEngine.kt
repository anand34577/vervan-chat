package com.vervan.chat.voice

import com.vervan.chat.data.db.dao.TtsVoiceModelDao

/**
 * Wraps sherpa-onnx's `kokoro-multi-lang` model — an explicit "higher quality voice (slower)"
 * opt-in tier ([com.vervan.chat.data.settings.SettingsRepository.kokoroQualityEnabled]), never
 * selected by [TtsEngineSelector]'s `AUTO` ordering since it can run 2-3 minutes of compute
 * per minute of audio on budget/mid-range devices — that would break the realtime feel.
 *
 * same API-surface caveat as [PiperTtsEngine] — confirm
 * `OfflineTtsKokoroModelConfig`'s exact required fields against the real AAR at first sync.
 */
class KokoroTtsEngine(private val voiceModelDao: TtsVoiceModelDao) : TtsEngine {
    override val engineName = "Kokoro (higher quality)"

    private var tts: com.k2fsa.sherpa.onnx.OfflineTts? = null
    private var attemptedLoad = false

    override suspend fun isReady(): Boolean {
        ensureLoaded()
        return tts != null
    }

    private suspend fun ensureLoaded() {
        if (attemptedLoad) return
        attemptedLoad = true
        val row = voiceModelDao.getByEngine("KOKORO", "multi") ?: return
        tts = runCatching { loadVoice(row.filePath) }.getOrNull()
    }

    private fun loadVoice(voiceDir: String): com.k2fsa.sherpa.onnx.OfflineTts {
        val kokoro = com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig(
            model = "$voiceDir/model.onnx",
            voices = "$voiceDir/voices.bin",
            tokens = "$voiceDir/tokens.txt",
            dataDir = "$voiceDir/espeak-ng-data",
            lengthScale = 1.0f
        )
        val config = com.k2fsa.sherpa.onnx.OfflineTtsConfig(
            model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(kokoro = kokoro, numThreads = 2, provider = "cpu"),
            maxNumSentences = 1
        )
        // See PiperTtsEngine.loadVoice for why this is positional + a null AssetManager, and why
        // a self-test synthesis call follows rather than trusting construction alone.
        val tts = com.k2fsa.sherpa.onnx.OfflineTts(null, config)
        val selfTest = tts.generate("test", 0, 1.0f)
        if (selfTest.samples.isEmpty()) {
            tts.release()
            throw IllegalStateException("Voice loaded but self-test synthesis produced no audio")
        }
        return tts
    }

    override suspend fun synthesize(text: String, lang: String): TtsAudio {
        ensureLoaded()
        val engine = tts ?: throw IllegalStateException("Kokoro model not available")
        // Kokoro's multi-lang voice bank picks language from the speaker id; language routing
        // beyond the default English/multilingual voice isn't wired up for v1 — the quality
        // tier is opt-in and secondary, not worth the same per-sentence routing as Piper yet.
        val audio = engine.generate(text, 0, 1.0f)
        val samples = ShortArray(audio.samples.size) { i ->
            (audio.samples[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return TtsAudio(samples, audio.sampleRate)
    }

    fun release() {
        tts?.release()
        tts = null
        attemptedLoad = false
    }
}
