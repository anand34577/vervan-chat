package com.vervan.chat.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes an audio document with Android's installed codecs, downmixes it to mono, and writes
 * the 16 kHz/16-bit PCM WAV accepted by the app's local multimodal audio path. No cloud service
 * or format-specific dependency is needed; supported containers/codecs follow the device.
 */
object AudioNormalizer {
    private const val TARGET_SAMPLE_RATE = 16_000
    private const val MAX_DURATION_US = 30L * 60L * 1_000_000L
    private const val MAX_DECODED_BYTES = 256L * 1024L * 1024L
    private const val TIMEOUT_US = 10_000L

    fun normalize(context: Context, source: Uri, destination: File): File {
        destination.parentFile?.mkdirs()
        val decoded = File.createTempFile("audio-import-", ".pcm", context.cacheDir)
        try {
            val format = decode(context, source, decoded)
            writeMonoWav(decoded, destination, format)
            require(destination.length() > 44L) { "The audio file did not contain any playable sound" }
            return destination
        } catch (t: Throwable) {
            destination.delete()
            throw t
        } finally {
            decoded.delete()
        }
    }

    private data class DecodedFormat(val sampleRate: Int, val channels: Int, val pcmEncoding: Int)

    private fun decode(context: Context, source: Uri, output: File): DecodedFormat {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, source, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("This file does not contain a supported audio track")
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val durationUs = inputFormat.getLongOrNull(MediaFormat.KEY_DURATION)
            require(durationUs == null || durationUs <= MAX_DURATION_US) { "Audio must be 30 minutes or shorter" }

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("The audio track has no recognized format")
            extractor.selectTrack(trackIndex)
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            var sampleRate = inputFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: TARGET_SAMPLE_RATE
            var channels = inputFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var inputEnded = false
            var outputEnded = false
            var decodedBytes = 0L
            val info = MediaCodec.BufferInfo()

            output.outputStream().buffered().use { sink ->
                while (!outputEnded) {
                    if (!inputEnded) {
                        val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val buffer = codec.getInputBuffer(inputIndex)
                                ?: error("The device audio decoder did not provide an input buffer")
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outputFormat = codec.outputFormat
                            sampleRate = outputFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: sampleRate
                            channels = outputFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: channels
                            pcmEncoding = outputFormat.getIntegerOrNull(MediaFormat.KEY_PCM_ENCODING)
                                ?: AudioFormat.ENCODING_PCM_16BIT
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            codec.getOutputBuffer(outputIndex)?.let { buffer ->
                                if (info.size > 0) {
                                    decodedBytes += info.size
                                    require(decodedBytes <= MAX_DECODED_BYTES) { "Decoded audio is too large" }
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    val bytes = ByteArray(info.size)
                                    buffer.get(bytes)
                                    sink.write(bytes)
                                }
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
            require(sampleRate > 0 && channels > 0) { "The decoded audio format is invalid" }
            return DecodedFormat(sampleRate, channels, pcmEncoding)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun writeMonoWav(source: File, destination: File, format: DecodedFormat) {
        val bytesPerSample = when (format.pcmEncoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            else -> error("The device returned an unsupported decoded PCM format")
        }
        val frameSize = bytesPerSample * format.channels
        val frame = ByteArray(frameSize)
        var outputSamples = 0L
        var phase = 0L

        RandomAccessFile(destination, "rw").use { wav ->
            wav.setLength(0)
            wav.write(ByteArray(WavFormat.HEADER_SIZE))
            BufferedInputStream(source.inputStream()).use { input ->
                while (input.readFrame(frame)) {
                    val mono = downmix(frame, format.channels, format.pcmEncoding, bytesPerSample)
                    phase += TARGET_SAMPLE_RATE
                    while (phase >= format.sampleRate) {
                        wav.write(mono.toInt() and 0xff)
                        wav.write((mono.toInt() shr 8) and 0xff)
                        outputSamples++
                        phase -= format.sampleRate
                    }
                }
            }
            val dataLength = (outputSamples * 2L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            wav.seek(0)
            wav.write(WavFormat.header(dataLength, TARGET_SAMPLE_RATE, 1))
        }
    }

    private fun downmix(frame: ByteArray, channels: Int, encoding: Int, bytesPerSample: Int): Short {
        var sum = 0L
        repeat(channels) { channel ->
            val offset = channel * bytesPerSample
            sum += when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> ((frame[offset].toInt() and 0xff) - 128) shl 8
                AudioFormat.ENCODING_PCM_16BIT ->
                    (frame[offset].toInt() and 0xff) or (frame[offset + 1].toInt() shl 8)
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                    val raw = (frame[offset].toInt() and 0xff) or
                        ((frame[offset + 1].toInt() and 0xff) shl 8) or
                        (frame[offset + 2].toInt() shl 16)
                    raw shr 8
                }
                AudioFormat.ENCODING_PCM_32BIT -> {
                    val raw = ByteBuffer.wrap(frame, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    raw shr 16
                }
                AudioFormat.ENCODING_PCM_FLOAT -> {
                    val value = ByteBuffer.wrap(frame, offset, 4).order(ByteOrder.LITTLE_ENDIAN).float
                    (value.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
                }
                else -> 0
            }
        }
        return (sum / channels).coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
    }

    private fun BufferedInputStream.readFrame(frame: ByteArray): Boolean {
        var offset = 0
        while (offset < frame.size) {
            val read = read(frame, offset, frame.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private fun MediaFormat.getLongOrNull(key: String): Long? =
        if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
}

/** Small pure WAV-header builder shared by imported and recorded audio. */
internal object WavFormat {
    const val HEADER_SIZE = 44

    fun header(dataLength: Int, sampleRate: Int = 16_000, channels: Int = 1): ByteArray {
        require(dataLength >= 0 && sampleRate > 0 && channels > 0)
        val result = ByteArray(HEADER_SIZE)
        "RIFF".writeTo(result, 0)
        result.writeIntLe(4, 36 + dataLength)
        "WAVE".writeTo(result, 8)
        "fmt ".writeTo(result, 12)
        result.writeIntLe(16, 16)
        result.writeShortLe(20, 1)
        result.writeShortLe(22, channels)
        result.writeIntLe(24, sampleRate)
        result.writeIntLe(28, sampleRate * channels * 2)
        result.writeShortLe(32, channels * 2)
        result.writeShortLe(34, 16)
        "data".writeTo(result, 36)
        result.writeIntLe(40, dataLength)
        return result
    }

    private fun String.writeTo(target: ByteArray, offset: Int) =
        forEachIndexed { index, char -> target[offset + index] = char.code.toByte() }

    private fun ByteArray.writeIntLe(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value shr 8).toByte()
        this[offset + 2] = (value shr 16).toByte()
        this[offset + 3] = (value shr 24).toByte()
    }

    private fun ByteArray.writeShortLe(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value shr 8).toByte()
    }
}
