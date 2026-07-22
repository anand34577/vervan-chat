package com.vervan.chat.store

import com.vervan.chat.store.license.LicenseAcceptanceStore
import com.vervan.chat.store.model.ModelLicense
import com.vervan.chat.store.model.ModelTask
import com.vervan.chat.store.model.StoreModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Licence acceptance (spec §11). The rule with real legal weight is the **re-prompt on change**:
 * acceptance is keyed on the licence's content hash, not on the model id, so a catalogue update
 * that alters the licence cannot leave a user silently bound to terms they never saw.
 */
class LicenseAcceptanceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = LicenseAcceptanceStore(File(temp.newFolder(), "acceptances.json"))

    private fun model(licenseHash: String, name: String = "Llama-3-Community") = StoreModel(
        modelId = "m1", displayName = "M", publisher = "Acme", description = "",
        tasks = setOf(ModelTask.CHAT), languages = listOf("en"),
        license = ModelLicense(
            name = name, url = "https://x", redistributionPermitted = true, gated = false,
            commercialUseAllowed = true, acceptableUseRestrictions = emptyList(),
            attributionRequired = true, usageThresholdClause = null, acceptanceHash = licenseHash
        ),
        modelCardUrl = "https://x", variants = emptyList()
    )

    @Test
    fun `nothing is accepted by default`() {
        assertFalse(store().isAccepted(model("h1")))
    }

    @Test
    fun `acceptance persists across store instances`() {
        val file = File(temp.newFolder(), "acceptances.json")
        LicenseAcceptanceStore(file).accept(model("h1"), catalogVersion = 42)
        assertTrue(LicenseAcceptanceStore(file).isAccepted(model("h1")))
    }

    @Test
    fun `a changed licence hash re-prompts`() {
        val s = store()
        s.accept(model("h1"), catalogVersion = 42)
        assertTrue(s.isAccepted(model("h1")))
        // Catalogue v43 ships a revised licence for the same model.
        assertFalse("a revised licence must re-prompt", s.isAccepted(model("h2")))
    }

    @Test
    fun `accepting a revised licence keeps the earlier acceptance on record`() {
        val s = store()
        s.accept(model("h1"), catalogVersion = 42)
        s.accept(model("h2"), catalogVersion = 43)

        // Both remain: that the user once agreed to the older terms is part of the audit trail,
        // not something a later acceptance should erase.
        assertEquals(2, s.readAll().size)
        assertTrue(s.isAccepted(model("h1")))
        assertTrue(s.isAccepted(model("h2")))
    }

    @Test
    fun `re-accepting the same licence does not duplicate the record`() {
        val s = store()
        s.accept(model("h1"), catalogVersion = 42)
        s.accept(model("h1"), catalogVersion = 43)
        assertEquals(1, s.readAll().size)
        // The record reflects the most recent acceptance's catalogue version.
        assertEquals(43, s.readAll().first().catalogVersion)
    }

    @Test
    fun `acceptance records the catalogue version and a timestamp`() {
        val s = store()
        s.accept(model("h1"), catalogVersion = 42)
        val record = s.readAll().single()
        assertEquals("m1", record.modelId)
        assertEquals(42, record.catalogVersion)
        assertEquals("Llama-3-Community", record.licenseName)
        assertTrue("a timestamp is required for the audit trail", record.acceptedAt > 0)
    }

    @Test
    fun `a corrupt acceptance file reads as nothing accepted rather than everything accepted`() {
        val file = File(temp.newFolder(), "acceptances.json")
        LicenseAcceptanceStore(file).accept(model("h1"), catalogVersion = 42)
        file.writeText("{not json")

        // Failing open here would silently skip the licence gate for every model.
        assertFalse(LicenseAcceptanceStore(file).isAccepted(model("h1")))
    }

    @Test
    fun `acceptances are per model`() {
        val s = store()
        s.accept(model("h1"), catalogVersion = 42)
        val other = model("h1").copy(modelId = "m2")
        assertFalse("a different model must not inherit acceptance", s.isAccepted(other))
    }
}
