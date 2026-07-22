package com.vervan.chat.store

import com.vervan.chat.store.catalog.CatalogSignatureVerifier
import com.vervan.chat.store.catalog.CatalogStore
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The two defences that stand between a network attacker and an arbitrary model install:
 * signature verification, and the monotonic version watermark. Both are exercised against real
 * primitives here — a real P-256 keypair, real files — rather than mocks, because a mock would
 * happily "verify" whatever the test told it to.
 */
class CatalogTrustTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun keyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun sign(privateKey: java.security.PrivateKey, payload: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(payload)
            sign()
        }

    // --- Signature verification ----------------------------------------------------------------

    @Test
    fun `genuine signature verifies`() {
        val keys = keyPair()
        val payload = """{"catalogVersion":1}""".toByteArray()
        val verifier = CatalogSignatureVerifier(listOf(keys.public))
        assertTrue(verifier.verify(payload, sign(keys.private, payload)))
    }

    @Test
    fun `signature from a different key is rejected`() {
        val trusted = keyPair()
        val attacker = keyPair()
        val payload = """{"catalogVersion":1}""".toByteArray()
        val verifier = CatalogSignatureVerifier(listOf(trusted.public))
        assertFalse(verifier.verify(payload, sign(attacker.private, payload)))
    }

    @Test
    fun `tampered payload is rejected under a genuine signature`() {
        val keys = keyPair()
        val original = """{"catalogVersion":1}""".toByteArray()
        val signature = sign(keys.private, original)
        val tampered = """{"catalogVersion":9}""".toByteArray()
        val verifier = CatalogSignatureVerifier(listOf(keys.public))
        assertFalse(verifier.verify(tampered, signature))
    }

    @Test
    fun `garbage signature bytes are rejected without throwing`() {
        val keys = keyPair()
        val payload = "x".toByteArray()
        val verifier = CatalogSignatureVerifier(listOf(keys.public))
        assertFalse(verifier.verify(payload, ByteArray(16) { 0x41 }))
    }

    @Test
    fun `a build with no trusted keys rejects everything`() {
        // The "someone stripped the key constant" case must fail closed, not open.
        val keys = keyPair()
        val payload = "x".toByteArray()
        assertFalse(CatalogSignatureVerifier(emptyList()).verify(payload, sign(keys.private, payload)))
    }

    @Test
    fun `during rotation both the old and new key are accepted`() {
        val old = keyPair()
        val new = keyPair()
        val payload = """{"catalogVersion":7}""".toByteArray()
        val verifier = CatalogSignatureVerifier(listOf(old.public, new.public))
        assertTrue("old key must keep working mid-rotation", verifier.verify(payload, sign(old.private, payload)))
        assertTrue("new key must already work mid-rotation", verifier.verify(payload, sign(new.private, payload)))
    }

    // --- Rollback watermark --------------------------------------------------------------------

    @Test
    fun `watermark starts at zero and rises on commit`() {
        val store = CatalogStore(temp.newFolder())
        assertEquals(0, store.highestAcceptedVersion())
        store.commit("""{"catalogVersion":42}""", 42)
        assertEquals(42, store.highestAcceptedVersion())
    }

    @Test
    fun `watermark never falls even if an older version is committed`() {
        val store = CatalogStore(temp.newFolder())
        store.commit("""{"catalogVersion":42}""", 42)
        // CatalogRepository refuses this before it gets here, but the store is the last line of
        // defence and must not lower the watermark on its own.
        store.commit("""{"catalogVersion":7}""", 7)
        assertEquals(42, store.highestAcceptedVersion())
    }

    @Test
    fun `watermark survives the catalogue file being deleted`() {
        val root = temp.newFolder()
        val store = CatalogStore(root)
        store.commit("""{"catalogVersion":42}""", 42)
        java.io.File(root, "catalog.json").delete()

        // A replayed v41 must still be refused even with no catalogue on disk, which is why the
        // watermark lives in its own file rather than being read back out of the catalogue.
        assertEquals(42, CatalogStore(root).highestAcceptedVersion())
    }

    @Test
    fun `commit round-trips the catalogue body`() {
        val store = CatalogStore(temp.newFolder())
        val body = """{"catalogVersion":42,"models":[]}"""
        store.commit(body, 42)
        assertEquals(body, store.readCatalogJson())
    }

    @Test
    fun `corrupt meta file degrades to never-accepted rather than throwing`() {
        val root = temp.newFolder()
        CatalogStore(root).commit("""{"catalogVersion":42}""", 42)
        java.io.File(root, "catalog-meta.json").writeText("{not json")
        assertEquals(0, CatalogStore(root).highestAcceptedVersion())
    }

    @Test
    fun `recordSyncAttempt does not disturb the watermark`() {
        val store = CatalogStore(temp.newFolder())
        store.commit("""{"catalogVersion":42}""", 42)
        store.recordSyncAttempt()
        assertEquals(42, store.highestAcceptedVersion())
        assertTrue(store.lastSyncAt() > 0)
    }
}
