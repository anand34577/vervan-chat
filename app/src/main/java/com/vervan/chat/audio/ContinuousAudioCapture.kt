package com.vervan.chat.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Continuous mic capture for the realtime voice pipeline. Unlike [WavRecorder] (a bounded
 * start/stop-to-one-file lifecycle used by the manual voice-message attach flow), this opens
 * the mic ONCE for the whole voice session and multicasts frames via [frames] (a hot
 * [SharedFlow]) so STT endpointing and barge-in detection can both subscribe without each
 * triggering its own [AudioRecord] open/close — re-opening per phase (every listening cycle,
 * every barge-in watch) was adding real setup latency exactly where barge-in responsiveness
 * matters most.
 *
 * Uses [MediaRecorder.AudioSource.VOICE_COMMUNICATION] + [AcousticEchoCanceler] so the mic
 * doesn't pick up the device's own TTS output as "the user talking" while barge-in listening
 * is active. Hardware AEC isn't universal — [hasEchoCancellation] reports whether it actually
 * engaged for this session, so callers can disable true barge-in and fall back to
 * tap-to-interrupt on devices where it's absent, instead of false-triggering on the app's own
 * voice.
 */
class ContinuousAudioCapture {
    val sampleRateHz = 16000

    @Volatile var hasEchoCancellation: Boolean = false
        private set

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var captureThread: Thread? = null

    private val _frames = MutableSharedFlow<ShortArray>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Hot stream of raw PCM16 frames — subscribe with `collect`/`takeWhile` from as many
     * places as needed; none of them open their own mic. Empty (no frames) until [start] has
     * been called. */
    val frames: SharedFlow<ShortArray> = _frames

    @SuppressLint("MissingPermission") // Caller checks RECORD_AUDIO before calling start().
    fun start(frameMs: Int = 20) {
        if (audioRecord != null) return
        val minBufSize = AudioRecord.getMinBufferSize(sampleRateHz, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = if (minBufSize > 0) minBufSize else 4096
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 2
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Microphone could not be initialized")
        }

        echoCanceler = if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(record.audioSessionId)?.also { it.enabled = true }
        } else null
        hasEchoCancellation = echoCanceler != null

        record.startRecording()
        audioRecord = record

        val frameSamples = (sampleRateHz * frameMs / 1000).coerceAtLeast(1)
        captureThread = Thread {
            val buffer = ShortArray(frameSamples)
            while (audioRecord != null && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = record.read(buffer, 0, buffer.size)
                when {
                    read > 0 -> _frames.tryEmit(if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read))
                    read < 0 -> {
                        Log.w(TAG, "AudioRecord.read() returned error code $read; stopping continuous capture")
                        return@Thread
                    }
                }
            }
        }.apply { start() }
    }

    fun stop() {
        val record = audioRecord ?: return
        audioRecord = null
        runCatching { record.stop() }
        captureThread?.join(500)
        captureThread = null
        echoCanceler?.release()
        echoCanceler = null
        record.release()
    }

    companion object {
        private const val TAG = "ContinuousAudioCapture"
    }
}
