package com.vervan.chat.system

import java.io.File

/**
 * Picks a sensible default thread count for CPU inference.
 *
 * Phones are big.LITTLE: decode is memory-bound and every step waits for the slowest worker, so
 * scheduling llama.cpp threads onto efficiency cores usually makes generation *slower* than using
 * the performance cores alone. We group cores by their maximum frequency (sysfs `cpuinfo_max_freq`)
 * and count every core outside the lowest-frequency cluster. When sysfs is unreadable, or the SoC
 * is homogeneous, fall back to half the cores (never below 2, never above 8).
 */
object CpuTopology {
    private const val MAX_DEFAULT_THREADS = 8

    val recommendedInferenceThreads: Int by lazy { compute(readMaxFrequencies(), Runtime.getRuntime().availableProcessors()) }

    internal fun compute(maxFreqsKhz: List<Long>, availableProcessors: Int): Int {
        val cores = availableProcessors.coerceAtLeast(1)
        val fallback = (cores / 2).coerceIn(minOf(2, cores), MAX_DEFAULT_THREADS)
        val freqs = maxFreqsKhz.filter { it > 0 }
        if (freqs.size < 2) return fallback
        val lowest = freqs.min()
        val performance = freqs.count { it > lowest }
        // Homogeneous cluster (all the same max frequency): no LITTLE cores to avoid.
        if (performance == 0) return fallback
        return performance.coerceIn(1, MAX_DEFAULT_THREADS)
    }

    private fun readMaxFrequencies(): List<Long> = runCatching {
        val cpuDir = File("/sys/devices/system/cpu")
        cpuDir.listFiles { f -> f.name.matches(Regex("cpu\\d+")) }
            .orEmpty()
            .mapNotNull { File(it, "cpufreq/cpuinfo_max_freq").takeIf(File::canRead)?.readText()?.trim()?.toLongOrNull() }
    }.getOrDefault(emptyList())
}
