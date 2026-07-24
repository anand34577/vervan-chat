package com.vervan.chat.store

import com.vervan.chat.store.install.ArtifactFetcher
import com.vervan.chat.store.install.FetchMetadata
import com.vervan.chat.store.install.InstallOutcome
import com.vervan.chat.store.install.PermanentFetchException
import com.vervan.chat.store.install.VariantInstaller
import com.vervan.chat.store.model.Artifact
import com.vervan.chat.store.model.ArtifactRole
import com.vervan.chat.store.model.ArtifactSource
import com.vervan.chat.store.model.ModelLicense
import com.vervan.chat.store.model.ModelTask
import com.vervan.chat.store.model.ModelVariant
import com.vervan.chat.store.model.RuntimeId
import com.vervan.chat.store.model.SourceProvider
import com.vervan.chat.store.model.StoreModel
import com.vervan.chat.store.model.VariantRequirements
import com.vervan.chat.store.runtime.LlamaCppAdapter
import com.vervan.chat.store.runtime.RuntimeAdapterRegistry
import com.vervan.chat.store.storage.BlobStore
import com.vervan.chat.store.storage.InstalledManifestStore
import com.vervan.chat.store.storage.StoreMaintenance
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The all-or-nothing install invariant, plus deduplication and GC. The invariant under test
 * throughout: **a variant is installed if and only if its manifest exists** — so every failure
 * mode below is checked by asserting no manifest was written, not merely that an error came back.
 */
class VariantInstallerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var blobStore: BlobStore
    private lateinit var manifestStore: InstalledManifestStore

    /** GGUF magic, so the format probe accepts the payload. */
    private fun ggufBytes(size: Int): ByteArray =
        ByteArray(size).also { "GGUF".toByteArray().copyInto(it) }

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /** Writes exactly the bytes it was constructed with, so hashes are predictable. */
    private class FakeFetcher(
        private val payloads: Map<String, ByteArray>,
        private val failures: Map<String, Throwable> = emptyMap()
    ) : ArtifactFetcher {
        val attempted = mutableListOf<String>()

        override suspend fun fetch(
            source: ArtifactSource,
            dest: File,
            expectedBytes: Long,
            onProgress: suspend (Long) -> Unit,
            knownEtag: String?,
            knownLastModified: String?
        ): FetchMetadata {
            val url = source.toUrl()
            attempted += url
            failures[url]?.let { throw it }
            val payload = payloads[url] ?: throw PermanentFetchException("404 for $url")
            dest.parentFile?.mkdirs()
            dest.writeBytes(payload)
            onProgress(payload.size.toLong())
            return FetchMetadata(etag = null, lastModified = null)
        }
    }

    private fun source(path: String, provider: SourceProvider = SourceProvider.HUGGING_FACE) =
        ArtifactSource(provider, if (provider == SourceProvider.HUGGING_FACE) "acme/m" else "https://mirror.example", "b".repeat(40), path)

    private fun artifact(
        id: String,
        role: ArtifactRole,
        payload: ByteArray,
        sources: List<ArtifactSource>
    ) = Artifact(id, role, "$id.gguf", payload.size.toLong(), sha256(payload), sources)

    private fun variant(
        artifacts: List<Artifact>,
        runtimeConfig: Map<ArtifactRole, String>,
        capabilities: Set<ModelTask> = setOf(ModelTask.CHAT)
    ) = ModelVariant(
        variantId = "v1", version = "1", runtime = RuntimeId.LLAMA_CPP, runtimeSubtype = null,
        format = "gguf", quantization = "Q4_K_M", capabilities = capabilities,
        totalSizeBytes = artifacts.sumOf { it.sizeBytes },
        requirements = VariantRequirements(1, null, setOf("arm64-v8a"), 100, com.vervan.chat.store.model.AcceleratorRequirement.NONE),
        artifacts = artifacts, runtimeConfig = runtimeConfig, defaultContextTokens = 4096
    )

    private val model = StoreModel(
        "m1", "Model One", "Acme", "d", setOf(ModelTask.CHAT), listOf("en"),
        ModelLicense("Apache-2.0", "https://x", true, false, true, emptyList(), true, null, "lh"),
        "https://x", emptyList()
    )

    private fun installer(fetcher: ArtifactFetcher, usableSpace: Long = Long.MAX_VALUE) =
        VariantInstaller(
            blobStore, manifestStore, fetcher,
            RuntimeAdapterRegistry(listOf(LlamaCppAdapter())),
            safetyMarginBytes = 0L,
            usableSpaceProvider = { usableSpace }
        )

    @Before
    fun setUp() {
        blobStore = BlobStore(temp.newFolder())
        manifestStore = InstalledManifestStore(blobStore)
    }

    // --- Happy path ----------------------------------------------------------------------------

    @Test
    fun `successful install writes a manifest resolving roles to blobs`() = runBlocking {
        val weights = ggufBytes(64)
        val a = artifact("w", ArtifactRole.WEIGHTS, weights, listOf(source("model.gguf")))
        val v = variant(listOf(a), mapOf(ArtifactRole.WEIGHTS to "w"))
        val fetcher = FakeFetcher(mapOf(source("model.gguf").toUrl() to weights))

        val outcome = installer(fetcher).install(model, v, catalogVersion = 42, acceptedLicenseHash = "lh")

        assertTrue("expected success, got $outcome", outcome is InstallOutcome.Success)
        val record = manifestStore.read("v1")
        assertNotNull(record)
        assertEquals(42, record!!.catalogVersion)
        assertEquals("lh", record.acceptedLicenseHash)
        val resolved = manifestStore.resolve(record)!!
        assertTrue(File(resolved.roleToPath[ArtifactRole.WEIGHTS]!!).isFile)
    }

    // --- All-or-nothing ------------------------------------------------------------------------

    @Test
    fun `a failing second artifact leaves no manifest`() = runBlocking {
        val weights = ggufBytes(64)
        val projector = ggufBytes(32)
        val a1 = artifact("w", ArtifactRole.WEIGHTS, weights, listOf(source("model.gguf")))
        val a2 = artifact("p", ArtifactRole.MULTIMODAL_PROJECTOR, projector, listOf(source("mmproj.gguf")))
        val v = variant(
            listOf(a1, a2),
            mapOf(ArtifactRole.WEIGHTS to "w", ArtifactRole.MULTIMODAL_PROJECTOR to "p"),
            capabilities = setOf(ModelTask.CHAT, ModelTask.VISION)
        )
        // Only the weights are downloadable; the projector 404s from every source.
        val fetcher = FakeFetcher(mapOf(source("model.gguf").toUrl() to weights))

        val outcome = installer(fetcher).install(model, v, 42, "lh")

        assertTrue(outcome is InstallOutcome.Failed)
        assertNull("a partial install must never leave a manifest", manifestStore.read("v1"))
        // The verified sibling is kept as a reusable blob rather than thrown away.
        assertTrue("verified blob should survive for retry", blobStore.contains(a1.sha256))
    }

    @Test
    fun `checksum mismatch is rejected and never becomes a blob`() = runBlocking {
        val declared = ggufBytes(64)
        val served = ggufBytes(64).also { it[10] = 0x7F } // different bytes, same length
        val a = artifact("w", ArtifactRole.WEIGHTS, declared, listOf(source("model.gguf")))
        val v = variant(listOf(a), mapOf(ArtifactRole.WEIGHTS to "w"))
        val fetcher = FakeFetcher(mapOf(source("model.gguf").toUrl() to served))

        val outcome = installer(fetcher).install(model, v, 42, "lh")

        assertTrue(outcome is InstallOutcome.Failed)
        assertTrue("checksum failure must be permanent", (outcome as InstallOutcome.Failed).permanent)
        assertFalse(blobStore.contains(a.sha256))
        assertNull(manifestStore.read("v1"))
    }

    @Test
    fun `wrong size is rejected even before hashing`() = runBlocking {
        val declared = ggufBytes(64)
        val a = artifact("w", ArtifactRole.WEIGHTS, declared, listOf(source("model.gguf")))
        val v = variant(listOf(a), mapOf(ArtifactRole.WEIGHTS to "w"))
        val fetcher = FakeFetcher(mapOf(source("model.gguf").toUrl() to ggufBytes(32)))

        assertTrue(installer(fetcher).install(model, v, 42, "lh") is InstallOutcome.Failed)
        assertNull(manifestStore.read("v1"))
    }

    @Test
    fun `a git-lfs pointer served instead of weights is rejected by the format probe`() = runBlocking {
        // The classic mis-pinned-path failure: HF serves a small text pointer, and it hashes and
        // sizes correctly because the catalogue was built from that same wrong response.
        val pointer = "version https://git-lfs.github.com/spec/v1\noid sha256:abc\nsize 123\n".toByteArray()
        val a = artifact("w", ArtifactRole.WEIGHTS, pointer, listOf(source("model.gguf")))
        val v = variant(listOf(a), mapOf(ArtifactRole.WEIGHTS to "w"))
        val fetcher = FakeFetcher(mapOf(source("model.gguf").toUrl() to pointer))

        val outcome = installer(fetcher).install(model, v, 42, "lh")

        assertTrue("LFS pointer must not install as weights", outcome is InstallOutcome.Failed)
        assertNull(manifestStore.read("v1"))
    }

    @Test
    fun `vision variant missing a projector is rejected before any download`() = runBlocking {
        val weights = ggufBytes(64)
        val a = artifact("w", ArtifactRole.WEIGHTS, weights, listOf(source("model.gguf")))
        val v = variant(
            listOf(a), mapOf(ArtifactRole.WEIGHTS to "w"),
            capabilities = setOf(ModelTask.CHAT, ModelTask.VISION)
        )
        val fetcher = FakeFetcher(mapOf(source("model.gguf").toUrl() to weights))

        val outcome = installer(fetcher).install(model, v, 42, "lh")

        assertTrue(outcome is InstallOutcome.Failed)
        assertTrue("must fail before spending bandwidth", fetcher.attempted.isEmpty())
    }

    @Test
    fun `insufficient disk space fails before downloading`() = runBlocking {
        val weights = ggufBytes(64)
        val a = artifact("w", ArtifactRole.WEIGHTS, weights, listOf(source("model.gguf")))
        val v = variant(listOf(a), mapOf(ArtifactRole.WEIGHTS to "w"))
        val fetcher = FakeFetcher(mapOf(source("model.gguf").toUrl() to weights))

        val outcome = installer(fetcher, usableSpace = 10L).install(model, v, 42, "lh")

        assertTrue(outcome is InstallOutcome.Failed)
        assertTrue(fetcher.attempted.isEmpty())
    }

    // --- Failover & dedup ----------------------------------------------------------------------

    @Test
    fun `falls over to a mirror when the primary source fails`() = runBlocking {
        val weights = ggufBytes(64)
        val primary = source("model.gguf")
        val mirror = source("model.gguf", SourceProvider.MIRROR)
        val a = artifact("w", ArtifactRole.WEIGHTS, weights, listOf(primary, mirror))
        val v = variant(listOf(a), mapOf(ArtifactRole.WEIGHTS to "w"))
        val fetcher = FakeFetcher(
            payloads = mapOf(mirror.toUrl() to weights),
            failures = mapOf(primary.toUrl() to PermanentFetchException("410 gone"))
        )

        assertTrue(installer(fetcher).install(model, v, 42, "lh") is InstallOutcome.Success)
        assertEquals(listOf(primary.toUrl(), mirror.toUrl()), fetcher.attempted)
    }

    @Test
    fun `a mirror serving different content is still rejected`() = runBlocking {
        // Failover is only safe because of the hash gate — prove the gate actually holds.
        val declared = ggufBytes(64)
        val primary = source("model.gguf")
        val mirror = source("model.gguf", SourceProvider.MIRROR)
        val a = artifact("w", ArtifactRole.WEIGHTS, declared, listOf(primary, mirror))
        val v = variant(listOf(a), mapOf(ArtifactRole.WEIGHTS to "w"))
        val fetcher = FakeFetcher(
            payloads = mapOf(mirror.toUrl() to ggufBytes(64).also { it[20] = 0x66 }),
            failures = mapOf(primary.toUrl() to PermanentFetchException("410 gone"))
        )

        assertTrue(installer(fetcher).install(model, v, 42, "lh") is InstallOutcome.Failed)
        assertNull(manifestStore.read("v1"))
    }

    @Test
    fun `an already-present blob is not downloaded again`() = runBlocking {
        val weights = ggufBytes(64)
        val a = artifact("w", ArtifactRole.WEIGHTS, weights, listOf(source("model.gguf")))
        val v = variant(listOf(a), mapOf(ArtifactRole.WEIGHTS to "w"))

        // First install populates the blob store.
        installer(FakeFetcher(mapOf(source("model.gguf").toUrl() to weights))).install(model, v, 42, "lh")
        manifestStore.uninstall("v1")

        // Second install must reuse the blob and touch the network zero times.
        val secondFetcher = FakeFetcher(emptyMap())
        val outcome = installer(secondFetcher).install(model, v, 43, "lh")

        assertTrue(outcome is InstallOutcome.Success)
        assertTrue("shared blob must be reused, not re-fetched", secondFetcher.attempted.isEmpty())
    }

    // --- GC ------------------------------------------------------------------------------------

    @Test
    fun `gc reclaims orphaned blobs but keeps referenced ones`() = runBlocking {
        val weights = ggufBytes(64)
        val a = artifact("w", ArtifactRole.WEIGHTS, weights, listOf(source("model.gguf")))
        val v = variant(listOf(a), mapOf(ArtifactRole.WEIGHTS to "w"))
        installer(FakeFetcher(mapOf(source("model.gguf").toUrl() to weights))).install(model, v, 42, "lh")

        val maintenance = StoreMaintenance(blobStore, manifestStore)

        // Still installed: nothing to collect.
        assertEquals(0L, maintenance.reconcileAndCollect().reclaimedBytes)
        assertTrue(blobStore.contains(a.sha256))

        // Uninstalled: the blob is now a true orphan.
        manifestStore.uninstall("v1")
        assertTrue(maintenance.reconcileAndCollect().reclaimedBytes > 0)
        assertFalse(blobStore.contains(a.sha256))
    }

    @Test
    fun `gc keeps a blob shared by a second installed variant`() = runBlocking {
        val weights = ggufBytes(64)
        val a = artifact("w", ArtifactRole.WEIGHTS, weights, listOf(source("model.gguf")))
        val fetcher = FakeFetcher(mapOf(source("model.gguf").toUrl() to weights))

        installer(fetcher).install(model, variant(listOf(a), mapOf(ArtifactRole.WEIGHTS to "w")), 42, "lh")
        val second = variant(listOf(a), mapOf(ArtifactRole.WEIGHTS to "w")).copy(variantId = "v2")
        installer(fetcher).install(model, second, 42, "lh")

        manifestStore.uninstall("v1")
        val maintenance = StoreMaintenance(blobStore, manifestStore)
        maintenance.reconcileAndCollect()

        assertTrue("blob still referenced by v2 must survive", blobStore.contains(a.sha256))
    }

    @Test
    fun `spot check deletes a corrupted blob and reports the affected variant`() = runBlocking {
        val weights = ggufBytes(64)
        val a = artifact("w", ArtifactRole.WEIGHTS, weights, listOf(source("model.gguf")))
        val v = variant(listOf(a), mapOf(ArtifactRole.WEIGHTS to "w"))
        installer(FakeFetcher(mapOf(source("model.gguf").toUrl() to weights))).install(model, v, 42, "lh")

        // Simulate on-disk corruption after a successful install.
        File(blobStore.pathFor(a.sha256)!!).writeBytes(ggufBytes(64).also { it[30] = 0x11 })

        val broken = StoreMaintenance(blobStore, manifestStore).spotCheckIntegrity(sampleSize = 10)

        assertEquals(setOf("v1"), broken)
        assertFalse(blobStore.contains(a.sha256))
        // The manifest is intentionally left alone so the UI can offer a re-download rather than
        // silently losing the user's model.
        assertNotNull(manifestStore.read("v1"))
        assertNull("a variant with a missing blob must not resolve", manifestStore.resolve(manifestStore.read("v1")!!))
    }
}
