package com.vervan.chat.llm

import com.vervan.chat.data.db.entities.ModelInfo
import java.io.File

/**
 * Picks which installed GENERATION model a turn should use when the user hasn't explicitly
 * pinned one (no chat/folder override) and "Model selection: Auto" is on (see
 * [com.vervan.chat.data.settings.SettingsRepository.autoModelSelectionEnabled]) — the answer to
 * "most users want 'answer quickly' / 'give me the best result', not to choose a model
 * identifier." A no-op when only one GENERATION model is installed, which is the common case;
 * only relevant once a device has several.
 *
 * Deliberately narrow: it chooses among models the user already installed, it never downloads or
 * loads anything itself (the caller's normal [com.vervan.chat.modelload.ModelLoadCoordinator]
 * path still does that), and thermal/battery pressure is already folded into [profile] by
 * [com.vervan.chat.system.DeviceAwareProfile] before this is called — this only adds the "which
 * of my installed models fits the task and the chosen profile" axis on top.
 */
object AutoModelSelector {
    /**
     * @param candidates every installed GENERATION [ModelInfo] (any engine/backend — Auto
     *   reasons about model identity, not runtime; [com.vervan.chat.data.db.entities.BackendChoice.AUTO]
     *   already handles NPU/GPU/CPU selection for whichever model this picks).
     * @param needsVision/needsAudio required modalities for this specific turn (an attached
     *   image/audio file) — models proven NOT to support a required modality
     *   (`supportsVision == false` / `supportsAudio == false`) are excluded; `null` (never
     *   tested) is treated as eligible rather than penalized, since ruling out an untested model
     *   would be a false negative more often than not.
     * @return null only when [candidates] is empty or nothing meets the modality requirement —
     *   the caller's existing "no model resolved" handling covers that the same as today.
     */
    fun select(
        candidates: List<ModelInfo>,
        profile: ModelProfileType,
        needsVision: Boolean = false,
        needsAudio: Boolean = false
    ): ModelInfo? {
        if (candidates.isEmpty()) return null
        val capable = candidates.filter {
            (!needsVision || it.supportsVision != false) && (!needsAudio || it.supportsAudio != false)
        }
        // Degrade gracefully: if nothing claims the modality, still answer with the best model
        // available rather than refusing outright — the existing per-generate vision/audio
        // guards in ChatViewModel.beginGeneration already tell the user plainly when the
        // resolved model truly can't handle the attachment.
        val pool = capable.ifEmpty { candidates }
        // File size is the same size/capability proxy already used by ModelLoadCoordinator's
        // memory-budget check, Model Calculator, and the onboarding recommendation — no per-model
        // benchmark data exists at this layer to do better than that.
        val bySizeAscending = pool.sortedBy { sizeBytes(it) }
        return when (profile) {
            // Larger is the deliberate proxy for "more capable" here — a bigger checkpoint the
            // user already chose to install is presumed to be the better answer, not just slower.
            ModelProfileType.QUALITY -> bySizeAscending.last()
            ModelProfileType.FAST, ModelProfileType.BATTERY_SAVER, ModelProfileType.THERMAL_SAFE -> bySizeAscending.first()
            ModelProfileType.BALANCED -> bySizeAscending[bySizeAscending.size / 2]
        }
    }

    private fun sizeBytes(model: ModelInfo): Long =
        File(model.filePath).takeIf { it.isFile }?.length()?.takeIf { it > 0 } ?: model.fileSizeBytes

    /**
     * Per-turn "fast vs capable" routing signal for a BALANCED-profile chat — the practical
     * version of "pre-load a small model for quick turns, only pay the big-model cost for
     * genuinely long/complex ones." A phone can't usefully keep two LLMs resident at once (see
     * [com.vervan.chat.modelload.ModelLoadCoordinator]'s single-resident-generation-model
     * invariant, which exists for memory-budget reasons), so this doesn't pre-load a second
     * model in the background — it picks *which already-installed* model this specific turn
     * loads, same swap cost as switching chat profiles manually, just automatic. Only meant to
     * apply when the chat's own profile is BALANCED ("let the app decide") — an explicit
     * FAST/QUALITY pin is the user's own choice and must never be silently overridden by a
     * per-turn heuristic.
     *
     * ponytail: prompt length is a proxy for complexity, not a real classifier — a short prompt
     * asking for a hard multi-step derivation still routes FAST. Upgrade path: reuse the same
     * generation model to classify complexity before routing (mirrors the query-rewriting LLM
     * call already used for retrieval), if the length heuristic proves too coarse in practice.
     *
     * @return null when the turn is unclear enough to just defer to the chat's normal profile.
     */
    fun complexityProfileHint(triggerText: String): ModelProfileType? {
        val tokens = estimateTokens(triggerText)
        return when {
            tokens <= FAST_TURN_TOKEN_THRESHOLD -> ModelProfileType.FAST
            tokens >= QUALITY_TURN_TOKEN_THRESHOLD -> ModelProfileType.QUALITY
            else -> null
        }
    }

    private const val FAST_TURN_TOKEN_THRESHOLD = 12
    private const val QUALITY_TURN_TOKEN_THRESHOLD = 120
}
