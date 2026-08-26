package com.vervan.chat.system

import java.io.FileNotFoundException
import java.util.concurrent.TimeoutException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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
        assertTrue("No active model".toUserMessage().contains("Settings → AI models"))
        assertTrue("backend failed while loading model".toUserMessage().contains("smaller model"))
    }

    @Test
    fun cancellationIsNeverConvertedIntoAUserFacingFailure() {
        val cancellation = CancellationException("cancelled")
        assertThrows(CancellationException::class.java) { cancellation.toUserMessage() }
        assertThrows(CancellationException::class.java) {
            IllegalStateException("wrapped", cancellation).toUserMessage()
        }
    }
}
