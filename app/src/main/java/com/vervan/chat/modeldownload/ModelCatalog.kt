package com.vervan.chat.modeldownload

import com.vervan.chat.data.db.entities.ModelFileRole
import com.vervan.chat.data.db.entities.ModelRole

/** Model formats the app's loaders (LlmEngine, EmbeddingEngine) actually know how to open —
 * kept to exactly what's real rather than a speculative superset. */
enum class ModelFormat { LITERTLM, TFLITE }

data class ModelFileSpec(
    val fileId: String,
    val fileName: String,
    val downloadUrl: String,
    val role: ModelFileRole,
    val required: Boolean = true,
    val expectedBytes: Long? = null,
    val sha256: String? = null
)

/**
 * Static metadata for one downloadable model version. Category reuses [ModelRole] rather than a
 * separate enum — GENERATION/EMBEDDING are the only two the app's loaders can actually load
 * today, so a parallel taxonomy (Audio/Vision/Reranking/...) would just be display text nothing
 * downstream can act on; extend [ModelRole] itself if/when a new loadable kind is added.
 *
 * Identity is [modelId] + [version] together (see [DownloadIds.packageId]) — a future catalogue
 * update can ship a new version of an already-installed model as a distinct install rather than
 * silently reinterpreting history for the old one. Download records copy this metadata into
 * Room at start time ([com.vervan.chat.data.db.entities.DownloadPackage]/[com.vervan.chat.data.db.entities.DownloadFile])
 * rather than re-reading the live catalogue during resume/recovery, since a future app release
 * could change these entries out from under an in-progress download.
 */
data class CatalogModel(
    val modelId: String,
    val version: String,
    val displayName: String,
    val description: String,
    val category: ModelRole,
    val format: ModelFormat,
    val files: List<ModelFileSpec>,
    val totalExpectedBytes: Long?,
    val minimumRamBytes: Long? = null,
    val capabilities: Set<String> = emptySet(),
    val precision: String? = null,
    val sourceName: String = "Hugging Face",
    val sourceUrl: String,
    val requiresAuthToken: Boolean = false,
    val requiresLicenseAcceptance: Boolean = false,
    val licenseName: String? = null,
    val licenseUrl: String? = null,
    val enabled: Boolean = true
)

object DownloadIds {
    fun packageId(modelId: String, version: String) = "$modelId:$version"
}

/** The downloadable model catalogue. Purely static data — no download/import/validation logic
 * lives here (see ModelInstallationRepository for that), so adding a model later is a
 * one-entry diff. */
object ModelCatalog {
    val all: List<CatalogModel> = listOf(
        CatalogModel(
            modelId = "gemma-4-e2b-it-litert",
            version = "1",
            displayName = "Gemma 4 E2B IT",
            description = "Google's Gemma 4, 2B-parameter instruction-tuned model, packaged for on-device LiteRT-LM inference.",
            category = ModelRole.GENERATION,
            format = ModelFormat.LITERTLM,
            files = listOf(
                ModelFileSpec(
                    fileId = "model",
                    fileName = "gemma-4-E2B-it.litertlm",
                    downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                    role = ModelFileRole.MODEL,
                    expectedBytes = 2_588_147_712L,
                    sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
                )
            ),
            totalExpectedBytes = 2_588_147_712L,
            capabilities = setOf("Text generation"),
            sourceUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
            requiresAuthToken = false
        ),
        CatalogModel(
            modelId = "embeddinggemma-300m-litert",
            version = "1",
            displayName = "EmbeddingGemma 300M",
            description = "Google's EmbeddingGemma 300M, 512-token sequence length, mixed precision — for on-device semantic search and retrieval.",
            category = ModelRole.EMBEDDING,
            format = ModelFormat.TFLITE,
            files = listOf(
                ModelFileSpec(
                    fileId = "model",
                    fileName = "embeddinggemma-300M_seq512_mixed-precision.tflite",
                    downloadUrl = "https://huggingface.co/ghanashyamvtatti/embeddinggemma-300m-litert/resolve/main/embeddinggemma-300M_seq512_mixed-precision.tflite",
                    role = ModelFileRole.MODEL,
                    expectedBytes = 179_132_472L,
                    sha256 = "ad09e81557203cb0e177abf9bf8727dfe138a7d394aa0f70f0b2ed16432e121a"
                ),
                ModelFileSpec(
                    fileId = "tokenizer",
                    fileName = "sentencepiece.model",
                    downloadUrl = "https://huggingface.co/ghanashyamvtatti/embeddinggemma-300m-litert/resolve/main/sentencepiece.model",
                    role = ModelFileRole.TOKENIZER,
                    expectedBytes = 4_683_319L,
                    sha256 = "d6daa52d93d7aad10e8388bd526c4e501d914b47177398d1d9621f1fe48438c7"
                )
            ),
            totalExpectedBytes = 183_815_791L,
            capabilities = setOf("Embeddings", "Semantic search"),
            precision = "Mixed precision",
            sourceUrl = "https://huggingface.co/ghanashyamvtatti/embeddinggemma-300m-litert",
            requiresAuthToken = false
        )
    )

    fun find(modelId: String, version: String): CatalogModel? =
        all.find { it.modelId == modelId && it.version == version }
}
