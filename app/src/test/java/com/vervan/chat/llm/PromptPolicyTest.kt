package com.vervan.chat.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptPolicyTest {
    @Test
    fun corePromptDoesNotPrimeNormalChatsWithDiagramTerms() {
        assertFalse(PromptPolicy.CORE_SYSTEM.contains("mermaid", ignoreCase = true))
        assertFalse(PromptPolicy.CORE_SYSTEM.contains("diagram", ignoreCase = true))
    }

    @Test
    fun clarificationIsAlwaysAvailableAsAMandatoryModule() {
        assertTrue(PromptPolicy.CLARIFICATION.contains("<clarify>"))
        assertTrue(PromptPolicy.CLARIFICATION.contains("options"))
    }

    @Test
    fun formattingIsOnlyAddedForExplicitRequests() {
        assertTrue(PromptPolicy.formattingInstructions("Hi").isBlank())
        assertTrue(PromptPolicy.formattingInstructions("Create a flowchart of this process").contains("Mermaid"))
        assertTrue(PromptPolicy.formattingInstructions("Write the equation in LaTeX").contains("LaTeX"))
    }
}
