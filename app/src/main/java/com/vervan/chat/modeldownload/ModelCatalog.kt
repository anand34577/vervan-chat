package com.vervan.chat.modeldownload

import com.vervan.chat.data.db.entities.ModelFileRole
import com.vervan.chat.data.db.entities.ModelRole

/** Model formats the app's loaders (LlmEngine, EmbeddingEngine, sherpa-onnx TTS voices,
 * whisper.cpp ASR) actually know how to open — kept to exactly what's real rather than a
 * speculative superset. ONNX_TTS/WHISPER_CPP need no litertlm/tflite-style validation (see
 * ModelDownloadRepository.verifyAndImport's format `when` — both are no-op branches there), just
 * the files present. */
enum class ModelFormat {
    LITERTLM, TFLITE, ONNX_TTS, WHISPER_CPP,
    /** Cactus needle `.cact` speech model (Udgam Labs' Shruti), read by ShrutiSttEngine. */
    NEEDLE_CACT,
    /** LFM3 Omni's speech-output graphs (plain .tflite files), read by com.vervan.chat.omni. */
    OMNI_SPEECH
}

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
    // write once the package reaches READY, so PiperTtsEngine/KokoroTtsEngine/WhisperCppSttEngine
    // (which all read via TtsVoiceModelDao, not the download system) find it exactly like any
    // other downloaded voice/STT model. Despite the "tts" name, the same two fields carry the
    // engine/language identity for a downloadable STT model too — it's the same
    // (engine, language) -> on-disk-directory row shape either way, so a second parallel pair of
    // fields would be pure duplication.
    val ttsEngine: String? = null,
    val ttsLanguage: String? = null,
    /** Non-empty for a package compiled for specific NPUs (catalogue SoC tags, see
     * [com.vervan.chat.system.DeviceSoc.matchesAny]); offered only on those chips. */
    val npuSocs: Set<String> = emptySet(),
    /** LiteRT-LM ships no 32-bit native runtime, so its packages are hidden on armeabi-v7a phones. */
    val requires64Bit: Boolean = format == ModelFormat.LITERTLM,
    /** Size tier shown in the picker, smallest first. Drives the onboarding recommendation. */
    val tier: ModelTier? = null
) {
    /** Whether this phone can run the package at all (chip, ABI); RAM fit is judged separately. */
    val runsOnThisDevice: Boolean
        get() = enabled &&
            (!requires64Bit || android.os.Build.SUPPORTED_64_BIT_ABIS?.isNotEmpty() != false) &&
            (npuSocs.isEmpty() || (com.vervan.chat.system.DeviceSoc.hasLiteRtNpu && com.vervan.chat.system.DeviceSoc.matchesAny(npuSocs)))
}

