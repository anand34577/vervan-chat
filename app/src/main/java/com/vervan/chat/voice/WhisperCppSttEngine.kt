package com.vervan.chat.voice

import android.content.Context
import android.content.SharedPreferences
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

/**
 * whisper.cpp-backed offline speech-to-text — the app's only [SttEngine] implementation (Android's
 * system speech recognizer is deliberately never used anywhere in this app; a prior sherpa-onnx
 * `WhisperSttEngine` alternative was removed once whisper.cpp was working end to end). Same role
 * in the realtime voice pipeline's 2-tier STT policy (see [RealtimeVoiceController]): used when
 * the active generation model can't transcribe audio itself, or as a fallback when it can but a
 * transcription comes back blank.
 *
 * ## Build dependency
 * Every native call is gated on [BuildConfig.WHISPER_CPP_AVAILABLE], set at compile time from
 * whether a prebuilt `libwhisper.so` was present in `jniLibs/<abi>/` (see `app/build.gradle.kts`
 * and `scripts/build-whisper-android.ps1`, which builds it automatically when `whispercpp.dir` is
 * set in local.properties). On a build without whisper.cpp, [isReady] simply returns false and
 * [transcribe] returns null — the pipeline falls through to the active model's own audio input
 * exactly as if this model weren't downloaded.
 *
 * ## Native lifecycle safety
 * All native access — load, decode, and free — is serialized through [mutex], and [release] is
 * *cooperative*: it never frees the whisper context while a decode holds the lock. [release],
 * invoked from [RealtimeVoiceController.stop] on the UI thread, must never free native memory out
 * from under a running decode on the session's background thread — that SIGSEGV is uncatchable
 * from Kotlin. A [releaseRequested] hand-off lets a release arriving mid-decode be applied by the
 * decode itself on its way out of the mutex.
 *
 * ## Load reliability
 * The successful-load latch ([loaded]) is set only when a whisper context was actually built, so
 * a model that finishes downloading *during* a voice session is picked up on the next
 * [isReady]/[transcribe] instead of being latched off forever. We validate the .bin file exists
 * and is non-empty (cheap, pure-JVM) and let a genuinely bad model fail the first real
 * [transcribe], which returns null and falls through rather than crashing.
 *
 * ## GPU: opt-in, with a crash-loop breaker as a second line of defense
 * whisper.cpp's Vulkan backend (see `scripts/build-whisper-android.ps1`) has been observed to
 * crash the whole process with a native SIGSEGV during device/pipeline init on at least one real
 * Adreno device — not a graceful failure, a hard crash before any Kotlin catch block or
 * whisper.cpp's own "no GPU found -> use CPU" fallback ever runs. CPU-only whisper.cpp worked
 * reliably before this, so GPU defaults OFF ([SettingsRepository.whisperGpuEnabled]) — a user has
 * to explicitly opt in before this engine ever attempts Vulkan at all, which is the real fix for
 * "if GPU isn't working it should fall back to CPU": most installs never risk the crash in the
 * first place.
 *
 * For anyone who does opt in, a second-line defense still applies: a flag persisted to
 * [SharedPreferences] with a *synchronous* [SharedPreferences.Editor.commit] (not `apply()` — the
 * write must be durably on disk before the risky call, since a crash a few frames later gives an
 * async write no chance to flush) marks GPU-init "pending" right before calling
 * [WhisperCppJni.nativeInit] with GPU on, and clears it right after the call returns (proving the
 * process survived). If the app is later relaunched and finds "pending" still set, that only
 * happens because the process died mid-call last time — GPU is then marked permanently disabled
 * for this device/install (independent of the setting, which stays on) and every future load uses
 * the CPU backend, so opting in can cost at most one crash ever, not a crash every session.
 *
 * [activeBackendLabel] (persisted alongside the crash-loop state) reflects whichever backend the
 * most recent successful load actually used, for the "STT: whisper.cpp (CPU/GPU)" UI badge — see
 * [RealtimeVoiceController.sttLabel] and [VoiceSettingsScreen][com.vervan.chat.ui.settings.VoiceSettingsScreen].
 */
