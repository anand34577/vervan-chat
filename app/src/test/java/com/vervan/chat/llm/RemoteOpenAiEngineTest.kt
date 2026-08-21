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
    fun acceptsCleartextHttpOnlyForLoopbackAndEmulatorHosts() {
        // Android permits cleartext only for services that stay on the same device or the emulator
        // host. A LAN provider must expose HTTPS so the API key cannot be sniffed in transit.
        assertNull(RemoteOpenAiEngine.baseUrlError("HTTP://localhost:11434/v1"))
        assertNull(RemoteOpenAiEngine.baseUrlError("http://127.0.0.1:1234/v1"))
        assertNull(RemoteOpenAiEngine.baseUrlError("http://10.0.2.2:1234/v1"))
        assertNotNull(RemoteOpenAiEngine.baseUrlError("http://192.168.1.10:8080/v1"))
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

    @Test
    fun rejectsCredentialQueryFragmentAndParentPathComponents() {
        assertNotNull(RemoteOpenAiEngine.baseUrlError("https://user:secret@example.com/v1"))
        assertNotNull(RemoteOpenAiEngine.baseUrlError("https://example.com/v1?api_key=secret"))
        assertNotNull(RemoteOpenAiEngine.baseUrlError("https://example.com/v1#fragment"))
        assertNotNull(RemoteOpenAiEngine.baseUrlError("https://example.com/v1/../admin"))
        assertNotNull(RemoteOpenAiEngine.baseUrlError("https://example.com:70000/v1"))
    }
}
