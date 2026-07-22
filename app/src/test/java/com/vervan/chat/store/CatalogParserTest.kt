package com.vervan.chat.store

import com.vervan.chat.store.catalog.CatalogParser
import com.vervan.chat.store.catalog.CatalogRejectedException
import com.vervan.chat.store.model.ArtifactRole
import com.vervan.chat.store.model.RuntimeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the parser's security-critical rejections. Every test here maps to a numbered
 * non-negotiable in the store spec §14 — these are the rules that, if they silently stop holding,
 * let a bad catalogue install something the signature never covered.
 */
class CatalogParserTest {

    private val parser = CatalogParser(
        supportedSchemaVersions = setOf(1),
        appVersionCode = 10,
        availableRuntimes = setOf(RuntimeId.LLAMA_CPP, RuntimeId.WHISPER_CPP, RuntimeId.SHERPA_ONNX)
    )

    private val validSha = "a".repeat(64)
    private val validCommit = "b".repeat(40)

    private fun catalog(
        models: String,
        schemaVersion: Int = 1,
        catalogVersion: Int = 42
    ) = """
        {"schemaVersion":$schemaVersion,"catalogVersion":$catalogVersion,
         "generatedAt":1700000000,"models":[$models]}
    """.trimIndent()

    private fun artifact(
        id: String = "w",
        role: String = "weights",
        sha: String = validSha,
        revision: String = validCommit,
        installPath: String = "model.gguf",
        size: Long = 1000
    ) = """
        {"artifactId":"$id","role":"$role","installPath":"$installPath","sizeBytes":$size,
         "sha256":"$sha","sources":[{"provider":"huggingface","repository":"acme/model",
         "revision":"$revision","path":"model.gguf"}]}
    """.trimIndent()

    private fun model(
        variantExtras: String = "",
        artifacts: String = artifact(),
        runtimeConfig: String = """{"weights":"w"}""",
        runtime: String = "llama.cpp",
        capabilities: String = """["chat"]"""
    ) = """
        {"modelId":"m1","displayName":"Model One","publisher":"Acme",
         "description":"d","tasks":["chat"],"languages":["en"],
         "license":{"name":"Apache-2.0","url":"https://x","acceptanceHash":"h"},
         "modelCardUrl":"https://x","variants":[
           {"variantId":"v1","version":"1","runtime":"$runtime","format":"gguf",
            "capabilities":$capabilities,"totalSizeBytes":1000,
            "requirements":{"minAppVersion":1,"supportedAbis":["arm64-v8a"],
                            "estimatedMinRamBytes":100,"acceleratorRequirement":"none"},
            "artifacts":[$artifacts],"runtimeConfig":$runtimeConfig $variantExtras}]}
    """.trimIndent()

    // --- Whole-catalogue rejections (fail closed) ---------------------------------------------

    @Test(expected = CatalogRejectedException::class)
    fun `unknown schema version is rejected outright`() {
        parser.parse(catalog(model(), schemaVersion = 99))
    }

    @Test(expected = CatalogRejectedException::class)
    fun `malformed json is rejected`() {
        parser.parse("{not json")
    }

    @Test(expected = CatalogRejectedException::class)
    fun `missing models array is rejected`() {
        parser.parse("""{"schemaVersion":1,"catalogVersion":1,"generatedAt":0}""")
    }

    // --- Per-variant rejections (catalogue survives) ------------------------------------------

    @Test
    fun `branch revision is rejected but catalogue survives`() {
        val result = parser.parse(catalog(model(artifacts = artifact(revision = "main"))))
        // The model had exactly one variant, so dropping it drops the model — but we still got a
        // catalogue object back rather than an exception.
        assertEquals(42, result.catalogVersion)
        assertTrue("variant pinned to a branch must be dropped", result.models.isEmpty())
    }

    @Test
    fun `short commit sha is rejected`() {
        val result = parser.parse(catalog(model(artifacts = artifact(revision = "b".repeat(7)))))
        assertTrue(result.models.isEmpty())
    }

    @Test
    fun `malformed sha256 is rejected`() {
        val result = parser.parse(catalog(model(artifacts = artifact(sha = "abc"))))
        assertTrue(result.models.isEmpty())
    }

    @Test
    fun `path traversal in installPath is rejected`() {
        val result = parser.parse(catalog(model(artifacts = artifact(installPath = "../../etc/passwd"))))
        assertTrue(result.models.isEmpty())
    }

    @Test
    fun `absolute installPath is rejected`() {
        val result = parser.parse(catalog(model(artifacts = artifact(installPath = "/data/x.gguf"))))
        assertTrue(result.models.isEmpty())
    }

    @Test
    fun `executable artifact is rejected regardless of declared role`() {
        // Declares itself as innocuous weights; the extension is what disqualifies it.
        val result = parser.parse(catalog(model(artifacts = artifact(installPath = "libevil.so"))))
        assertTrue(result.models.isEmpty())
    }

    @Test
    fun `runtime with no adapter in this build is dropped`() {
        val result = parser.parse(catalog(model(runtime = "litert-lm")))
        assertTrue(result.models.isEmpty())
    }

    @Test
    fun `runtimeConfig pointing at a missing artifact is rejected`() {
        val result = parser.parse(catalog(model(runtimeConfig = """{"weights":"nope"}""")))
        assertTrue(result.models.isEmpty())
    }

    @Test
    fun `sherpa-onnx without a subtype is rejected`() {
        val result = parser.parse(catalog(model(runtime = "sherpa-onnx")))
        assertTrue(result.models.isEmpty())
    }

    @Test
    fun `variant needing a newer app version is dropped`() {
        val json = catalog(model()).replace("\"minAppVersion\":1", "\"minAppVersion\":999")
        assertTrue(parser.parse(json).models.isEmpty())
    }

    // --- Happy path & isolation ----------------------------------------------------------------

    @Test
    fun `well formed variant parses with roles resolved`() {
        val result = parser.parse(catalog(model()))
        val (model, variant) = result.variant("v1")!!
        assertEquals("m1", model.modelId)
        assertEquals(RuntimeId.LLAMA_CPP, variant.runtime)
        assertNotNull(variant.artifactFor(ArtifactRole.WEIGHTS))
        assertNull(variant.artifactFor(ArtifactRole.MULTIMODAL_PROJECTOR))
        assertEquals(
            "https://huggingface.co/acme/model/resolve/$validCommit/model.gguf",
            variant.artifactFor(ArtifactRole.WEIGHTS)!!.sources.first().toUrl()
        )
    }

    @Test
    fun `one bad model does not take down the rest of the catalogue`() {
        val bad = model(artifacts = artifact(revision = "main")).replace("\"m1\"", "\"bad\"")
        val result = parser.parse(catalog("$bad,${model()}"))
        assertEquals(1, result.models.size)
        assertEquals("m1", result.models.first().modelId)
    }

    @Test
    fun `license defaults are conservative when the block is absent`() {
        val noLicense = model().replace(
            """"license":{"name":"Apache-2.0","url":"https://x","acceptanceHash":"h"},""", ""
        )
        val license = parser.parse(catalog(noLicense)).models.first().license
        // An unreviewed entry must never read as "redistribution permitted".
        assertTrue(!license.redistributionPermitted)
        assertTrue(!license.commercialUseAllowed)
        assertTrue(license.attributionRequired)
    }
}
