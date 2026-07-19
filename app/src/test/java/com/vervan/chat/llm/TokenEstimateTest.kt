package com.vervan.chat.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenEstimateTest {

    @Test
    fun asciiStaysAtCharsOverFour() {
        assertEquals(25, estimateTokens("a".repeat(100)))
    }

    @Test
    fun nonLatinCountsFarDenser() {
        // 100 Devanagari chars ≈ 67 tokens, not 25 — the whole point of the fix.
        val hindi = "न".repeat(100)
        assertTrue(estimateTokens(hindi) >= 60)
    }

    @Test
    fun emptyIsZero() {
        assertEquals(0, estimateTokens(""))
    }

    @Test
    fun truncateRespectsBudgetAndKeepsPrefix() {
        val text = "hello ".repeat(100) // ~150 tokens
        val cut = truncateToTokens(text, 20)
        assertTrue(text.startsWith(cut))
        assertTrue(estimateTokens(cut) <= 20)
        assertTrue(cut.isNotEmpty())
    }

    @Test
    fun truncateNoOpWhenUnderBudget() {
        assertEquals("short", truncateToTokens("short", 100))
    }
}
