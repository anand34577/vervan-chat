package com.vervan.chat.llm

/**
 * Raw JNI surface for the llama.cpp bridge (see `app/src/main/cpp/llama_bridge.cpp`). Kept as a
 * separate object from [LlamaCppEngine] so the native contract — what's actually declared
 * `external` and must have a matching `JNIEXPORT` in the bridge — is easy to find and audit on
 * its own, the same reasoning `GenerationLoadable`/`EmbeddingLoadable` keep the coordinator's
 * dependency surface separate from each engine's full implementation.
 *
 * `System.loadLibrary` runs once, in the companion `init` block, the first time this object is
 * referenced — matching the standard Android JNI convention (see any `external fun` sample).
 * This will throw `UnsatisfiedLinkError` if the app was built without `llamacpp.dir` set (see
 * `app/build.gradle.kts`) or if the native build hasn't produced `libvervan_llama_jni.so` yet —
 * callers must only reach this object once a GGUF model is actually being loaded, never at
 * app startup, so a machine without llama.cpp built can still run every other feature.
 */
internal object LlamaCppJni {
    init {
        System.loadLibrary("vervan_llama_jni")
    }

    /** Invoked once per generated token, synchronously, on the calling thread — [LlamaCppEngine]
     * wraps this into a `Flow<String>` via `callbackFlow`, mirroring how `LlmEngine.generate()`
     * wraps LiteRT-LM's `MessageCallback`. */
    fun interface TokenCallback {
        fun onToken(token: String)
    }

    /** Must be called once before the first [nativeLoadModel], with the app's own
     * `applicationInfo.nativeLibraryDir` — the on-device directory the native build's CPU/Vulkan
     * backend plugin `.so`s land in (see `scripts/build-llama-android-vulkan.ps1`'s
     * `GGML_CPU_ALL_VARIANTS`/`GGML_BACKEND_DL` build). Native-side backend registration happens
     * lazily on the first call via `ggml_backend_load_all_from_path()`, scoring every
     * `libggml-cpu-*.so` in that directory against the device's actual CPU features and loading
     * the best match — this is what replaces one build-time `-march` choice with per-device
     * runtime dispatch. Cheap to call repeatedly; only the first call's directory is used.
     * [LlamaCppEngine.load] calls this before every load. */
    external fun nativeInit(nativeLibDir: String)

    /** Returns an opaque native context handle, or 0 on failure — call [nativeGetLastError] to
     * find out why. [mmprojPath] is non-null only for a vision-capable model (initializes an
     * `mtmd_context` alongside the base model). [useMmap] should stay true except for
     * troubleshooting — mirrors llama.cpp's own default. [flashAttnMode] is a raw
     * `LLAMA_FLASH_ATTN_TYPE_*` value (-1 Auto / 0 Disabled / 1 Enabled). [kvCacheType] is
     * "f16"/"q8_0"/"q4_0". [ropeFreqBase]/[ropeFreqScale] of 0 mean "use the model's own". */
    external fun nativeLoadModel(
        modelPath: String,
        mmprojPath: String?,
        nCtx: Int,
        nGpuLayers: Int,
        nThreads: Int,
        useMmap: Boolean,
        nBatch: Int,
        nUbatch: Int,
        useMlock: Boolean,
        flashAttnMode: Int,
        kvCacheType: String,
        vulkanDeviceIndex: Int,
        ropeFreqBase: Float,
        ropeFreqScale: Float,
        loraPath: String?,
        loraScale: Float
    ): Long

    /** Blocks the calling thread until generation completes, cancels, or errors — callers must
     * invoke this off the main thread (same requirement as `LlmEngine.load()`/native decode).
     * [imagePath] non-null routes the prompt through mtmd tokenization first; only valid if the
     * handle was loaded with a non-null `mmprojPath`. [assistantPrefill], when non-null/non-empty,
     * is appended to the prompt verbatim right after the chat template's assistant-turn-start
     * tokens, before generation begins — e.g. `"<think>\n\n</think>\n\n"` forces thinking off by
     * making the model continue from an already-closed reasoning block, since a plain text
     * instruction in the prompt is only ever a request the model can ignore. [systemPrompt], when
     * non-null/non-blank, becomes its own `"system"`-role chat-template turn ahead of [prompt]'s
     * `"user"` turn, instead of [prompt] alone being wrapped as the only message.
     * [reasoningBudget] is the hard cap on reasoning tokens: once the model has generated this many
     * tokens still inside an open `<think>` block (one left open by [assistantPrefill]), the native
     * loop force-injects `</think>` so the model must start answering. -1 disables the cap. It has
     * no effect unless [assistantPrefill] actually opened a `<think>` block.
     *
     * [messages] is the optional full conversation as a flat `[role0, content0, role1, content1,
     * …]` array. When non-null it **replaces** [prompt]/[systemPrompt]: each pair becomes its own
     * chat-template turn, so a real multi-turn conversation reaches the model with the turn
     * boundaries it was trained on instead of being flattened into one `"user"` turn. Roles are
     * passed to the template verbatim, so `"tool"`/`"function"` turns work without another
     * signature change. Null keeps the original single-user-turn behavior for every caller that
     * has genuinely assembled one prompt string (the native chat path, voice, tools). */
    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        imagePath: String?,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        repeatPenalty: Float,
        repeatLastN: Int,
        seed: Int,
        maxTokens: Int,
        chatTemplateOverride: String?,
        assistantPrefill: String?,
        systemPrompt: String?,
        reasoningBudget: Int,
        messages: Array<String>?,
        callback: TokenCallback
    ): String?

    /** Signals an in-progress [nativeGenerate] call on another thread to stop after its current
     * token — safe to call from any thread. */
    external fun nativeCancelGeneration(handle: Long)

    /** Frees the model/context/mtmd state (and any attached LoRA adapter). Safe to call on an
     * already-closed (0) handle. */
    external fun nativeCloseModel(handle: Long)

    /** Human-readable detail for the most recent failure on the calling thread, or null. */
    external fun nativeGetLastError(): String?

    /** `"$desc|$nCtxTrain|$nLayer"` for the currently-loaded model on [handle], or null if
     * nothing is loaded — see [LlamaCppEngine.readModelInfo]. */
    external fun nativeGetModelInfo(handle: Long): String?

    /** `llama_chat_builtin_templates()` names (e.g. "chatml", "llama3") — static to the build,
     * not model-specific. */
    external fun nativeListChatTemplates(): Array<String>
}
