package com.vervan.chat.voice

import android.content.Context
import android.util.Log
import com.vervan.chat.BuildConfig
import com.vervan.chat.data.db.dao.TtsVoiceModelDao
import com.vervan.chat.data.settings.SettingsRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Offline speech-to-text with Udgam Labs' Shruti models (huggingface.co/udgamlabs) on Cactus's
 * needle engine. Each Shruti model is ~30 MB and covers one Indian language plus English (code
 * mixing such as Hinglish included); the user downloads the languages they want from the model
 * store and picks the active one in Voice settings ([SettingsRepository.shrutiModelVariant]).
 *
 * Same lifecycle contract as [WhisperCppSttEngine]: every native call is serialized through
 * [mutex], [release] is cooperative (never frees under a running decode), a failed or absent load
 * never latches, and [transcribe] returns null on any failure so the pipeline falls through to its
 * next tier. The needle runtime keeps a single process-global speech model, so all instances of
 * this class share it; the bridge serializes access natively as well.
 */
class ShrutiSttEngine(
    private val context: Context,
    private val voiceModelDao: TtsVoiceModelDao,
    private val settingsRepository: SettingsRepository
) : SttEngine {

    override val label: String
        get() = "Shruti (${loadedVariant?.let { LANGUAGES[it] } ?: "offline"})"

    private val mutex = Mutex()
    @Volatile private var loadedVariant: String? = null
    @Volatile private var releaseRequested = false

    override suspend fun isReady(): Boolean = mutex.withLock {
        ensureLoadedLocked()
        val ready = loadedVariant != null
        val releasing = releaseRequested
        applyPendingReleaseLocked()
        ready && !releasing
    }

    override suspend fun transcribe(pcm: ShortArray, sampleRateHz: Int): String? {
        val audio = if (sampleRateHz == SAMPLE_RATE) pcm else resampleTo16k(pcm, sampleRateHz)
        return runDecode(audio) { samples, language, keywords ->
            val json = ShrutiJni.utf8(ShrutiJni.nativeTranscribe(samples, samples.size, language, keywords, false))
                ?: return@runDecode null
            JSONObject(json).optString("text").trim().takeIf { it.isNotBlank() }
        }
    }

    /** Per-sentence segments with timestamps, for the Transcription screen. Words come back with
     *  start/end times; they are grouped into segments at sentence ends and pauses. */
    suspend fun transcribeWithTimestamps(pcm: ShortArray): List<WhisperCppSttEngine.TranscriptSegment>? =
        runDecode(pcm) { samples, language, keywords ->
            val json = ShrutiJni.utf8(ShrutiJni.nativeTranscribe(samples, samples.size, language, keywords, true))
                ?: return@runDecode null
            segmentsFrom(JSONObject(json))
        }

    /** Live transcription for dictation: feed roughly one second of 16 kHz PCM per call. Returns
     *  (committed text this call, unconfirmed tail), or null when not ready. */
    suspend fun streamProcess(pcm: ShortArray): Pair<String, String>? =
        runDecode(pcm) { samples, language, keywords ->
            val json = ShrutiJni.utf8(ShrutiJni.nativeStreamProcess(samples, samples.size, language, keywords))
                ?: return@runDecode null
            val o = JSONObject(json)
            o.optString("text") to o.optString("pending")
        }

    /** Ends a live stream, returning the final committed tail. */
    suspend fun streamStop(): String? = mutex.withLock {
        if (loadedVariant == null) return@withLock null
        val out = runCatching {
            withContext(Dispatchers.Default) { ShrutiJni.utf8(ShrutiJni.nativeStreamStop()) }
        }.getOrNull()?.let { JSONObject(it).optString("text") }
        applyPendingReleaseLocked()
        out
    }

    override fun release() {
        releaseRequested = true
        if (mutex.tryLock()) {
            try { freeAndResetLocked() } finally { mutex.unlock() }
        }
    }

    private suspend fun <T> runDecode(pcm: ShortArray, block: (FloatArray, String?, String?) -> T?): T? {
        if (pcm.isEmpty()) return null
        return mutex.withLock {
            ensureLoadedLocked()
            val result = if (loadedVariant == null) null else try {
                val language = if (settingsRepository.shrutiEnglishOnly.first()) "en" else null
                val keywords = settingsRepository.shrutiKeywords.first().trim().takeIf { it.isNotEmpty() }
                withContext(Dispatchers.Default) {
                    block(FloatArray(pcm.size) { pcm[it] / 32768f }, language, keywords)
                }
            } catch (c: CancellationException) {
                applyPendingReleaseLocked()
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "Shruti decode failed: ${runCatching { ShrutiJni.nativeGetLastError() }.getOrNull()}", t)
                null
            }
            applyPendingReleaseLocked()
            result
        }
    }

    private suspend fun ensureLoadedLocked() {
        if (releaseRequested || !BuildConfig.NEEDLE_AVAILABLE) return
        val variant = settingsRepository.shrutiModelVariant.first()
        if (loadedVariant == variant) return
        val row = voiceModelDao.getByEngine(ENGINE, variant)
        val file = findInstalledModelFile(context, row?.filePath, variant) ?: return
        val ok = withContext(Dispatchers.Default) {
            try {
                ShrutiJni.load()
                ShrutiJni.nativeLoad(file.absolutePath).also {
                    if (!it) Log.w(TAG, "Shruti model load failed: ${ShrutiJni.nativeGetLastError()}")
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "needle engine unavailable", t)
                false
            }
        }
        loadedVariant = if (ok) variant else null
    }

    private fun applyPendingReleaseLocked() {
        if (releaseRequested) freeAndResetLocked()
    }

    private fun freeAndResetLocked() {
        if (loadedVariant != null) runCatching { ShrutiJni.nativeForget() }
        loadedVariant = null
        releaseRequested = false
    }

    companion object {
        private const val TAG = "ShrutiSttEngine"
        const val ENGINE = "SHRUTI"
        const val SAMPLE_RATE = 16_000

        /** Linear resample to 16 kHz; the voice pipeline already captures at 16 kHz, so this only
         *  runs for an odd caller. */
        internal fun resampleTo16k(pcm: ShortArray, fromHz: Int): ShortArray {
            if (fromHz <= 0 || pcm.isEmpty()) return pcm
            val n = (pcm.size.toLong() * SAMPLE_RATE / fromHz).toInt().coerceAtLeast(1)
            val step = fromHz.toDouble() / SAMPLE_RATE
            return ShortArray(n) { i ->
                val x = i * step
                val i0 = x.toInt().coerceAtMost(pcm.size - 1)
                val i1 = (i0 + 1).coerceAtMost(pcm.size - 1)
                val f = x - i0
                (pcm[i0] * (1 - f) + pcm[i1] * f).toInt().toShort()
            }
        }

        /** Catalog language key -> display name. Each model also understands English. */
        val LANGUAGES = linkedMapOf(
            "hi" to "Hindi",
            "mr" to "Marathi",
            "ta" to "Tamil",
            "bn" to "Bengali",
            "te" to "Telugu",
            "gu" to "Gujarati",
        )

        fun findInstalledModelFile(context: Context, recordedPath: String? = null, variant: String): File? {
            val canonicalDir = File(context.filesDir, "stt_models/${ENGINE.lowercase()}_$variant")
            return listOfNotNull(recordedPath?.let(::File), canonicalDir)
                .asSequence()
                .flatMap { path ->
                    when {
                        path.isFile -> sequenceOf(path)
                        path.isDirectory -> path.listFiles().orEmpty().asSequence()
                        else -> emptySequence()
                    }
                }
                .filter { it.isFile && it.length() > 0L && it.extension.equals("cact", ignoreCase = true) }
                .maxByOrNull { it.length() }
        }

        /** Groups needle's word timings into sentence-sized segments (ms offsets). Falls back to a
         *  single segment when the engine returned text but no words. */
        internal fun segmentsFrom(o: JSONObject): List<WhisperCppSttEngine.TranscriptSegment>? {
            val words = o.optJSONArray("words")
            val text = o.optString("text").trim()
            if (words == null || words.length() == 0) {
                return if (text.isBlank()) null else listOf(WhisperCppSttEngine.TranscriptSegment(0, 0, text))
            }
            val out = ArrayList<WhisperCppSttEngine.TranscriptSegment>()
            val sb = StringBuilder()
            var segStart = -1.0
            var lastEnd = 0.0
            fun flush() {
                val t = sb.toString().trim()
                if (t.isNotEmpty()) out.add(WhisperCppSttEngine.TranscriptSegment((segStart * 1000).toLong(), (lastEnd * 1000).toLong(), t))
                sb.setLength(0)
                segStart = -1.0
            }
            for (i in 0 until words.length()) {
                val w = words.optJSONObject(i) ?: continue
                val word = w.optString("word")
                val start = w.optDouble("start", lastEnd)
                val end = w.optDouble("end", start)
                if (segStart >= 0 && start - lastEnd > PAUSE_SPLIT_S) flush()
                if (segStart < 0) segStart = start
                if (sb.isNotEmpty() && !word.startsWith(" ")) sb.append(' ')
                sb.append(word)
                lastEnd = end
                val trimmed = word.trimEnd()
                if (trimmed.endsWith('.') || trimmed.endsWith('?') || trimmed.endsWith('!') || trimmed.endsWith('।') ||
                    end - segStart > MAX_SEGMENT_S
                ) flush()
            }
            flush()
            return out.takeIf { it.isNotEmpty() }
        }

        private const val PAUSE_SPLIT_S = 0.8
        private const val MAX_SEGMENT_S = 12.0
    }
}
