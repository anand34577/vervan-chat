package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class ModelBackend { NPU, GPU, CPU, UNVERIFIED }

fun ModelBackend.displayName(): String = when (this) {
    ModelBackend.NPU -> "NPU"
    ModelBackend.GPU -> "GPU"
    ModelBackend.CPU -> "CPU"
    ModelBackend.UNVERIFIED -> "Not tested yet"
}

enum class ModelRole { GENERATION, EMBEDDING }

/** Explicit per-model hardware choice (user ask: "if GPU is selected load only on GPU, if not
 * able then give error" — no silent fallback unless the user picks AUTO). AUTO tries NPU first,
 * then GPU, then CPU. */
enum class BackendChoice { AUTO, GPU, CPU, NPU }

/** Governs whether a non-read-only tool call needs the user's tap before it runs (see
 * ChatViewModel.runGenerationLoop). Only meaningful when the model's Tools capability is on. */
enum class ToolApprovalMode { ALWAYS_ASK, AUTO_APPROVE_REVERSIBLE, AUTO_APPROVE_ALL }

@Entity(tableName = "models")
data class ModelInfo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val role: ModelRole = ModelRole.GENERATION,
    val lastWorkingBackend: ModelBackend = ModelBackend.UNVERIFIED,
    val isActive: Boolean = false,
    val importedAt: Long = System.currentTimeMillis(),
    // Bring-your-own-model means this app never ships or verifies the model's actual
    // license — it can only make the user actively acknowledge that one applies (spec
    // §12) before the model is used. Not legal advice, not a fetched/verified license text.
    val licenseAcknowledged: Boolean = false,
    val supportsVision: Boolean? = null,
    val supportsAudio: Boolean? = null,
    val supportsTools: Boolean? = null,
    val supportsThinking: Boolean? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxNumImages: Int? = null,
    val contextTokens: Int? = null,
    val preferredBackend: BackendChoice = BackendChoice.AUTO,
    // MTP (speculative decoding) — null means "not checked yet"; set once a load/verify pass
    // queries the model file directly via LiteRT-LM's Capabilities API. mtpEnabled is the user's
    // own on/off choice and only has any effect when mtpSupported is true.
    val mtpSupported: Boolean? = null,
    val mtpEnabled: Boolean = true,
    val seed: Int? = null,
    val toolApprovalMode: ToolApprovalMode = ToolApprovalMode.ALWAYS_ASK,
    // Only set (and only needed) for an EMBEDDING model that's a bare TFLite graph rather than
    // a MediaPipe Task Bundle — its companion SentencePiece tokenizer file, imported alongside
    // the model itself since the graph has no bundled tokenizer of its own.
    val tokenizerPath: String? = null
)

/**
 * After a real load attempt, reconciles a model's declared capabilities/MTP against what
 * actually worked on this device/backend — a model can be told to override a capability on
 * (or leave MTP on) but the load ladder may still have had to drop it. Used by both the model
 * manager's manual Load and chat's on-demand load, so either path auto-disables what didn't
 * actually work and reports what changed (for a toast) instead of silently mismatching what
 * the rest of the app shows as available.
 */
fun ModelInfo.reconcileCapabilities(
    lastWorkingBackend: ModelBackend,
    visionEnabled: Boolean,
    audioEnabled: Boolean,
    // Whether this load attempt actually requested MTP at all — a validate/verify load that
    // never asked for speculative decoding shouldn't be read as "MTP failed" and disable it.
    mtpAttempted: Boolean,
    mtpActive: Boolean
): Pair<ModelInfo, List<String>> {
    var updated = copy(lastWorkingBackend = lastWorkingBackend)
    val disabled = mutableListOf<String>()
    if (supportsVision != false && (maxNumImages ?: 1) > 0 && !visionEnabled) {
        updated = updated.copy(supportsVision = false)
        disabled += "Vision"
    }
    if (supportsAudio != false && !audioEnabled) {
        updated = updated.copy(supportsAudio = false)
        disabled += "Audio"
    }
    if (mtpAttempted && mtpEnabled && !mtpActive) {
        updated = updated.copy(mtpEnabled = false, mtpSupported = false)
        disabled += "Speculative decoding (MTP)"
    }
    return updated to disabled
}
