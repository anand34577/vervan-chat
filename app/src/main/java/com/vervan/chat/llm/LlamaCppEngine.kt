package com.vervan.chat.llm

import android.content.Context
import android.util.Log
import com.vervan.chat.BuildConfig
import com.vervan.chat.modelload.GenerationLoadable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** Load-time knobs beyond the LiteRT-LM-shaped [GenerationLoadable.loadModel] signature — bundled
 * into one object once the count passed enough params that a flat positional call site stopped
 * being readable. All defaults match llama.cpp's own CLI/library defaults (see `llama.h`), except
 * [nBatch]/[nUbatch]/[flashAttention] which follow this app's own already-shipped choices. */
data class LlamaLoadOptions(
    val cpuThreads: Int = 0, // 0 = auto (Runtime.getRuntime().availableProcessors())
    val nBatch: Int = 2048,
    val nUbatch: Int = 512,
    val useMlock: Boolean = false,
    // null = Auto (degrades safely if unsupported), true = force-enabled, false = force-disabled.
    val flashAttention: Boolean? = null,
    val kvCacheType: String = "f16", // "f16" / "q8_0" / "q4_0"
    val vulkanDeviceIndex: Int = 0,
    val ropeFreqBase: Float = 0f, // 0 = from model
    val ropeFreqScale: Float = 0f, // 0 = from model
    val loraPath: String? = null,
    val loraScale: Float = 1.0f
)

/**
 * Wraps a single loaded GGUF model via llama.cpp (see [LlamaCppJni] for the native surface).
 * One instance = one loaded model, mirroring [LlmEngine]'s lifecycle so both can implement
 * [GenerationLoadable] and be routed between by `ModelLoadCoordinator` without either engine
 * knowing the other exists.
 */
class LlamaCppEngine(private val context: Context) : GenerationLoadable {

    @Volatile private var handle: Long = 0L
    private var loadedMmprojPath: String? = null
    private var loadedOptions: LlamaLoadOptions = LlamaLoadOptions()
    private data class LoadKey(
        val modelPath: String,
        val mmprojPath: String?,
        val nCtx: Int,
        val nGpuLayers: Int,
        val nThreads: Int,
        val options: LlamaLoadOptions
    )
    private var loadedKey: LoadKey? = null

    override var loadedModelPath: String? = null
        private set
    override val loadedContextTokens: Int? get() = loadedKey?.nCtx
    override var activeBackend: LlmEngine.ModelBackend = LlmEngine.ModelBackend.UNVERIFIED
        private set
    override var visionEnabled: Boolean = false
        private set
    override val audioEnabled: Boolean = false // no audio-input JNI in this pass
    override val speculativeDecodingActive: Boolean = false // no draft-model decoding in this pass

    /**
     * Set by `ModelLoadCoordinator.doLoadGeneration` immediately before calling [loadModel], for
     * exactly the duration of that single call — [GenerationLoadable.loadModel]'s signature is
     * shared with [LlmEngine], which has no mmproj/llama.cpp-specific concept, so widening the
     * shared interface just for this engine's extra knobs isn't worth it. Not thread-hazardous in
     * practice: every load for this engine already runs under the coordinator's per-engine mutex,
     * so there is never more than one in-flight load reading these fields at a time.
     */
    var pendingMmprojPath: String? = null
    var pendingGpuLayerCount: Int? = null
    var pendingLayerCount: Int? = null
    var pendingOptions: LlamaLoadOptions = LlamaLoadOptions()

