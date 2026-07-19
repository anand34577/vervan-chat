package com.vervan.chat.system

import java.io.FileNotFoundException
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {
    @Test
    fun unknownTechnicalDetailsAreNotShown() {
        val message = IllegalStateException("JNI_ERR_473 native pointer 0xdeadbeef").toUserMessage()
        assertFalse(message.contains("JNI_ERR_473"))
        assertTrue(message.contains("Diagnostics"))
    }

    @Test
    fun commonFailuresProvideARecoveryAction() {
        assertTrue(FileNotFoundException().toUserMessage().contains("Choose it again"))
        assertTrue(TimeoutException().toUserMessage().contains("Try again"))
        assertTrue("No active model".toUserMessage().contains("Open Models"))
        assertTrue("backend failed while loading model".toUserMessage().contains("smaller model"))
    }
}
