package com.vervan.chat.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRequestGuardTest {
    @Test fun `loopback and ip literal hosts are allowed`() {
        assertTrue(BrowserRequestGuard.isAllowedHost("127.0.0.1:8080"))
        assertTrue(BrowserRequestGuard.isAllowedHost("localhost:8080"))
        assertTrue(BrowserRequestGuard.isAllowedHost("192.168.1.20:8080"))
        assertTrue(BrowserRequestGuard.isAllowedHost("[::1]:8080"))
        assertTrue(BrowserRequestGuard.isAllowedHost(null))
    }

    @Test fun `dns rebinding hostnames are refused`() {
        assertFalse(BrowserRequestGuard.isAllowedHost("evil.example.com:8080"))
        assertFalse(BrowserRequestGuard.isAllowedHost("127.0.0.1.nip.io:8080"))
    }

    @Test fun `only same-origin browser requests pass`() {
        assertTrue(BrowserRequestGuard.isSameOrigin(null, "127.0.0.1:8080"))
        assertTrue(BrowserRequestGuard.isSameOrigin("http://127.0.0.1:8080", "127.0.0.1:8080"))
        assertFalse(BrowserRequestGuard.isSameOrigin("https://evil.example.com", "127.0.0.1:8080"))
        assertFalse(BrowserRequestGuard.isSameOrigin("null", "127.0.0.1:8080"))
        assertFalse(BrowserRequestGuard.isSameOrigin("http://localhost:8080", "127.0.0.1:8080"))
    }
}
