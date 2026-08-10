package com.vervan.chat.voice

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.vervan.chat.validation.InputLimits
import kotlin.math.roundToInt

/** Decodes the audio track of any file Android's built-in codecs understand — MP3/WAV/M4A/AAC/
 * FLAC/OGG, or the audio track of an MP4/MOV/MKV/WebM video — into mono 16 kHz PCM16, the format
 * [WhisperCppSttEngine.transcribe] expects. Used by the Transcription screen for imported files;
 * [com.vervan.chat.audio.WavRecorder] already records directly in this format, so recorded audio
 * skips this path entirely. */
object AudioDecoder {
    private const val TARGET_SAMPLE_RATE = 16000

    class UnsupportedAudioException(message: String) : Exception(message)

    /** Blocking — call off the main thread. Returns mono 16 kHz PCM16 samples plus the source
     * file's total duration in milliseconds (from the container's own metadata, not resampled). */
    fun decodeToPcm16k(file: File): Pair<ShortArray, Long> {
        require(file.isFile && file.length() <= 50L * 1024 * 1024) {
            "Audio file is missing or exceeds the 50 MB limit"
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw UnsupportedAudioException("No audio track found in ${file.name}")
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else 0L
            require(durationMs <= InputLimits.MAX_AUDIO_DURATION_MS) {
                "Audio must be 30 minutes or shorter"
            }
            extractor.selectTrack(trackIndex)

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val pcm = decodeLoop(extractor, codec)
                val mono = if (sourceChannels > 1) downmix(pcm, sourceChannels) else pcm
                val resampled = if (sourceSampleRate == TARGET_SAMPLE_RATE) mono else resample(mono, sourceSampleRate, TARGET_SAMPLE_RATE)
                return resampled to durationMs
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    /** Classic synchronous MediaCodec buffer loop — feeds compressed samples in, drains decoded
     * PCM16 out, until the extractor and decoder both signal end-of-stream. */
    private fun decodeLoop(extractor: MediaExtractor, codec: MediaCodec): ShortArray {
        val info = MediaCodec.BufferInfo()
        val out = java.io.ByteArrayOutputStream()
        var sawInputEos = false
        var sawOutputEos = false
        while (!sawOutputEos) {
            if (!sawInputEos) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outputIndex >= 0) {
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                if (info.size > 0) {
                    require(out.size().toLong() + info.size <= InputLimits.MAX_DECODED_AUDIO_BYTES) {
                        "Decoded audio is too large"
                    }
                    val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                    outputBuffer.position(info.offset)
                    outputBuffer.limit(info.offset + info.size)
                    val chunk = ByteArray(info.size)
                    outputBuffer.get(chunk)
                    out.write(chunk)
                }
                codec.releaseOutputBuffer(outputIndex, false)
            }
        }
        val bytes = out.toByteArray()
        val samples = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        return samples
    }

    private fun downmix(interleaved: ShortArray, channels: Int): ShortArray {
        val frames = interleaved.size / channels
        return ShortArray(frames) { i ->
            var sum = 0
            for (c in 0 until channels) sum += interleaved[i * channels + c]
            (sum / channels).toShort()
        }
    }

    /** ponytail: linear interpolation, not a proper sinc/polyphase resampler — introduces a
     * little high-frequency aliasing, which whisper.cpp's own robustness to noisy audio absorbs
     * fine for speech. Upgrade to a real resampler if transcription quality on resampled files
     * measurably lags behind natively-16kHz recordings. */
    private fun resample(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (input.isEmpty()) return input
        val ratio = fromRate.toDouble() / toRate
        val outLength = (input.size / ratio).roundToInt()
        return ShortArray(outLength) { i ->
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, input.size - 1)
            val idx1 = (idx0 + 1).coerceAtMost(input.size - 1)
            val frac = srcPos - idx0
            (input[idx0] * (1 - frac) + input[idx1] * frac).roundToInt().toShort()
        }
    }
}
