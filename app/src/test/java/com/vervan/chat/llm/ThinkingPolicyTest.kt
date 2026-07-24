package com.vervan.chat.llm

import com.vervan.chat.data.db.entities.ModelEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingPolicyTest {

    // --- reasoningInstruction ---------------------------------------------------------------

    @Test
    fun `OFF on a non-reasoning model produces no instruction`() {
        assertEquals("", ThinkingPolicy.reasoningInstruction("OFF", ModelEngine.LITERT_LM, isReasoningModel = false))
    }

    @Test
    fun `OFF on a reasoning model actively suppresses thinking`() {
        val text = ThinkingPolicy.reasoningInstruction("OFF", ModelEngine.LITERT_LM, isReasoningModel = true)
        assertTrue(text.contains("only the final answer"))
    }

    @Test
    fun `llama_cpp appends the no_think token when OFF`() {
        val text = ThinkingPolicy.reasoningInstruction("OFF", ModelEngine.LLAMA_CPP, isReasoningModel = true)
        assertTrue(text.trim().endsWith("/no_think"))
    }

    @Test
    fun `llama_cpp appends think plus a budget hint when on`() {
        val text = ThinkingPolicy.reasoningInstruction("BALANCED", ModelEngine.LLAMA_CPP, isReasoningModel = true)
        assertTrue(text.contains("/think"))
        assertTrue(text.contains("1024"))
    }

    // --- assistantPrefillFor ----------------------------------------------------------------

    @Test
    fun `no prefill for a non-reasoning model even on llama_cpp`() {
        assertNull(ThinkingPolicy.assistantPrefillFor("BALANCED", ModelEngine.LLAMA_CPP, isReasoningModel = false))
    }

    @Test
    fun `no prefill for LiteRT (no native prefill hook)`() {
        assertNull(ThinkingPolicy.assistantPrefillFor("BALANCED", ModelEngine.LITERT_LM, isReasoningModel = true))
    }

    @Test
    fun `OFF prefill closes the think block so the model starts answering`() {
        assertEquals("<think>\n\n</think>\n\n", ThinkingPolicy.assistantPrefillFor("OFF", ModelEngine.LLAMA_CPP, isReasoningModel = true))
    }

    @Test
    fun `on-mode prefill opens the think block`() {
        assertEquals("<think>\n", ThinkingPolicy.assistantPrefillFor("DEEP", ModelEngine.LLAMA_CPP, isReasoningModel = true))
    }

    // --- reasoningBudgetFor -----------------------------------------------------------------

    @Test
    fun `budget maps effort levels for a reasoning llama_cpp model`() {
        assertEquals(256, ThinkingPolicy.reasoningBudgetFor("FAST", ModelEngine.LLAMA_CPP, true))
        assertEquals(1024, ThinkingPolicy.reasoningBudgetFor("BALANCED", ModelEngine.LLAMA_CPP, true))
        assertEquals(4096, ThinkingPolicy.reasoningBudgetFor("DEEP", ModelEngine.LLAMA_CPP, true))
    }

    @Test
    fun `budget is unlimited for OFF, LiteRT, and non-reasoning models`() {
        assertEquals(-1, ThinkingPolicy.reasoningBudgetFor("OFF", ModelEngine.LLAMA_CPP, true))
        assertEquals(-1, ThinkingPolicy.reasoningBudgetFor("DEEP", ModelEngine.LITERT_LM, true))
        assertEquals(-1, ThinkingPolicy.reasoningBudgetFor("DEEP", ModelEngine.LLAMA_CPP, false))
    }
}
