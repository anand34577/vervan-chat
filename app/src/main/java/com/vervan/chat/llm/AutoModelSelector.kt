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
}
