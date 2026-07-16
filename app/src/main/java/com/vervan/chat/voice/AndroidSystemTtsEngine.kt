package com.vervan.chat.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Wraps Android's built-in [TextToSpeech] as a [TtsEngine] — always available, zero setup, no
 * model download. The guaranteed final fallback tier: this is what the realtime voice
 * pipeline speaks with on day one, before any Supertonic/Piper model has downloaded, and
 * whenever a device has neither.
 *
 * Uses [TextToSpeech.synthesizeToFile] (not [TextToSpeech.speak]) because the pipeline — not
 * this engine — owns playback timing via [TtsPlaybackQueue]; the WAV file synthesis produces
 * is decoded to raw PCM and the temp file discarded immediately.
 */
class AndroidSystemTtsEngine(private val context: Context) : TtsEngine {
    override val engineName = "Android system"

    private var tts: TextToSpeech? = null
    private var initResult: Int = TextToSpeech.ERROR

    override suspend fun isReady(): Boolean {
        ensureInitialized()
        return initResult == TextToSpeech.SUCCESS
    }

    private suspend fun ensureInitialized() {
        if (tts != null) return
        initResult = suspendCancellableCoroutine { cont ->
            val instance = TextToSpeech(context) { status -> if (cont.isActive) cont.resume(status) }
            tts = instance
        }
    }

    override suspend fun synthesize(text: String, lang: String): TtsAudio {
        ensureInitialized()
        val engine = tts ?: throw IllegalStateException("Android TTS failed to initialize")
        if (initResult != TextToSpeech.SUCCESS) throw IllegalStateException("Android TTS failed to initialize")

        val locale = when (lang) {
            "hi" -> Locale("hi", "IN")
            else -> Locale.US
        }
        // Falls back to whatever's already selected if the requested language's voice data
        // isn't installed — synthesis still proceeds, just not in the requested language.
        engine.setLanguage(locale)

        val outFile = File(context.cacheDir, "tts-${UUID.randomUUID()}.wav")
        val utteranceId = outFile.name
        suspendCancellableCoroutine<Unit> { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { if (id == utteranceId && cont.isActive) cont.resume(Unit) }
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resumeWithException(IllegalStateException("Synthesis failed"))
                }
            })
            val result = engine.synthesizeToFile(text, Bundle(), outFile, utteranceId)
            if (result != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resumeWithException(IllegalStateException("synthesizeToFile rejected the request"))
            }
        }

        return try {
            WavPcmDecoder.decode(outFile.readBytes())
        } finally {
            outFile.delete()
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
