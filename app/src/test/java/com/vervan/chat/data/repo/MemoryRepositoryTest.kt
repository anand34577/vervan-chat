package com.vervan.chat.data.repo

import com.vervan.chat.data.db.entities.Memory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRepositoryTest {
    @Test
    fun semanticRecallRanksRelevantMemoryAndHonorsLimit() {
        val relevant = Memory(id = "relevant", text = "Prefers Kotlin for Android apps")
        val unrelated = Memory(id = "unrelated", text = "Favorite dessert is tiramisu")

        val ranked = rankMemoryCandidates(
            query = "Which language should I use for Android?",
            queryVector = floatArrayOf(1f, 0f),
            candidates = listOf(unrelated, relevant),
            vectors = mapOf(
                relevant.id to floatArrayOf(1f, 0f),
                unrelated.id to floatArrayOf(0f, 1f)
            ),
            topK = 1
        )

        assertEquals(listOf("relevant"), ranked.map { it.memory.id })
        assertTrue(ranked.single().score > 1f)
    }

    @Test
    fun textFallbackPrefersMatchingMemory() {
        val language = Memory(id = "language", text = "Prefers Kotlin for Android apps")
        val dessert = Memory(id = "dessert", text = "Favorite dessert is tiramisu")

        val ranked = rankTextMemoryCandidates("Android language", listOf(dessert, language), topK = 1)

        assertEquals("language", ranked.single().memory.id)
    }
}
