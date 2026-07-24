package com.vervan.chat.store.model

/**
 * Tier-1 (curated catalogue) data model: Model -> Variant -> Artifact.
 *
 * This is deliberately a *separate* type family from [com.vervan.chat.modeldownload.CatalogModel],
 * which is the app's existing in-APK flat catalogue (one model = one flat file list, one runtime
 * implied by [com.vervan.chat.modeldownload.ModelFormat]). That shape cannot express the things
 * the store needs — several runtimes per model, sherpa-onnx compositions that vary by subtype,
 * per-variant device requirements, or multiple failover sources per file — so the store gets its
 * own model and the old catalogue is adapted *into* it (see CatalogBootstrap) rather than the two
 * being merged. When the store ships, `CatalogModel` becomes the bootstrap format only.
 *
 * Nothing here carries install state; these are the immutable descriptions fetched from the
 * signed catalogue. Install state stays in Room, keyed by [ModelVariant.variantId].
 */

/** Inference runtimes the app can actually drive. A variant naming a runtime this build has no
 * adapter for is dropped at parse time rather than shown as an install the user cannot use. */
enum class RuntimeId(val wireName: String) {
    LLAMA_CPP("llama.cpp"),
    WHISPER_CPP("whisper.cpp"),
    SHERPA_ONNX("sherpa-onnx"),
    LITERT_LM("litert-lm");

    companion object {
        fun fromWire(value: String): RuntimeId? = entries.find { it.wireName == value }
    }
}

/** sherpa-onnx composes completely different artifact sets per TTS architecture — VITS is one
 * ONNX graph, Matcha splits acoustic model and vocoder, Kokoro/Kitten add a voices bank. The
 * subtype is what tells [com.vervan.chat.store.runtime.SherpaOnnxAdapter] which roles are
 * mandatory, so it is required on every sherpa-onnx variant and meaningless on the others. */
enum class RuntimeSubtype(val wireName: String) {
    VITS("vits"),
    MATCHA_TTS("matcha-tts"),
    KOKORO("kokoro"),
    KITTEN("kitten"),
    WHISPER("whisper"),
    TRANSDUCER("transducer");

    companion object {
        fun fromWire(value: String): RuntimeSubtype? = entries.find { it.wireName == value }
    }
}

/**
 * What an artifact *is*, semantically. This is the single most important field in the schema:
 * every runtime resolves the files it needs by role, never by filename or extension, so a repo
 * that names its projector `mmproj-f16.gguf` and one that names it `projector.gguf` are handled
 * by the same code path. Spec §14: never infer runtime behavior from a filename.
 *
 * Adding a value here is a schema change and must be accompanied by a `schemaVersion` bump —
 * see [com.vervan.chat.store.catalog.CatalogParser] for why an unknown role fails closed.
 */
enum class ArtifactRole(val wireName: String) {
    WEIGHTS("weights"),
    MULTIMODAL_PROJECTOR("multimodal_projector"),
    TOKENIZER("tokenizer"),
    CHAT_TEMPLATE("chat_template"),
    DRAFT_WEIGHTS("draft_weights"),
    ACOUSTIC_MODEL("acoustic_model"),
    VOCODER("vocoder"),
    TOKENS("tokens"),
    VOICES("voices"),
    LEXICON("lexicon"),
    VAD_MODEL("vad_model"),
    DATA_DIRECTORY("data_directory"),
    RUNTIME_CONTAINER("runtime_container"),
    ASR_MODEL("asr_model"),

    /** Kept for schema parity with the iOS/desktop catalogue consumers only — Core ML has no
     * meaning on Android. A variant that *requires* this role is treated as incompatible here
     * rather than silently installed without it. */
    COREML_ENCODER("coreml_encoder");

    companion object {
        fun fromWire(value: String): ArtifactRole? = entries.find { it.wireName == value }
    }
}

/** What the model does, for filtering/presentation. Broader than the app's existing
 * [com.vervan.chat.data.db.entities.ModelRole] (which only names what the *loaders* support)
 * because the catalogue must be able to describe a model before this build can run it. */
enum class ModelTask(val wireName: String) {
    CHAT("chat"),
    VISION("vision"),
    ASR("asr"),
    TTS("tts"),
    EMBEDDING("embedding");

    companion object {
        fun fromWire(value: String): ModelTask? = entries.find { it.wireName == value }
    }
}

enum class AcceleratorRequirement(val wireName: String) {
    NONE("none"), GPU("gpu"), NPU("npu");

    companion object {
        fun fromWire(value: String): AcceleratorRequirement? = entries.find { it.wireName == value }
    }
}

enum class SourceProvider(val wireName: String) {
    HUGGING_FACE("huggingface"), MIRROR("mirror");

    companion object {
        fun fromWire(value: String): SourceProvider? = entries.find { it.wireName == value }
    }
}

/**
 * One place an artifact's bytes can be fetched from. A variant may list several; failover to a
 * later source is only permitted when the fetched bytes hash to the artifact's declared
 * [Artifact.sha256], so a compromised or stale mirror cannot substitute different content
 * (spec §9).
 *
 * [revision] is always an immutable commit SHA. `main` or any branch name is rejected at parse
 * time by [com.vervan.chat.store.catalog.CatalogParser] — a repo owner replacing a file behind a
 * moving ref would otherwise silently invalidate the size/hash the catalogue was signed with.
 */
