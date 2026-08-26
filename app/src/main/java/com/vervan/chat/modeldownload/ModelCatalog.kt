package com.vervan.chat.modeldownload

import com.vervan.chat.data.db.entities.ModelFileRole
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.llm.ThinkingSpec

/** Model formats the app's loaders (LlmEngine, EmbeddingEngine, sherpa-onnx TTS voices,
 * whisper.cpp ASR) actually know how to open — kept to exactly what's real rather than a
 * speculative superset. ONNX_TTS/WHISPER_CPP need no litertlm/tflite-style validation (see
 * ModelDownloadRepository.verifyAndImport's format `when` — both are no-op branches there), just
 * the files present. */
enum class ModelFormat { LITERTLM, TFLITE, ONNX_TTS, WHISPER_CPP }

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
    // Optional model-protocol metadata. This is data, not model-specific generation code: a
    // future catalog entry can declare a new activation token without changing the engine.
    val thinkingSpecJson: String? = null,
    val requiresAuthToken: Boolean = false,
    val requiresLicenseAcceptance: Boolean = false,
    val licenseName: String? = null,
    val licenseUrl: String? = null,
    val enabled: Boolean = true,
    // Only set (and only meaningful) for category == ModelRole.TTS_VOICE or ModelRole.STT_MODEL —
    // tells ModelDownloadRepository.verifyAndImport which TtsVoiceModel(engine, language) row to
    // write once the package reaches READY, so PiperTtsEngine/KokoroTtsEngine/WhisperCppSttEngine
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
            thinkingSpecJson = ThinkingSpec.systemToken("<|think|>", ThinkingSpec.Source.CATALOG).toJson(),
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
        // Supertonic 3 (github.com/supertone-inc/supertonic) — a 31-language flow-matching TTS,
        // run through plain onnxruntime-android rather than sherpa-onnx (see
        // com.vervan.chat.voice.SupertonicTtsEngine). An opt-in third engine tier, never in
        // TtsEngineSelector's AUTO path — its 4-graph, 8-step denoising pipeline (~400 MB of
        // weights, vector_estimator.onnx alone is 256 MB) is heavier than Kokoro's single
        // forward pass. All URLs/sizes/hashes verified live against the source repo below.
        // Only one default voice style is bundled for v1; multi-voice selection is a fast-follow.
        CatalogModel(
            modelId = "supertonic-3",
            version = "1",
            displayName = "Supertonic 3",
            description = "Supertone's 31-language on-device flow-matching TTS for the realtime voice pipeline.",
            category = ModelRole.TTS_VOICE,
            format = ModelFormat.ONNX_TTS,
            files = listOf(
                ModelFileSpec(
                    fileId = "duration_predictor", fileName = "duration_predictor.onnx",
                    downloadUrl = "$SUPERTONIC_BASE/onnx/duration_predictor.onnx", role = ModelFileRole.MODEL,
                    expectedBytes = 3_700_147L,
                    sha256 = "c3eb91414d5ff8a7a239b7fe9e34e7e2bf8a8140d8375ffb14718b1c639325db"
                ),
                ModelFileSpec(
                    fileId = "text_encoder", fileName = "text_encoder.onnx",
                    downloadUrl = "$SUPERTONIC_BASE/onnx/text_encoder.onnx", role = ModelFileRole.AUXILIARY,
                    expectedBytes = 36_416_150L,
                    sha256 = "c7befd5ea8c3119769e8a6c1486c4edc6a3bc8365c67621c881bbb774b9902ff"
                ),
                ModelFileSpec(
                    fileId = "vector_estimator", fileName = "vector_estimator.onnx",
                    downloadUrl = "$SUPERTONIC_BASE/onnx/vector_estimator.onnx", role = ModelFileRole.AUXILIARY,
                    expectedBytes = 256_534_781L,
                    sha256 = "883ac868ea0275ef0e991524dc64f16b3c0376efd7c320af6b53f5b780d7c61c"
                ),
                ModelFileSpec(
                    fileId = "vocoder", fileName = "vocoder.onnx",
                    downloadUrl = "$SUPERTONIC_BASE/onnx/vocoder.onnx", role = ModelFileRole.AUXILIARY,
                    expectedBytes = 101_424_195L,
                    sha256 = "085de76dd8e8d5836d6ca66826601f615939218f90e519f70ee8a36ed2a4c4ba"
                ),
                ModelFileSpec(
                    fileId = "tts_config", fileName = "tts.json",
                    downloadUrl = "$SUPERTONIC_BASE/onnx/tts.json", role = ModelFileRole.CONFIG,
                    expectedBytes = 8_253L,
                    sha256 = "42078d3aef1cd43ab43021f3c54f47d2d75ceb4e75f627f118890128b06a0d09"
                ),
                ModelFileSpec(
                    fileId = "unicode_indexer", fileName = "unicode_indexer.json",
                    downloadUrl = "$SUPERTONIC_BASE/onnx/unicode_indexer.json", role = ModelFileRole.VOCABULARY,
                    expectedBytes = 277_676L,
                    sha256 = "9bf7346e43883a81f8645c81224f786d43c5b57f3641f6e7671a7d6c493cb24f"
                ),
                // Renamed from HF's generic "M1.json" so SupertonicTtsEngine doesn't hardcode
                // an upstream voice name.
                ModelFileSpec(
                    fileId = "voice_style_default", fileName = "voice_style_default.json",
                    downloadUrl = "$SUPERTONIC_BASE/voice_styles/M1.json", role = ModelFileRole.AUXILIARY,
                    expectedBytes = 291_748L,
                    sha256 = "e35604687f5d23694b8e91593a93eec0e4eca6c0b02bb8ed69139ab2ea6b0a5b"
                )
            ),
            totalExpectedBytes = 398_652_750L,
            capabilities = setOf("Text-to-speech", "Multilingual", "Offline"),
            sourceUrl = "https://huggingface.co/Supertone/supertonic-3",
            requiresAuthToken = false,
            requiresLicenseAcceptance = true,
            licenseName = "OpenRAIL-M",
            licenseUrl = "https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE",
            ttsEngine = "SUPERTONIC",
            ttsLanguage = "multi"
        ),
        // Inbuilt offline speech-to-text tier for the realtime voice pipeline (see
        // com.vervan.chat.voice.WhisperCppSttEngine / RealtimeVoiceController's 2-tier STT
        // policy): used when the loaded generation model doesn't support audio input, or as a
        // fallback when it does but a transcription attempt comes back blank. Lands in
        // stt_models/whisper_cpp_multi/ and is read by WhisperCppSttEngine (engine="WHISPER_CPP")
        // via TtsVoiceModelDao. Gated on WHISPER_CPP_AVAILABLE (a prebuilt libwhisper.so in
        // jniLibs, built by scripts/build-whisper-android.ps1 when whispercpp.dir is set) so a
        // whisper-less build doesn't offer a download that can never actually be used — see the
        // `enabled` filter in ModelDownloadRepository. Multilingual (not the English-only tiny.en
        // variant) covers Hindi + English in one ~75 MB model.
        CatalogModel(
            modelId = "whisper-cpp-tiny",
            version = "1",
            displayName = "Whisper Tiny — whisper.cpp (offline speech-to-text)",
            description = "Multilingual Whisper Tiny for whisper.cpp, an alternative offline STT runtime.",
            category = ModelRole.STT_MODEL,
            format = ModelFormat.WHISPER_CPP,
            files = listOf(
                ModelFileSpec(
                    fileId = "model",
                    fileName = "ggml-tiny.bin",
                    downloadUrl = "$WHISPER_CPP_BASE/ggml-tiny.bin",
                    role = ModelFileRole.MODEL
                )
            ),
            totalExpectedBytes = null,
            capabilities = setOf("Speech-to-text", "Multilingual", "Offline"),
            sourceUrl = "https://huggingface.co/ggerganov/whisper.cpp",
            requiresAuthToken = false,
            enabled = com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE,
            ttsEngine = "WHISPER_CPP",
            ttsLanguage = "multi"
        ),
        // Larger whisper.cpp sizes, same runtime/pipeline as Tiny above — WhisperCppSttEngine picks
        // whichever one SettingsRepository.whisperModelVariant names (falling back to "multi"/Tiny),
        // so installing these doesn't change behavior until the user explicitly switches. Sizes and
        // sha256 verified against the live files at $WHISPER_CPP_BASE.
        whisperCppModel(
            variant = "base", fileName = "ggml-base.bin", displayName = "Whisper Base — whisper.cpp (offline speech-to-text)",
            expectedBytes = 147_951_465L, sha256 = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"
        ),
        whisperCppModel(
            variant = "small", fileName = "ggml-small.bin", displayName = "Whisper Small — whisper.cpp (offline speech-to-text)",
            expectedBytes = 487_601_967L, sha256 = "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b"
        ),
        // Supertonic voice styles beyond the bundled M1 (the "supertonic-3" entry above, kept at
        // ttsLanguage="multi" for backward compatibility — it's the only one that also carries the
        // shared ~400 MB acoustic model). Each of these is just the ~290 KB voice_style_default.json
        // equivalent; SupertonicTtsEngine loads the acoustic model from the "multi" package and the
        // voice tensors from whichever package SettingsRepository.supertonicVoiceVariant names, so
        // installing one of these requires "multi" to already be installed. All 10 voices (M1-M5,
        // F1-F5) confirmed live at $SUPERTONIC_BASE/voice_styles/.
        supertonicVoice("F1", "Supertonic 3 — Voice F1 (female)", 292_046L, "bbdec6ee00231c2c742ad05483df5334cab3b52fda3ba38e6a07059c4563dbc2"),
        supertonicVoice("F2", "Supertonic 3 — Voice F2 (female)", 292_423L, "7c722c6a72707b1a77f035d67f0d1351ba187738e06f7683e8c72b1df3477fc6"),
        supertonicVoice("F3", "Supertonic 3 — Voice F3 (female)", 290_794L, "12f6ef2573baa2defa1128069cb59f203e3ab67c92af77b42df8a0e3a2f7c6ab"),
        supertonicVoice("F4", "Supertonic 3 — Voice F4 (female)", 291_808L, "c2fa764c1225a76dfc3e2c73e8aa4f70d9ee48793860eb34c295fff01c2e032b"),
        supertonicVoice("F5", "Supertonic 3 — Voice F5 (female)", 291_479L, "45966e73316415626cf41a7d1c6f3b4c70dbc1ba2bee5c1978ef0ce33244fc8d"),
        supertonicVoice("M2", "Supertonic 3 — Voice M2 (male)", 292_055L, "b76cbf62bac707c710cf0ae5aba5e31eea1a6339a9734bfae33ab98499534a50"),
        supertonicVoice("M3", "Supertonic 3 — Voice M3 (male)", 290_198L, "ea1ac35ccb91b0d7ecad533a2fbd0eec10c91513d8951e3b25fbba99954e159b"),
        supertonicVoice("M4", "Supertonic 3 — Voice M4 (male)", 291_522L, "ca8eefad4fcd989c9379032ff3e50738adc547eeb5e221b82593a6d7b3bac303"),
        supertonicVoice("M5", "Supertonic 3 — Voice M5 (male)", 291_469L, "dd22b92740314321f8ae11c5e87f8dd60d060f15dd3a632b5adf77f471f77af2")
    )

    private const val MMS_BASE = "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main"
    private const val WHISPER_CPP_BASE = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"
    private const val SUPERTONIC_BASE = "https://huggingface.co/Supertone/supertonic-3/resolve/main"

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

    private fun whisperCppModel(variant: String, fileName: String, displayName: String, expectedBytes: Long, sha256: String) = CatalogModel(
        modelId = "whisper-cpp-$variant",
        version = "1",
        displayName = displayName,
        description = "whisper.cpp offline speech-to-text model — larger than Tiny, slower, more accurate.",
        category = ModelRole.STT_MODEL,
        format = ModelFormat.WHISPER_CPP,
        files = listOf(
            ModelFileSpec(
                fileId = "model", fileName = fileName,
                downloadUrl = "$WHISPER_CPP_BASE/$fileName", role = ModelFileRole.MODEL,
                expectedBytes = expectedBytes, sha256 = sha256
            )
        ),
        totalExpectedBytes = expectedBytes,
        capabilities = setOf("Speech-to-text", "Multilingual", "Offline"),
        sourceUrl = "https://huggingface.co/ggerganov/whisper.cpp",
        enabled = com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE,
        ttsEngine = "WHISPER_CPP",
        ttsLanguage = variant
    )

    private fun supertonicVoice(voiceId: String, displayName: String, expectedBytes: Long, sha256: String) = CatalogModel(
        modelId = "supertonic-3-${voiceId.lowercase()}",
        version = "1",
        displayName = displayName,
        description = "Additional Supertonic 3 voice style. Requires the \"Supertonic 3\" download " +
            "(the shared 31-language acoustic model) to already be installed.",
        category = ModelRole.TTS_VOICE,
        format = ModelFormat.ONNX_TTS,
        files = listOf(
            ModelFileSpec(
                fileId = "voice_style", fileName = "voice_style_default.json",
                downloadUrl = "$SUPERTONIC_BASE/voice_styles/$voiceId.json", role = ModelFileRole.AUXILIARY,
                expectedBytes = expectedBytes, sha256 = sha256
            )
        ),
        totalExpectedBytes = expectedBytes,
        capabilities = setOf("Text-to-speech", "Multilingual", "Offline"),
        sourceUrl = "https://huggingface.co/Supertone/supertonic-3",
        requiresLicenseAcceptance = true,
        licenseName = "OpenRAIL-M",
        licenseUrl = "https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE",
        ttsEngine = "SUPERTONIC",
        ttsLanguage = voiceId
    )

    fun find(modelId: String, version: String): CatalogModel? =
        all.find { it.modelId == modelId && it.version == version }
}
