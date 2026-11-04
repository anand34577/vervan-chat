package com.vervan.chat.omni

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.omni.runtime.Lfm3OmniEngine
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the one LFM3 Omni speech runtime ([Lfm3OmniEngine]) the app can have open, for everything
 * the LiteRT-LM chat path cannot do with the same model: spoken replies (text and speech
 * interleaved), text-to-speech with a chosen voice, multi-turn voice conversations, and video input.
 *
 * The runtime reads the language model, audio encoder and vision encoder out of the same
 * `LFM3-Omni-2B_int8.litertlm` the chat uses (downloaded as "LFM3 Omni 2B"), plus the three speech
 * graphs of the "LFM3 Omni speech pack" download. The first open copies the bundle's sections into
 * their own files (LiteRT opens graphs by path) — about 3.2 GB, once — which [status] reports and
 * [deletePrepared] gives back.
 *
 * The engine is not thread-safe: every call goes through [use], which runs on one dedicated thread
 * under [mutex].
 */
object OmniRuntime {
    private const val TAG = "OmniRuntime"
    const val SPEECH_ENGINE = "LFM3_OMNI"
    const val SPEECH_LANGUAGE = "en"
    const val CHAT_CATALOG_ID = "lfm3-omni-2b-litert"

    /** Free space kept on top of the sections' size before preparing. */
    private const val PREPARE_MARGIN_BYTES = 500_000_000L

