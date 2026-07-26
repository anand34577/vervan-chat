package com.vervan.chat.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.vervan.chat.data.db.dao.TtsVoiceModelDao
import com.vervan.chat.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.text.Normalizer
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Wraps Supertonic 3 (github.com/supertone-inc/supertonic) — a 31-language flow-matching TTS
 * run through plain `onnxruntime-android` (there's no official Android SDK; this ports the
 * single-utterance path of the project's own `java/Helper.java` reference example, which is
 * plain `ai.onnxruntime.*` code with no Android-specific dependency). An opt-in third engine
 * tier, only ever selected by an explicit "SUPERTONIC" pin in [TtsEngineSelector] — its 4-graph,
 * 8-step denoising pipeline (~400 MB of weights; `vector_estimator.onnx` alone is 256 MB, run
 * once per step) is heavier than [KokoroTtsEngine]'s single forward pass.
 *
 * [TtsPlaybackQueue] already splits AI replies into sentences via `SentenceChunker` before
 * calling [synthesize], so only the single-utterance inference path is needed here — not the
 * upstream example's outer multi-chunk/silence-stitching wrapper.
 *
 * ## Multiple voices, one shared acoustic model
 * Supertone publishes 10 voice styles (M1-M5, F1-F5) that are each just a ~290 KB style-tensor
 * file layered on the *same* ~400 MB acoustic model (the 4 ONNX graphs + config/indexer) — see
 * [ModelCatalog][com.vervan.chat.modeldownload.ModelCatalog]'s `supertonicVoice` entries. Rather
 * than have every voice re-download and store its own full copy of that shared model (10x the
 * storage for 10 voices), the "multi" catalog package alone carries the graphs, and every other
 * voice package carries only its style file — so [ensureLoaded] loads the graphs from the
 * "multi" row once ([attemptedLoad] latches that, same as before), while [ensureVoiceLoaded]
 * reloads just the ~290 KB style tensors whenever [SettingsRepository.supertonicVoiceVariant]
 * names a different installed voice than the one currently loaded — cheap enough to check on
 * every [synthesize] call.
 */
