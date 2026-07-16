package com.vervan.chat.voice

/** Shared standard-44-byte-header WAV <-> PCM16 mono codec. [decode] is used by every
 * [TtsEngine] that hands back a WAV file/bytes instead of raw samples (Android system TTS,
 * Supertonic) — stereo is downmixed to mono (left channel) since every downstream consumer
 * (VAD, playback) is mono. [encode] is used by [RealtimeVoiceController] to package captured
 * mic PCM into the WAV format `LlmEngine.generate(audioPath=...)` requires. */
internal object WavPcmDecoder {
    fun decode(bytes: ByteArray): TtsAudio {
        require(bytes.size >= 44) { "Not a valid WAV: only ${bytes.size} bytes" }
        val channels = readShortLE(bytes, 22)
        val sampleRate = readIntLE(bytes, 24)
        val pcm = bytes.copyOfRange(44, bytes.size)
        val samples = if (channels.toInt() == 2) {
            ShortArray(pcm.size / 4) { i -> readShortLE(pcm, i * 4) }
        } else {
            ShortArray(pcm.size / 2) { i -> readShortLE(pcm, i * 2) }
        }
        return TtsAudio(samples, sampleRate)
    }

    /** Mono PCM16 samples -> a complete 44-byte-header WAV file's bytes. */
    fun encode(samples: ShortArray, sampleRateHz: Int): ByteArray {
        val dataLen = samples.size * 2
        val byteRate = sampleRateHz * 2
        val out = ByteArray(44 + dataLen)
        "RIFF".forEachIndexed { i, c -> out[i] = c.code.toByte() }
        writeIntLE(out, 4, 36 + dataLen)
        "WAVE".forEachIndexed { i, c -> out[8 + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> out[12 + i] = c.code.toByte() }
        writeIntLE(out, 16, 16)
        writeShortLE(out, 20, 1) // PCM
        writeShortLE(out, 22, 1) // mono
        writeIntLE(out, 24, sampleRateHz)
        writeIntLE(out, 28, byteRate)
        writeShortLE(out, 32, 2) // block align
        writeShortLE(out, 34, 16) // bits per sample
        "data".forEachIndexed { i, c -> out[36 + i] = c.code.toByte() }
        writeIntLE(out, 40, dataLen)
        for (i in samples.indices) writeShortLE(out, 44 + i * 2, samples[i].toInt())
        return out
    }

    private fun readShortLE(b: ByteArray, offset: Int): Short =
        ((b[offset].toInt() and 0xff) or ((b[offset + 1].toInt() and 0xff) shl 8)).toShort()

    private fun readIntLE(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xff) or ((b[offset + 1].toInt() and 0xff) shl 8) or
            ((b[offset + 2].toInt() and 0xff) shl 16) or ((b[offset + 3].toInt() and 0xff) shl 24)

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
