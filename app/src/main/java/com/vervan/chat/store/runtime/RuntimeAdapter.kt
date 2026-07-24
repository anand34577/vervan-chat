package com.vervan.chat.store.runtime

import com.vervan.chat.store.model.ArtifactRole
import com.vervan.chat.store.model.ModelTask
import com.vervan.chat.store.model.ModelVariant
import com.vervan.chat.store.model.RuntimeId
import com.vervan.chat.store.model.RuntimeSubtype
import java.io.File

/**
 * What actually got installed, read back from `installed-manifest.json`.
 *
 * The map is role -> **absolute path**, and that indirection is the entire point of this layer:
 * the bytes live in the content-addressed blob store under their SHA-256, so there is no filename
 * left to infer anything from even if someone wanted to. A runtime asks for
 * [ArtifactRole.WEIGHTS] and gets a path; it never asks for "the .gguf file".
 */
data class InstalledManifest(
    val variantId: String,
    val runtime: RuntimeId,
    val runtimeSubtype: RuntimeSubtype?,
    val capabilities: Set<ModelTask>,
    val roleToPath: Map<ArtifactRole, String>
) {
    fun path(role: ArtifactRole): String? = roleToPath[role]
}

/** A resolved, runtime-specific instruction to load a model. Deliberately dumb data — the engines
 * (LlamaCppEngine, WhisperCppSttEngine, the sherpa-onnx TTS/STT engines, LlmEngine's LiteRT path)
 * consume these instead of building paths themselves. */
sealed interface LoadSpec {
    val variantId: String

    data class LlamaCpp(
        override val variantId: String,
        val modelPath: String,
        val mmprojPath: String?,
        val draftModelPath: String?,
        val contextTokens: Int?
    ) : LoadSpec

    data class WhisperCpp(
        override val variantId: String,
        val modelPath: String,
        val vadModelPath: String?
    ) : LoadSpec

    data class SherpaOnnx(
        override val variantId: String,
        val subtype: RuntimeSubtype,
        /** Role-keyed rather than field-per-file: the required set genuinely differs per subtype
         * (Matcha has a vocoder, VITS does not), and a struct with six nullable fields would just
         * push the same conditional into every caller. */
        val paths: Map<ArtifactRole, String>
    ) : LoadSpec

    data class LiteRtLm(
        override val variantId: String,
        val containerPath: String
    ) : LoadSpec
}

class RuntimeValidationException(message: String) : Exception(message)

/**
 * One implementation per runtime. Two jobs, both about role completeness:
 *
 *  - [requiredRoles] is asked *before* download so a variant that could never load (a vision
 *    variant with no projector) is rejected at catalogue-parse/install time, not after the bytes
 *    land.
 *  - [validate] is asked *after* install, against real paths, because a blob can go missing
 *    between install and load — an SD card pulled mid-session, a GC bug, a user with a file
 *    manager. Failing here marks the model NOT_READY instead of crashing the native layer.
 */
interface RuntimeAdapter {
    val runtimeId: RuntimeId

    fun requiredRoles(variant: ModelVariant): Set<ArtifactRole>

    fun validate(manifest: InstalledManifest)

    fun createLoadSpec(manifest: InstalledManifest, contextTokens: Int? = null): LoadSpec

    /** Shared post-install check: every required role is present *and* its file exists on disk. */
    fun validateRolesPresent(manifest: InstalledManifest, required: Set<ArtifactRole>) {
        val missing = required.filter { manifest.path(it) == null }
        if (missing.isNotEmpty()) {
            throw RuntimeValidationException(
                "${manifest.variantId}: missing required ${missing.joinToString { it.wireName }}"
            )
        }
        val absent = required.mapNotNull { role ->
            manifest.path(role)?.let { role to File(it) }
        }.filter { !it.second.exists() }
        if (absent.isNotEmpty()) {
            throw RuntimeValidationException(
                "${manifest.variantId}: files missing on disk for " +
                    absent.joinToString { it.first.wireName }
            )
        }
    }
}

/**
 * llama.cpp (GGUF). The projector rule is the one that matters: a variant advertising
 * [ModelTask.VISION] without a [ArtifactRole.MULTIMODAL_PROJECTOR] must fail validation rather
 * than load as a text-only model, because the user would have installed it specifically for
 * images and the failure would otherwise surface as garbage output, not an error.
 *
 * The chat template is usually embedded in the GGUF itself, so it is optional here even though
 * llama.cpp needs one — an absent role means "the GGUF carries it".
 */
class LlamaCppAdapter : RuntimeAdapter {
    override val runtimeId = RuntimeId.LLAMA_CPP

    override fun requiredRoles(variant: ModelVariant): Set<ArtifactRole> = buildSet {
        add(ArtifactRole.WEIGHTS)
        if (variant.requiresVision()) add(ArtifactRole.MULTIMODAL_PROJECTOR)
    }

    override fun validate(manifest: InstalledManifest) {
        val required = buildSet {
            add(ArtifactRole.WEIGHTS)
            if (ModelTask.VISION in manifest.capabilities) add(ArtifactRole.MULTIMODAL_PROJECTOR)
        }
        validateRolesPresent(manifest, required)
    }

    override fun createLoadSpec(manifest: InstalledManifest, contextTokens: Int?) = LoadSpec.LlamaCpp(
        variantId = manifest.variantId,
        modelPath = manifest.path(ArtifactRole.WEIGHTS)
            ?: throw RuntimeValidationException("${manifest.variantId}: no weights"),
        mmprojPath = manifest.path(ArtifactRole.MULTIMODAL_PROJECTOR),
        draftModelPath = manifest.path(ArtifactRole.DRAFT_WEIGHTS),
        contextTokens = contextTokens
    )
}