/** Rough capability ladder for on-device chat models. */
enum class ModelTier(val label: String) { TINY("Tiny"), SMALL("Small"), STANDARD("Standard"), LARGE("Large") }

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
            displayName = "Gemma 4 E2B",
            description = "Google's capable everyday model. Best balance of quality and speed on most recent phones.",
            category = ModelRole.GENERATION,
            format = ModelFormat.LITERTLM,
            files = listOf(
                ModelFileSpec(
                    fileId = "model",
                    fileName = "gemma-4-E2B-it.litertlm",
                    downloadUrl = "$GEMMA4_E2B_BASE/gemma-4-E2B-it.litertlm",
                    role = ModelFileRole.MODEL,
                    expectedBytes = 2_588_147_712L,
                    sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
                )
            ),
            totalExpectedBytes = 2_588_147_712L,
            minimumRamBytes = 3_500_000_000L,
            capabilities = setOf("Text generation"),
            sourceUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
            requiresAuthToken = false,
            licenseName = "Gemma Terms of Use",
            licenseUrl = "https://ai.google.dev/gemma/terms",
            tier = ModelTier.STANDARD
        ),
        // Smaller tiers so phones with 2-4 GB of RAM have something that runs well. Revisions are
        // pinned, and sizes and hashes were verified against the Hugging Face API.
        CatalogModel(
            modelId = "qwen3-0.6b-litert",
            version = "1",
            displayName = "Qwen3 0.6B",
            description = "Very small and fast. Good for quick answers, rewriting and summaries on entry-level phones.",
            category = ModelRole.GENERATION,
            format = ModelFormat.LITERTLM,
            files = listOf(
                ModelFileSpec(
                    fileId = "model", fileName = "qwen3_0_6b_mixed_int4.litertlm",
                    downloadUrl = "$QWEN3_06B_BASE/qwen3_0_6b_mixed_int4.litertlm", role = ModelFileRole.MODEL,
                    expectedBytes = 497_516_544L,
                    sha256 = "7900eb4e7362d88c58782c6f9999bb7a129e03544aa98b8f338ea0cc5d8c22c1"
                )
            ),
            totalExpectedBytes = 497_516_544L,
            minimumRamBytes = 1_500_000_000L,
            capabilities = setOf("Text generation", "Reasoning"),
            precision = "Mixed int4",
            sourceUrl = "https://huggingface.co/litert-community/Qwen3-0.6B",
            licenseName = "Apache 2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            tier = ModelTier.TINY
        ),
        CatalogModel(
            modelId = "lfm2.5-1.2b-instruct-litert",
            version = "1",
            displayName = "LFM2.5 1.2B",
            description = "Small model with strong instruction following. A good default for phones with 3-4 GB of RAM.",
            category = ModelRole.GENERATION,
            format = ModelFormat.LITERTLM,
            files = listOf(
                ModelFileSpec(
                    fileId = "model", fileName = "LFM2.5-1.2B-Instruct_int4.litertlm",
                    downloadUrl = "$LFM25_BASE/LFM2.5-1.2B-Instruct_int4.litertlm", role = ModelFileRole.MODEL,
                    expectedBytes = 736_015_744L,
                    sha256 = "e904729a8a8a0160658cb7cd22992e270a0547c05cc45b08c47ece5c0e29425b"
                )
            ),
            totalExpectedBytes = 736_015_744L,
            minimumRamBytes = 2_200_000_000L,
            capabilities = setOf("Text generation"),
            precision = "int4",
            sourceUrl = "https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct",
            requiresLicenseAcceptance = true,
            licenseName = "LFM Open License v1.0",
            licenseUrl = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct/blob/main/LICENSE",
            tier = ModelTier.SMALL
        ),
        CatalogModel(
            modelId = "lfm3-1.7b-litert",
            version = "1",
            displayName = "LFM3 1.7B",
            description = "Liquid's newest small model. Quick, strong at instructions and tools, and light on memory.",
            category = ModelRole.GENERATION,
            format = ModelFormat.LITERTLM,
            files = listOf(
                ModelFileSpec(
                    fileId = "model", fileName = "LFM3-1.7B_mixed_int4_ekv4096.litertlm",
                    downloadUrl = "$LFM3_17B_BASE/LFM3-1.7B_mixed_int4_ekv4096.litertlm", role = ModelFileRole.MODEL,
                    expectedBytes = 1_441_629_120L,
                    sha256 = "327486bf01d0b1eefd727b7aadec8627a2b8db143349262857ac98403109d72b"
                )
            ),
            totalExpectedBytes = 1_441_629_120L,
            minimumRamBytes = 3_000_000_000L,
            capabilities = setOf("Text generation"),
            precision = "Mixed int4",
            sourceUrl = "https://huggingface.co/litert-community/LFM3-1.7B-LiteRT",
            requiresLicenseAcceptance = true,
            licenseName = "LFM Open License v1.0",
            licenseUrl = "https://huggingface.co/litert-community/LFM3-1.7B-LiteRT/blob/main/LICENSE",
            tier = ModelTier.SMALL
        ),
        CatalogModel(
            modelId = "lfm3-omni-2b-litert",
            version = "1",
            displayName = "LFM3 Omni 2B",
            description = "Understands text, photos and voice recordings in one model. Ask about a picture or a clip.",
            category = ModelRole.GENERATION,
            format = ModelFormat.LITERTLM,
            files = listOf(
                ModelFileSpec(
                    fileId = "model", fileName = "LFM3-Omni-2B_int8.litertlm",
                    downloadUrl = "$LFM3_OMNI_BASE/LFM3-Omni-2B_int8.litertlm", role = ModelFileRole.MODEL,
                    expectedBytes = 3_226_750_192L,
                    sha256 = "959617973a7ddcb3d887fa0d9891d79556a266490d5c432b02a18ffcac3124ed"
                )
            ),
            totalExpectedBytes = 3_226_750_192L,
            // Model card: 1.05 GB process memory plus 3.08 GB of GPU allocations on a Galaxy S26.
            minimumRamBytes = 5_500_000_000L,
            capabilities = setOf("Text generation", "Image input", "Audio input"),
            precision = "int8",
            sourceUrl = "https://huggingface.co/litert-community/LFM3-Omni-2B-LiteRT",
            requiresLicenseAcceptance = true,
            licenseName = "LFM Open License v1.0",
            licenseUrl = "https://huggingface.co/litert-community/LFM3-Omni-2B-LiteRT/blob/main/LICENSE",
            tier = ModelTier.STANDARD
        ),
        CatalogModel(
            modelId = "gemma-4-e4b-it-litert",
            version = "1",
            displayName = "Gemma 4 E4B",
            description = "Google's larger on-device model. Noticeably smarter; needs a flagship-class phone.",
            category = ModelRole.GENERATION,
            format = ModelFormat.LITERTLM,
            files = listOf(
                ModelFileSpec(
                    fileId = "model", fileName = "gemma-4-E4B-it.litertlm",
                    downloadUrl = "$GEMMA4_E4B_BASE/gemma-4-E4B-it.litertlm", role = ModelFileRole.MODEL,
                    expectedBytes = 3_659_530_240L,
                    sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"
                )
            ),
            totalExpectedBytes = 3_659_530_240L,
            // 12 GB-class phones only: on 8 GB the OS leaves too little headroom for E4B plus KV cache.
            minimumRamBytes = 9_500_000_000L,
            capabilities = setOf("Text generation"),
            sourceUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm",
            licenseName = "Gemma Terms of Use",
            licenseUrl = "https://ai.google.dev/gemma/terms",
            tier = ModelTier.LARGE
        ),
        // NPU-compiled builds: offered only on the chip each file was compiled for (see
        // CatalogModel.runsOnThisDevice), and loaded on LiteRT-LM's NPU backend first.
        npuGemma4E2B(
            socTag = "sm8750", chipName = "Snapdragon 8 Elite",
            fileName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm", base = GEMMA4_E2B_NPU_QCOM_BASE,
            bytes = 3_016_294_400L, sha256 = "41dd675fbe735b6029012b5576a5716bac614fd8156de0128db4c9dff3cebd4e"
        ),
        npuGemma4E2B(
            socTag = "tensor-g5", chipName = "Google Tensor G5",
            fileName = "gemma-4-E2B-it_Google_Tensor_G5.litertlm", base = GEMMA4_E2B_BASE,
            bytes = 3_113_545_589L, sha256 = "af1082986639ecde7db95d91be6fe54f8b6b458104734c5bafc204e69d6852dc"
        ),
        npuGemma4E2B(
            socTag = "tensor-g6", chipName = "Google Tensor G6",
            fileName = "gemma-4-E2B-it_Google_Tensor_G6.litertlm", base = GEMMA4_E2B_BASE,
            bytes = 3_313_938_293L, sha256 = "f86c7c19c736e9307267946edef58b0a45494a9127036d54d90dd6b7617c95b7"
        ),
        // MediaTek chips with no ahead-of-time Gemma build (Dimensity 9300, 9400, 9500): Google's
        // NPU-ready but uncompiled package, which LiteRT compiles for the phone's own NPU with the
        // MediaTek compiler plugin on first load (slow once; cached after). Not offered on MT6897
        // (Dimensity 8300/8350): tested on a Motorola Edge 60 Pro, its NeuroPilot 8.2 rejects the
        // package's int4 weights ("special case: int4/int64") and one of its ops, so it can never
        // compile there.
        CatalogModel(
            modelId = "gemma-4-e2b-it-npu-mediatek-jit",
            version = "1",
            displayName = "Gemma 4 E2B (MediaTek NPU)",
            description = "Runs on your Dimensity NPU for lower battery use. The first load compiles it for this phone, which takes a few minutes.",
            category = ModelRole.GENERATION,
            format = ModelFormat.LITERTLM,
            files = listOf(
                ModelFileSpec(
                    fileId = "model", fileName = "gemma-4-E2B-it_NPU_Uncompiled.litertlm",
                    downloadUrl = "$GEMMA4_E2B_NPU_JIT_BASE/gemma-4-E2B-it_NPU_Uncompiled.litertlm", role = ModelFileRole.MODEL,
                    expectedBytes = 2_614_578_293L,
                    sha256 = "b4f86de05f54bda24e8633ef20375b81b3fcc5dace7c6ac9f4269644267ca4aa"
                )
            ),
            totalExpectedBytes = 2_614_578_293L,
            minimumRamBytes = 5_000_000_000L,
            capabilities = setOf("Text generation", "NPU accelerated"),
            sourceUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
            licenseName = "Gemma Terms of Use",
            licenseUrl = "https://ai.google.dev/gemma/terms",
            npuSocs = setOf("mt6989", "mt6991", "mt6993"),
            tier = ModelTier.STANDARD
        ),
        CatalogModel(
            modelId = "qwen3-0.6b-mt6993-npu",
            version = "1",
            displayName = "Qwen3 0.6B (Dimensity 9500 NPU)",
            description = "Compiled for the MediaTek Dimensity 9500 NPU: very fast and power-efficient on this phone.",
            category = ModelRole.GENERATION,
            format = ModelFormat.LITERTLM,
            files = listOf(
                ModelFileSpec(
                    fileId = "model", fileName = "Qwen3-0.6B.mediatek.mt6993.litertlm",
                    downloadUrl = "$QWEN3_06B_BASE/Qwen3-0.6B.mediatek.mt6993.litertlm", role = ModelFileRole.MODEL,
                    expectedBytes = 1_202_913_280L,
                    sha256 = "cc87c8a3df6eb680266a385c22fc3e867824495ff676583716ebe77e6ddd6c97"
                )
            ),
            totalExpectedBytes = 1_202_913_280L,
            minimumRamBytes = 2_000_000_000L,
            capabilities = setOf("Text generation", "NPU accelerated"),
            sourceUrl = "https://huggingface.co/litert-community/Qwen3-0.6B",
            licenseName = "Apache 2.0",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            npuSocs = setOf("mt6993"),
            tier = ModelTier.TINY
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
                    downloadUrl = "$EMBEDDING_GEMMA_BASE/embeddinggemma-300M_seq512_mixed-precision.tflite",
                    role = ModelFileRole.MODEL,
                    expectedBytes = 179_132_472L,
                    sha256 = "ad09e81557203cb0e177abf9bf8727dfe138a7d394aa0f70f0b2ed16432e121a"
                ),
                ModelFileSpec(
                    fileId = "tokenizer",
                    fileName = "sentencepiece.model",
                    downloadUrl = "$EMBEDDING_GEMMA_BASE/sentencepiece.model",
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
        // Udgam Labs' Shruti speech-to-text family on Cactus's needle engine (ShrutiSttEngine):
        // one ~30 MB model per Indian language, each also transcribing English and code-mixed
        // speech. Files land in stt_models/shruti_<lang>/; the active one is
        // SettingsRepository.shrutiModelVariant. Revisions and sha256 pinned to the live files.
        shrutiModel("hi", "Hindi", "Hindi, English and Hinglish", "Shruti", "shruti.cact",
            "0b04196e1df3e32edc233df2ea3385873f2356a6", 29_546_527L, "b189e954aaed4a9fd41acbad3ac05e486381e96325b22a4c695b9ea5153d7065"),
        shrutiModel("mr", "Marathi", "Marathi and English", "shruti-marathi", "shruti-marathi.cact",
            "9424043e0b4b1895dcb790b2e9c556f7a73f717f", 29_659_445L, "f51a92944cc25515e703518cfef5ec9c52bf0db4cf91741530ebdded1df5bb05"),
        shrutiModel("ta", "Tamil", "Tamil and English", "shruti-tamil", "shruti-tamil.cact",
            "15c545c7c20f91b4ec682d2e5e9dc3c11f19df7e", 29_743_335L, "8202f454350fb9ca2d2115ef5e81332c99de93b7c2f7c06fc9d2457995a4dec8"),
        shrutiModel("bn", "Bengali", "Bengali and English", "shruti-bengali", "shruti-bengali.cact",
            "6ddfde9e2c4af50113c55748bd1a5a4d26c5a2bf", 29_742_459L, "0a423c25f534e2099b43bc4e826018978c44a9f523cea19125686883a2a7c572"),
        shrutiModel("te", "Telugu", "Telugu and English (no punctuation, numbers as words)", "shruti-telugu", "shruti-telugu.cact",
            "8757e318917e88cbd403fbabab047d24ab7b0362", 29_742_633L, "197166720f32163bc2102ebf9dc0374d51ec9928175649071da92d943cca0d9e"),
        // LFM3 Omni speech output: the depth decoder, frame embedding and codec graphs that turn
        // the Omni model's hidden states into 24 kHz speech (com.vervan.chat.omni). Needs the
        // "LFM3 Omni 2B" model above too — the language model, audio and vision encoders are read
        // from that .litertlm. Lands in tts_voices/lfm3_omni_en/ as a TTS voice row, so it shows
        // up as a selectable voice engine and unlocks spoken replies and video in Omni Studio.
        CatalogModel(
            modelId = "lfm3-omni-2b-speech",
            version = "1",
            displayName = "LFM3 Omni speech pack",
            description = "Lets LFM3 Omni talk: natural spoken replies, text-to-speech in a male or female voice, " +
                "and voice chat about photos and videos. Requires LFM3 Omni 2B.",
            category = ModelRole.TTS_VOICE,
            format = ModelFormat.OMNI_SPEECH,
            files = listOf(
                ModelFileSpec(
                    fileId = "depth_frame2", fileName = "LFM3-Omni-2B_depth_frame2_f32.tflite",
                    downloadUrl = "$LFM3_OMNI_BASE/speech/LFM3-Omni-2B_depth_frame2_f32.tflite", role = ModelFileRole.MODEL,
                    expectedBytes = 435_046_636L, sha256 = "19646b570f453711a60add2523febb4884e3214e8c3b7f5ca898dc29dae3d60a"
                ),
                ModelFileSpec(
                    fileId = "frame_embed", fileName = "LFM3-Omni-2B_frame_embed_f32.tflite",
                    downloadUrl = "$LFM3_OMNI_BASE/speech/LFM3-Omni-2B_frame_embed_f32.tflite", role = ModelFileRole.AUXILIARY,
                    expectedBytes = 198_281_972L, sha256 = "71b68529124ef770a495554ea6928f42a66c8c0f2756df2452b15b22bdbc707d"
                ),
                ModelFileSpec(
                    fileId = "codec_f12", fileName = "LFM3-Omni-2B_codec_decoder_f12_f32.tflite",
                    downloadUrl = "$LFM3_OMNI_BASE/speech/LFM3-Omni-2B_codec_decoder_f12_f32.tflite", role = ModelFileRole.AUXILIARY,
                    expectedBytes = 152_370_912L, sha256 = "7134523fb54c2fd9b31697373b37e1a5bd1669c3a432ab7896a8a7d070f38ed3"
                )
            ),
            totalExpectedBytes = 785_699_520L,
            minimumRamBytes = 6_000_000_000L,
            capabilities = setOf("Text-to-speech", "Spoken replies", "Video input", "Offline"),
            precision = "float32",
            sourceUrl = "https://huggingface.co/litert-community/LFM3-Omni-2B-LiteRT",
            requiresLicenseAcceptance = true,
            licenseName = "LFM Open License v1.0",
            licenseUrl = "https://huggingface.co/litert-community/LFM3-Omni-2B-LiteRT/blob/main/LICENSE",
            ttsEngine = "LFM3_OMNI",
            ttsLanguage = "en",
            requires64Bit = true
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

    // Every URL is pinned to a commit, never a branch: files are hash-checked, so a re-upload on
    // `main` would otherwise break first-run downloads for everyone until an app update.
    private const val MMS_BASE = "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/4f49fc254f11ddb0e35fbf25b6e48b04463a6779"
    private const val WHISPER_CPP_BASE = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1"
    private const val SUPERTONIC_BASE = "https://huggingface.co/Supertone/supertonic-3/resolve/3cadd1ee6394adea1bd021217a0e650ede09a323"
    private const val GEMMA4_E2B_NPU_JIT_BASE = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/c658da437244f072bf2f1cabc916b60d66d97663"
    private const val GEMMA4_E2B_BASE = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/cbb97a3e73fd566bd089ca762461458c78ed4761"
    // The Snapdragon 8 Elite build was removed upstream after this revision.
    private const val GEMMA4_E2B_NPU_QCOM_BASE = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/9262660a1676eed6d0c477ab1a86344430854664"
    private const val GEMMA4_E4B_BASE = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/e6a3892a8c22cf87ff99583996149cf3cb32452b"
    private const val QWEN3_06B_BASE = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/722a0a54c1301a4b07a9856a9590d56362f0969b"
    private const val LFM3_17B_BASE = "https://huggingface.co/litert-community/LFM3-1.7B-LiteRT/resolve/9bbda264c623f30c905b9c247761ee7463f2b00d"
    private const val LFM3_OMNI_BASE = "https://huggingface.co/litert-community/LFM3-Omni-2B-LiteRT/resolve/a287b6e6e2535de6a4b48b50e5a5a5b9b82632cc"
    private const val LFM25_BASE = "https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/a7a19073d20333b24be0e9b0dad02a0735154376"
    private const val EMBEDDING_GEMMA_BASE = "https://huggingface.co/ghanashyamvtatti/embeddinggemma-300m-litert/resolve/99e742a7683d0db7c496bb4aafb93fa8dc8e151a"

    /** Models this phone can run (chip and ABI), in catalogue order. */
    val availableOnThisDevice: List<CatalogModel> get() = all.filter { it.runsOnThisDevice }

    private fun npuGemma4E2B(socTag: String, chipName: String, fileName: String, base: String, bytes: Long, sha256: String) = CatalogModel(
        modelId = "gemma-4-e2b-it-npu-$socTag",
        version = "1",
        displayName = "Gemma 4 E2B ($chipName NPU)",
        description = "Compiled for the $chipName NPU: faster replies and much lower battery use than the GPU build.",
        category = ModelRole.GENERATION,
        format = ModelFormat.LITERTLM,
        files = listOf(
            ModelFileSpec(fileId = "model", fileName = fileName, downloadUrl = "$base/$fileName", role = ModelFileRole.MODEL, expectedBytes = bytes, sha256 = sha256)
        ),
        totalExpectedBytes = bytes,
        minimumRamBytes = 4_000_000_000L,
        capabilities = setOf("Text generation", "NPU accelerated"),
        sourceUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
        licenseName = "Gemma Terms of Use",
        licenseUrl = "https://ai.google.dev/gemma/terms",
        npuSocs = setOf(socTag),
        tier = ModelTier.STANDARD
    )

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

    private fun shrutiModel(
        lang: String, languageName: String, covers: String, repo: String, fileName: String,
        revision: String, bytes: Long, sha256: String
    ) = CatalogModel(
        modelId = "shruti-$lang",
        version = "1",
        displayName = "Shruti $languageName (offline speech-to-text)",
        description = "Udgam Labs' $languageName speech recognition: $covers. About 30 MB, runs fully on the phone.",
        category = ModelRole.STT_MODEL,
        format = ModelFormat.NEEDLE_CACT,
        files = listOf(
            ModelFileSpec(
                fileId = "model", fileName = fileName,
                downloadUrl = "https://huggingface.co/udgamlabs/$repo/resolve/$revision/$fileName", role = ModelFileRole.MODEL,
                expectedBytes = bytes, sha256 = sha256
            )
        ),
        totalExpectedBytes = bytes,
        capabilities = setOf("Speech-to-text", languageName, "English", "Offline"),
        precision = "W4A8",
        sourceUrl = "https://huggingface.co/udgamlabs/$repo",
        licenseName = "Apache 2.0",
        licenseUrl = "https://huggingface.co/udgamlabs/$repo/blob/$revision/LICENSE",
        enabled = com.vervan.chat.BuildConfig.NEEDLE_AVAILABLE,
        ttsEngine = "SHRUTI",
        ttsLanguage = lang
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