    fun load(
        modelPath: String, mmprojPath: String?, nCtx: Int, nGpuLayers: Int, nThreads: Int,
        options: LlamaLoadOptions = LlamaLoadOptions()
    ): LlmEngine.LoadResult {
        check(BuildConfig.LLAMA_CPP_AVAILABLE) {
            "This app build does not include the llama.cpp native runtime. Install a build with GGUF support."
        }
        require(File(modelPath).isFile) { "GGUF model file does not exist: $modelPath" }
        if (mmprojPath != null) require(File(mmprojPath).isFile) { "Vision projector does not exist: $mmprojPath" }
        options.loraPath?.let { require(File(it).isFile) { "LoRA adapter does not exist: $it" } }
        val requestedKey = LoadKey(modelPath, mmprojPath, nCtx, nGpuLayers, nThreads, options)
        Log.i(TAG, "load() requested: ${File(modelPath).name}, nCtx=$nCtx, nGpuLayers=$nGpuLayers, nThreads=$nThreads, mmproj=${mmprojPath != null}")
        if (loadedKey == requestedKey && handle != 0L) {
            Log.i(TAG, "load() short-circuit: '${File(modelPath).name}' already loaded on $activeBackend, reusing")
            return LlmEngine.LoadResult(activeBackend, fellBackToCpu = false)
        }
        close()
        val flashAttnMode = when (options.flashAttention) { null -> -1; true -> 1; false -> 0 } // matches LLAMA_FLASH_ATTN_TYPE_* values
        val newHandle = LlamaCppJni.nativeLoadModel(
            // mmap OFF on Android: ggml's CPU repack pass (interleaving Q4_0 weights into a
            // NEON-friendly layout at load time) needs to rewrite tensor memory in place, which a
            // live mmap can't do — PocketPal/llama.rn make this same call for the same reason.
            modelPath, mmprojPath, nCtx, nGpuLayers, nThreads, /* useMmap = */ false,
            options.nBatch, options.nUbatch, options.useMlock, flashAttnMode,
            options.kvCacheType, options.vulkanDeviceIndex, options.ropeFreqBase, options.ropeFreqScale,
            options.loraPath, options.loraScale
        )
        if (newHandle == 0L) {
            val error = LlamaCppJni.nativeGetLastError() ?: "unknown error"
            Log.e(TAG, "load() FAILED for ${File(modelPath).name}: $error")
            throw IllegalStateException("Could not load '${File(modelPath).name}' with llama.cpp: $error")
        }
        handle = newHandle
        loadedModelPath = modelPath
        loadedMmprojPath = mmprojPath
        loadedOptions = options
        loadedKey = requestedKey
        visionEnabled = mmprojPath != null
        activeBackend = if (nGpuLayers > 0) LlmEngine.ModelBackend.GPU else LlmEngine.ModelBackend.CPU
        Log.i(TAG, "load() SUCCESS: ${File(modelPath).name} on $activeBackend, vision=$visionEnabled")
        return LlmEngine.LoadResult(activeBackend, fellBackToCpu = false)
    }

    /** Reads GGUF metadata from the currently-loaded model (architecture+quant description,
     * training context length, layer count) — meant to be called once right after a load, from
     * `ModelManagerViewModel`'s existing validate-then-close pass, to populate `ModelInfo`'s
     * read-only `modelDesc`/`nativeMaxContext`/`layerCount` fields. Returns null if nothing is
     * loaded or the native call failed. */
    fun readModelInfo(): LlamaModelMetadata? {
        if (handle == 0L) return null
        val raw = LlamaCppJni.nativeGetModelInfo(handle) ?: return null
        val parts = raw.split("|")
        if (parts.size != 3) return null
        return LlamaModelMetadata(
            desc = parts[0].ifBlank { null },
            nativeMaxContext = parts[1].toIntOrNull(),
            layerCount = parts[2].toIntOrNull()
        )
    }

