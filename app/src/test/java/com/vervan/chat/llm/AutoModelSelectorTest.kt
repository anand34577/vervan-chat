package com.vervan.chat.llm

import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoModelSelectorTest {

    private fun model(
        name: String,
        sizeBytes: Long,
        supportsVision: Boolean? = null,
        supportsAudio: Boolean? = null
    ) = ModelInfo(
        displayName = name,
        // Doesn't exist on disk in a unit test, so AutoModelSelector's File(filePath).isFile
        // check fails and it falls back to fileSizeBytes — exactly what this test wants.
        filePath = "/nonexistent/$name.gguf",
        fileSizeBytes = sizeBytes,
        sha256 = "",
        role = ModelRole.GENERATION,
        supportsVision = supportsVision,
        supportsAudio = supportsAudio
    )

    private val small = model("small", 1_000_000_000L)
    private val medium = model("medium", 4_000_000_000L)
    private val large = model("large", 13_000_000_000L)

    @Test
    fun `empty candidates returns null`() {
        assertNull(AutoModelSelector.select(emptyList(), ModelProfileType.BALANCED))
    }

    @Test
    fun `single candidate is returned regardless of profile`() {
        assertEquals(small, AutoModelSelector.select(listOf(small), ModelProfileType.QUALITY))
    }

    @Test
    fun `FAST picks the smallest installed model`() {
        val picked = AutoModelSelector.select(listOf(small, medium, large), ModelProfileType.FAST)
        assertEquals(small, picked)
    }

    @Test
    fun `QUALITY picks the largest installed model`() {
        val picked = AutoModelSelector.select(listOf(small, medium, large), ModelProfileType.QUALITY)
        assertEquals(large, picked)
    }

    @Test
    fun `BATTERY_SAVER and THERMAL_SAFE also pick the smallest`() {
        assertEquals(small, AutoModelSelector.select(listOf(small, medium, large), ModelProfileType.BATTERY_SAVER))
        assertEquals(small, AutoModelSelector.select(listOf(small, medium, large), ModelProfileType.THERMAL_SAFE))
    }

    @Test
    fun `BALANCED picks a middle-sized model`() {
        val picked = AutoModelSelector.select(listOf(small, medium, large), ModelProfileType.BALANCED)
        assertEquals(medium, picked)
    }

    @Test
    fun `a model proven not to support vision is excluded when the turn needs vision`() {
        val noVision = model("no-vision", 2_000_000_000L, supportsVision = false)
        val hasVision = model("has-vision", 5_000_000_000L, supportsVision = true)
        val picked = AutoModelSelector.select(listOf(noVision, hasVision), ModelProfileType.FAST, needsVision = true)
        assertEquals(hasVision, picked)
    }

    @Test
    fun `an untested (null) capability is treated as eligible, not excluded`() {
        val untested = model("untested", 2_000_000_000L, supportsVision = null)
        val picked = AutoModelSelector.select(listOf(untested), ModelProfileType.FAST, needsVision = true)
        assertEquals(untested, picked)
    }

    @Test
    fun `degrades gracefully to the best model when nothing meets the modality requirement`() {
        val noVisionSmall = model("a", 1_000_000_000L, supportsVision = false)
        val noVisionLarge = model("b", 8_000_000_000L, supportsVision = false)
        val picked = AutoModelSelector.select(listOf(noVisionSmall, noVisionLarge), ModelProfileType.QUALITY, needsVision = true)
        assertEquals(noVisionLarge, picked)
    }
}
