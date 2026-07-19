package com.vervan.chat.modeldownload

import com.vervan.chat.data.db.entities.ModelFileRole
import com.vervan.chat.data.db.entities.ModelRole

/** Model formats the app's loaders (LlmEngine, EmbeddingEngine, sherpa-onnx TTS voices, sherpa-onnx
 * offline ASR) actually know how to open — kept to exactly what's real rather than a speculative
 * superset. ONNX_TTS/ONNX_STT need no litertlm/tflite-style validation (see
 * ModelDownloadRepository.verifyAndImport's format `when` — both are no-op branches there), just
 * the files present. */
enum class ModelFormat { LITERTLM, TFLITE, ONNX_TTS, ONNX_STT }

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
    val enabled: Boolean = true,
    // Only set (and only meaningful) for category == ModelRole.TTS_VOICE or ModelRole.STT_MODEL —
    // tells ModelDownloadRepository.verifyAndImport which TtsVoiceModel(engine, language) row to
    // write once the package reaches READY, so PiperTtsEngine/KokoroTtsEngine/WhisperSttEngine
    // (which all read via TtsVoiceModelDao, not the download system) find it exactly like any
    // other downloaded voice/STT model. Despite the "tts" name, the same two fields carry the
    // engine/language identity for a downloadable STT model too — it's the same
    // (engine, language) -> on-disk-directory row shape either way, so a second parallel pair of
    // fields would be pure duplication.
    val ttsEngine: String? = null,
    val ttsLanguage: String? = null
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
            description = "Google's 2B instruction model for on-device LiteRT-LM.",
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
            description = "Google's 300M embedding model for local semantic search.",
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
        ),
        // MMS-TTS voices (Meta, mirrored as plain files — no espeak-ng-data/tokenizer needed,
        // confirmed live) for the realtime voice pipeline's Piper fallback tier. See
        // com.vervan.chat.voice.PiperTtsEngine — it checks for espeak-ng-data at load time and
        // only requires it if actually present, so these MMS voices and any future "real" Piper
        // voice both work through the same loader.
        mmsVoice(iso = "hin", displayName = "Hindi Voice (MMS)", language = "hi"),
        mmsVoice(iso = "eng", displayName = "English Voice (MMS)", language = "en"),
        // Inbuilt offline speech-to-text tier for the realtime voice pipeline (see
        // com.vervan.chat.voice.WhisperSttEngine / RealtimeVoiceController's 3-tier STT policy):
        // used when the loaded generation model doesn't support audio input, or as a fallback
        // when it does but a transcription attempt comes back blank. Multilingual Whisper tiny
        // (not the English-only tiny.en variant) covers Hindi + English in one model. int8
        // quantized files confirmed present at this exact layout on the source repo.
        CatalogModel(
            modelId = "sherpa-onnx-whisper-tiny",
            version = "1",
            displayName = "Whisper Tiny (offline speech-to-text)",
            description = "Multilingual Whisper Tiny for offline voice transcription.",
            category = ModelRole.STT_MODEL,
            format = ModelFormat.ONNX_STT,
            files = listOf(
                ModelFileSpec(fileId = "encoder", fileName = "model.onnx", downloadUrl = "$WHISPER_BASE/tiny-encoder.int8.onnx", role = ModelFileRole.MODEL),
                ModelFileSpec(fileId = "decoder", fileName = "decoder.onnx", downloadUrl = "$WHISPER_BASE/tiny-decoder.int8.onnx", role = ModelFileRole.AUXILIARY),
                ModelFileSpec(fileId = "tokens", fileName = "tokens.txt", downloadUrl = "$WHISPER_BASE/tiny-tokens.txt", role = ModelFileRole.TOKENIZER)
            ),
            totalExpectedBytes = null,
            capabilities = setOf("Speech-to-text", "Multilingual", "Offline"),
            sourceUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny",
            requiresAuthToken = false,
            ttsEngine = "WHISPER",
            ttsLanguage = "multi"
        )
    )

    private const val MMS_BASE = "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main"
    private const val WHISPER_BASE = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main"

    private fun mmsVoice(iso: String, displayName: String, language: String) = CatalogModel(
        modelId = "mms-tts-$iso",
        version = "1",
        displayName = displayName,
        description = "Meta's on-device speech model for voice replies.",
        category = ModelRole.TTS_VOICE,
        format = ModelFormat.ONNX_TTS,
        files = listOf(
            ModelFileSpec(fileId = "model", fileName = "model.onnx", downloadUrl = "$MMS_BASE/$iso/model.onnx", role = ModelFileRole.MODEL),
            ModelFileSpec(fileId = "tokens", fileName = "tokens.txt", downloadUrl = "$MMS_BASE/$iso/tokens.txt", role = ModelFileRole.TOKENIZER)
        ),
        totalExpectedBytes = null,
        capabilities = setOf("Text-to-speech"),
        sourceUrl = "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx",
        ttsEngine = "PIPER",
        ttsLanguage = language
    )

    fun find(modelId: String, version: String): CatalogModel? =
        all.find { it.modelId == modelId && it.version == version }
}
