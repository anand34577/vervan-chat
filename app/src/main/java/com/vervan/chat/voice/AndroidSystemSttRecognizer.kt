package com.vervan.chat.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.media.AudioFormat
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/** Thin, lifecycle-safe wrapper around the device speech service. It owns microphone capture,
 * so callers must stop their AudioRecord session before invoking it. */
object AndroidSystemSttRecognizer {
    private data class ActiveSession(
        val stop: () -> Unit,
        val cancel: () -> Unit,
    )

    @Volatile
    private var activeSession: ActiveSession? = null

    fun isAvailable(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ||
                SpeechRecognizer.isRecognitionAvailable(context)
        } else {
            SpeechRecognizer.isRecognitionAvailable(context)
        }

    suspend fun recognizeOnce(
        context: Context,
        languageSetting: String,
        maxSeconds: Int
    ): Result<String> = recognize(context, languageSetting, maxSeconds, null)

    /** Transcribes normalized mono PCM WAV without opening the microphone. Android 13 added the
     * documented audio-source descriptor used here; older system recognizers only accept live
     * capture and therefore report this route unavailable. */
    suspend fun recognizeAudioFile(
        context: Context,
        wavFile: File,
        languageSetting: String,
        maxSeconds: Int,
    ): Result<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return Result.failure(IllegalStateException("Android file transcription requires Android 13 or newer"))
        }
        return runCatching {
            val decoded = WavPcmDecoder.decode(wavFile.readBytes())
            val rawFile = File.createTempFile("android-stt-", ".pcm", context.cacheDir)
            try {
                val encoded = WavPcmDecoder.encode(decoded.samples, decoded.sampleRateHz)
                rawFile.writeBytes(encoded.copyOfRange(44, encoded.size))
                val descriptor = ParcelFileDescriptor.open(rawFile, ParcelFileDescriptor.MODE_READ_ONLY)
                recognize(
                    context, languageSetting, maxSeconds,
                    AudioSource(descriptor, decoded.sampleRateHz)
                ).getOrThrow()
            } finally {
                rawFile.delete()
            }
        }
    }

    private data class AudioSource(val descriptor: ParcelFileDescriptor, val sampleRateHz: Int)

    private suspend fun recognize(
        context: Context,
        languageSetting: String,
        maxSeconds: Int,
        audioSource: AudioSource?,
    ): Result<String> = runCatching {
        withTimeout((maxSeconds.coerceIn(10, 180) + 8) * 1_000L) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    val recognizer = if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                    ) {
                        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    } else {
                        SpeechRecognizer.createSpeechRecognizer(context)
                    }
                    var finished = false
                    var session: ActiveSession? = null
                    fun finish(result: Result<String>) {
                        if (finished) return
                        finished = true
                        if (activeSession === session) activeSession = null
                        runCatching { audioSource?.descriptor?.close() }
                        recognizer.destroy()
                        if (continuation.isActive) continuation.resumeWith(result)
                    }
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onPartialResults(partialResults: Bundle?) = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                        override fun onError(error: Int) {
                            Log.w(TAG, "Android speech recognition error: $error (${errorMessage(error)})")
                            finish(Result.failure(IllegalStateException(errorMessage(error))))
                        }

                        override fun onResults(results: Bundle?) {
                            val text = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            finish(
                                if (text.isBlank()) Result.failure(IllegalStateException("No speech was recognized"))
                                else Result.success(text)
                            )
                        }
                    })
                    val language = when (languageSetting) {
                        "EN" -> "en-US"
                        "HI" -> "hi-IN"
                        else -> null
                    }
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        language?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && audioSource != null) {
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audioSource.descriptor)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, audioSource.sampleRateHz)
                        }
                    }
                    continuation.invokeOnCancellation {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            recognizer.cancel()
                            if (activeSession === session) activeSession = null
                            runCatching { audioSource?.descriptor?.close() }
                            recognizer.destroy()
                        }
                    }
                    session = ActiveSession(
                        stop = { if (!finished) recognizer.stopListening() },
                        cancel = {
                            if (!finished) {
                                recognizer.cancel()
                                finish(Result.failure(CancellationException("Speech input cancelled")))
                            }
                        }
                    )
                    activeSession = session
                    recognizer.startListening(intent)
                }
            }
        }
    }

    fun finishActiveRecognition() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            activeSession?.stop?.invoke()
        }
    }

    fun cancelActiveRecognition() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            activeSession?.cancel?.invoke()
        }
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "The microphone could not be read"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "The device speech service could not run offline"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The device speech service is busy"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was heard"
        else -> "Device speech recognition failed"
    }

    private const val TAG = "AndroidSystemStt"
}
