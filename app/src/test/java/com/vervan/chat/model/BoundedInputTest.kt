package com.vervan.chat.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedInputTest {
    @Test
    fun acceptsInputAtLimit() {
        val bytes = ByteArray(16) { it.toByte() }
        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readBytesLimited(16))
    }

    @Test
    fun rejectsInputPastLimitWhileStreaming() {
        assertThrows(InputLimitExceededException::class.java) {
            ByteArrayInputStream(ByteArray(17)).readBytesLimited(16)
        }
    }

    @Test
    fun boundedCopyRejectsAfterWritingOnlyTheAllowedBytes() {
        val output = ByteArrayOutputStream()
        assertThrows(InputLimitExceededException::class.java) {
            ByteArrayInputStream(ByteArray(17)).copyToLimited(output, 16)
        }
        org.junit.Assert.assertEquals(0, output.size())
    }

    @Test
    fun boundedReaderRejectsOversizedText() {
        assertThrows(InputLimitExceededException::class.java) {
            StringReader("123456789").readTextLimited(8)
        }
    }
}
