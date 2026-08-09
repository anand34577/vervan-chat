package com.vervan.chat.llm

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
    fun acceptsCleartextHttpForSelfHostedEndpoints() {
        // A server on the user's own LAN has no certificate to present; the dialog warns about the
        // key travelling unencrypted rather than refusing the URL outright.
        assertNull(RemoteOpenAiEngine.baseUrlError("http://192.168.1.10:8080/v1"))
        assertNull(RemoteOpenAiEngine.baseUrlError("HTTP://localhost:11434/v1"))
    }

    @Test
    fun rejectsSchemesThatArentHttp() {
        assertNotNull(RemoteOpenAiEngine.baseUrlError("ftp://example.com/v1"))
        assertNotNull(RemoteOpenAiEngine.baseUrlError("file:///data/v1"))
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
