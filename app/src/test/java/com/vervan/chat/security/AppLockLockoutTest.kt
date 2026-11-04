package com.vervan.chat.security

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockLockoutTest {
    @Test fun `lockouts escalate and cap at one hour`() {
        assertEquals(30_000L, AppLockManager.lockoutDurationMs(0))
        assertEquals(60_000L, AppLockManager.lockoutDurationMs(1))
        assertEquals(5 * 60_000L, AppLockManager.lockoutDurationMs(2))
        assertEquals(15 * 60_000L, AppLockManager.lockoutDurationMs(3))
        assertEquals(60 * 60_000L, AppLockManager.lockoutDurationMs(4))
        assertEquals(60 * 60_000L, AppLockManager.lockoutDurationMs(40))
    }
}