    @OptIn(ExperimentalCoroutinesApi::class)
    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "lfm3-omni").apply { isDaemon = true } }
        .asCoroutineDispatcher()
    private val mutex = Mutex()
    @Volatile private var engine: Lfm3OmniEngine? = null

    sealed interface State {
        data object Closed : State
        data class Loading(val step: String) : State
        data class Ready(val placement: String) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Closed)
    val state: StateFlow<State> = _state.asStateFlow()

    data class Status(
        /** The LFM3-Omni-2B .litertlm, when installed. */
        val bundle: File?,
        /** The speech pack's directory, when downloaded. */
        val speechDir: File?,
        /** Whether the one-time section copy is done. */
        val prepared: Boolean,
        /** Disk the prepared copy and the runtime caches use now. */
        val preparedBytes: Long,
        /** Disk the one-time preparation still needs (0 when prepared). */
        val prepareNeedsBytes: Long,
        val freeBytes: Long,
    ) {
        val installed: Boolean get() = bundle != null && speechDir != null
    }

    suspend fun status(app: VervanApp): Status {
        val bundle = findBundle(app)
        val speech = findSpeechDir(app)
        val sectionsDir = File(app.filesDir, "lfm3_sections")
        val prepared = isPrepared(app)
        val used = dirSize(sectionsDir) + dirSize(File(app.filesDir, "xnnpack_cache"))
        val needs = if (prepared) 0L else Lfm3OmniEngine.bundleBytes(app)
        return Status(bundle, speech, prepared, used, needs, StatFs(app.filesDir.absolutePath).availableBytes)
    }

    /** The installed Omni bundle: the catalog download, or any imported model file of exactly the
     *  bundle's size (the runtime checks its sections by sha256 on first use). */
    suspend fun findBundle(app: VervanApp): File? {
        val bytes = Lfm3OmniEngine.bundleBytes(app)
        val dao = app.container.db.modelDao()
        dao.findByCatalogEntry(CHAT_CATALOG_ID, "1")?.filePath?.let(::File)
            ?.takeIf { it.isFile && it.length() == bytes }?.let { return it }
        return dao.observeModels().first()
            .filter { it.role == ModelRole.GENERATION }
            .map { File(it.filePath) }
            .firstOrNull { it.isFile && it.length() == bytes && it.name.endsWith(".litertlm") }
    }

    suspend fun findSpeechDir(app: VervanApp): File? {
        val row = app.container.db.ttsVoiceModelDao().getByEngine(SPEECH_ENGINE, SPEECH_LANGUAGE)
        val dir = row?.filePath?.let(::File) ?: File(app.filesDir, "tts_voices/${SPEECH_ENGINE.lowercase()}_$SPEECH_LANGUAGE")
        val needed = listOf(Lfm3OmniEngine.DEPTH_FRAME2, Lfm3OmniEngine.FRAME, Lfm3OmniEngine.CODEC)
        return dir.takeIf { d -> needed.all { File(d, it).isFile } }
    }

    private fun isPrepared(context: Context): Boolean {
        val root = File(context.filesDir, "lfm3_sections")
        val dir = root.listFiles()?.firstOrNull { it.isDirectory } ?: return false
        return listOf("decoder", "embedder", "audio", "vision_encoder", "vision_adapter").all { File(dir, "$it.verified").isFile }
    }

    /**
     * Runs [block] on the open engine, opening it first when needed. [exclusive] unloads the chat
     * model before an open (Omni Studio: the runtime needs ~1.2 GB for its language model on top of
     * the speech graphs); the voice reply path passes false and relies on the runtime's own free-memory
     * checks instead, so a spoken reply never yanks the chat model out from under the conversation.
     */
    suspend fun <T> use(app: VervanApp, exclusive: Boolean, block: (Lfm3OmniEngine) -> T): T = mutex.withLock {
        val e = engine ?: openLocked(app, exclusive)
        withContext(thread) { block(e) }
    }

    /** Opens the engine now (for a screen that wants to show progress before the first question). */
    suspend fun open(app: VervanApp, exclusive: Boolean = true) {
        mutex.withLock { if (engine == null) openLocked(app, exclusive) }
    }

    private suspend fun openLocked(app: VervanApp, exclusive: Boolean): Lfm3OmniEngine {
        val bundle = findBundle(app)
            ?: fail("Download \"LFM3 Omni 2B\" in Models first.")
        val speech = findSpeechDir(app)
            ?: fail("Download the \"LFM3 Omni speech pack\" in Models first.")
        if (!isPrepared(app)) {
            val free = StatFs(app.filesDir.absolutePath).availableBytes
            val need = Lfm3OmniEngine.bundleBytes(app) + PREPARE_MARGIN_BYTES
            if (free < need) {
                fail("LFM3 Omni needs %.1f GB of free space once to prepare its files; %.1f GB is free."
                    .format(need / 1e9, free / 1e9))
            }
        }
        if (exclusive) {
            _state.value = State.Loading("Unloading the chat model")
            runCatching { app.container.modelLoadCoordinator.unload(ModelRole.GENERATION) }
                .onFailure { Log.w(TAG, "unload before Omni open failed", it) }
        }
        Lfm3OmniEngine.searchDirs = listOf(bundle.parentFile, speech).filterNotNull()
        _state.value = State.Loading("Loading LFM3 Omni")
        return try {
            val e = withContext(thread) {
                Lfm3OmniEngine.open(app, Lfm3OmniEngine.Config()) { step -> _state.value = State.Loading(step) }
            }
            engine = e
            _state.value = State.Ready(e.placement())
            e
        } catch (t: Throwable) {
            Log.w(TAG, "LFM3 Omni open failed", t)
            _state.value = State.Failed(t.message ?: "LFM3 Omni could not be loaded.")
            throw t
        }
    }

    private fun fail(message: String): Nothing {
        _state.value = State.Failed(message)
        throw IllegalStateException(message)
    }

    /** Closes the engine and frees its memory (the prepared files stay). */
    suspend fun close() {
        mutex.withLock {
            val e = engine ?: return@withLock
            engine = null
            withContext(thread) { runCatching { e.close() } }
            _state.value = State.Closed
        }
    }

    /** Closes the engine and deletes the prepared sections and caches (~3+ GB). The next open
     *  prepares them again. */
    suspend fun deletePrepared(app: VervanApp) {
        close()
        mutex.withLock {
            withContext(thread) {
                File(app.filesDir, "lfm3_sections").deleteRecursively()
                File(app.filesDir, "xnnpack_cache").deleteRecursively()
            }
        }
    }

    val isOpen: Boolean get() = engine != null

    private fun dirSize(dir: File): Long =
        if (!dir.exists()) 0L else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}
