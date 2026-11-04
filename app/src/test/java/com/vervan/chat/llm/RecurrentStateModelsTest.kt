package com.vervan.chat.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrentStateModelsTest {
    @Test
    fun matchesRunningStateFamilies() {
        listOf(
            "LFM3-Omni-2B_int8.litertlm",
            "1793_LFM2.5-1.2B-Instruct_int4.litertlm",
            "LFM3-1.7B_mixed_int4_ekv4096.litertlm",
            "Qwen3.5-0.8B_int8.litertlm",
            "granite-4.0-h-1b.litertlm",
            "mamba2-130m.litertlm"
        ).forEach { assertTrue(it, RecurrentStateModels.matches(it)) }
    }

    @Test
    fun ignoresAttentionOnlyModels() {
        listOf(
            "gemma-4-E2B-it.litertlm",
            "qwen3_0_6b_mixed_int4.litertlm",
            "Qwen3-4B.litertlm",
            "Phi-4-mini.litertlm"
        ).forEach { assertFalse(it, RecurrentStateModels.matches(it)) }
    }
}
