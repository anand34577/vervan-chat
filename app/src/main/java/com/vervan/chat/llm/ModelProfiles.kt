package com.vervan.chat.llm

/**
 * Named generation profiles (spec §11.9). A profile shapes the *kind* of answer — how much
 * context to spend, how hard to retrieve, how deeply to reason, how long to write — without
 * touching the raw sampler knobs (temperature / top-p / top-k), which stay in Settings as the
 * underlying sampler configuration for every profile.
 *
 * The user picks one per chat (default from Settings); advanced users who want per-knob
 * control pick BALANCED (the no-op preset) and tune Settings directly.
 */
enum class ModelProfileType(val id: String, val label: String, val description: String) {
    FAST(
        "FAST", "Fast",
        "Smaller context, shorter output, minimal retrieval, fastest thinking. Best for quick questions."
    ),
    BALANCED(
        "BALANCED", "Balanced",
        "Moderate context, standard retrieval, normal output length. The everyday default."
    ),
    QUALITY(
        "QUALITY", "Quality",
        "Larger context, more retrieval, longer output, deeper reasoning. Best for complex analysis."
    ),
    BATTERY_SAVER(
        "BATTERY_SAVER", "Battery saver",
        "Conservative output, lower context, reduced retrieval. Trades quality for battery on the go."
    ),
    THERMAL_SAFE(
        "THERMAL_SAFE", "Thermal safe",
        "Reduced workload, no concurrent embedding, lower context. Use when the device is hot."
    );

    companion object {
        fun fromId(id: String?): ModelProfileType =
            entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

/**
 * Resolved shape parameters for one profile. These adjust generation *behavior*, not the
 * sampler itself — the sampler (temperature/topP/topK) still comes from Settings so a user
 * who tunes those doesn't lose their tuning by switching profiles.
 *
 * @property contextFraction  multiplier applied to the user's context-token budget (0..1).
 * @property maxOutputHint    "SHORT"/"NORMAL"/"DETAILED" — folded into the prompt as a length
 *                            instruction. Empty = no instruction (BALANCED).
 * @property thinkingDefault  default thinking-mode id ("OFF"/"FAST"/"BALANCED"/"DEEP") the
 *                            chat starts with; the user can still override per-chat.
 * @property retrievalTopK    how many source passages to retrieve when grounded (0 = skip
 *                            retrieval entirely even if KBs are selected — BATTERY_SAVER).
 * @property allowConcurrentEmbedding whether embedding/indexing may run alongside generation.
 */
data class ResolvedProfile(
    val contextFraction: Float,
    val maxOutputHint: String,
    val thinkingDefault: String,
    val retrievalTopK: Int,
    val allowConcurrentEmbedding: Boolean
)

object ModelProfiles {
    fun resolve(profile: ModelProfileType): ResolvedProfile = when (profile) {
        ModelProfileType.FAST -> ResolvedProfile(
            contextFraction = 0.5f,
            maxOutputHint = "SHORT",
            thinkingDefault = "OFF",
            retrievalTopK = 3,
            allowConcurrentEmbedding = true
        )
        ModelProfileType.BALANCED -> ResolvedProfile(
            contextFraction = 0.75f,
            maxOutputHint = "",
            thinkingDefault = "OFF",
            retrievalTopK = 5,
            allowConcurrentEmbedding = true
        )
        ModelProfileType.QUALITY -> ResolvedProfile(
            contextFraction = 1.0f,
            maxOutputHint = "DETAILED",
            thinkingDefault = "BALANCED",
            retrievalTopK = 8,
            allowConcurrentEmbedding = true
        )
        ModelProfileType.BATTERY_SAVER -> ResolvedProfile(
            contextFraction = 0.5f,
            maxOutputHint = "SHORT",
            thinkingDefault = "OFF",
            retrievalTopK = 0,
            allowConcurrentEmbedding = false
        )
        ModelProfileType.THERMAL_SAFE -> ResolvedProfile(
            contextFraction = 0.5f,
            maxOutputHint = "NORMAL",
            thinkingDefault = "OFF",
            retrievalTopK = 3,
            allowConcurrentEmbedding = false
        )
    }
}