/**
 * whisper.cpp. Only ever loads a converted `ggml-*.bin`; a raw Hugging Face Transformers Whisper
 * repo is *not* compatible and must be a separate catalogue entry after conversion —
 * enforced by the catalogue build pipeline, not here, since by this point the role has already
 * asserted the file is a `asr_model` in ggml form.
 */
class WhisperCppAdapter : RuntimeAdapter {
    override val runtimeId = RuntimeId.WHISPER_CPP

    override fun requiredRoles(variant: ModelVariant) = setOf(ArtifactRole.ASR_MODEL)

    override fun validate(manifest: InstalledManifest) =
        validateRolesPresent(manifest, setOf(ArtifactRole.ASR_MODEL))

    override fun createLoadSpec(manifest: InstalledManifest, contextTokens: Int?) = LoadSpec.WhisperCpp(
        variantId = manifest.variantId,
        modelPath = manifest.path(ArtifactRole.ASR_MODEL)
            ?: throw RuntimeValidationException("${manifest.variantId}: no asr_model"),
        vadModelPath = manifest.path(ArtifactRole.VAD_MODEL)
    )
}

/**
 * sherpa-onnx, where composition is entirely a function of [RuntimeSubtype] — this is exactly the
 * case warns about, where assuming a single `model.onnx` silently breaks half the
 * architectures. Matcha splits the acoustic model from the vocoder; Kokoro and Kitten carry a
 * separate voices bank; VITS is the only one that really is one graph.
 *
 * A lexicon is genuinely optional across all of them (espeak-ng data substitutes for it in the
 * languages that need g2p), so it is never required, only passed through when present.
 */
class SherpaOnnxAdapter : RuntimeAdapter {
    override val runtimeId = RuntimeId.SHERPA_ONNX

    override fun requiredRoles(variant: ModelVariant): Set<ArtifactRole> =
        rolesFor(variant.runtimeSubtype)

    override fun validate(manifest: InstalledManifest) =
        validateRolesPresent(manifest, rolesFor(manifest.runtimeSubtype))

    override fun createLoadSpec(manifest: InstalledManifest, contextTokens: Int?): LoadSpec {
        val subtype = manifest.runtimeSubtype
            ?: throw RuntimeValidationException("${manifest.variantId}: sherpa-onnx with no subtype")
        return LoadSpec.SherpaOnnx(manifest.variantId, subtype, manifest.roleToPath)
    }

    private fun rolesFor(subtype: RuntimeSubtype?): Set<ArtifactRole> = when (subtype) {
        RuntimeSubtype.VITS -> setOf(ArtifactRole.WEIGHTS, ArtifactRole.TOKENS)
        RuntimeSubtype.MATCHA_TTS ->
            setOf(ArtifactRole.ACOUSTIC_MODEL, ArtifactRole.VOCODER, ArtifactRole.TOKENS)
        RuntimeSubtype.KOKORO ->
            setOf(ArtifactRole.WEIGHTS, ArtifactRole.VOICES, ArtifactRole.TOKENS, ArtifactRole.DATA_DIRECTORY)
        RuntimeSubtype.KITTEN ->
            setOf(ArtifactRole.WEIGHTS, ArtifactRole.VOICES, ArtifactRole.TOKENS, ArtifactRole.DATA_DIRECTORY)
        RuntimeSubtype.WHISPER, RuntimeSubtype.TRANSDUCER ->
            setOf(ArtifactRole.WEIGHTS, ArtifactRole.TOKENS)
        null -> throw RuntimeValidationException("sherpa-onnx variant declares no runtimeSubtype")
    }
}

/**
 * LiteRT-LM. The `.litertlm` container already packages the TFLite graph, tokenizer, weights and
 * metadata, so a single [ArtifactRole.RUNTIME_CONTAINER] is the whole variant — and because the
 * Android API takes a filesystem path (not a descriptor or a URI), the manifest must resolve this
 * role to a real absolute path. That is why SAF imports are copied into managed storage rather
 * than handed over as `content://`.
 */
class LiteRtLmAdapter : RuntimeAdapter {
    override val runtimeId = RuntimeId.LITERT_LM

    override fun requiredRoles(variant: ModelVariant) = setOf(ArtifactRole.RUNTIME_CONTAINER)

    override fun validate(manifest: InstalledManifest) =
        validateRolesPresent(manifest, setOf(ArtifactRole.RUNTIME_CONTAINER))

    override fun createLoadSpec(manifest: InstalledManifest, contextTokens: Int?) = LoadSpec.LiteRtLm(
        variantId = manifest.variantId,
        containerPath = manifest.path(ArtifactRole.RUNTIME_CONTAINER)
            ?: throw RuntimeValidationException("${manifest.variantId}: no runtime_container")
    )
}

/** Lookup for the adapters this build has. Registered explicitly rather than reflectively so the
 * set stays in step with what the APK's native libraries can actually load. */
class RuntimeAdapterRegistry(adapters: List<RuntimeAdapter>) {
    private val byRuntime = adapters.associateBy { it.runtimeId }

    val availableRuntimes: Set<RuntimeId> get() = byRuntime.keys

    fun adapterFor(runtime: RuntimeId): RuntimeAdapter? = byRuntime[runtime]

    fun require(runtime: RuntimeId): RuntimeAdapter = byRuntime[runtime]
        ?: throw RuntimeValidationException("No adapter for runtime ${runtime.wireName}")
}