data class ArtifactSource(
    val provider: SourceProvider,
    val repository: String,
    val revision: String,
    val path: String
) {
    /** Hugging Face `/resolve/` endpoints 302 to a CDN URL with its own expiry; resume logic must
     * re-resolve from *this* URL rather than reusing the expired redirect target (spec §6.5). */
    fun toUrl(): String = when (provider) {
        SourceProvider.HUGGING_FACE ->
            "https://huggingface.co/$repository/resolve/$revision/$path"
        // Mirrors carry an absolute base in `repository` so a CDN layout need not mimic HF's.
        SourceProvider.MIRROR -> "${repository.trimEnd('/')}/$path"
    }
}

/**
 * One physical file a variant needs. [installPath] is the path *relative to the variant's install
 * root* that the role resolves to — the content-addressed store keeps the bytes under their hash
 * and the installed manifest maps role -> blob, so this is only used for runtimes that need a
 * conventional on-disk layout next to the weights (espeak-ng data directories, mainly).
 */
data class Artifact(
    val artifactId: String,
    val role: ArtifactRole,
    val installPath: String,
    val sizeBytes: Long,
    val sha256: String,
    /** Ordered; index 0 is primary, the rest are hash-gated failovers. Never empty. */
    val sources: List<ArtifactSource>
)

/**
 * What a device must offer before a variant may be installed. Checked *before* download starts
 * (spec §5) — discovering incompatibility after pulling 3 GB is the failure mode this exists to
 * prevent.
 */
data class VariantRequirements(
    val minAppVersionCode: Int,
    val minRuntimeVersion: String?,
    val supportedAbis: Set<String>,
    /** Must already account for the variant's default context size, not just the weight bytes —
     * a 4 GB model at 32k context does not fit in a 6 GB phone even though the file does. */
    val estimatedMinRamBytes: Long,
    val acceleratorRequirement: AcceleratorRequirement
)

/**
 * One installable configuration of a model for exactly one runtime. This — not [StoreModel] — is
 * the unit of install, of the download state machine, and of the Room install record.
 *
 * [runtimeConfig] maps a semantic role to the id of the artifact that fills it. It exists so that
 * a runtime adapter can ask "which artifact is my `weights`?" without scanning filenames, and so
 * a catalogue can carry two artifacts of the same role (e.g. two `voices` banks) and still say
 * unambiguously which one the runtime should load.
 */
data class ModelVariant(
    val variantId: String,
    val version: String,
    val runtime: RuntimeId,
    val runtimeSubtype: RuntimeSubtype?,
    val format: String,
    val quantization: String?,
    val capabilities: Set<ModelTask>,
    val totalSizeBytes: Long,
    val requirements: VariantRequirements,
    val artifacts: List<Artifact>,
    val runtimeConfig: Map<ArtifactRole, String>,
    /** Default context length this variant's RAM estimate was computed against; surfaced in the
     * eligibility check so a user raising the context sees the estimate move with it. */
    val defaultContextTokens: Int?
) {
    fun artifactFor(role: ArtifactRole): Artifact? =
        runtimeConfig[role]?.let { id -> artifacts.find { it.artifactId == id } }

    fun requiresVision(): Boolean = ModelTask.VISION in capabilities
}

/** Licence facts a human reviewer recorded for a curated entry. Everything here is reviewed, not
 * scraped (spec §11) — [acceptanceHash] is what the tap-to-accept record is keyed on, so a
 * catalogue update that changes the licence text re-prompts an existing installer. */
data class ModelLicense(
    val name: String,
    val url: String,
    val redistributionPermitted: Boolean,
    val gated: Boolean,
    val commercialUseAllowed: Boolean,
    val acceptableUseRestrictions: List<String>,
    val attributionRequired: Boolean,
    val usageThresholdClause: String?,
    val acceptanceHash: String
)

/** The user-facing catalogue entry. Carries no runtime detail of its own — everything installable
 * lives on a [ModelVariant]. */
data class StoreModel(
    val modelId: String,
    val displayName: String,
    val publisher: String,
    val description: String,
    val tasks: Set<ModelTask>,
    val languages: List<String>,
    val license: ModelLicense,
    val modelCardUrl: String,
    val variants: List<ModelVariant>
)

/**
 * A parsed, signature-verified catalogue.
 *
 * [catalogVersion] is monotonic: the client refuses any fetched catalogue whose version is lower
 * than the highest it has already accepted, which is what makes a replayed old (e.g. known-bad
 * pinned-revision) catalogue useless to an attacker who can control the network but not the
 * signing key. See CatalogRepository for the acceptance rule.
 */
data class StoreCatalog(
    val schemaVersion: Int,
    val catalogVersion: Int,
    val generatedAt: Long,
    val models: List<StoreModel>
) {
    fun variant(variantId: String): Pair<StoreModel, ModelVariant>? =
        models.firstNotNullOfOrNull { m ->
            m.variants.find { it.variantId == variantId }?.let { m to it }
        }
}
