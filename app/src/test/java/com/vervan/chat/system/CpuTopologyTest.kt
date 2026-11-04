package com.vervan.chat.system

import org.junit.Assert.assertEquals
import org.junit.Test

class CpuTopologyTest {
    @Test fun `big little SoC uses only non-efficiency cores`() {
        // 1 prime + 3 performance + 4 efficiency (typical Snapdragon 8 Gen 2 layout).
        val freqs = listOf(2_016_000L, 2_016_000, 2_016_000, 2_016_000, 2_803_000, 2_803_000, 2_803_000, 3_187_000)
        assertEquals(4, CpuTopology.compute(freqs, 8))
    }

    @Test fun `homogeneous cluster falls back to half the cores`() {
        assertEquals(4, CpuTopology.compute(List(8) { 2_000_000L }, 8))
    }

    @Test fun `unreadable sysfs falls back to half the cores`() {
        assertEquals(4, CpuTopology.compute(emptyList(), 8))
        assertEquals(2, CpuTopology.compute(emptyList(), 4))
        assertEquals(1, CpuTopology.compute(emptyList(), 1))
    }

    @Test fun `never exceeds eight threads`() {
        val freqs = List(4) { 1_000_000L } + List(12) { 3_000_000L }
        assertEquals(8, CpuTopology.compute(freqs, 16))
    }
}
