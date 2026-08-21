package com.vervan.chat.llm

import com.vervan.chat.data.db.entities.ModelEngine
import com.vervan.chat.data.db.entities.ModelInfo
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

    // --- effectiveThinkingMode ---------------------------------------------------------------

    @Test
    fun `chat override wins over model default`() {
        assertEquals("DEEP", ThinkingPolicy.effectiveThinkingMode("DEEP", "FAST", supportsThinking = true))
    }

    @Test
    fun `falls back to model default when chat has no override`() {
        assertEquals("BALANCED", ThinkingPolicy.effectiveThinkingMode(null, "BALANCED", supportsThinking = true))
    }

    @Test
    fun `falls back to OFF when neither chat nor model set a mode`() {
        assertEquals("OFF", ThinkingPolicy.effectiveThinkingMode(null, null, supportsThinking = true))
    }

    @Test
    fun `capability off forces OFF regardless of overrides`() {
        assertEquals("OFF", ThinkingPolicy.effectiveThinkingMode("DEEP", "DEEP", supportsThinking = false))
    }

    @Test
    fun `unrecognized mode value falls back to OFF instead of being sent to the engine`() {
        assertEquals("OFF", ThinkingPolicy.effectiveThinkingMode("ULTRA", null, supportsThinking = true))
    }

    // --- model-specific activation ----------------------------------------------------------

    @Test
    fun `Gemma 4 LiteRT thinking gets its native system token`() {
        val model = ModelInfo(
            displayName = "gemma-4-E2B-it",
            filePath = "/models/gemma-4-E2B-it.litertlm",
            fileSizeBytes = 1L,
            sha256 = "hash",
            supportsThinking = true
        )

        assertEquals(
            "<|think|>\nSystem instructions",
            ThinkingPolicy.withModelThinkingActivation("System instructions", model, "DEEP")
        )
    }

    @Test
    fun `Gemma 4 older row with unknown capability still gets its native system token`() {
        val model = ModelInfo(
            displayName = "gemma-4-E2B",
            filePath = "/models/gemma-4-E2B.litertlm",
            fileSizeBytes = 1L,
            sha256 = "hash"
        )

        assertEquals(
            "<|think|>\nSystem instructions",
            ThinkingPolicy.withModelThinkingActivation("System instructions", model, "DEEP")
        )
    }

    @Test
    fun `Gemma 4 OFF does not add its native system token`() {
        val model = ModelInfo(
            displayName = "Gemma 4 E2B IT",
            filePath = "/models/gemma-4-E2B-it.litertlm",
            fileSizeBytes = 1L,
            sha256 = "hash",
            supportsThinking = true
        )

        assertEquals(
            "System instructions",
            ThinkingPolicy.withModelThinkingActivation("System instructions", model, "OFF")
        )
    }

    @Test
    fun `native Gemma activation does not affect other engines or models`() {
        val gemma = ModelInfo(
            displayName = "gemma-4-E2B-it",
            filePath = "/models/gemma-4-E2B-it.litertlm",
            fileSizeBytes = 1L,
            sha256 = "hash",
            supportsThinking = true
        )
        val llamaGemma = gemma.copy(engine = ModelEngine.LLAMA_CPP)
        val otherLiteRt = gemma.copy(displayName = "other-2b", filePath = "/models/other.litertlm")

        assertEquals("System", ThinkingPolicy.withModelThinkingActivation("System", llamaGemma, "DEEP"))
        assertEquals("System", ThinkingPolicy.withModelThinkingActivation("System", otherLiteRt, "DEEP"))
    }

    @Test
    fun `thinking spec survives persistence and activates from metadata`() {
        val spec = ThinkingSpec.systemToken("<|reason|>").toJson()
        val model = ModelInfo(
            displayName = "future-model",
            filePath = "/models/future.litertlm",
            fileSizeBytes = 1L,
            sha256 = "hash",
            supportsThinking = true,
            thinkingSpecJson = spec
        )

        assertEquals(
            "<|reason|>\nSystem",
            ThinkingPolicy.withModelThinkingActivation("System", model, "BALANCED")
        )
    }

    @Test
    fun `system-token protocols do not also receive llama prefill or budget`() {
        val spec = ThinkingSpec.systemToken("<|reason|>")
        assertNull(ThinkingPolicy.assistantPrefillFor("DEEP", ModelEngine.LLAMA_CPP, true, spec))
        assertEquals(-1, ThinkingPolicy.reasoningBudgetFor("DEEP", ModelEngine.LLAMA_CPP, true, spec))
    }

    @Test
    fun `chat template detection does not depend on a model name`() {
        val spec = ThinkingSpec.detectFromTemplate(
            "{% if enable_thinking %}<|reason|>{% endif %}"
        )
        assertEquals(ThinkingSpec.Activation.PROMPT_ONLY, spec?.activation)

        val tokenSpec = ThinkingSpec.detectFromTemplate("system <|think|> user")
        assertEquals(ThinkingSpec.Activation.SYSTEM_TOKEN, tokenSpec?.activation)
        assertEquals("<|think|>", tokenSpec?.enableText)
    }

    @Test
    fun `metadata fields declare thinking without model-specific code`() {
        val spec = ThinkingSpec.detectFromMetadata(
            listOf("{\"enable_thinking\":true}", "{\"chat_template\":\"ignored\"}")
        )
        assertEquals(ThinkingSpec.Activation.PROMPT_ONLY, spec?.activation)
    }

    @Test
    fun `standalone jinja metadata declares thinking`() {
        val spec = ThinkingSpec.detectFromMetadata(
            listOf("{% if enable_thinking %}reason{% endif %}")
        )
        assertEquals(ThinkingSpec.Activation.PROMPT_ONLY, spec?.activation)
    }

    @Test
    fun `false thinking metadata does not opt model in`() {
        assertNull(ThinkingSpec.detectFromMetadata(listOf("{\"thinking\":false}")))
    }

    @Test
    fun `remote thinking parameter survives persistence`() {
        val spec = ThinkingSpec(remoteParameter = "enable_thinking", source = ThinkingSpec.Source.USER)
        assertEquals("enable_thinking", ThinkingSpec.fromJson(spec.toJson())?.remoteParameter)
    }
}