class SupertonicTtsEngine(
    private val voiceModelDao: TtsVoiceModelDao,
    private val settingsRepository: SettingsRepository
) : TtsEngine {
    override val engineName = "Supertonic"

    private var env: OrtEnvironment? = null
    private var dpSession: OrtSession? = null
    private var textEncSession: OrtSession? = null
    private var vectorEstSession: OrtSession? = null
    private var vocoderSession: OrtSession? = null
    private var indexer: LongArray? = null
    private var styleTtl: OnnxTensor? = null
    private var styleDp: OnnxTensor? = null
    private var config: Config? = null
    private var attemptedLoad = false
    /** Which voice's tensors are currently in [styleTtl]/[styleDp], or null before the first
     * successful voice load. */
    private var loadedVariant: String? = null

    private data class Config(val sampleRate: Int, val baseChunkSize: Int, val chunkCompressFactor: Int, val latentDim: Int)

    override suspend fun isReady(): Boolean {
        ensureLoaded()
        if (vocoderSession == null) return false
        ensureVoiceLoaded()
        return styleTtl != null
    }

    /** Loads the shared 4-graph acoustic model from the "multi" catalog package. Every voice —
     * including "multi"'s own M1 — needs this; it never depends on which voice is selected. */
    private suspend fun ensureLoaded() {
        if (attemptedLoad) return
        attemptedLoad = true
        val row = voiceModelDao.getByEngine("SUPERTONIC", "multi") ?: return
        runCatching { loadGraphs(row.filePath) }.onFailure { release() }
    }

    /** Loads (or reloads) just the voice-style tensors for whichever voice
     * [SettingsRepository.supertonicVoiceVariant] currently names — a no-op once that voice is
     * already loaded. MUST run after [ensureLoaded] has confirmed the graphs are present, since
     * "multi" doubles as both the graphs package and the M1 style file. */
    private suspend fun ensureVoiceLoaded() {
        val ortEnv = env ?: return
        val variant = settingsRepository.supertonicVoiceVariant.first()
        if (variant == loadedVariant) return
        val voiceDir = if (variant == "multi") {
            voiceModelDao.getByEngine("SUPERTONIC", "multi")
        } else {
            voiceModelDao.getByEngine("SUPERTONIC", variant)
        }?.filePath ?: return
        runCatching {
            val (ttl, dp) = loadVoiceStyle(File(voiceDir, "voice_style_default.json"), ortEnv)
            styleTtl?.close()
            styleDp?.close()
            styleTtl = ttl
            styleDp = dp
            loadedVariant = variant
        }
    }

    private fun loadGraphs(dir: String) {
        val graphsDir = File(dir)
        val ortEnv = OrtEnvironment.getEnvironment()
        env = ortEnv

        // Assign each resource to its field as soon as it's created (rather than all at once
        // at the end) so a failure partway through still leaves release() able to close
        // whatever was already opened, instead of leaking native OrtSession/OnnxTensor handles.
        val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
        dpSession = ortEnv.createSession(File(graphsDir, "duration_predictor.onnx").absolutePath, opts)
        textEncSession = ortEnv.createSession(File(graphsDir, "text_encoder.onnx").absolutePath, opts)
        vectorEstSession = ortEnv.createSession(File(graphsDir, "vector_estimator.onnx").absolutePath, opts)
        vocoderSession = ortEnv.createSession(File(graphsDir, "vocoder.onnx").absolutePath, opts)

        config = loadConfig(File(graphsDir, "tts.json"))
        indexer = loadIndexer(File(graphsDir, "unicode_indexer.json"))
        val (ttl, dpStyle) = loadVoiceStyle(File(graphsDir, "voice_style_default.json"), ortEnv)
        styleTtl = ttl
        styleDp = dpStyle
        loadedVariant = "multi"

        // Self-test synthesis before trusting the load — catches both a broken/incomplete
        // download and a native ONNX Runtime version mismatch (see the build.gradle.kts comment
        // on the vendored sherpa-onnx AAR repack) immediately rather than on first real use.
        val selfTest = runInference("Hello.", "en")
        if (selfTest.samples.isEmpty()) {
            throw IllegalStateException("Supertonic loaded but self-test synthesis produced no audio")
        }
    }

    override suspend fun synthesize(text: String, lang: String): TtsAudio {
        ensureLoaded()
        if (vocoderSession == null) throw IllegalStateException("Supertonic model not available")
        ensureVoiceLoaded()
        if (styleTtl == null) throw IllegalStateException("Supertonic voice not available")
        val supertonicLang = if (SUPPORTED_LANGS.contains(lang)) lang else "na"
        return runInference(text, supertonicLang)
    }

    private fun runInference(text: String, lang: String): TtsAudio {
        val ortEnv = env!!
        val cfg = config!!
        val idx = indexer!!
        val dp = dpSession!!
        val textEnc = textEncSession!!
        val vectorEst = vectorEstSession!!
        val vocoder = vocoderSession!!

        val textIds = tokenize(text, lang, idx)
        val textLen = textIds.size

        val textIdsTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(textIds), longArrayOf(1, textLen.toLong()))
        val textMaskTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(FloatArray(textLen) { 1f }), longArrayOf(1, 1, textLen.toLong()))

        var duration: Float
        val dpResult = dp.run(mapOf("text_ids" to textIdsTensor, "style_dp" to styleDp!!, "text_mask" to textMaskTensor))
        dpResult.use {
            val raw = it.get(0).value
            duration = when (raw) {
                is Array<*> -> (raw[0] as FloatArray)[0]
                is FloatArray -> raw[0]
                else -> throw IllegalStateException("Unexpected duration_predictor output type: ${raw?.javaClass}")
            }
        }

        val textEmbTensor: OnnxTensor
        val textEncResult = textEnc.run(mapOf("text_ids" to textIdsTensor, "style_ttl" to styleTtl!!, "text_mask" to textMaskTensor))
        textEmbTensor = textEncResult.get(0) as OnnxTensor

        val sampleRate = cfg.sampleRate
        val chunkSize = cfg.baseChunkSize * cfg.chunkCompressFactor
        val wavLen = (duration * sampleRate).toLong()
        val latentLen = ((wavLen + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
        val latentDim = cfg.latentDim * cfg.chunkCompressFactor

        var xt = sampleNoisyLatent(latentDim, latentLen)
        val latentMaskTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(FloatArray(latentLen) { 1f }), longArrayOf(1, 1, latentLen.toLong()))
        val totalStep = TOTAL_STEP

        for (step in 0 until totalStep) {
            val currentStepTensor = OnnxTensor.createTensor(ortEnv, floatArrayOf(step.toFloat()))
            val totalStepTensor = OnnxTensor.createTensor(ortEnv, floatArrayOf(totalStep.toFloat()))
            val noisyLatentTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(xt), longArrayOf(1, latentDim.toLong(), latentLen.toLong()))

            val stepResult = vectorEst.run(
                mapOf(
                    "noisy_latent" to noisyLatentTensor,
                    "text_emb" to textEmbTensor,
                    "style_ttl" to styleTtl!!,
                    "latent_mask" to latentMaskTensor,
                    "text_mask" to textMaskTensor,
                    "current_step" to currentStepTensor,
                    "total_step" to totalStepTensor
                )
            )
            stepResult.use {
                xt = flatten3D(it.get(0).value)
            }
            currentStepTensor.close()
            totalStepTensor.close()
            noisyLatentTensor.close()
        }

        val finalLatentTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(xt), longArrayOf(1, latentDim.toLong(), latentLen.toLong()))
        val wav: FloatArray
        val vocoderResult = vocoder.run(mapOf("latent" to finalLatentTensor))
        vocoderResult.use {
            @Suppress("UNCHECKED_CAST")
            val wavBatch = it.get(0).value as Array<FloatArray>
            wav = wavBatch[0]
        }

        textIdsTensor.close()
        textMaskTensor.close()
        textEmbTensor.close()
        latentMaskTensor.close()
        finalLatentTensor.close()

        val actualLen = min((sampleRate * duration).toInt(), wav.size).coerceAtLeast(0)
        val samples = ShortArray(actualLen) { i ->
            (wav[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return TtsAudio(samples, sampleRate)
    }

    private fun sampleNoisyLatent(latentDim: Int, latentLen: Int): FloatArray {
        val out = FloatArray(latentDim * latentLen)
        for (i in out.indices) {
            val u1 = max(1e-10, Random.nextDouble())
            val u2 = Random.nextDouble()
            out[i] = (sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)).toFloat()
        }
        return out
    }

    /** Flattens a 3D ONNX tensor output (`Array<Array<FloatArray>>`, shape [1, dim, len]) back
     * into the flat [FloatArray] the next denoising step's input tensor is built from. */
    private fun flatten3D(value: Any?): FloatArray {
        @Suppress("UNCHECKED_CAST")
        val arr = value as Array<Array<FloatArray>>
        val dim = arr[0].size
        val len = arr[0][0].size
        val out = FloatArray(dim * len)
        var i = 0
        for (d in 0 until dim) {
            for (t in 0 until len) {
                out[i++] = arr[0][d][t]
            }
        }
        return out
    }

    private fun tokenize(text: String, lang: String, idx: LongArray): LongArray {
        val processed = preprocessText(text, lang)
        val codePoints = processed.codePoints().toArray()
        return LongArray(codePoints.size) { i ->
            val cp = codePoints[i]
            if (cp in idx.indices) idx[cp] else -1L
        }
    }

    private fun preprocessText(text: String, lang: String): String {
        var t = Normalizer.normalize(text, Normalizer.Form.NFKD)
        t = removeEmojis(t)

        val replacements = mapOf(
            "–" to "-", "‑" to "-", "—" to "-", "_" to " ",
            "“" to "\"", "”" to "\"", "‘" to "'", "’" to "'",
            "´" to "'", "`" to "'", "[" to " ", "]" to " ", "|" to " ",
            "/" to " ", "#" to " ", "→" to " ", "←" to " "
        )
        for ((from, to) in replacements) t = t.replace(from, to)

        t = t.replace(Regex("[♥☆♡©\\\\]"), "")

        val exprReplacements = mapOf("@" to " at ", "e.g.," to "for example, ", "i.e.," to "that is, ")
        for ((from, to) in exprReplacements) t = t.replace(from, to)

        t = t.replace(" ,", ",").replace(" .", ".").replace(" !", "!")
            .replace(" ?", "?").replace(" ;", ";").replace(" :", ":").replace(" '", "'")

        while (t.contains("\"\"")) t = t.replace("\"\"", "\"")
        while (t.contains("''")) t = t.replace("''", "'")
        while (t.contains("``")) t = t.replace("``", "`")

        t = t.replace(Regex("\\s+"), " ").trim()

        if (!Regex(".*[.!?;:,'\"“”‘’)\\]}…。」』】〉》›»]$").matches(t)) {
            t += "."
        }

        return "<$lang>$t</$lang>"
    }

    private fun removeEmojis(text: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp: Int
            if (Character.isHighSurrogate(text[i]) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1])) {
                cp = Character.codePointAt(text, i)
                i++
            } else {
                cp = text[i].code
            }
            val isEmoji = (cp in 0x1F600..0x1F64F) || (cp in 0x1F300..0x1F5FF) || (cp in 0x1F680..0x1F6FF) ||
                (cp in 0x1F700..0x1F77F) || (cp in 0x1F780..0x1F7FF) || (cp in 0x1F800..0x1F8FF) ||
                (cp in 0x1F900..0x1F9FF) || (cp in 0x1FA00..0x1FA6F) || (cp in 0x1FA70..0x1FAFF) ||
                (cp in 0x2600..0x26FF) || (cp in 0x2700..0x27BF) || (cp in 0x1F1E6..0x1F1FF)
            if (!isEmoji) result.appendCodePoint(cp)
            i++
        }
        return result.toString()
    }

    private fun loadConfig(file: File): Config {
        val root = JSONObject(file.readText())
        val ae = root.getJSONObject("ae")
        val ttl = root.getJSONObject("ttl")
        return Config(
            sampleRate = ae.getInt("sample_rate"),
            baseChunkSize = ae.getInt("base_chunk_size"),
            chunkCompressFactor = ttl.getInt("chunk_compress_factor"),
            latentDim = ttl.getInt("latent_dim")
        )
    }

    private fun loadIndexer(file: File): LongArray {
        val arr = JSONArray(file.readText())
        return LongArray(arr.length()) { i -> arr.getLong(i) }
    }

    private fun loadVoiceStyle(file: File, ortEnv: OrtEnvironment): Pair<OnnxTensor, OnnxTensor> {
        val root = JSONObject(file.readText())
        val ttl = parseStyleTensor(root.getJSONObject("style_ttl"), ortEnv)
        val dp = parseStyleTensor(root.getJSONObject("style_dp"), ortEnv)
        return ttl to dp
    }

    private fun parseStyleTensor(node: JSONObject, ortEnv: OrtEnvironment): OnnxTensor {
        val dims = node.getJSONArray("dims")
        val shape = LongArray(dims.length()) { dims.getLong(it) }
        val data = node.getJSONArray("data")
        val flat = ArrayList<Float>()
        for (i in 0 until data.length()) {
            val batch = data.getJSONArray(i)
            for (j in 0 until batch.length()) {
                val row = batch.getJSONArray(j)
                for (k in 0 until row.length()) flat.add(row.getDouble(k).toFloat())
            }
        }
        return OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(flat.toFloatArray()), shape)
    }

    fun release() {
        runCatching { dpSession?.close() }
        runCatching { textEncSession?.close() }
        runCatching { vectorEstSession?.close() }
        runCatching { vocoderSession?.close() }
        runCatching { styleTtl?.close() }
        runCatching { styleDp?.close() }
        dpSession = null
        textEncSession = null
        vectorEstSession = null
        vocoderSession = null
        styleTtl = null
        styleDp = null
        indexer = null
        config = null
        env = null
        attemptedLoad = false
        loadedVariant = null
    }

    companion object {
        private const val TOTAL_STEP = 8
        private val SUPPORTED_LANGS = setOf(
            "en", "ko", "ja", "ar", "bg", "cs", "da", "de", "el", "es", "et", "fi", "fr", "hi",
            "hr", "hu", "id", "it", "lt", "lv", "nl", "pl", "pt", "ro", "ru", "sk", "sl", "sv",
            "tr", "uk", "vi", "na"
        )
    }
}
