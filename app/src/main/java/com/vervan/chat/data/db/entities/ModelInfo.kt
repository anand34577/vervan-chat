package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File
import java.util.UUID

enum class ModelBackend { NPU, GPU, CPU, UNVERIFIED }

fun ModelBackend.displayName(): String = when (this) {
    ModelBackend.NPU -> "NPU"
    ModelBackend.GPU -> "GPU"
    ModelBackend.CPU -> "CPU"
    ModelBackend.UNVERIFIED -> "Not tested yet"
}

// TTS_VOICE/STT_MODEL never produce a ModelInfo row (neither is a loadable chat model) — they
// exist purely so a TTS voice or an inbuilt speech-to-text model can be a normal
// ModelCatalog/ModelDownloadRepository entry, reusing the same download/pause/resume/delete UI
// as GENERATION/EMBEDDING instead of a second bespoke downloader. See
// ModelDownloadRepository.verifyAndImport's category branch — both write into the same
// TtsVoiceModelDao-backed table (engine/language identify the row either way).
enum class ModelRole { GENERATION, EMBEDDING, TTS_VOICE, STT_MODEL }

/** How a [ModelInfo] row got here — drives whether the model downloader offers a catalogue
 * entry as "already installed" (see ModelInstallationRepository's duplicate-prevention check). */
enum class ModelOrigin { LOCAL_IMPORT, DOWNLOADED }

/** Explicit per-model hardware choice (user ask: "if GPU is selected load only on GPU, if not
 * able then give error" — no silent fallback unless the user picks AUTO). AUTO tries NPU first,
 * then GPU, then CPU. */
enum class BackendChoice { AUTO, GPU, CPU, NPU }

/** Governs whether a non-read-only tool call needs the user's tap before it runs (see
 * ChatViewModel.runGenerationLoop). Only meaningful when the model's Tools capability is on. */
enum class ToolApprovalMode { ALWAYS_ASK, AUTO_APPROVE_REVERSIBLE, AUTO_APPROVE_ALL }

/** Which native runtime a GENERATION model loads through — LiteRT-LM (the original, MediaPipe/
 * .task-.litertlm-.litert based) or llama.cpp (GGUF). Irrelevant for EMBEDDING (always
 * LiteRT-LM/MediaPipe in this app) but stored on every row for schema simplicity; existing rows
 * default to LITERT_LM. See ModelLoadCoordinator, which routes load/unload per-engine. */
enum class ModelEngine { LITERT_LM, LLAMA_CPP }

