package com.vervan.chat.voice

import com.vervan.chat.BuildConfig

/**
 * Raw JNI surface for the needle bridge (`app/src/main/cpp/needle_bridge.cpp`), which links Cactus's
 * prebuilt needle engine — the runtime Udgam Labs' Shruti speech-to-text models run on. Mirrors the
 * [WhisperCppJni]/[WhisperCppSttEngine] split: this object is only the native contract.
 *
 * The library is loaded lazily from [load], reached only after [BuildConfig.NEEDLE_AVAILABLE] has
 * been checked, so a build without the engine never attempts it. JSON results come back as raw
 * UTF-8 bytes (see the bridge for why) and are decoded with [utf8].
 */
internal object ShrutiJni {
    @Volatile private var loaded = false

    /** Loads `libvervan_needle_jni.so` once. Throws [UnsatisfiedLinkError] on an ABI the engine
     *  was not packaged for — callers treat that as "not available". */
    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            System.loadLibrary("vervan_needle_jni")
            loaded = true
        }
    }

    /** Maps and loads a `.cact` speech model. A second call with the same path is a no-op. */
    external fun nativeLoad(path: String): Boolean

    /** Transcribes 16 kHz mono float PCM of any length (30 s windows internally). [language] null
     *  = detect; [keywords] null or newline-separated bias terms. Returns needle's JSON:
     *  `{"text","language","ttft_ms","decode_tps"[,"words":[{"word","start","end","probability"}]]}`. */
    external fun nativeTranscribe(samples: FloatArray, n: Int, language: String?, keywords: String?, wordTimestamps: Boolean): ByteArray?

    /** Live transcription: feed ~1 s of 16 kHz PCM per call; returns the newly committed words plus
     *  the unconfirmed tail as JSON (`{"text","words","pending","language","received","pass_ms"}`). */
    external fun nativeStreamProcess(samples: FloatArray, n: Int, language: String?, keywords: String?): ByteArray?

    /** Ends the live stream; the tail becomes committed text. */
    external fun nativeStreamStop(): ByteArray?

    external fun nativeReset()

    /** Forgets the loaded path so the next [nativeLoad] reloads from disk. */
    external fun nativeForget()

    external fun nativeGetLastError(): String?

    fun utf8(bytes: ByteArray?): String? = bytes?.toString(Charsets.UTF_8)
}
