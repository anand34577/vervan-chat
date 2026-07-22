package com.vervan.chat.ui.onboarding

import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.modeldownload.CatalogModel
import com.vervan.chat.modeldownload.ModelFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GB = 1024L * 1024 * 1024

class ModelRecommendationTest {

    private fun gen(
        id: String,
        sizeBytes: Long,
        minRam: Long? = null,
        enabled: Boolean = true,
        category: ModelRole = ModelRole.GENERATION
    ) = CatalogModel(
        modelId = id, version = "1", displayName = id, description = "",
        category = category, format = ModelFormat.LITERTLM, files = emptyList(),
        totalExpectedBytes = sizeBytes, minimumRamBytes = minRam, enabled = enabled,
        sourceUrl = "https://example.com"
    )

    @Test
    fun noGenerationModelReturnsNull() {
        // Only an embedding model present → nothing to recommend.
        assertNull(recommendModel(8 * GB, listOf(gen("emb", GB, category = ModelRole.EMBEDDING))))
    }

    @Test
    fun picksLargestThatFits() {
        val small = gen("small", 1 * GB)   // needs ~1.3 GB
        val large = gen("large", 4 * GB)   // needs ~5.2 GB
        val rec = recommendModel(6 * GB, listOf(small, large))!!
        assertEquals("large", rec.model.modelId)
        assertTrue(rec.fits)
    }

    @Test
    fun fallsBackToSmallestWhenNoneFitAndFlagsTight() {
        val small = gen("small", 4 * GB)   // needs ~5.2 GB
        val large = gen("large", 8 * GB)   // needs ~10.4 GB
        val rec = recommendModel(3 * GB, listOf(small, large))!!
        assertEquals("small", rec.model.modelId)
        assertFalse(rec.fits)
    }

    @Test
    fun honorsDeclaredMinimumRamOverSizeEstimate() {
        // Small download but a declared 12 GB floor → does not fit an 8 GB device.
        val rec = recommendModel(8 * GB, listOf(gen("hungry", 1 * GB, minRam = 12 * GB)))!!
        assertFalse(rec.fits)
    }

    @Test
    fun skipsDisabledModels() {
        val rec = recommendModel(8 * GB, listOf(gen("off", 2 * GB, enabled = false)))
        assertNull(rec)
    }
}
