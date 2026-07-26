package com.vervan.chat.voice

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Encodes mono PCM16 to AAC-in-M4A via Android's built-in hardware/software AAC encoder — no
 * external library needed, unlike MP3 (no license-free encoder ships with Android, so MP3 export
 * stays out of scope; M4A/AAC covers the same "compressed export" need with what's already on
 * every device). Classic synchronous [MediaCodec] buffer loop, same shape as
 * [AudioDecoder]'s decode loop but in the encode direction, muxed into an .m4a container with
 * [MediaMuxer]. */
object Mp4aEncoder {
    private const val BIT_RATE = 96_000

    fun encode(samples: ShortArray, sampleRateHz: Int, outputFile: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRateHz, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        outputFile.parentFile?.mkdirs()
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrack = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()

        try {
            val pcmBytes = ByteArray(samples.size * 2)
            java.nio.ByteBuffer.wrap(pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)
            var offset = 0
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val chunkSize = minOf(inputBuffer.capacity(), pcmBytes.size - offset)
                        if (chunkSize <= 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            inputBuffer.clear()
                            inputBuffer.put(pcmBytes, offset, chunkSize)
                            codec.queueInputBuffer(inputIndex, 0, chunkSize, 0, 0)
                            offset += chunkSize
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "Format changed twice" }
                        muxerTrack = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outputIndex >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                        if (info.size > 0 && muxerStarted) {
                            val outputBuffer: ByteBuffer = codec.getOutputBuffer(outputIndex)!!
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            muxer.writeSampleData(muxerTrack, outputBuffer, info)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }
}