class WhisperCppSttEngine(
    private val context: Context,
    private val voiceModelDao: TtsVoiceModelDao,
    private val settingsRepository: SettingsRepository
) : SttEngine {

    override val label: String
        get() = "whisper.cpp (${if (usingGpu) "GPU" else "CPU"})"

    /** Serializes every native call (load/decode/free) so no two ever overlap. */
    private val mutex = Mutex()
    private var handle: Long = 0L
    /** True only once a whisper context was successfully built — a failed/absent load never latches. */
    private var loaded = false
    /** Which backend the current [handle] (if any) was actually built with. */
    @Volatile private var usingGpu = false
    /** Set by [release]; the next mutex holder (a running decode, or release itself) frees. */
    @Volatile private var releaseRequested = false
    /** Which model variant (see [SettingsRepository.whisperModelVariant]) [handle] was built
     * from — lets [ensureLoadedLocked] notice the user switched models and rebuild instead of
     * silently keeping the old one loaded. */
    private var loadedVariant: String? = null

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

    /** One decoded whisper.cpp segment — [startMs]/[endMs] are offsets into the audio that was
     *  transcribed, for the Transcription screen's timestamp-synced playback. */
    data class TranscriptSegment(val startMs: Long, val endMs: Long, val text: String)

    /** Same shape as [transcribe] but returns per-segment timestamps — used by the Transcription
     *  screen (a whole-file batch job, unlike the realtime pipeline's short VAD utterances, where
     *  timestamps have no UI to attach to). Not part of the [SttEngine] interface: nothing else
     *  in the app needs timestamps, so this stays a concrete-class extra rather than a contract
     *  every [SttEngine] implementation would have to satisfy. */
    suspend fun transcribeWithTimestamps(pcm: ShortArray): List<TranscriptSegment>? = mutex.withLock {
        ensureLoadedLocked()
        val h = handle
        val result = if (h == 0L) {
            null
        } else {
            try {
                withContext(Dispatchers.Default) { decodeSegments(h, pcm) }
            } catch (c: CancellationException) {
                applyPendingReleaseLocked()
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "whisper.cpp segment decode failed", t)
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
        if (releaseRequested) return
        if (!BuildConfig.WHISPER_CPP_AVAILABLE) return
        val variant = settingsRepository.whisperModelVariant.first()
        if (loaded && variant == loadedVariant) return
        if (loaded && variant != loadedVariant) {
            // The selected model changed since the last load — free the stale context so the
            // block below rebuilds from the newly selected model instead of silently keeping it.
            freeAndResetLocked()
        }
        val row = voiceModelDao.getByEngine(ENGINE, variant) ?: return
        val modelFile = findInstalledModelFile(context, row.filePath, variant) ?: return
        // System.loadLibrary must be inside the guard, not outside it: WHISPER_CPP_AVAILABLE is a
        // single app-wide boolean, but the native libs are packaged per-ABI (see whisperCppAbis in
        // app/build.gradle.kts). A build that ships libwhisper.so for arm64-v8a only sets the flag
        // true on a 32-bit device where libvervan_whisper_jni.so is absent, and the resulting
        // UnsatisfiedLinkError would otherwise escape isReady() and kill the whole voice session
        // instead of falling through to the next STT tier.
        val gpuRequested = settingsRepository.whisperGpuEnabled.first()
        val h = withContext(Dispatchers.Default) {
            val prefs = context.getSharedPreferences(GPU_PREFS_NAME, Context.MODE_PRIVATE)
            val useGpu = if (gpuRequested) resolveGpuAttempt(prefs) else false
            try {
                WhisperCppJni.load()
                val result = WhisperCppJni.nativeInit(modelFile.absolutePath, N_THREADS_AUTO, useGpu)
                // Reaching this line at all proves nativeInit returned instead of crashing the
                // process — clear the pending marker regardless of whether the load itself
                // succeeded (result == 0L is a normal load failure, not evidence GPU is unsafe).
                if (useGpu) prefs.edit().putBoolean(KEY_GPU_INIT_PENDING, false).commit()
                if (result != 0L) prefs.edit().putString(KEY_LAST_BACKEND, if (useGpu) "GPU" else "CPU").apply()
                usingGpu = useGpu
                result
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (useGpu) prefs.edit().putBoolean(KEY_GPU_INIT_PENDING, false).commit()
                Log.w(TAG, "whisper.cpp model load failed (${modelFile.absolutePath})", t)
                0L
            }
        }
        if (h != 0L) {
            handle = h
            loaded = true
            loadedVariant = variant
        }
    }

    /** MUST be called before the (possibly crashing) `nativeInit` call — see the class doc's "GPU:
     *  opt-in" section. Synchronous [SharedPreferences.Editor.commit] is deliberate throughout: an
     *  `apply()`'d write racing a native SIGSEGV a few frames later could simply never reach disk,
     *  defeating the whole point of this gate. Only reached at all when the user has opted into
     *  [SettingsRepository.whisperGpuEnabled] — most installs never call this. */
    private fun resolveGpuAttempt(prefs: SharedPreferences): Boolean {
        if (prefs.getBoolean(KEY_GPU_DISABLED, false)) return false
        if (prefs.getBoolean(KEY_GPU_INIT_PENDING, false)) {
            // The pending marker from a previous attempt was never cleared — the only way that
            // happens is the process dying inside that nativeInit call. Disable GPU for good.
            Log.w(TAG, "Previous whisper.cpp GPU init never returned (process crash) — disabling GPU permanently for this install.")
            prefs.edit().putBoolean(KEY_GPU_DISABLED, true).putBoolean(KEY_GPU_INIT_PENDING, false).commit()
            return false
        }
        prefs.edit().putBoolean(KEY_GPU_INIT_PENDING, true).commit()
        return true
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
        loadedVariant = null
        releaseRequested = false
    }

    /** whisper.cpp expects mono float PCM at 16 kHz normalized to [-1, 1] — the SttEngine contract
     *  already hands us 16 kHz mono PCM16 (see [RealtimeVoiceController.captureUntilSilence]), so
     *  this is a straight int16→float divide-by-32768, same conversion sherpa's path does. */
    private fun decode(h: Long, pcm: ShortArray): String? {
        val samples = FloatArray(pcm.size) { pcm[it] / 32768f }
        return WhisperCppJni.nativeTranscribe(h, samples, samples.size, DECODE_LANGUAGE, translate = false)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun decodeSegments(h: Long, pcm: ShortArray): List<TranscriptSegment>? {
        val samples = FloatArray(pcm.size) { pcm[it] / 32768f }
        val json = WhisperCppJni.nativeTranscribeSegments(h, samples, samples.size, DECODE_LANGUAGE, translate = false) ?: return null
        val array = org.json.JSONArray(json)
        val segments = (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val text = obj.optString("text").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            TranscriptSegment(obj.optLong("start"), obj.optLong("end"), text)
        }
        return segments.takeIf { it.isNotEmpty() }
    }

    /** The downloader ([com.vervan.chat.modeldownload.ModelDownloadRepository.finalizeVoiceModel])
     *  places the catalog file into `stt_models/whisper_cpp_multi/` under its own `fileName`
     *  (e.g. `ggml-tiny.bin`). whisper.cpp model filenames aren't fixed — accept any `.bin` /
     *  `.gguf` file in the directory so a future catalog entry (tiny.en / base / small) needs no
     *  change here. The largest file wins when more than one is present, mirroring how a user
     *  would expect "the model" to resolve. */
    companion object {
        private const val TAG = "WhisperCppSttEngine"

        private const val GPU_PREFS_NAME = "whisper_cpp_gpu_state"
        private const val KEY_GPU_INIT_PENDING = "gpu_init_pending"
        private const val KEY_GPU_DISABLED = "gpu_disabled"
        private const val KEY_LAST_BACKEND = "last_backend"

        const val ENGINE = "WHISPER_CPP"

        /** Default row key in [TtsVoiceModelDao] — the catalog entry's `ttsLanguage` for the
         *  original bundled Tiny model (see [com.vervan.chat.modeldownload.ModelCatalog]). This
         *  is a *catalog* label meaning "the multilingual Tiny model", NOT a language whisper.cpp
         *  understands — see [DECODE_LANGUAGE]. Larger models ("base"/"small") use their own
         *  variant key instead — see [SettingsRepository.whisperModelVariant]. */
        const val MODEL_LANGUAGE_KEY = "multi"

        /** Resolves both catalog downloads and local imports for the given model [variant]
         * (default the original Tiny model, for callers that only care whether *something* is
         * installed). The canonical directory fallback also repairs status after an
         * older/stale database row even though the copied model is present and loadable on disk. */
        fun findInstalledModelFile(context: Context, recordedPath: String? = null, variant: String = MODEL_LANGUAGE_KEY): File? {
            val canonicalDir = File(
                context.filesDir,
                "stt_models/${ENGINE.lowercase()}_$variant"
            )
            return listOfNotNull(recordedPath?.let(::File), canonicalDir)
                .asSequence()
                .flatMap { path ->
                    when {
                        path.isFile -> sequenceOf(path)
                        path.isDirectory -> path.listFiles().orEmpty().asSequence()
                        else -> emptySequence()
                    }
                }
                .filter {
                    it.isFile && it.length() > 0L &&
                        (it.extension.equals("bin", ignoreCase = true) ||
                            it.extension.equals("gguf", ignoreCase = true))
                }
                .maxByOrNull { it.length() }
        }

        /** Language passed to whisper.cpp's decoder. Must be an ISO-639-1 code or "auto";
         *  whisper resolves it via `whisper_lang_id()` and an unknown value yields a bogus
         *  language token rather than an error, so it must never be [MODEL_LANGUAGE_KEY] ("multi"
         *  is not a Whisper language). Auto-detect is right for the multilingual tiny model, which
         *  is exactly what this engine ships — the pipeline has no per-utterance language hint. */
        private const val DECODE_LANGUAGE = "auto"

        /** 0 = let the bridge pick min(4, hardware_concurrency), matching whisper.cpp's own
         *  default (see nativeInit in whisper_bridge.cpp). */
        private const val N_THREADS_AUTO = 0

        /** Best-effort, no-engine-instance-needed status for UI display (e.g. Voice Settings,
         *  which doesn't hold a live [WhisperCppSttEngine] outside an active voice session):
         *  which backend the most recent successful load actually used, or null if whisper.cpp has
         *  never loaded successfully on this install yet. */
        fun lastKnownBackendLabel(context: Context): String? =
            context.getSharedPreferences(GPU_PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAST_BACKEND, null)

        /** True if a previous GPU attempt crashed the process and GPU was therefore permanently
         *  disabled for this install (see the class doc's "GPU: opt-in" section) — surfaced so the
         *  UI can explain *why* the GPU toggle isn't taking effect instead of leaving it looking
         *  silently ignored. */
        fun isGpuDisabledAfterCrash(context: Context): Boolean =
            context.getSharedPreferences(GPU_PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_GPU_DISABLED, false)
    }
}
