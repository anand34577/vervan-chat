package com.vervan.chat.security

import java.io.IOException
import java.security.InvalidKeyException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedPrefsTest {
    @Test
    fun `key and authenticated-cipher failures permit recovery`() {
        assertTrue(InvalidKeyException("invalid").isEncryptedPrefsKeyFailure())
        assertTrue(RuntimeException(AEADBadTagException("corrupt")).isEncryptedPrefsKeyFailure())
    }

    @Test
    fun `io and programming failures do not delete secrets`() {
        assertFalse(IOException("disk full").isEncryptedPrefsKeyFailure())
        assertFalse(IllegalStateException("bug").isEncryptedPrefsKeyFailure())
    }
}
