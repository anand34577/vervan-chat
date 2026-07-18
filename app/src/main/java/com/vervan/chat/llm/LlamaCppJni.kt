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
     * `"user"` turn, instead of [prompt] alone being wrapped as the only message. */
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
