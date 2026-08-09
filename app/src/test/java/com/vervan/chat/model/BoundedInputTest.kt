package com.vervan.chat.model

import java.io.ByteArrayInputStream
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
}