    /**
     * [GenerationLoadable] conformance — translates LiteRT-LM-shaped parameters onto llama.cpp's
     * actual knobs: [maxTokens] becomes the context size (`n_ctx`); [backendPreference] maps onto
     * `n_gpu_layers` (Vulkan offload): GPU_ONLY loads with the full GPU layer count and fails if
     * the driver can't, CPU_ONLY loads with 0, and AUTO tries GPU first and falls back to CPU on
     * any native load failure (out of device memory, shader compile failure, no Vulkan device).
     * The layer count offloaded is [pendingGpuLayerCount] when the user set a per-model override,
     * otherwise the whole model ([pendingLayerCount] + 1 to cover the output layer; llama.cpp
     * clamps anything above the real count). [maxNumImages]/[preferredBackendHint]/
     * [enableSpeculativeDecoding] have no llama.cpp equivalent in this pass and are ignored —
     * vision is determined purely by whether [pendingMmprojPath] is set, and there is no
     * draft-model speculative decoding wired up here.
     */
    override fun loadModel(
        modelPath: String,
        maxTokens: Int,
        maxNumImages: Int,
        backendPreference: LlmEngine.BackendPreference,
        preferredBackendHint: LlmEngine.ModelBackend?,
        enableSpeculativeDecoding: Boolean
    ): LlmEngine.LoadResult {
        val mmprojPath = pendingMmprojPath
        val options = pendingOptions
        val nThreads = options.cpuThreads.takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        if (backendPreference == LlmEngine.BackendPreference.NPU_ONLY) {
            throw IllegalArgumentException("llama.cpp does not support Android NPU execution. Choose Auto, GPU, or CPU.")
        }
        val gpuLayers = pendingGpuLayerCount?.coerceAtLeast(0)
            ?: pendingLayerCount?.plus(1)
            ?: ALL_GPU_LAYERS
        val attempts = when (backendPreference) {
            LlmEngine.BackendPreference.CPU_ONLY -> listOf(0)
            LlmEngine.BackendPreference.GPU_ONLY -> listOf(gpuLayers.coerceAtLeast(1))
            // AUTO: GPU first, CPU as the fallback rung. A per-model override of 0 GPU layers
            // means the user already decided this model stays on CPU, so skip the GPU attempt.
            else -> if (gpuLayers > 0) listOf(gpuLayers, 0) else listOf(0)
        }
        var lastError: Throwable? = null
        for (layers in attempts) {
            try {
                val result = load(modelPath, mmprojPath, maxTokens, layers, nThreads, options)
                return result.copy(fellBackToCpu = layers == 0 && attempts.first() > 0)
            } catch (t: Throwable) {
                lastError = t
                if (layers != attempts.last()) {
                    Log.w(TAG, "loadModel() failed with $layers GPU layers; trying the next AUTO level", t)
                }
            }
        }
        throw lastError ?: IllegalStateException("No llama.cpp load attempt was made")
    }

