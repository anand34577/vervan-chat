package com.vervan.chat.voice

import com.vervan.chat.data.db.dao.TtsVoiceModelDao

/**
 * Wraps sherpa-onnx offline ASR running multilingual Whisper Tiny (int8) — the "inbuilt"
 * downloadable speech-to-text tier in the realtime voice pipeline's 3-way STT policy (see
 * [com.vervan.chat.voice.RealtimeVoiceController]): used when the active generation model can't
 * transcribe audio itself, or as a fallback when it can but a transcription attempt comes back
 * blank. Multilingual Whisper (not the `.en`-only variant) covers Hindi + English in one model,
 * so there's no per-language routing to do here the way [PiperTtsEngine] needs for TTS.
 *
 * ponytail: exact sherpa-onnx Kotlin field names below were confirmed against the actual
 * `sherpa-onnx-1.13.4-api.jar` already resolved in this project's Gradle cache (javap on
 * `OfflineWhisperModelConfig`/`OfflineModelConfig`/`OfflineRecognizerConfig`/`OfflineRecognizer`/
 * `OfflineStream`), not guessed — unlike [PiperTtsEngine]'s TTS config classes, which predate
 * that confirmation step.
 */
class WhisperSttEngine(private val voiceModelDao: TtsVoiceModelDao) {

    private var recognizer: com.k2fsa.sherpa.onnx.OfflineRecognizer? = null
    private var attemptedLoad = false

    suspend fun isReady(): Boolean {
        ensureLoaded()
        return recognizer != null
    }

    private suspend fun ensureLoaded() {
        if (attemptedLoad) return
        attemptedLoad = true
        val row = voiceModelDao.getByEngine(ENGINE, LANGUAGE) ?: return
        recognizer = runCatching { loadRecognizer(row.filePath) }.getOrNull()
    }

    private fun loadRecognizer(modelDir: String): com.k2fsa.sherpa.onnx.OfflineRecognizer {
        val whisper = com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig(
            encoder = "$modelDir/model.onnx",
            decoder = "$modelDir/decoder.onnx",
            language = "",
            task = "transcribe",
            tailPaddings = -1,
            enableTokenTimestamps = false,
            enableSegmentTimestamps = false
        )
        val modelConfig = com.k2fsa.sherpa.onnx.OfflineModelConfig(
            whisper = whisper,
            tokens = "$modelDir/tokens.txt",
            numThreads = 2,
            provider = "cpu"
        )
        val config = com.k2fsa.sherpa.onnx.OfflineRecognizerConfig(
            featConfig = com.k2fsa.sherpa.onnx.FeatureConfig(sampleRate = SAMPLE_RATE_HZ, featureDim = 80, dither = 0f),
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )
        // Positional first arg (AssetManager), same reasoning as PiperTtsEngine.loadVoice: these
        // files are absolute filesystem paths from the downloader, not APK assets.
        val rec = com.k2fsa.sherpa.onnx.OfflineRecognizer(null, config)
        // Constructing successfully doesn't guarantee the model can actually decode (a bad
        // provider/quantization combo can "load" fine and fail at decode time) — run one real
        // decode on a short silence buffer before trusting this candidate, same rationale as
        // PiperTtsEngine's synthesis self-test.
        val stream = rec.createStream()
        try {
            stream.acceptWaveform(FloatArray(SAMPLE_RATE_HZ / 10), SAMPLE_RATE_HZ)
            rec.decode(stream)
            rec.getResult(stream)
        } finally {
            stream.release()
        }
        return rec
    }

    /** Transcribes one already-captured, VAD-endpointed utterance. [pcm] is 16-bit signed PCM at
     * [sampleRateHz] (the same buffer [RealtimeVoiceController.captureUntilSilence] produces).
     * Returns null on any failure (not ready, native decode error, empty output) so the caller
     * can fall through to the next STT tier. */
    suspend fun transcribe(pcm: ShortArray, sampleRateHz: Int): String? {
        ensureLoaded()
        val rec = recognizer ?: return null
        return runCatching {
            val samples = FloatArray(pcm.size) { pcm[it] / 32768f }
            val stream = rec.createStream()
            try {
                stream.acceptWaveform(samples, sampleRateHz)
                rec.decode(stream)
                rec.getResult(stream).text.trim().takeIf { it.isNotBlank() }
            } finally {
                stream.release()
            }
        }.getOrNull()
    }

    fun release() {
        recognizer?.release()
        recognizer = null
        attemptedLoad = false
    }

    companion object {
        const val ENGINE = "WHISPER"
        const val LANGUAGE = "multi"
        private const val SAMPLE_RATE_HZ = 16000
    }
}
