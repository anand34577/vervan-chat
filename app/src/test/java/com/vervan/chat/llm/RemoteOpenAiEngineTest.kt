package com.vervan.chat.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteOpenAiEngineTest {

    @Test
    fun acceptsHttpsEndpoints() {
        assertNull(RemoteOpenAiEngine.baseUrlError("https://api.openai.com/v1"))
        assertNull(RemoteOpenAiEngine.baseUrlError("https://openrouter.ai/api/v1"))
        // Trailing slash and surrounding whitespace are tolerated — the write path trims both.
        assertNull(RemoteOpenAiEngine.baseUrlError("  https://api.openai.com/v1/  "))
        // Uppercase scheme is still https.
        assertNull(RemoteOpenAiEngine.baseUrlError("HTTPS://api.openai.com/v1"))
    }

    @Test
    fun rejectsCleartextHttpBecauseAnApiKeyTravelsOnIt() {
        assertEquals(
            "Only https:// endpoints are supported — an API key must never travel unencrypted.",
            RemoteOpenAiEngine.baseUrlError("http://api.openai.com/v1")
        )
        // Self-hosted on the LAN is the tempting case for plain http, and is rejected too.
        assertNotNull(RemoteOpenAiEngine.baseUrlError("http://192.168.1.10:8080/v1"))
    }

    @Test
    fun rejectsBlankMissingSchemeAndHostlessUrls() {
        assertNotNull(RemoteOpenAiEngine.baseUrlError(""))
        assertNotNull(RemoteOpenAiEngine.baseUrlError("   "))
        // A bare host with no scheme is the most likely user typo.
        assertNotNull(RemoteOpenAiEngine.baseUrlError("api.openai.com/v1"))
        assertNotNull(RemoteOpenAiEngine.baseUrlError("https:///v1"))
    }
}
