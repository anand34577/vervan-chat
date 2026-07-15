package com.vervan.chat.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LlmEngine(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    // A fresh conversation is created per generated turn; see [generate].
    // every single turn — see the comment on [generate] for why that rebuild was corrupting the
    // engine after one exchange.
    // Purely for correlating log lines from overlapping/rapid generate() calls.
    private var generateCallCounter = 0
    private val sendInFlight = AtomicBoolean(false)
    private val stoppedSinceLastSend = AtomicBoolean(false)
    var loadedModelPath: String? = null
        private set
    var activeBackend: ModelBackend = ModelBackend.UNVERIFIED
        private set
    var visionEnabled: Boolean = false
        private set
    var audioEnabled: Boolean = false
        private set
    // Whether the currently-loaded model actually ran with MTP (speculative decoding) active —
    // distinct from [ModelInfo.mtpSupported]/[ModelInfo.mtpEnabled], which are the detected
    // capability and the user's on/off choice; this is what actually happened on this load.
    var speculativeDecodingActive: Boolean = false
        private set

    enum class ModelBackend { GPU, CPU, NPU, UNVERIFIED }
    enum class BackendPreference { AUTO, GPU_ONLY, CPU_ONLY, NPU_ONLY }
    data class LoadResult(val backend: ModelBackend, val fellBackToCpu: Boolean)

    /**
     * Queries the model file directly for MTP (speculative decoding / "multi-token prediction")
     * support via LiteRT-LM's [Capabilities] API — this is a static property of the model
     * package, not something that depends on backend or load settings, so it's safe to call
     * before ever loading the model (e.g. right after import).
     */
    fun detectSpeculativeDecodingSupport(modelPath: String): Boolean {
        return try {
            Capabilities(modelPath).use { it.hasSpeculativeDecodingSupport() }
        } catch (e: Throwable) {
            Log.w(TAG, "detectSpeculativeDecodingSupport() failed for ${File(modelPath).name}: ${e.message}")
            false
        }
    }

    @OptIn(ExperimentalApi::class)
    fun load(
        modelPath: String,
        maxTokens: Int = 1024,
        maxNumImages: Int = 1,
        backendPreference: BackendPreference = BackendPreference.AUTO,
        preferredBackendHint: ModelBackend? = null,
        // Only takes effect on the GPU backend attempt, matching LiteRT-LM's own MTP support
        // surface — CPU/NPU decoding doesn't have a speculative-decoding path today.
        enableSpeculativeDecoding: Boolean = false
    ): LoadResult {
        Log.i(TAG, "load() requested: ${File(modelPath).name}, maxTokens=$maxTokens, maxNumImages=$maxNumImages, backendPreference=$backendPreference, preferredBackendHint=$preferredBackendHint")
        if (loadedModelPath == modelPath && engine != null && conversation != null && !stoppedSinceLastSend.get()) {
            Log.i(TAG, "load() short-circuit: '${File(modelPath).name}' already loaded on $activeBackend, reusing")
            return LoadResult(activeBackend, fellBackToCpu = false)
        }
        close()
        // A backend explicitly picked by the user (GPU_ONLY/CPU_ONLY/NPU_ONLY) never falls back
        // to another backend — a single entry here means "load only on this, error if it can't."
        // Only AUTO tries more than one hardware backend.
        val backendOrder = when (backendPreference) {
            BackendPreference.GPU_ONLY -> listOf(Backend.GPU())
            BackendPreference.CPU_ONLY -> listOf(Backend.CPU())
            BackendPreference.NPU_ONLY -> listOf(Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir))
            // Default AUTO order is NPU first, then GPU, then CPU fallback (user ask). A hint
            // from the last backend that actually worked for this model still jumps the queue,
            // so a model known to only run on CPU doesn't re-attempt NPU/GPU every single load.
            BackendPreference.AUTO -> {
                val npu = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
                when (preferredBackendHint) {
                    ModelBackend.CPU -> listOf(Backend.CPU(), npu, Backend.GPU())
                    ModelBackend.GPU -> listOf(Backend.GPU(), npu, Backend.CPU())
                    ModelBackend.NPU -> listOf(npu, Backend.GPU(), Backend.CPU())
                    else -> listOf(npu, Backend.GPU(), Backend.CPU())
                }
            }
        }
        Log.i(TAG, "load() backend attempt order: ${backendOrder.map { it.name }}")
        var gpuFailed = false
        var lastError: Throwable? = null
        // Try each backend at full capability (vision + audio) first, then the same backend
        // with audio dropped, then with vision dropped too — a model whose .task/.litertlm
        // package has no audio (or vision) encoder can fail Engine.initialize() outright when
        // an unsupported modality backend is requested, and previously we asked for
        // audioBackend unconditionally on every load regardless of what the model actually
        // supports. This mirrors spec §2.5's graceful-degradation principle instead of treating
        // that as a hard failure and moving straight to the next hardware backend.
        for (backend in backendOrder) {
            for (capability in listOf(Capability.FULL, Capability.NO_AUDIO, Capability.TEXT_ONLY)) {
                // Try with MTP first (if requested and on GPU), then retry the same backend/
                // capability without it — a GPU init failure caused specifically by speculative
                // decoding shouldn't take the whole GPU backend out of the running, it should
                // just fall back to plain GPU decoding (user ask: disable MTP, keep loading).
                val mtpAttempts = if (enableSpeculativeDecoding && backend is Backend.GPU) listOf(true, false) else listOf(false)
                for (useMtp in mtpAttempts) {
                    val attemptStart = System.currentTimeMillis()
                    Log.i(TAG, "load() attempting ${backend.name} / $capability for ${File(modelPath).name} (mtp=$useMtp)")
                    // Global flag, not a per-engine setting — set immediately before init and reset
                    // right after, same pattern the reference implementation uses, so it can't leak
                    // into an unrelated load elsewhere.
                    ExperimentalFlags.enableSpeculativeDecoding = useMtp
                    try {
                        val newEngine = Engine(
                            EngineConfig(
                                modelPath = modelPath,
                                backend = backend,
                                visionBackend = if (maxNumImages > 0 && capability != Capability.TEXT_ONLY) Backend.GPU() else null,
                                audioBackend = if (capability == Capability.FULL) Backend.CPU() else null,
                                maxNumTokens = maxTokens,
                                cacheDir = context.cacheDir.absolutePath
                            )
                        )
                        newEngine.initialize()
                        engine = newEngine
                        conversation = newEngine.createConversation(ConversationConfig())
                        loadedModelPath = modelPath
                        activeBackend = when (backend) {
                            is Backend.GPU -> ModelBackend.GPU
                            is Backend.NPU -> ModelBackend.NPU
                            else -> ModelBackend.CPU
                        }
                        visionEnabled = capability != Capability.TEXT_ONLY && maxNumImages > 0
                        audioEnabled = capability == Capability.FULL
                        speculativeDecodingActive = useMtp
                        stoppedSinceLastSend.set(false)
                        Log.i(
                            TAG,
                            "load() SUCCESS: ${File(modelPath).name} on ${backend.name} ($capability) in " +
                                "${System.currentTimeMillis() - attemptStart}ms — vision=$visionEnabled audio=$audioEnabled mtp=$speculativeDecodingActive"
                        )
                        return LoadResult(activeBackend, fellBackToCpu = gpuFailed && activeBackend == ModelBackend.CPU)
                    } catch (e: Throwable) {
                        close()
                        lastError = e
                        if (backend is Backend.GPU) gpuFailed = true
                        Log.w(
                            TAG,
                            "load() FAILED ${backend.name} ($capability) for ${File(modelPath).name} after " +
                                "${System.currentTimeMillis() - attemptStart}ms: ${e::class.simpleName}: ${e.message}",
                            e
                        )
                    }
                }
            }
        }
        ExperimentalFlags.enableSpeculativeDecoding = false
        Log.e(TAG, "load() exhausted all backends for ${File(modelPath).name} with preference=$backendPreference", lastError)
        throw IllegalStateException(
            "Could not load '${File(modelPath).name}' with LiteRT-LM. Error: ${lastError?.message ?: lastError ?: "unknown error"}",
            lastError
        )
    }

    private enum class Capability { FULL, NO_AUDIO, TEXT_ONLY }

    fun generate(
        prompt: String,
        imagePath: String? = null,
        audioPath: String? = null,
        temperature: Float = 0.8f,
        topP: Float = 0.95f,
        topK: Int = 40,
        randomSeed: Int? = null
    ): Flow<String> = callbackFlow flow@{
        val conv = conversation ?: run {
            Log.e(TAG, "generate() called with no model loaded")
            throw IllegalStateException("No model loaded")
        }
        val contents = mutableListOf<Content>()
        imagePath?.let { path ->
            val image = File(path).takeIf { it.isFile }
                ?: throw IllegalStateException("Could not read image at $path")
            // Attachments are normalized once at import. Sending their encoded bytes avoids
            // another full Bitmap allocation and PNG recompression on every generation.
            contents += Content.ImageBytes(image.readBytes())
        }
        audioPath?.let { path -> contents += Content.AudioBytes(File(path).readBytes()) }
        if (prompt.isNotBlank()) contents += Content.Text(prompt)

        val sampler = SamplerConfig(
            topK = topK.coerceIn(1, MAX_TOP_K_CEILING),
            topP = topP.coerceIn(0.1f, 1f).toDouble(),
            temperature = temperature.coerceIn(0f, 2f).toDouble(),
            seed = randomSeed ?: 0
        )
        // Always build a fresh Conversation for this turn instead of reusing the previous one.
        // This app reconstructs the *entire* conversation history as prompt text on every call
        // (buildPrompt() re-embeds persona/memory/retrieved-sources/history fresh each turn,
        // since any of those can change turn to turn) — reusing one long-lived native Conversation
        // meant its own internal KV-cache turn memory kept accumulating IN ADDITION to our
        // re-sent history text, double-counting context every turn until it overflowed the
        // model's max_tokens and segfaulted natively a few messages in (SIGSEGV inside
        // liblitertlm_jni.so around message #6 in the field).
        //
        // The earlier "reuse" version of this code existed to fix a *different* bug — recreating
        // synchronously on every call previously corrupted the native session after the first
        // exchange. The actual cause of that was the blocking, same-thread `conversation?.close()`
        // racing the prior generation's native teardown, not the recreation itself. So: keep
        // recreating every turn (required for correctness given how prompts are built here), but
        // close the outgoing conversation on a background thread, fire-and-forget, the same way
        // the upstream reference implementation's closeConversationAsync avoids blocking on a
        // native session that might still be unwinding.
        val previousConversation = conversation
        conversation = engine?.createConversation(ConversationConfig(samplerConfig = sampler))
        if (previousConversation != null) {
            Thread {
                runCatching { previousConversation.close() }
                    .onFailure { Log.w(TAG, "generate() background close of previous conversation threw", it) }
            }.start()
        }
        val activeConversation = conversation ?: conv
        val callId = ++generateCallCounter
        Log.i(
            TAG,
            "generate() #$callId sending: promptLen=${prompt.length}, hasImage=${imagePath != null}, " +
                "hasAudio=${audioPath != null}"
        )
        var receivedAnyChunk = false
        val startedAt = System.currentTimeMillis()
        sendInFlight.set(true)
        activeConversation.sendMessageAsync(
            Contents.of(contents),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    textFrom(message).takeIf { it.isNotEmpty() }?.let {
                        receivedAnyChunk = true
                        trySend(it)
                    }
                }

                override fun onDone() {
                    Log.i(TAG, "generate() #$callId done in ${System.currentTimeMillis() - startedAt}ms, receivedAnyChunk=$receivedAnyChunk")
                    sendInFlight.set(false)
                    this@flow.close()
                }

                override fun onError(throwable: Throwable) {
                    Log.e(
                        TAG,
                        "generate() #$callId FAILED after ${System.currentTimeMillis() - startedAt}ms, " +
                            "receivedAnyChunk=$receivedAnyChunk: ${throwable::class.simpleName}: ${throwable.message}",
                        throwable
                    )
                    sendInFlight.set(false)
                    this@flow.close(throwable)
                }
            }
        )
        awaitClose {
            if (sendInFlight.getAndSet(false)) {
                Log.w(TAG, "generate() #$callId cancelled mid-flight (awaitClose)")
                stoppedSinceLastSend.set(true)
                runCatching { activeConversation.cancelProcess() }
            }
        }
    }

    fun close() {
        if (loadedModelPath != null) Log.i(TAG, "close() unloading ${File(loadedModelPath!!).name} (was on $activeBackend)")
        runCatching { conversation?.close() }.onFailure { Log.w(TAG, "close() conversation.close() threw", it) }
        runCatching { engine?.close() }.onFailure { Log.w(TAG, "close() engine.close() threw", it) }
        conversation = null
        engine = null
        loadedModelPath = null
        activeBackend = ModelBackend.UNVERIFIED
        visionEnabled = false
        audioEnabled = false
        speculativeDecodingActive = false
        sendInFlight.set(false)
    }

    private fun close(error: Throwable) {
        Log.e(TAG, "LiteRT-LM generation failed", error)
        close()
    }

    companion object {
        private const val TAG = "LlmEngine"
        private const val MAX_TOP_K_CEILING = 128

        fun mediaPipeCompatibilityIssue(modelPath: String): String? =
            if (File(modelPath).extension.lowercase() in setOf("task", "litertlm", "litert")) null
            else "LiteRT-LM chat models must be .task, .litertlm, or .litert packages."

        private fun textFrom(message: Message): String =
            message.contents.contents.joinToString("") { content ->
                when (content) {
                    is Content.Text -> content.text
                    else -> content.toString()
                }
            }
    }
}
