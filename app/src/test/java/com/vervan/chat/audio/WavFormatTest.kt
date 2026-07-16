package com.vervan.chat.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class WavFormatTest {
    @Test
    fun headerDescribesMono16KhzPcmAndPayloadLength() {
        val header = WavFormat.header(dataLength = 32_000)

        assertEquals("RIFF", header.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(32_036, header.intLe(4))
        assertEquals("WAVE", header.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertEquals(1, header.shortLe(20))
        assertEquals(1, header.shortLe(22))
        assertEquals(16_000, header.intLe(24))
        assertEquals(32_000, header.intLe(28))
        assertEquals(16, header.shortLe(34))
        assertEquals("data", header.copyOfRange(36, 40).toString(Charsets.US_ASCII))
        assertEquals(32_000, header.intLe(40))
    }

    private fun ByteArray.intLe(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

    private fun ByteArray.shortLe(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
}
