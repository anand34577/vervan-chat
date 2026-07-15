package com.vervan.chat.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile

/**
 * Records mono 16kHz/16-bit PCM straight to a `.wav` file via [AudioRecord] — the exact
 * format `LlmInferenceSession.addAudio()` requires (see [com.vervan.chat.llm.LlmEngine]).
 * ponytail: a plain background [Thread] for the blocking read loop, not a coroutine —
 * this is a bounded start/stop lifecycle, not a stream anything else needs to observe.
 * Caller must already hold RECORD_AUDIO before calling [start].
 */
class WavRecorder(val outputFile: File) {
    private val sampleRate = 16000
    private var audioRecord: AudioRecord? = null
    @Volatile private var recording = false
    private var recordThread: Thread? = null

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
                    if (read > 0) out.write(buffer, 0, read)
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
        val byteRate = sampleRate * 2 // mono, 16-bit
        val dataLength = pcmFile.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        RandomAccessFile(wavFile, "rw").use { raf ->
            raf.setLength(0)
            val header = ByteArray(44)
            writeWavHeader(header, sampleRate, byteRate, dataLength)
            raf.write(header)
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

    private fun writeWavHeader(h: ByteArray, sampleRate: Int, byteRate: Int, dataLen: Int) {
        "RIFF".forEachIndexed { i, c -> h[i] = c.code.toByte() }
        writeIntLE(h, 4, 36 + dataLen)
        "WAVE".forEachIndexed { i, c -> h[8 + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> h[12 + i] = c.code.toByte() }
        writeIntLE(h, 16, 16) // Subchunk1Size for PCM
        writeShortLE(h, 20, 1) // AudioFormat = PCM
        writeShortLE(h, 22, 1) // NumChannels = mono
        writeIntLE(h, 24, sampleRate)
        writeIntLE(h, 28, byteRate)
        writeShortLE(h, 32, 2) // BlockAlign = channels * bitsPerSample/8
        writeShortLE(h, 34, 16) // BitsPerSample
        "data".forEachIndexed { i, c -> h[36 + i] = c.code.toByte() }
        writeIntLE(h, 40, dataLen)
    }

    private fun writeIntLE(b: ByteArray, offset: Int, value: Int) {
        b[offset] = (value and 0xff).toByte()
        b[offset + 1] = ((value shr 8) and 0xff).toByte()
        b[offset + 2] = ((value shr 16) and 0xff).toByte()
        b[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    private fun writeShortLE(b: ByteArray, offset: Int, value: Int) {
        b[offset] = (value and 0xff).toByte()
        b[offset + 1] = ((value shr 8) and 0xff).toByte()
    }
}
