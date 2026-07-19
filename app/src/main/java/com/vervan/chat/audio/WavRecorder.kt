package com.vervan.chat.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * Records mono 16kHz/16-bit PCM straight to a `.wav` file via [AudioRecord] — the exact
 * format `LlmInferenceSession.addAudio()` requires (see [com.vervan.chat.llm.LlmEngine]).
 * a plain background [Thread] for the blocking read loop, not a coroutine —
 * this is a bounded start/stop lifecycle, not a stream anything else needs to observe.
 * Caller must already hold RECORD_AUDIO before calling [start].
 */
class WavRecorder(val outputFile: File) {
    private val sampleRate = 16000
    private var audioRecord: AudioRecord? = null
    @Volatile private var recording = false
    private var recordThread: Thread? = null

    /** Set if the record loop ended early because [AudioRecord.read] returned a persistent
     * error code — e.g. RECORD_AUDIO permission revoked mid-recording, or the mic taken by a
     * higher-priority app. Callers can check this after [stop] to tell "silent because nothing
     * was said" apart from "recording actually failed", which the loop used to swallow. */
    @Volatile var failureReason: String? = null
        private set

    @SuppressLint("MissingPermission") // Caller checks RECORD_AUDIO; start still fails closed below.
    fun start() {
        check(!recording) { "Recorder is already running" }
        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = if (minBufSize > 0) minBufSize else 4096
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 2
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Microphone could not be initialized")
        }
        try {
            record.startRecording()
        } catch (t: Throwable) {
            record.release()
            throw t
        }
        audioRecord = record
        recording = true
        recordThread = Thread {
            val buffer = ByteArray(bufSize)
            val pcmFile = File(outputFile.parentFile, outputFile.name + ".pcm")
            pcmFile.outputStream().use { out ->
                while (recording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        out.write(buffer, 0, read)
                    } else if (read < 0) {
                        // A negative return (ERROR_DEAD_OBJECT, ERROR_INVALID_OPERATION,
                        // ERROR_BAD_VALUE — e.g. RECORD_AUDIO revoked mid-recording via the
                        // notification-shade permission toggle) used to be silently ignored,
                        // spinning this loop forever doing nothing and producing a silent/empty
                        // WAV file with no indication anything went wrong.
                        Log.w(TAG, "AudioRecord.read() returned error code $read; stopping")
                        failureReason = "Recording stopped unexpectedly (error $read)"
                        recording = false
                    }
                }
            }
            writeWavFile(pcmFile, outputFile)
            pcmFile.delete()
        }.apply { start() }
    }

    /** Blocks briefly until the recorder thread has flushed the WAV file to disk. */
    fun stop() {
        recording = false
        runCatching { audioRecord?.stop() }
        recordThread?.join()
        recordThread = null
        audioRecord?.release()
        audioRecord = null
    }

    fun cancel() {
        recording = false
        runCatching { audioRecord?.stop() }
        recordThread?.join()
        recordThread = null
        audioRecord?.release()
        audioRecord = null
        outputFile.delete()
    }

    private fun writeWavFile(pcmFile: File, wavFile: File) {
        val dataLength = pcmFile.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        RandomAccessFile(wavFile, "rw").use { raf ->
            raf.setLength(0)
            raf.write(WavFormat.header(dataLength, sampleRate, 1))
            pcmFile.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    raf.write(buffer, 0, read)
                }
            }
        }
    }

    companion object {
        private const val TAG = "WavRecorder"
    }
}
