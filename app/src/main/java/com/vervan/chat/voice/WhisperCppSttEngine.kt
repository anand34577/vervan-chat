package com.vervan.chat.voice

import android.util.Log
import com.vervan.chat.BuildConfig
import com.vervan.chat.data.db.dao.TtsVoiceModelDao
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * whisper.cpp-backed offline speech-to-text — an alternative [SttEngine] implementation to
 * [WhisperSttEngine] (which wraps sherpa-onnx). Same role in the realtime voice pipeline's 3-tier
 * STT policy (see [RealtimeVoiceController]): used when the active generation model can't
 * transcribe audio itself, or as a fallback when it can but a transcription comes back blank.
 *
 * Coexists with [WhisperSttEngine] rather than replacing it — the user picks which one the
 * pipeline reaches for via the "Speech-to-text engine" setting (see
 * [com.vervan.chat.ui.settings.VoiceSettingsScreen]); AUTO prefers this one when available since
 * whisper.cpp generally decodes faster than the ONNX-runtime Whisper Tiny on the same hardware.
 * The two read distinct rows from [TtsVoiceModelDao] (`WHISPER_CPP` vs `WHISPER`), so either,
 * both, or neither can be downloaded at once.
 *
 * ## Build dependency
 * Every native call is gated on [BuildConfig.WHISPER_CPP_AVAILABLE], set at compile time from
 * whether a prebuilt `libwhisper.so` was present in `jniLibs/<abi>/` (see `app/build.gradle.kts`).
 * On a build without whisper.cpp, [isReady] simply returns false and [transcribe] returns null —
 * the pipeline falls through to the next STT tier exactly as if this model weren't downloaded.
 *
 * ## Native lifecycle safety
 * All native access — load, decode, and free — is serialized through [mutex], and [release] is
 * *cooperative*: it never frees the whisper context while a decode holds the lock. This is the
 * same use-after-free defense [WhisperSttEngine] uses (see its class doc): [release], invoked from
 * [RealtimeVoiceController.stop] on the UI thread, must never free native memory out from under a
 * running decode on the session's background thread — that SIGSEGV is uncatchable from Kotlin.
 * A [releaseRequested] hand-off lets a release arriving mid-decode be applied by the decode
 * itself on its way out of the mutex.
 *
 * ## Load reliability
 * The successful-load latch ([loaded]) is set only when a whisper context was actually built, so
 * a model that finishes downloading *during* a voice session is picked up on the next
 * [isReady]/[transcribe] instead of being latched off forever. We validate the .bin file exists
 * and is non-empty (cheap, pure-JVM) and let a genuinely bad model fail the first real
 * [transcribe], which returns null and falls through to the next STT tier rather than crashing —
 * matching [WhisperSttEngine]'s "no self-test decode at load time" decision.
 */
class WhisperCppSttEngine(private val voiceModelDao: TtsVoiceModelDao) : SttEngine {

    override val label: String = "whisper.cpp (offline)"

    /** Serializes every native call (load/decode/free) so no two ever overlap. */
    private val mutex = Mutex()
    private var handle: Long = 0L
    /** True only once a whisper context was successfully built — a failed/absent load never latches. */
    private var loaded = false
    /** Set by [release]; the next mutex holder (a running decode, or release itself) frees. */
    @Volatile private var releaseRequested = false

    override suspend fun isReady(): Boolean = mutex.withLock {
        ensureLoadedLocked()
        val ready = handle != 0L
        // Order matters: applyPendingReleaseLocked() clears releaseRequested, so `ready` must be
        // sampled before it. A release arriving mid-isReady() frees the context and reports false;
        // the engine stays reusable and a later transcribe() reloads it lazily.
        val releasing = releaseRequested
        applyPendingReleaseLocked()
        ready && !releasing
    }

