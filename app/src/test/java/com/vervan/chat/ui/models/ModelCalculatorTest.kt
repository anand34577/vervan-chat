package com.vervan.chat.ui.models

import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCalculatorTest {
    @Test
    fun `larger models require more estimated memory`() {
        val small = estimateModelMemory(parametersB = 3f, quantBits = 4, contextTokens = 4096)
        val large = estimateModelMemory(parametersB = 7f, quantBits = 4, contextTokens = 4096)
        assertTrue(large.totalGb > small.totalGb)
    }

    @Test
    fun `lower quantization and context reduce memory`() {
        val heavy = estimateModelMemory(parametersB = 7f, quantBits = 8, contextTokens = 16384)
        val light = estimateModelMemory(parametersB = 7f, quantBits = 4, contextTokens = 4096)
        assertTrue(light.weightsGb < heavy.weightsGb)
        assertTrue(light.kvCacheGb < heavy.kvCacheGb)
        assertTrue(light.totalGb < heavy.totalGb)
    }
}
