package com.vervan.chat.voice

import com.vervan.chat.BuildConfig

/**
 * Raw JNI surface for the whisper.cpp bridge (see `app/src/main/cpp/whisper_bridge.cpp`). Kept as
 * a separate object from [WhisperCppSttEngine] so the native contract — what's actually declared
 * `external` and must have a matching `JNIEXPORT` in the bridge — is easy to find and audit on
 * its own, mirroring the [com.vervan.chat.llm.LlamaCppJni]/[com.vervan.chat.llm.LlamaCppEngine]
 * split.
 *
 * `System.loadLibrary` runs lazily, the first time [load] is called. That call is reached only
 * once [WhisperCppSttEngine] has confirmed [BuildConfig.WHISPER_CPP_AVAILABLE] is true (i.e. a
 * prebuilt libwhisper.so + libvervan_whisper_jni.so are packaged in this APK), so a build without
 * whisper.cpp compiled in never attempts the load — every other feature still works. The lazy
 * [load] (instead of an `init` block) is what makes that possible: an `init` block would run on
 * first reference to the object, which can happen from code paths that don't gate on
 * [BuildConfig], so it would throw [UnsatisfiedLinkError] on a whisper-less build.
 */
internal object WhisperCppJni {
    @Volatile private var loaded = false

    /** Loads `libvervan_whisper_jni.so` once. No-op on subsequent calls. Callers MUST have
     *  already checked [BuildConfig.WHISPER_CPP_AVAILABLE]. */
    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            System.loadLibrary("vervan_whisper_jni")
            loaded = true
        }
    }

    /** Loads `modelPath` (a whisper.cpp `ggml-*.bin` file) and returns an opaque native handle,
     *  or 0 on failure — call [nativeGetLastError] to find out why. [nThreads] controls
     *  whisper.cpp's mel-spectrogram + encoder parallelism; pass 0 to let the bridge pick. */
    external fun nativeInit(modelPath: String, nThreads: Int): Long

    /** Transcribes [samples] (mono float PCM at 16 kHz, values normalized to [-1, 1]). Returns the
     *  trimmed transcript, or null on any failure (decode error / blank output) — see
     *  [WhisperCppSttEngine.transcribe]'s null-means-fall-through contract. [language] is an
     *  ISO-639-1 code ("en", "hi", ...) or "auto"; [translate] true requests translation to
     *  English instead of transcription. Blocks the calling thread — invoke off the main thread. */
    external fun nativeTranscribe(handle: Long, samples: FloatArray, nSamples: Int, language: String, translate: Boolean): String?

    /** Frees the whisper context. Safe to call on an already-closed (0) handle. */
    external fun nativeFree(handle: Long)

    /** Human-readable detail for the most recent failure on the calling thread, or null. */
    external fun nativeGetLastError(): String?
}