    /** Transcribes one already-captured, VAD-endpointed utterance ([RealtimeVoiceController]'s
     *  output). Returns null on any failure so the caller can fall through to the next STT tier. */
    override suspend fun transcribe(pcm: ShortArray, sampleRateHz: Int): String? = mutex.withLock {
        ensureLoadedLocked()
        val h = handle
        val result = if (h == 0L) {
            null
        } else {
            try {
                withContext(Dispatchers.Default) { decode(h, pcm) }
            } catch (c: CancellationException) {
                // Cancellation is not a decode failure — the session is tearing down. Re-throw so
                // this coroutine actually stops, but only after applyPendingReleaseLocked() below
                // has run, so a release racing the cancel still frees the native context.
                applyPendingReleaseLocked()
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "whisper.cpp decode failed; falling through to next STT tier", t)
                null
            }
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

    /** MUST be called with [mutex] held. Lazily builds the whisper context once the model is on
     *  disk; a no-op if already loaded, if a release is pending, if the native lib wasn't built
     *  into this APK, or if the model isn't downloaded yet (in which case it stays unlatched so a
     *  later call retries). */
    private suspend fun ensureLoadedLocked() {
        if (loaded || releaseRequested) return
        if (!BuildConfig.WHISPER_CPP_AVAILABLE) return
        val row = voiceModelDao.getByEngine(ENGINE, MODEL_LANGUAGE_KEY) ?: return
        val modelFile = findModelFile(row.filePath) ?: return
        // System.loadLibrary must be inside the guard, not outside it: WHISPER_CPP_AVAILABLE is a
        // single app-wide boolean, but the native libs are packaged per-ABI (see whisperCppAbis in
        // app/build.gradle.kts). A build that ships libwhisper.so for arm64-v8a only sets the flag
        // true on a 32-bit device where libvervan_whisper_jni.so is absent, and the resulting
        // UnsatisfiedLinkError would otherwise escape isReady() and kill the whole voice session
        // instead of falling through to the next STT tier.
        val h = withContext(Dispatchers.Default) {
            try {
                WhisperCppJni.load()
                WhisperCppJni.nativeInit(modelFile.absolutePath, N_THREADS_AUTO)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "whisper.cpp model load failed (${modelFile.absolutePath})", t)
                0L
            }
        }
        if (h != 0L) {
            handle = h
            loaded = true
        }
    }

    /** MUST be called with [mutex] held. */
    private fun applyPendingReleaseLocked() {
        if (releaseRequested) freeAndResetLocked()
    }

    /** MUST be called with [mutex] held (or after [Mutex.tryLock]). Frees native state and leaves
     *  the engine reusable — a later [isReady]/[transcribe] reloads from scratch. */
    private fun freeAndResetLocked() {
        if (handle != 0L) {
            runCatching { WhisperCppJni.nativeFree(handle) }
            handle = 0L
        }
        loaded = false
        releaseRequested = false
    }

    /** whisper.cpp expects mono float PCM at 16 kHz normalized to [-1, 1] — the SttEngine contract
     *  already hands us 16 kHz mono PCM16 (see [RealtimeVoiceController.captureUntilSilence]), so
     *  this is a straight int16→float divide-by-32768, same conversion sherpa's path does. */
    private fun decode(h: Long, pcm: ShortArray): String? {
        val samples = FloatArray(pcm.size) { pcm[it] / 32768f }
        return WhisperCppJni.nativeTranscribe(h, samples, samples.size, DECODE_LANGUAGE, translate = false)?.trim()?.takeIf { it.isNotBlank() }
    }

    /** The downloader ([com.vervan.chat.modeldownload.ModelDownloadRepository.finalizeVoiceModel])
     *  places the catalog file into `stt_models/whisper_cpp_multi/` under its own `fileName`
     *  (e.g. `ggml-tiny.bin`). whisper.cpp model filenames aren't fixed — accept any `.bin` /
     *  `.gguf` file in the directory so a future catalog entry (tiny.en / base / small) needs no
     *  change here. The largest file wins when more than one is present, mirroring how a user
     *  would expect "the model" to resolve. */
    private fun findModelFile(modelDir: String): File? =
        File(modelDir).takeIf { it.isDirectory }
            ?.listFiles { f -> f.isFile && (f.extension.equals("bin", ignoreCase = true) || f.extension.equals("gguf", ignoreCase = true)) }
            ?.maxByOrNull { it.length() }
            ?.takeIf { it.length() > 0L }

    companion object {
        private const val TAG = "WhisperCppSttEngine"

        const val ENGINE = "WHISPER_CPP"

        /** Row key in [TtsVoiceModelDao], matching the catalog entry's `ttsLanguage` (see
         *  [com.vervan.chat.modeldownload.ModelCatalog]). This is a *catalog* label meaning "the
         *  multilingual model", NOT a language whisper.cpp understands — see [DECODE_LANGUAGE]. */
        const val MODEL_LANGUAGE_KEY = "multi"

        /** Language passed to whisper.cpp's decoder. Must be an ISO-639-1 code or "auto";
         *  whisper resolves it via `whisper_lang_id()` and an unknown value yields a bogus
         *  language token rather than an error, so it must never be [MODEL_LANGUAGE_KEY] ("multi"
         *  is not a Whisper language). Auto-detect is right for the multilingual tiny model, which
         *  is exactly what this engine ships — the pipeline has no per-utterance language hint. */
        private const val DECODE_LANGUAGE = "auto"

        /** 0 = let the bridge pick min(4, hardware_concurrency), matching whisper.cpp's own
         *  default (see nativeInit in whisper_bridge.cpp). */
        private const val N_THREADS_AUTO = 0
    }
}
