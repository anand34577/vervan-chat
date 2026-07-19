package com.vervan.chat.voice

import com.vervan.chat.data.db.dao.TtsVoiceModelDao
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wraps sherpa-onnx offline ASR running multilingual Whisper Tiny (int8) — the "inbuilt"
 * downloadable speech-to-text tier in the realtime voice pipeline's 3-way STT policy (see
 * [RealtimeVoiceController]): used when the active generation model can't transcribe audio
 * itself, or as a fallback when it can but a transcription attempt comes back blank. Multilingual
 * Whisper (not the `.en`-only variant) covers Hindi + English in one model, so there's no
 * per-language routing to do here the way [PiperTtsEngine] needs for TTS.
 *
 * ## Native lifecycle safety
 * All native access — load, decode, and free — is serialized through [mutex], and [release] is
 * *cooperative*: it never frees the recognizer while a decode holds the lock. This is the whole
 * point of the rewrite. Previously [release] (invoked from [RealtimeVoiceController.stop] on the
 * UI thread) freed the native `OfflineRecognizer` pointer while [transcribe] could still be
 * decoding on the session's background thread — a use-after-free that crashes the entire process
 * with a SIGSEGV that no `try`/`runCatching` on the Kotlin side can catch. Serializing the two,
 * plus a [releaseRequested] hand-off so a release that arrives mid-decode is applied by the decode
 * itself on the way out, removes that crash.
 *
 * ## Load reliability
 * The successful-load latch ([loaded]) is set only when a recognizer was actually built, so a
 * Whisper model that finishes downloading *during* a voice session is picked up on the next
 * [isReady]/[transcribe] instead of being latched off forever. Load also no longer runs a native
 * "self-test decode on silence" — that decode was a second uncatchable-SIGSEGV surface at load
 * time; instead we validate the three model files exist and are non-empty (cheap, pure-JVM) and
 * let a genuinely bad model fail the first real [transcribe], which returns null and falls through
 * to the next STT tier rather than crashing.
 *
 * exact sherpa-onnx Kotlin field names below were confirmed against the actual
 * `sherpa-onnx-1.13.4-api.jar` resolved in this project's `app/libs/` (javap on
 * `OfflineWhisperModelConfig`/`OfflineModelConfig`/`OfflineRecognizerConfig`/`OfflineRecognizer`/
 * `OfflineStream`).
 */
class WhisperSttEngine(private val voiceModelDao: TtsVoiceModelDao) : SttEngine {

    override val label: String = "Whisper (offline)"

    /** Serializes every native call (load/decode/free) so no two ever overlap. */
    private val mutex = Mutex()
    private var recognizer: com.k2fsa.sherpa.onnx.OfflineRecognizer? = null
    /** True only once a recognizer was successfully built — a failed/absent load never latches. */
    private var loaded = false
    /** Set by [release]; the next mutex holder (a running decode, or release itself) frees. */
    @Volatile private var releaseRequested = false

    override suspend fun isReady(): Boolean = mutex.withLock {
        ensureLoadedLocked()
        val ready = recognizer != null
        applyPendingReleaseLocked()
        ready && !releaseRequested
    }

    /** Transcribes one already-captured, VAD-endpointed utterance ([RealtimeVoiceController.captureUntilSilence]'s
     * output). Returns null on any failure so the caller can fall through to the next STT tier. */
    override suspend fun transcribe(pcm: ShortArray, sampleRateHz: Int): String? = mutex.withLock {
        ensureLoadedLocked()
        val rec = recognizer
        val result = if (rec == null) {
            null
        } else {
            runCatching {
                withContext(Dispatchers.Default) { decode(rec, pcm, sampleRateHz) }
            }.getOrNull()
        }
        applyPendingReleaseLocked()
        result
    }

    override fun release() {
        releaseRequested = true
        // If no decode/load currently holds the lock, free immediately. Otherwise the in-flight
        // holder frees on its way out (applyPendingReleaseLocked), so we never race a native free
        // against a native decode.
        if (mutex.tryLock()) {
            try { freeAndResetLocked() } finally { mutex.unlock() }
        }
    }

    /** MUST be called with [mutex] held. Lazily builds the recognizer once the model is on disk;
     * a no-op if already loaded, if a release is pending, or if the model isn't downloaded yet
     * (in which case it stays unlatched so a later call retries). */
    private suspend fun ensureLoadedLocked() {
        if (loaded || releaseRequested) return
        val row = voiceModelDao.getByEngine(ENGINE, LANGUAGE) ?: return
        val dir = row.filePath
        if (!modelFilesPresent(dir)) return
        val rec = withContext(Dispatchers.Default) { runCatching { loadRecognizer(dir) }.getOrNull() }
        if (rec != null) {
            recognizer = rec
            loaded = true
        }
    }

    /** MUST be called with [mutex] held. */
    private fun applyPendingReleaseLocked() {
        if (releaseRequested) freeAndResetLocked()
    }

    /** MUST be called with [mutex] held (or after [Mutex.tryLock]). Frees native state and leaves
     * the engine reusable — a later [isReady]/[transcribe] reloads from scratch. */
    private fun freeAndResetLocked() {
        recognizer?.release()
        recognizer = null
        loaded = false
        releaseRequested = false
    }

    private fun modelFilesPresent(modelDir: String): Boolean =
        REQUIRED_FILES.all { File(modelDir, it).let { f -> f.isFile && f.length() > 0L } }

    private fun decode(rec: com.k2fsa.sherpa.onnx.OfflineRecognizer, pcm: ShortArray, sampleRateHz: Int): String? {
        val samples = FloatArray(pcm.size) { pcm[it] / 32768f }
        val stream = rec.createStream()
        return try {
            stream.acceptWaveform(samples, sampleRateHz)
            rec.decode(stream)
            rec.getResult(stream).text.trim().takeIf { it.isNotBlank() }
        } finally {
            stream.release()
        }
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
        // Positional first arg (AssetManager) = null: these files are absolute filesystem paths
        // from the downloader, not APK assets, so sherpa uses newFromFile. Same reasoning as
        // PiperTtsEngine.loadVoice.
        return com.k2fsa.sherpa.onnx.OfflineRecognizer(null, config)
    }

    companion object {
        const val ENGINE = "WHISPER"
        const val LANGUAGE = "multi"
        private const val SAMPLE_RATE_HZ = 16000
        private val REQUIRED_FILES = listOf("model.onnx", "decoder.onnx", "tokens.txt")
    }
}