    /** Streaming generation — own method, not part of [GenerationLoadable] (which only covers
     * load/state), called directly once a caller has resolved this engine as the active one for
     * a given model. [imagePath] is only meaningful if the loaded model has an mmproj/[visionEnabled].
     * [chatTemplateOverride] is a `llama_chat_builtin_templates()` name (e.g. "chatml") or raw
     * custom Jinja text — null uses the GGUF's own embedded `tokenizer.chat_template`.
     * [systemPrompt], when non-null/non-blank, is sent as its own `"system"` chat-template turn
     * instead of being folded into [prompt]'s `"user"` turn — most instruction-tuned models treat
     * system content very differently (higher trust, different RLHF shaping) from user content,
     * so this matters for behavior fidelity, not just formatting. */
    fun generate(
        prompt: String,
        imagePath: String? = null,
        temperature: Float = 0.8f,
        topP: Float = 0.95f,
        topK: Int = 40,
        randomSeed: Int? = null,
        maxTokens: Int = 1024,
        minP: Float = 0.05f,
        repeatPenalty: Float = 1.1f,
        repeatLastN: Int = 64,
        chatTemplateOverride: String? = null,
        assistantPrefill: String? = null,
        systemPrompt: String? = null,
        // Hard cap on reasoning tokens before the native loop force-injects `</think>` — only
        // acts when [assistantPrefill] left an open `<think>` block. -1 = no cap (see
        // nativeGenerate). This is the real reasoning-budget control llama.cpp's raw
        // (non-Jinja) template path otherwise can't express.
        reasoningBudget: Int = -1
    ): Flow<String> = callbackFlow flow@{
        // Labeled + this@flow.close() throughout, not bare close() — this class has its own
        // close() (GenerationLoadable conformance), which would otherwise shadow/collide with
        // ProducerScope's close() inside this lambda (same reasoning as LlmEngine.generate()).
        val activeHandle = handle
        if (activeHandle == 0L) {
            this@flow.close(IllegalStateException("No model loaded"))
            return@flow
        }
        val callback = LlamaCppJni.TokenCallback { token -> trySend(token) }
        // nativeGenerate() is a blocking native call with no cancellation checks of its own
        // besides polling session->cancelled once per token (see nativeCancelGeneration's doc
        // comment: "signals ... on another thread"). Running it inline in this producer block
        // would block awaitClose from ever being registered until it returns on its own —
        // Stop would then have no way to reach nativeCancelGeneration until generation finished
        // naturally. Launching it on its own coroutine lets awaitClose register immediately, so
        // cancelling this flow calls nativeCancelGeneration right away instead of after the fact.
        launch(Dispatchers.IO) {
            try {
                val error = LlamaCppJni.nativeGenerate(
                    activeHandle, prompt, imagePath, temperature, topP, topK, minP,
                    repeatPenalty, repeatLastN, randomSeed ?: DEFAULT_SEED, maxTokens, chatTemplateOverride,
                    assistantPrefill, systemPrompt, reasoningBudget, callback
                )
                if (error != null) throw IllegalStateException(error)
                this@flow.close()
            } catch (t: Throwable) {
                Log.e(TAG, "generate() FAILED", t)
                this@flow.close(t)
            }
        }
        awaitClose { LlamaCppJni.nativeCancelGeneration(activeHandle) }
        // Same reasoning as LlmEngine.generate(): callbackFlow's default 64-slot buffer lets
        // trySend silently drop tokens when the native producer outruns the collector.
    }.buffer(kotlinx.coroutines.channels.Channel.UNLIMITED)

    // handle is a plain Long read/written on whichever thread calls load()/close(); the native
    // flag it signals is a std::atomic<bool> safe to set from any thread (see
    // nativeCancelGeneration's own doc comment) — no mutex needed here, which is the whole
    // point: this must work without waiting on llamaCppMutex, which generate() holds for the
    // full duration of a response.
    override fun cancelActiveGeneration() {
        if (handle != 0L) LlamaCppJni.nativeCancelGeneration(handle)
    }

    override fun close() {
        if (handle != 0L) {
            loadedModelPath?.let { path -> Log.i(TAG, "close() unloading ${File(path).name} (was on $activeBackend)") }
            LlamaCppJni.nativeCloseModel(handle)
        }
        handle = 0L
        loadedModelPath = null
        loadedMmprojPath = null
        loadedOptions = LlamaLoadOptions()
        loadedKey = null
        activeBackend = LlmEngine.ModelBackend.UNVERIFIED
        visionEnabled = false
    }

    companion object {
        private const val TAG = "LlamaCppEngine"
        private const val DEFAULT_SEED = -1

        /** "Offload everything" when the model's real layer count isn't known yet — llama.cpp
         * clamps n_gpu_layers to the model's actual count, so any large value means "all". */
        private const val ALL_GPU_LAYERS = 999

        /** `llama_chat_builtin_templates()` names, for the chat-template-override dropdown —
         * static to the llama.cpp build, not per-model, so fetched once and cached. */
        val builtinChatTemplates: List<String> by lazy {
            if (!BuildConfig.LLAMA_CPP_AVAILABLE) emptyList()
            else runCatching { LlamaCppJni.nativeListChatTemplates().toList() }.getOrDefault(emptyList())
        }
    }
}

data class LlamaModelMetadata(val desc: String?, val nativeMaxContext: Int?, val layerCount: Int?)
