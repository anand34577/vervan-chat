package com.vervan.chat.voice

import com.vervan.chat.data.db.dao.TtsVoiceModelDao
import java.io.File

/**
 * Wraps sherpa-onnx offline TTS running Piper `hi_IN` and `en_IN` VITS voices — the fallback
 * tier when Supertonic isn't available (unsupported device, download failure). Unlike
 * Supertonic, each Piper voice is single-language, so this implements the spec's script-based
 * routing per sentence: Devanagari Unicode range (`0x0900-0x097F`) present -> Hindi voice,
 * else -> English voice. Code-mixed sentences route by whichever script dominates — a v1
 * heuristic, not per-word switching (revisit only if real usage shows it's a problem).
 *
 * sherpa-onnx's real `OfflineTts`/`OfflineTtsVitsModelConfig` Kotlin API is isolated
 * to this file — confirm exact field names/required files (Piper VITS voices typically need
 * `model.onnx` + `tokens.txt` + an `espeak-ng-data` directory for phonemization) against the
 * actual AAR at first Gradle sync. [TtsVoiceModel.filePath] is treated as a per-voice
 * directory (not a single file) so [TtsModelDownloadManager] can lay out all of a voice's
 * companion files together under one path.
 */
class PiperTtsEngine(private val voiceModelDao: TtsVoiceModelDao) : TtsEngine {
    override val engineName = "Piper"

    private var hindiTts: com.k2fsa.sherpa.onnx.OfflineTts? = null
    private var englishTts: com.k2fsa.sherpa.onnx.OfflineTts? = null
    private var attemptedLoad = false

    override suspend fun isReady(): Boolean {
        ensureLoaded()
        return hindiTts != null || englishTts != null
    }

    private suspend fun ensureLoaded() {
        if (attemptedLoad) return
        attemptedLoad = true
        voiceModelDao.getByEngine("PIPER", "hi")?.let { row ->
            hindiTts = runCatching { loadVoice(row.filePath) }.getOrNull()
        }
        voiceModelDao.getByEngine("PIPER", "en")?.let { row ->
            englishTts = runCatching { loadVoice(row.filePath) }.getOrNull()
        }
    }

    private fun loadVoice(voiceDir: String): com.k2fsa.sherpa.onnx.OfflineTts {
        // MMS-TTS voices (the current Hindi/English default catalog — see
        // com.vervan.chat.modeldownload.ModelCatalog) ship with no espeak-ng-data at all; only a
        // "real" Piper voice needs it for phonemization. Checking for the directory's actual
        // presence (rather than a hardcoded catalog flag) works for either voice shape through
        // the same code path.
        val espeakDataDir = File(voiceDir, "espeak-ng-data")
        val vits = com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig(
            model = "$voiceDir/model.onnx",
            tokens = "$voiceDir/tokens.txt",
            dataDir = if (espeakDataDir.isDirectory) espeakDataDir.path else "",
            lengthScale = 1.0f
        )
        val config = com.k2fsa.sherpa.onnx.OfflineTtsConfig(
            model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(vits = vits, numThreads = 2, provider = "cpu"),
            maxNumSentences = 1
        )
        // Positional, not named args: config.model/tokens/dataDir above are absolute
        // filesystem paths (downloaded by TtsModelDownloadManager, not APK assets), so the
        // AssetManager argument is null — OfflineTts branches on that internally
        // (newFromAsset vs. newFromFile). Real Kotlin parameter names for this constructor
        // weren't visible via javap on the compiled AAR, hence positional over named.
        val tts = com.k2fsa.sherpa.onnx.OfflineTts(null, config)
        // Constructing successfully doesn't guarantee the engine can actually synthesize (a bad
        // provider/NNAPI combo can "load" fine and fail at generate time) — run one real
        // synthesis call before trusting a candidate rather than only trusting the constructor.
        val selfTest = tts.generate("test", 0, 1.0f)
        if (selfTest.samples.isEmpty()) {
            tts.release()
            throw IllegalStateException("Voice loaded but self-test synthesis produced no audio")
        }
        return tts
    }

    override suspend fun synthesize(text: String, lang: String): TtsAudio {
        ensureLoaded()
        val useHindi = when (lang) {
            "hi" -> true
            "en" -> false
            else -> isDevanagariDominant(text)
        }
        val engine = (if (useHindi) hindiTts else englishTts) ?: englishTts ?: hindiTts
            ?: throw IllegalStateException("No Piper voice available")
        val audio = engine.generate(text, 0, 1.0f)
        val samples = ShortArray(audio.samples.size) { i ->
            (audio.samples[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return TtsAudio(samples, audio.sampleRate)
    }

    private fun isDevanagariDominant(text: String): Boolean {
        var devanagari = 0
        var latin = 0
        for (c in text) {
            when {
                c.code in 0x0900..0x097F -> devanagari++
                c.isLetter() -> latin++
            }
        }
        return devanagari > latin
    }

    fun release() {
        hindiTts?.release()
        englishTts?.release()
        hindiTts = null
        englishTts = null
        attemptedLoad = false
    }
}