@Entity(tableName = "models")
data class ModelInfo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val role: ModelRole = ModelRole.GENERATION,
    val engine: ModelEngine = ModelEngine.LITERT_LM,
    val lastWorkingBackend: ModelBackend = ModelBackend.UNVERIFIED,
    val isActive: Boolean = false,
    val importedAt: Long = System.currentTimeMillis(),
    // Set by ModelLoadCoordinator every time a load into the native engine actually succeeds —
    // doubles as both "last successfully used" and the tiebreaker for picking a replacement
    // default when the current default is deleted (spec §4.3 priority order).
    val lastLoadedAt: Long? = null,
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
    val tokenizerPath: String? = null,
    // Companion mtmd projector file (a second GGUF) for a vision-capable llama.cpp model —
    // only set when engine == LLAMA_CPP && supportsVision == true. LiteRT-LM models bundle
    // their own vision encoder in the single .task/.litertlm package, so this stays null there.
    val mmprojPath: String? = null,
    // Common generation-param overrides (both engines) — null means "inherit the app-wide
    // Settings value", same pattern as temperature/topP/topK above.
    val minP: Float? = null,
    val repetitionPenalty: Float? = null,
    val maxOutputTokens: Int? = null,
    // Newline-joined stop sequences — a plain String column (no Room TypeConverter) parsed at
    // GenerationParamsResolver with a simple .split("\n"), same reasoning as reusing a plain
    // String for tokenizerPath/mmprojPath rather than adding new converter machinery for a list.
    val stopSequences: String? = null,
    // llama.cpp-only overrides below — all null on a LiteRT-LM model, never read for one.
    val gpuLayerCount: Int? = null,
    val cpuThreads: Int? = null,
    val nBatch: Int? = null,
    val nUbatch: Int? = null,
    val useMlock: Boolean? = null,
    // null = Auto, true = force-enabled, false = force-disabled (flash attention).
    val flashAttention: Boolean? = null,
    val kvCacheType: String? = null,
    val vulkanDeviceIndex: Int? = null,
    val ropeFreqBase: Float? = null,
    val ropeFreqScale: Float? = null,
    // A llama_chat_builtin_templates() name (e.g. "chatml") or raw custom Jinja text — null uses
    // the GGUF's own embedded tokenizer.chat_template.
    val chatTemplateOverride: String? = null,
    val loraPath: String? = null,
    val loraScale: Float? = null,
    // Auto-read once (validateAndActivate's existing verify-load) from the GGUF header via
    // llama_model_desc()/llama_model_n_ctx_train()/llama_model_n_layer() — read-only, never set
    // by the user. nativeMaxContext caps the "Context length" slider; layerCount bounds the
    // GPU-layers slider.
    val modelDesc: String? = null,
    val nativeMaxContext: Int? = null,
    val layerCount: Int? = null,
    // Catalogue provenance for a model that came through the downloader (see
    // com.vervan.chat.modeldownload) — null/LOCAL_IMPORT for anything imported the original way.
    // catalogModelId+catalogVersion double as the "already installed" duplicate check against
    // ModelCatalog entries.
    val origin: ModelOrigin = ModelOrigin.LOCAL_IMPORT,
    val catalogModelId: String? = null,
    val catalogVersion: String? = null,
    val sourceUrl: String? = null
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
    mtpActive: Boolean,
    // Whether the load ladder actually *proved* the modality is absent from the model package
    // (same-backend degraded retry succeeded after a modality-specific failure), vs. merely
    // ending up degraded for some transient reason (memory pressure, flaky backend attempt).
    // Latching supportsX=false on a transient failure is permanent — the known-capability skip
    // in LlmEngine.load() then never retries the modality — so only proven absence may latch.
    audioProvenUnsupported: Boolean = true,
    visionProvenUnsupported: Boolean = true
): Pair<ModelInfo, List<String>> {
    var updated = copy(lastWorkingBackend = lastWorkingBackend)
    val disabled = mutableListOf<String>()
    if (supportsVision != false && (maxNumImages ?: 1) > 0 && !visionEnabled && visionProvenUnsupported) {
        updated = updated.copy(supportsVision = false)
        disabled += "Vision"
    }
    if (supportsAudio != false && !audioEnabled && audioProvenUnsupported) {
        updated = updated.copy(supportsAudio = false)
        disabled += "Audio"
    }
    // Symmetric positive latch: a load that came up with the modality actually working is proof
    // it's supported — without this, supportsAudio stays null forever unless the user manually
    // configures it, and the voice-chat UI (which gates on supportsAudio == true) never appears.
    if (supportsVision != true && visionEnabled) updated = updated.copy(supportsVision = true)
    if (supportsAudio != true && audioEnabled) updated = updated.copy(supportsAudio = true)
    if (mtpAttempted && mtpEnabled && !mtpActive) {
        updated = updated.copy(mtpEnabled = false, mtpSupported = false)
        disabled += "Speculative decoding (MTP)"
    }
    return updated to disabled
}

/** Whether this model *can* support vision at all — the hard physical limit the Vision
 * capability toggle respects (and that [supportsVision] is meaningless without). A llama.cpp
 * model needs its companion mmproj projector file present on disk (set at import — see
 * `ModelImportManager.importLlamaCppModel`); without one the native loader has no vision
 * encoder to invoke, so letting the user turn Vision on would be a silent no-op that misleads
 * the rest of the app into offering image attachments. A LiteRT-LM model bundles its vision
 * encoder in the single .task/.litertlm package, so it always can. */
fun ModelInfo.canSupportVision(): Boolean = when (engine) {
    ModelEngine.LLAMA_CPP -> !mmprojPath.isNullOrBlank() && File(mmprojPath).isFile
    ModelEngine.LITERT_LM -> true
}

/** Whether this model *can* support native audio input at all — same hard-limit reasoning as
 * [canSupportVision]. llama.cpp has no audio-input JNI in this app (see `LlamaCppEngine`'s
 * hardcoded `audioEnabled = false`); a LiteRT-LM model may bundle an audio encoder. */
fun ModelInfo.canSupportAudio(): Boolean = when (engine) {
    ModelEngine.LLAMA_CPP -> false
    ModelEngine.LITERT_LM -> true
}
