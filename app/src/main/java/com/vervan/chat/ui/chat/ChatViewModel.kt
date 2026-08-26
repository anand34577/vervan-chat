package com.vervan.chat.ui.chat

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.audio.AudioNormalizer
import com.vervan.chat.data.branch.BranchUtil
import com.vervan.chat.data.repo.MessageAttachmentCleanup
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.MessageState
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.traits
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.Folder
import com.vervan.chat.data.db.entities.ToolAudit
import com.vervan.chat.data.audit.ToolAuditSanitizer
import com.vervan.chat.data.db.entities.Workspace
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.data.db.entities.displayName
import com.vervan.chat.data.db.entities.reconcileCapabilities
import com.vervan.chat.model.ChatDefaults
import com.vervan.chat.model.DocumentImportOutcome
import com.vervan.chat.model.ImportLimits
import com.vervan.chat.model.copyToLimited
import com.vervan.chat.llm.ModelProfileType
import com.vervan.chat.llm.ModelProfiles
import com.vervan.chat.llm.PromptPolicy
import com.vervan.chat.llm.TitleGenerator
import com.vervan.chat.modelload.LoadTrigger
import com.vervan.chat.modelload.ModelLoadInfo
import com.vervan.chat.modelload.ModelLoadPhase
import com.vervan.chat.retrieval.RetrievalMode
import com.vervan.chat.llm.ThinkingPolicy
import com.vervan.chat.llm.ThinkingSpec
import com.vervan.chat.llm.ThinkingParser
import com.vervan.chat.llm.ClarificationParser
import com.vervan.chat.llm.RemoteRequestOptions
import com.vervan.chat.llm.RemoteToolDefinition
import com.vervan.chat.retrieval.SourcePassage
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.validation.InputLimits
import com.vervan.chat.tools.ToolCallParser
import com.vervan.chat.tools.ToolRegistry
import com.vervan.chat.tools.ToolResult
import com.vervan.chat.tools.ToolRisk
import com.vervan.chat.tools.withImagePathContext
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModel(private val app: VervanApp, private val chatId: String) : ViewModel() {

    private val db = app.container.db
    private val engine = app.container.llmEngine
    private val retrievalEngine = app.container.retrievalEngine

    /** Whichever engine [model] actually loads through — used for reading loaded-state
     * (visionEnabled/audioEnabled/activeBackend) once the coordinator confirms it's resident. */
    private fun activeEngineFor(model: ModelInfo) =
        if (model.engine == com.vervan.chat.data.db.entities.ModelEngine.LLAMA_CPP) app.container.llamaCppEngine else engine

    /** Every message in every branch — raw input to [BranchUtil]. */
    val allMessages: StateFlow<List<Message>> = db.messageDao().observeMessages(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chat: StateFlow<Chat?> = db.chatDao().observeChat(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Root-to-active-leaf path — what's actually rendered and fed to the model. */
    val messages: StateFlow<List<Message>> = combine(allMessages, chat) { all, chatRow ->
        BranchUtil.pathTo(all, chatRow?.activeLeafId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personas = db.personaDao().observePersonas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Chat Screen — workspace/folder indicators in the top bar.
    val workspace: StateFlow<Workspace?> = chat.flatMapLatest { chatRow ->
        if (chatRow == null) flowOf(null) else db.workspaceDao().observe(chatRow.workspaceId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val folder: StateFlow<Folder?> = chat.flatMapLatest { chatRow ->
        if (chatRow?.folderId == null) flowOf(null) else db.folderDao().observeAll().map { list -> list.find { it.id == chatRow.folderId } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Chat Screen — an archived workspace remains viewable but blocks new
    // messages until it (or the chat) is restored.
    val isWorkspaceArchived: StateFlow<Boolean> = workspace.map { it?.archived == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Chat's effective persona (priority chain via [ChatDefaults]: chat override → folder default
     * → workspace persona → Default Persona) — the top bar's "honesty layer" chip reads this, and
     * generation resolves the identical chain via [resolveEffectivePersona], both through
     * [ChatDefaults], so the two can't drift.
     */
    val persona: StateFlow<Persona?> = combine(chat, folder, workspace, db.personaDao().observePersonas()) { chatRow, folderRow, ws, personaList ->
        val effectiveId = ChatDefaults.personaId(chatRow, folderRow, ws)
        personaList.find { it.id == effectiveId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedGenerationModel: StateFlow<ModelInfo?> = combine(
        chat, folder, db.modelDao().observeModels(), app.container.modelLoadCoordinator.observeState(ModelRole.GENERATION)
    ) { chatRow, folderRow, models, loadInfo ->
        resolveGenerationModel(chatRow, folderRow, models, loadInfo.currentModelId)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeModelName: StateFlow<String?> = selectedGenerationModel
        .map { model -> model?.let { "${it.displayName} · ${it.lastWorkingBackend.displayName()}" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val generationModels = db.modelDao().observeModels()
        .map { models -> models.filter { it.role == ModelRole.GENERATION } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    /** Live tokens/sec + memory readout while a response is streaming — the "Show generation
     * stats" setting already surfaces this per-message after the fact (ChatInfoScreen); this is
     * the same numbers updated in real time so users can see it while it's happening, not just
     * after. Cleared (null) whenever generation isn't active. */
    data class LiveGenStats(val tokens: Int, val tokensPerSecond: Float, val availMemMb: Long, val totalMemMb: Long)
    private val _liveGenStats = MutableStateFlow<LiveGenStats?>(null)
    val liveGenStats: StateFlow<LiveGenStats?> = _liveGenStats

    /** True only while [retrieveSources] is actively embedding the query/scanning chunks —
     * lets the UI show "Searching knowledge base" instead of folding that time invisibly
     * into the generic generating state.
     *
     * Backed by an [java.util.concurrent.atomic.AtomicInteger] counter rather than a boolean
     * so two overlapping [retrieveSources] calls (e.g. a slow first call still in flight when
     * the next keystroke triggers another) don't race the boolean back to false while work is
     * still running — the flag is only cleared when the *last* call finishes. */
    private val retrievingCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val _isRetrieving = MutableStateFlow(false)
    val isRetrieving: StateFlow<Boolean> = _isRetrieving

    private val recallingMemoryCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val _isRecallingMemory = MutableStateFlow(false)
    val isRecallingMemory: StateFlow<Boolean> = _isRecallingMemory

    sealed class ModelLoadState {
        data object NoModel : ModelLoadState()
        data class NotLoaded(val modelName: String) : ModelLoadState()
        data class Loading(val modelName: String, val stage: String) : ModelLoadState()
        data class Ready(val modelName: String, val backend: String) : ModelLoadState()
        data class Failed(val modelName: String, val reason: String) : ModelLoadState()
    }

    private val _modelLoadState = MutableStateFlow<ModelLoadState>(ModelLoadState.NoModel)
    val modelLoadState: StateFlow<ModelLoadState> = _modelLoadState

    /** Null = not yet tested this process (attach stays enabled — "declared" capability,
     * not disproven). Set once a real load attempt has happened — a declared
     * vs. tested distinction. We don't gate on a static per-model flag because the same
     * model can load with or without vision depending on which backend actually worked. */
    private val _visionAvailable = MutableStateFlow<Boolean?>(null)
    val visionAvailable: StateFlow<Boolean?> = _visionAvailable

    /** Same declared-vs-tested reasoning as [visionAvailable], for native audio input. */
    private val _audioAvailable = MutableStateFlow<Boolean?>(null)
    val audioAvailable: StateFlow<Boolean?> = _audioAvailable

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** State of a document (or camera-OCR scan) attached via the chat composer — separate
     * from [error] since this is a transient composing-tray status, not a chat-level error. */
    sealed class DocumentAttachState {
        data class Importing(val name: String) : DocumentAttachState()
        data class Ready(val documentId: String, val name: String, val grounded: Boolean) : DocumentAttachState()
        data class Failed(val name: String, val reason: String) : DocumentAttachState()
    }
    private val _pendingDocument = MutableStateFlow<DocumentAttachState?>(null)
    val pendingDocument: StateFlow<DocumentAttachState?> = _pendingDocument

    private var generationJob: Job? = null
    // Fire-and-forget post-reply work (auto-title, context summary). Both run an on-device
    // generation that holds the single generation mutex for its whole decode, so an interactive
    // send starting while one is mid-flight would block on that mutex for seconds. They're
    // non-critical (a failure just leaves the title/summary as-is), so launchGeneration cancels
    // them the moment a real send/regenerate begins — interactive work always wins the model.
    private var titleJob: Job? = null
    private var summaryJob: Job? = null
    private var draftSaveJob: Job? = null
    private val leaveCleanupStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Not-yet-sent composer attachments. Held here (not as `remember` state in ChatScreen) so a
     * forward navigation — Chat Info, branch tree, a document, a citation — doesn't dispose the
     * screen state and silently drop an attached-but-unsent photo/recording. Files are only
     * deleted when the attachment is explicitly replaced/removed, consumed by send, or the
     * ViewModel itself is cleared (the chat's back-stack entry is truly gone).
     */
    data class ComposerAttachments(
        val imagePath: String? = null,
        val ocrImagePath: String? = null,
        val ocrText: String? = null,
        val qrImagePath: String? = null,
        val qrText: String? = null,
        val audioPath: String? = null
    )
    private val _attachments = MutableStateFlow(ComposerAttachments())
    val attachments: StateFlow<ComposerAttachments> = _attachments

    private fun deleteFileQuietly(path: String?) {
        path?.let { runCatching { java.io.File(it).delete() } }
    }

    // All attachment setters race on _attachments when called from background dispatchers
    // (OCR extraction, audio import, image attach all complete off the main thread). The previous
    // read-then-write pattern orphaned attachment files when two setters landed in the same frame
    // — the loser's read-modify-write clobbered the winner's path without deleting it. CAS loop
    // guarantees exactly one delete per displaced value, on the snapshot we actually replaced.
    fun setPendingImage(path: String?) {
        var displaced: String? = null
        while (true) {
            val current = _attachments.value
            if (current.imagePath == path) return
            val next = current.copy(imagePath = path)
            if (_attachments.compareAndSet(current, next)) { displaced = current.imagePath; break }
        }
        if (displaced != null && displaced != path) deleteFileQuietly(displaced)
    }

    fun setPendingOcr(imagePath: String?, text: String?) {
        var displacedImage: String? = null
        while (true) {
            val current = _attachments.value
            val next = current.copy(ocrImagePath = imagePath, ocrText = text)
            if (_attachments.compareAndSet(current, next)) { displacedImage = current.ocrImagePath; break }
        }
        if (displacedImage != null && displacedImage != imagePath) deleteFileQuietly(displacedImage)
    }

    fun updateOcrText(text: String) {
        _attachments.update { it.copy(ocrText = text) }
    }

    /** Same shape as [setPendingOcr]/[updateOcrText] — a QR/barcode scan attach, decoded
     * on-device via [com.vervan.chat.model.BarcodeExtractor]. Only the decoded text is folded
     * into the outgoing message; the LLM never sees the photo. */
    fun setPendingQr(imagePath: String?, text: String?) {
        var displacedImage: String? = null
        while (true) {
            val current = _attachments.value
            val next = current.copy(qrImagePath = imagePath, qrText = text)
            if (_attachments.compareAndSet(current, next)) { displacedImage = current.qrImagePath; break }
        }
        if (displacedImage != null && displacedImage != imagePath) deleteFileQuietly(displacedImage)
    }

    fun updateQrText(text: String) {
        _attachments.update { it.copy(qrText = text) }
    }

    fun setPendingAudio(path: String?) {
        var displaced: String? = null
        while (true) {
            val current = _attachments.value
            if (current.audioPath == path) return
            val next = current.copy(audioPath = path)
            if (_attachments.compareAndSet(current, next)) { displaced = current.audioPath; break }
        }
        if (displaced != null && displaced != path) deleteFileQuietly(displaced)
    }

    /** Clears attachment state for a send. Image/audio files are kept — the sent Message row
     * references them — but the OCR photo is deleted: only its extracted text goes into the
     * message. Returns what was pending so the caller can fold it into the outgoing send. */
    fun consumeAttachments(): ComposerAttachments {
        while (true) {
            val current = _attachments.value
            if (_attachments.compareAndSet(current, ComposerAttachments())) {
                deleteFileQuietly(current.ocrImagePath)
                deleteFileQuietly(current.qrImagePath)
                return current
            }
        }
    }

    /**
     * The only reliable "the user has actually left this chat" signal: the ViewModel is scoped
     * to the chat's NavBackStackEntry, so this fires when that entry is popped (or the Activity
     * finishes for good) — NOT on forward navigation or configuration change. The previous
     * DisposableEffect-based cleanup ran on *any* composition dispose, which meant opening Chat
     * Info mid-generation cancelled the stream, hard-purged incognito chats out from under the
     * back stack, and deleted unsent attachments. viewModelScope cancellation already stops any
     * in-flight generation (its CancellationException handler runs NonCancellable cleanup).
     */
    override fun onCleared() {
        val pending = _attachments.value
        if (leaveCleanupStarted.compareAndSet(false, true)) {
            app.applicationScope.launch(Dispatchers.IO) {
                cleanupChatOnLeave()
                listOf(pending.imagePath, pending.ocrImagePath, pending.qrImagePath, pending.audioPath).forEach(::deleteFileQuietly)
            }
        } else {
            listOf(pending.imagePath, pending.ocrImagePath, pending.qrImagePath, pending.audioPath).forEach(::deleteFileQuietly)
        }
    }

    private data class AutoLoadSnapshot(
        val model: ModelInfo?,
        val loadInfo: ModelLoadInfo
    )

    init {
        // Model Loading Strategy — chat/conversation screens are a Category B screen
        // ("can be viewed without a model, specific actions require one"), not Category A.
        // Opening a chat must never trigger a load on its own; this collector only *reflects*
        // whatever the coordinator's shared state already is (so this screen stays in sync if
        // some other trigger — Send, Voice, another screen — loaded something), it never calls
        // ensureLoaded() itself. The actual load happens in beginGeneration(), triggered only by
        // a real model-dependent action (send/regenerate/continue).
        viewModelScope.launch {
            combine(
                chat, folder, generationModels,
                app.container.modelLoadCoordinator.observeState(ModelRole.GENERATION)
            ) { chatRow, folderRow, models, loadInfo ->
                AutoLoadSnapshot(resolveGenerationModel(chatRow, folderRow, models, loadInfo.currentModelId), loadInfo)
            }.distinctUntilChanged().collect { snapshot ->
                val (model, loadInfo) = snapshot
                _modelLoadState.value = toChatModelLoadState(model, loadInfo)
                if (model != null && loadInfo.currentModelId == model.id) {
                    val activeEngine = activeEngineFor(model)
                    _visionAvailable.value = model.supportsVision ?: activeEngine.visionEnabled
                    _audioAvailable.value = model.supportsAudio ?: activeEngine.audioEnabled
                } else {
                    _visionAvailable.value = model?.supportsVision
                    _audioAvailable.value = model?.supportsAudio
                }
            }
        }
        // Home's "Ask anything" quick-ask (see com.vervan.chat.model.PendingChatSend) — a text
        // stashed for this exact chatId means the user already hit Send once on Home; submit it
        // for real instead of leaving it sitting as an unsent draft. consume() is a one-shot
        // remove, so a later recomposition/navigation back to this same ChatViewModel instance
        // (or a fresh instance for the same chatId, e.g. after a config change) never re-sends it.
        com.vervan.chat.model.PendingChatSend.consume(chatId)?.takeIf { it.isNotBlank() }?.let { send(it) }
    }

    /** Chat pin > whatever generation model is currently loaded > role default. Without the
     * "currently loaded" step, opening a chat with no pin (e.g. a brand-new chat) would force a
     * switch to the stored default even when the user just manually loaded a different model
     * they intend to keep using — auto-loading should never evict a model nobody asked to
     * replace. */
    private fun resolveGenerationModel(chatRow: Chat?, folder: Folder?, models: List<ModelInfo>, loadedModelId: String? = null): ModelInfo? =
        ChatDefaults.modelId(chatRow, folder)?.let { id -> models.find { it.id == id && it.role == ModelRole.GENERATION } }
            ?: loadedModelId?.let { id -> models.find { it.id == id && it.role == ModelRole.GENERATION } }
            ?: models.firstOrNull { it.role == ModelRole.GENERATION && it.isActive }

    /** Suspend counterpart of [resolveGenerationModel] for call sites that only have a chat row
     * and query the DB directly, rather than an already-loaded `models` list. Resolves the same
     * chat → folder-default → loaded/active chain via [ChatDefaults]. */
    private suspend fun resolveGenerationModelForChat(chatRow: Chat?): ModelInfo? {
        val folderRow = chatRow?.folderId?.let { db.folderDao().get(it) }
        ChatDefaults.modelId(chatRow, folderRow)?.let { id ->
            db.modelDao().get(id)?.takeIf { it.role == ModelRole.GENERATION }?.let { return it }
        }
        app.container.modelLoadCoordinator.state.value[ModelRole.GENERATION]?.currentModelId?.let { id ->
            db.modelDao().get(id)?.takeIf { it.role == ModelRole.GENERATION }?.let { return it }
        }
        return db.modelDao().getActiveModel(ModelRole.GENERATION)
    }

    /**
     * [resolveGenerationModelForChat]'s counterpart for the one call site that's actually about
     * to start a fresh turn ([beginGeneration]) rather than reflect already-resident state: when
     * neither the chat nor its folder has explicitly pinned a model, AND nothing is currently
     * loaded for GENERATION, and "Model selection: Auto" is on, picks among every installed
     * GENERATION model via [com.vervan.chat.llm.AutoModelSelector] instead of leaving the user
     * with no model at all.
     *
     * A model already resident (loaded manually, or by a previous turn/screen) always wins over
     * Auto — Auto's job is to pick a starting point from cold, not to second-guess or evict
     * whatever the user is already talking to. Without this ordering, Auto would re-run on every
     * single turn and could silently swap out a model the user explicitly loaded (e.g. from Model
     * Manager) for a smaller/larger one chosen purely by size heuristics, which is surprising and
     * wastes a reload. This also keeps this resolver in sync with [resolveGenerationModel]'s
     * display-path ordering (chat pin > currently loaded > default), so the "Ready"/attachment
     * capability the UI shows for the loaded model matches what generation actually uses.
     *
     * Deliberately NOT used by [resolveGenerationModelForChat]'s other call sites (per-hop model
     * lookup inside the tool loop, display/preload state, `inspectContext`) — once this picks a
     * model and [beginGeneration] loads it, the coordinator's "currently loaded" state naturally
     * makes every later call in the same turn resolve to that same model via the existing
     * fallback rung, with no risk of re-picking a different one mid-turn as memory/thermal state
     * shifts under a multi-hop tool call.
     */
    private suspend fun resolveGenerationModelForTurn(chatRow: Chat, triggerText: String?, imagePath: String?, audioPath: String?): ModelInfo? {
        val folderRow = chatRow.folderId?.let { db.folderDao().get(it) }
        ChatDefaults.modelId(chatRow, folderRow)?.let { id ->
            db.modelDao().get(id)?.takeIf { it.role == ModelRole.GENERATION }?.let { return it }
        }
        app.container.modelLoadCoordinator.state.value[ModelRole.GENERATION]?.currentModelId?.let { id ->
            db.modelDao().get(id)?.takeIf { it.role == ModelRole.GENERATION }?.let { return it }
        }
        if (app.container.settingsRepository.autoModelSelectionEnabled.first()) {
            val installed = db.modelDao().observeModels().first().filter { it.role == ModelRole.GENERATION }
            // A single installed model has nothing to choose between — skip straight to the
            // plain fallback chain below rather than spend an extra pass over it.
            if (installed.size > 1) {
                val rawProfile = ModelProfileType.fromId(chatRow.profile)
                val effectiveProfileType = com.vervan.chat.system.DeviceAwareProfile.resolve(app, rawProfile)
                // Complexity-based fast/capable routing is opt-in (default off — see
                // SettingsRepository.fastCapableRoutingEnabled) and only kicks in for a chat left
                // on BALANCED with no thermal/battery downgrade already in play — see
                // AutoModelSelector.complexityProfileHint's doc comment.
                val routedProfile = if (rawProfile == ModelProfileType.BALANCED && effectiveProfileType == ModelProfileType.BALANCED &&
                    app.container.settingsRepository.fastCapableRoutingEnabled.first()
                ) {
                    com.vervan.chat.llm.AutoModelSelector.complexityProfileHint(triggerText.orEmpty()) ?: effectiveProfileType
                } else effectiveProfileType
                com.vervan.chat.llm.AutoModelSelector.select(
                    installed, routedProfile, needsVision = imagePath != null, needsAudio = audioPath != null
                )?.let { return it }
            }
        }
        return db.modelDao().getActiveModel(ModelRole.GENERATION)
    }

    /** Maps the coordinator's role-wide load state onto this chat's specific resolved model —
     * the coordinator only knows "what's loaded/loading for GENERATION app-wide", so this chat
     * only shows Ready/Loading/Failed when that happens to be *its* model, not just any
     * generation model some other screen triggered. */
    private fun toChatModelLoadState(model: ModelInfo?, info: ModelLoadInfo): ModelLoadState {
        if (model == null) return ModelLoadState.NoModel
        return when {
            info.currentModelId == model.id -> ModelLoadState.Ready(
                model.displayName,
                // An off-device model has no hardware backend to report — activeEngineFor would
                // otherwise fall back to the LiteRT-LM engine's backend, which reflects whatever
                // (unrelated) model that engine last happened to load, not this one.
                if (!model.traits.runsOnDevice) REMOTE_BACKEND_LABEL
                else activeEngineFor(model).activeBackend.name.lowercase().replaceFirstChar { it.uppercase() }
            )
            // An off-device model has no memory to load into — ModelLoadCoordinator's own
            // REMOTE_API short-circuit still technically passes through LOADING/READY for internal
            // bookkeeping (see its doc comment), but nothing is actually happening on this device,
            // so this chat should never claim otherwise even for the one synchronous frame it might
            // be observed in.
            info.phase == ModelLoadPhase.LOADING && info.loadingModelId == model.id && model.traits.runsOnDevice ->
                ModelLoadState.Loading(model.displayName, "Loading model into memory")
            info.error?.loadedModelId == model.id ->
                ModelLoadState.Failed(model.displayName, info.error?.errorMessage.toUserMessage())
            // nothing auto-loads this model just because the chat is open, so absent any
            // of the above this chat's model is simply not loaded yet; ModelReadinessPanel offers
            // "Load", and beginGeneration() loads it for real the moment a model-dependent action
            // (Send/regenerate/continue) actually happens.
            else -> ModelLoadState.NotLoaded(model.displayName)
        }
    }

    private suspend fun computeAdjustedContext(profileId: String, model: ModelInfo): Int {
        val requestedContext = model.contextTokens ?: app.container.settingsRepository.contextTokenLimit.first()
        val profile = ModelProfiles.resolve(ModelProfileType.fromId(profileId))
        return (requestedContext * profile.contextFraction).toInt().coerceAtLeast(1024)
    }

    fun retryModelLoad() {
        if (_modelLoadState.value is ModelLoadState.Loading) return
        viewModelScope.launch {
            val chatRow = db.chatDao().getChat(chatId) ?: return@launch
            val model = resolveGenerationModelForChat(chatRow)
            if (model == null) {
                _modelLoadState.value = ModelLoadState.NoModel
            } else {
                val adjustedContext = computeAdjustedContext(chatRow.profile, model)
                app.container.modelLoadCoordinator.ensureLoaded(model, LoadTrigger.CHAT_SEND, adjustedContext)
            }
        }
    }

    private data class GenerationRequest(
        val triggerText: String,
        val imagePath: String?,
        val audioPath: String?,
        // One-shot overrides for a small-model recovery retry (see retryWithSources/
        // retryWithQuality) — never persisted to the chat row, so later turns are unaffected.
        val forceGrounding: Boolean = false,
        val profileOverride: com.vervan.chat.llm.ModelProfileType? = null
    )

    /**
     * Fresh, non-cached read of every message in the chat. Internal logic that writes
     * then immediately needs to see its own write uses this instead of [allMessages]'s
     * StateFlow value — Room's Flow re-emits on its own schedule after invalidation,
     * not synchronously within the same coroutine, so the StateFlow can be briefly stale
     * right after an insert/update. [allMessages] itself is fine for UI reads, where the
     * next recomposition already implies the Flow caught up.
     */
    private suspend fun getAllMessages(): List<Message> = db.messageDao().getMessages(chatId)

    /**
     * Effective persona for generation and the context inspector — chat override → folder default
     * → workspace persona → Default Persona, resolved through [ChatDefaults] so it can't drift from
     * the display Flow above. Suspend so the prompt path can query folder/workspace rows directly.
     */
    private suspend fun resolveEffectivePersona(chatRow: Chat?): Persona? {
        if (chatRow == null) return null
        val folderRow = chatRow.folderId?.let { db.folderDao().get(it) }
        val workspaceRow = db.workspaceDao().get(chatRow.workspaceId)
        return db.personaDao().getPersona(ChatDefaults.personaId(chatRow, folderRow, workspaceRow))
    }

    /** A fresh on-disk path for a new voice-message recording — the caller (ChatScreen's
     * [com.vervan.chat.audio.WavRecorder]) writes directly here, no separate copy step
     * needed since audio never comes from a transient content:// Uri like images do. */
    fun newAudioFile(): java.io.File {
        val dir = java.io.File(app.filesDir, "audio").apply { mkdirs() }
        return java.io.File(dir, "${System.currentTimeMillis()}.wav")
    }

    /** Materializes any device-decodable audio document as the same mono 16 kHz WAV used by
     * microphone recordings. The returned file is app-owned, so the SAF grant can safely end. */
    suspend fun importAudio(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        com.vervan.chat.system.runCatchingPreservingCancellation {
            AudioNormalizer.normalize(app, uri, newAudioFile()).absolutePath
        }.onFailure { Log.e(TAG, "Audio import failed", it) }
    }

    /** Copies a picked image into app storage so it survives the source content:// Uri
     * disappearing. Returns null (instead of throwing) on a lapsed SAF grant, a cloud-gallery
     * URI that failed to materialize, or malformed EXIF — callers already treat null as
     * "couldn't attach this image", so this used to crash the app for the same cases. */
    suspend fun copyImage(uri: Uri): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var copied: java.io.File? = null
        try {
            val dir = java.io.File(app.filesDir, "images").apply { mkdirs() }
            val dest = java.io.File(dir, "${System.currentTimeMillis()}.jpg")
            copied = dest
            app.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyToLimited(output, ImportLimits.MAX_IMAGE_SOURCE_BYTES) }
            } ?: return@withContext null
            // Orientation fix — bakes the EXIF rotation into the pixels so
            // the vision model (which has no EXIF awareness) doesn't see a sideways image.
            check(com.vervan.chat.model.ImageUtils.normalizeForModel(dest)) { "The selected file is not a readable image" }
            dest.absolutePath
        } catch (t: Throwable) {
            com.vervan.chat.system.rethrowCancellation(t)
            Log.e(TAG, "copyImage failed", t)
            copied?.delete()
            null
        }
    }

    /** A fresh on-disk file (+ its content:// Uri via FileProvider) for a camera capture —
     * same directory [copyImage] copies picked images into, so both paths look identical to
     * the rest of the app afterward. */
    fun newCameraImageFile(): Pair<java.io.File, Uri> {
        val dir = java.io.File(app.filesDir, "images").apply { mkdirs() }
        val file = java.io.File(dir, "${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return file to uri
    }

    /** Result of an OCR attach — unlike a vision attachment, the LLM never sees [imagePath];
     * only [text] gets folded into the outgoing message (see ChatScreen's send handler), so this
     * works with any loaded model, vision-capable or not. [imagePath] is kept only so the
     * composer can show the same "picked/captured photo" preview UX as a real image attachment,
     * and so the user can confirm what was actually scanned before sending. Camera captures for
     * OCR reuse [newCameraImageFile]'s dir — that one's already registered with FileProvider;
     * a separate cache-dir target crashed with "Failed to find configured root" since it wasn't. */
    data class OcrResult(val imagePath: String, val text: String)

    /** Runs on-device ML Kit OCR (see [com.vervan.chat.model.OcrExtractor]) over a picked
     * gallery image. */
    suspend fun extractOcr(uri: Uri): Result<OcrResult> = withContext(Dispatchers.IO) {
        var copied: java.io.File? = null
        com.vervan.chat.system.runCatchingPreservingCancellation {
            val dir = java.io.File(app.filesDir, "images").apply { mkdirs() }
            val dest = java.io.File(dir, "${System.currentTimeMillis()}_ocr.jpg")
            copied = dest
            app.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyToLimited(output, ImportLimits.MAX_IMAGE_SOURCE_BYTES) }
            } ?: throw java.io.IOException("Could not read image")
            check(com.vervan.chat.model.ImageUtils.normalizeForModel(dest)) { "The selected file is not a readable image" }
            val text = com.vervan.chat.model.OcrExtractor.extractFromImage(dest)
            require(text.length <= InputLimits.OCR_TEXT_CHARS) {
                "OCR produced too much text (maximum ${InputLimits.OCR_TEXT_CHARS} characters)"
            }
            OcrResult(dest.absolutePath, text)
        }.onFailure {
            copied?.delete()
            Log.e(TAG, "OCR extraction failed", it)
        }
    }

    /** Same as [extractOcr] but for a camera capture already materialized as a file (via
     * [newCameraImageFile]). */
    suspend fun extractOcrFromFile(file: java.io.File): Result<OcrResult> = withContext(Dispatchers.IO) {
        com.vervan.chat.system.runCatchingPreservingCancellation {
            require(file.length() <= ImportLimits.MAX_IMAGE_SOURCE_BYTES) { "Image exceeds the 64 MB limit" }
            check(com.vervan.chat.model.ImageUtils.normalizeForModel(file)) { "The captured file is not a readable image" }
            val text = com.vervan.chat.model.OcrExtractor.extractFromImage(file)
            require(text.length <= InputLimits.OCR_TEXT_CHARS) {
                "OCR produced too much text (maximum ${InputLimits.OCR_TEXT_CHARS} characters)"
            }
            OcrResult(file.absolutePath, text)
        }.onFailure { Log.e(TAG, "OCR extraction failed", it) }
    }

    /** QR/barcode counterpart of [OcrResult] — see its doc comment, same shape and reasoning. */
    data class QrResult(val imagePath: String, val text: String)

    /** Runs on-device ML Kit barcode scanning (see [com.vervan.chat.model.BarcodeExtractor])
     * over a picked gallery image. */
    suspend fun extractQr(uri: Uri): Result<QrResult> = withContext(Dispatchers.IO) {
        var copied: java.io.File? = null
        com.vervan.chat.system.runCatchingPreservingCancellation {
            val dir = java.io.File(app.filesDir, "images").apply { mkdirs() }
            val dest = java.io.File(dir, "${System.currentTimeMillis()}_qr.jpg")
            copied = dest
            app.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyToLimited(output, ImportLimits.MAX_IMAGE_SOURCE_BYTES) }
            } ?: throw java.io.IOException("Could not read image")
            check(com.vervan.chat.model.ImageUtils.normalizeForModel(dest)) { "The selected file is not a readable image" }
            val text = com.vervan.chat.model.BarcodeExtractor.extractFromImage(dest)
            QrResult(dest.absolutePath, text)
        }.onFailure {
            copied?.delete()
            Log.e(TAG, "QR extraction failed", it)
        }
    }

    /** Same as [extractQr] but for a camera capture already materialized as a file. */
    suspend fun extractQrFromFile(file: java.io.File): Result<QrResult> = withContext(Dispatchers.IO) {
        com.vervan.chat.system.runCatchingPreservingCancellation {
            require(file.length() <= ImportLimits.MAX_IMAGE_SOURCE_BYTES) { "Image exceeds the 64 MB limit" }
            check(com.vervan.chat.model.ImageUtils.normalizeForModel(file)) { "The captured file is not a readable image" }
            val text = com.vervan.chat.model.BarcodeExtractor.extractFromImage(file)
            QrResult(file.absolutePath, text)
        }.onFailure { Log.e(TAG, "QR extraction failed", it) }
    }

    /**
     * Attaches a document to this chat: extract -> chunk -> embed (if an embedding model is
     * available; falls back to keyword-only chunks otherwise, same pipeline Knowledge Base
     * import uses) -> store as a one-document knowledge base scoped to this chat, then turn
     * on source grounding so the next send retrieves against it. No separate "describe this
     * document" generation step is triggered here — [ChatScreen] fills a default prompt when
     * the user sends with only a document attached and an empty draft.
     */
    fun attachDocument(uri: Uri) {
        viewModelScope.launch {
            // queryDisplayName/ensureEmbeddingModelLoaded used to sit outside this try block —
            // a lapsed SAF grant (contentResolver.query throwing SecurityException) or a native
            // embedding-load failure there crashed the whole app instead of showing the same
            // "attachment failed" state the import step below already handles.
            val name = try {
                queryDisplayName(uri) ?: uri.lastPathSegment ?: "document"
            } catch (t: Throwable) {
                com.vervan.chat.system.rethrowCancellation(t)
                Log.e(TAG, "[$chatId] could not read document name", t)
                "document"
            }
            _pendingDocument.value = DocumentAttachState.Importing(name)
            val kb = KnowledgeBase(name = "Attached: $name")
            try {
                ensureEmbeddingModelLoaded()
                db.knowledgeBaseDao().upsert(kb)
                // reuseExistingByHash=true: every attach here creates its own throwaway KB (just
                // created above), so a duplicate can only ever be found somewhere else — this
                // chat's own earlier attachment, or a different chat's — never a false-positive
                // "duplicate" against the empty KB this call just made.
                when (val outcome = app.container.documentImportManager.import(kb.id, uri, reuseExistingByHash = true)) {
                    is DocumentImportOutcome.Imported -> applyImportOutcome(outcome.document, kb.id)
                    is DocumentImportOutcome.Duplicate -> {
                        // The KB created above was never used — nothing was imported into it.
                        db.knowledgeBaseDao().delete(kb)
                        val existingKbId = outcome.existing.knowledgeBaseId
                        val alreadyInThisChat = db.chatDao().getChat(chatId)?.kbIdList()?.contains(existingKbId) == true
                        if (alreadyInThisChat) {
                            _pendingDocument.value = DocumentAttachState.Failed(name, "Already attached to this chat")
                        } else {
                            // Same content already imported (this chat previously, or another
                            // chat) — reuse it instead of re-extracting/re-chunking/re-embedding
                            // identical bytes into a second copy. Routed through the same
                            // _confirmationMessage Snackbar every other one-shot banner in this
                            // class uses (see togglePin/moveToWorkspace/etc.) instead of a raw
                            // Toast — one user-feedback idiom, not two.
                            _confirmationMessage.value = "\"${outcome.existing.displayName}\" already exists — using the existing copy"
                            applyImportOutcome(outcome.existing, existingKbId)
                        }
                    }
                    is DocumentImportOutcome.VersionConflict -> {
                        // Chat-composer flow has no version-conflict dialog of its own — a changed
                        // re-attach just replaces the previous chat-scoped copy outright.
                        val replaced = app.container.documentImportManager.resolveVersionConflict(
                            outcome.existing, outcome.tempFilePath, outcome.mimeType, outcome.newHash, replace = true
                        )
                        applyImportOutcome(replaced, kb.id)
                    }
                }
            } catch (t: Throwable) {
                com.vervan.chat.system.rethrowCancellation(t)
                Log.e(TAG, "[$chatId] document attachment failed", t)
                db.knowledgeBaseDao().delete(kb)
                _pendingDocument.value = DocumentAttachState.Failed(name, t.toUserMessage())
            }
        }
    }

    private suspend fun applyImportOutcome(document: com.vervan.chat.data.db.entities.Document, kbId: String) {
        if (document.status != com.vervan.chat.data.db.entities.DocumentStatus.READY) {
            _pendingDocument.value = DocumentAttachState.Failed(document.displayName, document.failureReason ?: document.status.name)
            // Chat attachments use a private one-document KB. If import fails, keeping that
            // inaccessible KB/file around only leaks storage into the main Knowledge list.
            app.container.documentImportManager.delete(document)
            db.knowledgeBaseDao().get(kbId)?.let { db.knowledgeBaseDao().delete(it) }
            return
        }
        val chatRow = db.chatDao().getChat(chatId) ?: return
        db.chatDao().update(
            chatRow.copy(
                knowledgeBaseIds = (chatRow.kbIdList() + kbId).distinct().joinToString(","),
                sourceGrounded = true
            )
        )
        val grounded = com.vervan.chat.retrieval.embeddingReady(db.modelDao().getActiveModel(ModelRole.EMBEDDING), app.container.embeddingEngine)
        _pendingDocument.value = DocumentAttachState.Ready(document.id, document.displayName, grounded)
    }

    private suspend fun ensureEmbeddingModelLoaded() {
        val active = db.modelDao().getActiveModel(ModelRole.EMBEDDING) ?: return
        com.vervan.chat.system.runCatchingPreservingCancellation {
            app.container.modelLoadCoordinator.ensureLoaded(active, LoadTrigger.RAG_RETRIEVAL)
        }.onFailure {
            // A bare runCatching here previously swallowed every load failure — a corrupt or
            // missing embedding model silently no-ops retrieval with zero diagnostic. Surface it
            // so the failure is traceable in on-device logs; the DocumentAttachState "grounded"
            // flag below already degrades gracefully when the engine isn't loaded.
            Log.e(TAG, "ensureEmbeddingModelLoaded: embedding model failed to load", it)
        }
    }

    fun clearPendingDocument() {
        _pendingDocument.value = null
    }

    private fun queryDisplayName(uri: Uri): String? {
        app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
        }
        return null
    }

    fun saveDraft(text: String) {
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            // Avoid a Room write and full-chat Flow invalidation for every keystroke.
            // This also prevents a late autosave from restoring text after Send clears it.
            delay(DRAFT_SAVE_DEBOUNCE_MS)
            db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(draft = text)) }
        }
    }

    fun setSourceGrounding(enabled: Boolean, kbIds: List<String>) {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let {
                db.chatDao().update(it.copy(sourceGrounded = enabled, knowledgeBaseIds = kbIds.joinToString(",")))
            }
        }
    }

    fun setToolsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(toolsEnabled = enabled)) }
        }
    }

    /** Sets this chat's override for one tool — true/false forces it on/off just for this chat
     * regardless of the global Settings → Tools switch, null clears the override back to
     * "inherit the global setting". */
    fun setToolOverride(toolId: String, state: Boolean?) {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let { chatRow ->
                val overrides = chatRow.toolOverrideMap().toMutableMap()
                if (state == null) overrides.remove(toolId) else overrides[toolId] = state
                db.chatDao().update(chatRow.copy(toolOverrides = Chat.encodeToolOverrides(overrides)))
            }
        }
    }

    // null = "use model default" (see ThinkingPolicy.effectiveThinkingMode).
    fun setThinkingMode(mode: String?) {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(thinkingMode = mode)) }
        }
    }

    fun setProfile(profileId: String) {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(profile = profileId)) }
        }
    }

    fun setModel(modelId: String?) {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(modelId = modelId)) }
        }
    }

    fun setPersona(personaId: String?) {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(personaId = personaId)) }
        }
    }

    // Per-chat sampler overrides — null clears back to inherited (model, then app
    // default).
    fun setTemperatureOverride(value: Float?) {
        viewModelScope.launch { db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(temperature = value)) } }
    }
    fun setTopPOverride(value: Float?) {
        viewModelScope.launch { db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(topP = value)) } }
    }
    fun setTopKOverride(value: Int?) {
        viewModelScope.launch { db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(topK = value)) } }
    }

    // Chat Screen — persists the reading position (not just "was at the bottom") so
    // reopening the chat, rotating the device, or navigating back restores it instead of
    // always jumping to the latest message.
    fun saveScrollAnchor(messageId: String, offsetPx: Int) {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let {
                db.chatDao().update(it.copy(scrollAnchorMessageId = messageId, scrollAnchorOffsetPx = offsetPx))
            }
        }
    }

    /** B3: export/duplicate/archive/pin/trash used to only be reachable from the chat list
     * row menu — an already-open chat had no way to reach them. Same logic as
     * [com.vervan.chat.ui.chats.ChatListViewModel], scoped to this chat's id. */
    // A manual rename permanently opts the chat out of auto-title generation (:
    // "manually edited titles are never overwritten automatically") — titleIsCustom locks it.
    fun rename(newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let {
                db.chatDao().update(it.copy(title = newTitle.trim(), previousTitle = it.title, titleIsCustom = true, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    private val _titleGenerating = MutableStateFlow(false)
    val titleGenerating: StateFlow<Boolean> = _titleGenerating
    // If the user sends another message while the fire-and-forget auto-title request is still
    // running, launchGeneration cancels that request to keep interactive generation responsive.
    // Remember the interruption so the next completed reply gets one retry instead of leaving the
    // first-message placeholder forever.
    private var titleRetryPending = false

    // Manual "Generate title" / "Regenerate title" — an AI-produced title stays
    // eligible for later auto-regeneration (titleIsCustom = false), unlike a hand-typed rename.
    fun generateTitle() {
        if (_titleGenerating.value) return
        viewModelScope.launch {
            _titleGenerating.value = true
            try {
                val currentTitle = db.chatDao().getChat(chatId)?.title
                val result = TitleGenerator.generate(app, chatId, avoidTitle = currentTitle)
                if (result != null) {
                    db.chatDao().getChat(chatId)?.let {
                        db.chatDao().update(it.copy(title = result.title, previousTitle = it.title, titleIsCustom = false, updatedAt = System.currentTimeMillis()))
                    }
                    _confirmationMessage.value = when {
                        // The model spent its whole response budget on reasoning and never
                        // actually answered — TitleGenerator's fallback (first message, trimmed)
                        // filled in so the chat isn't left untitled, but this is NOT the AI's
                        // judgment on the best title, so don't claim it is (see B: "hi" chats
                        // reporting "still the best title" when the model never got a chance to
                        // answer at all).
                        result.isFallback -> "The model did not return a usable title — used the first message as a placeholder instead"
                        // Told the model to avoid this exact title and it still landed here — a
                        // thin/ambiguous conversation genuinely doesn't support a different one
                        // right now, not a wiring bug. Say so plainly instead of a "Title updated"
                        // that looks like nothing happened.
                        result.title.equals(currentTitle, ignoreCase = true) -> "Regenerated, but this is still the best title for this conversation so far"
                        else -> "Title updated"
                    }
                } else {
                    _confirmationMessage.value = "Not enough conversation content to generate a title"
                }
            } catch (t: Throwable) {
                com.vervan.chat.system.rethrowCancellation(t)
                Log.e(TAG, "[$chatId] title generation failed", t)
                _confirmationMessage.value = "Title generation failed: ${t.toUserMessage()}"
            } finally {
                _titleGenerating.value = false
            }
        }
    }

    fun restorePreviousTitle() {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let { c ->
                val prev = c.previousTitle ?: return@let
                db.chatDao().update(c.copy(title = prev, previousTitle = c.title, titleIsCustom = true, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    // Auto title generation, workspace-scoped: run once, only after the response
    // that pushes the assistant-message count to the trigger threshold, only while the title
    // is still non-custom, and only for workspaces that opted in. Fire-and-forget on its own
    // coroutine so it never blocks or interrupts the interactive response that triggered it.
    private fun maybeAutoGenerateTitle() {
        titleJob = viewModelScope.launch {
            // This fires automatically on nearly every ordinary multi-turn chat (see the trigger
            // check below), so an unguarded engine.load()/generate() failure here — e.g. the
            // model got evicted under memory pressure between the reply and this call — used to
            // crash the app on a completely routine action. It's fire-and-forget by design
            // (comment above), so a failure just means the title silently stays as-is.
            try {
                val chatRow = db.chatDao().getChat(chatId) ?: return@launch
                if (chatRow.titleIsCustom) return@launch
                val ws = db.workspaceDao().get(chatRow.workspaceId)
                if (ws?.autoTitleGeneration != true) return@launch
                val assistantReplies = getAllMessages().count { it.role == MessageRole.ASSISTANT && it.state == MessageState.COMPLETE }
                val retryingInterruptedTitle = titleRetryPending && assistantReplies > AUTO_TITLE_TRIGGER_REPLIES
                if (assistantReplies != AUTO_TITLE_TRIGGER_REPLIES && !retryingInterruptedTitle) return@launch
                val result = TitleGenerator.generate(app, chatId) ?: run {
                    titleRetryPending = true
                    return@launch
                }
                db.chatDao().getChat(chatId)?.let {
                    if (!it.titleIsCustom) db.chatDao().update(it.copy(title = result.title, previousTitle = it.title, updatedAt = System.currentTimeMillis()))
                }
                titleRetryPending = false
            } catch (cancelled: CancellationException) {
                titleRetryPending = true
                throw cancelled
            } catch (t: Throwable) {
                com.vervan.chat.system.rethrowCancellation(t)
                titleRetryPending = true
                Log.e(TAG, "[$chatId] auto title generation failed", t)
            }
        }
    }

    // Long-chat context management — see historyAfterSummary/trimHistoryToBudget and
    // Chat.contextSummary. Same fire-and-forget shape as maybeAutoGenerateTitle: runs after a
    // response completes, never blocks or interrupts interactive generation, and a failure here
    // just means the chat falls back to plain drop-oldest truncation next turn.
    private fun maybeSummarizeOlderHistory() {
        summaryJob = viewModelScope.launch {
            try {
                summarizeOlderHistoryIfNeeded()
            } catch (t: Throwable) {
                com.vervan.chat.system.rethrowCancellation(t)
                Log.e(TAG, "[$chatId] context summarization failed", t)
            }
        }
    }

    private suspend fun summarizeOlderHistoryIfNeeded() {
        val chatRow = db.chatDao().getChat(chatId) ?: return
        // Incognito chats hard-delete on close and are deliberately excluded from every other
        // form of derived persistence (memory suggestions, backup, search) — a summary is
        // exactly that kind of derived data, so it stays off here too.
        if (chatRow.isTemporary) return
        if (!app.container.settingsRepository.autoContextSummarization.first()) return
        val model = resolveGenerationModelForChat(chatRow) ?: return
        val effectiveProfileType = com.vervan.chat.system.DeviceAwareProfile.resolve(
            app,
            ModelProfileType.fromId(chatRow.profile),
        )
        val profile = ModelProfiles.resolve(effectiveProfileType)
        val contextLimitTokens = effectiveContextLimitTokens(model, profile)
        val budgetTokens = (contextLimitTokens * 0.6).toInt().coerceAtLeast(50)

        val fullHistory = BranchUtil.pathTo(getAllMessages(), chatRow.activeLeafId)
        val (uncovered, existingSummary) = ChatFormatting.historyAfterSummary(fullHistory, chatRow)
        if (uncovered.size <= KEEP_RAW_TURNS) return

        val includePastThinking = app.container.settingsRepository.includePastThinkingInContext.first()
        val uncoveredTokens = uncovered.sumOf {
            com.vervan.chat.llm.estimateTokens(ChatFormatting.contextMessageContent(it, includePastThinking))
        }
        // Only bother once the not-yet-summarized tail is already eating a large share of the
        // history budget — re-summarizing on every single turn for a chat nowhere near its
        // limit would just be a pointless extra generation call each time.
        if (uncoveredTokens < budgetTokens * SUMMARIZE_TRIGGER_FRACTION) return

        // Most recent turns stay raw (full fidelity for the model); only what's older than
        // that gets folded into the summary.
        val toFold = uncovered.dropLast(KEEP_RAW_TURNS)
        if (toFold.isEmpty()) return

        val transcript = toFold.mapNotNull { m ->
            val label = when (m.role) {
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
                MessageRole.SYSTEM -> "Tool result"
            }
            val content = ChatFormatting.contextMessageContent(m, includePastThinking)
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            "$label: ${content.take(2000)}"
        }.joinToString("\n")
        if (transcript.isBlank()) return
        val prompt = buildString {
            if (!existingSummary.isNullOrBlank()) {
                appendLine("Here is a running summary of an earlier part of a conversation:")
                appendLine(existingSummary)
                appendLine()
                appendLine(
                    "Update it to also incorporate the additional turns below. Keep the result " +
                        "concise (a paragraph or two), preserve concrete facts, names, decisions, " +
                        "and preferences the user stated, and drop small talk. Respond with ONLY " +
                        "the updated summary, nothing else."
                )
            } else {
                appendLine(
                    "Summarize the following conversation turns concisely (a paragraph or two). " +
                        "Preserve concrete facts, names, decisions, and preferences the user " +
                        "stated. Drop small talk and pleasantries. Respond with ONLY the summary, " +
                        "nothing else."
                )
            }
            appendLine()
            appendLine("Turns to summarize:")
            appendLine(transcript)
        }
        // Reuses whichever model is already loaded for this chat — summarization runs right
        // after that same model just finished generating, so this never triggers a cold load.
        // Passing `model` explicitly (rather than letting OneShotLlm fall back to the app-wide
        // active model) is what makes that true: a chat pinning a non-default model would
        // otherwise evict its just-used model to load the global default and summarize with the
        // wrong one.
        val summary = com.vervan.chat.llm.OneShotLlm.run(app, prompt, model = model)?.trim()?.takeIf { it.isNotBlank() } ?: return
        db.chatDao().getChat(chatId)?.let {
            db.chatDao().update(it.copy(contextSummary = summary, summaryCoversUpToMessageId = toFold.last().id))
        }
    }

    fun togglePin() {
        viewModelScope.launch { db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(pinned = !it.pinned)) } }
    }

    /** Incognito mode. Takes effect for messages sent from this point forward — see
     * [send]'s memory-suggestion skip and [com.vervan.chat.ui.chat.ChatScreen]'s hard-delete-on-
     * close, which only fires once this is true. */
    fun toggleTemporary() {
        viewModelScope.launch { db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(isTemporary = !it.isTemporary)) } }
    }

    fun toggleArchive() {
        viewModelScope.launch { db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(archived = !it.archived)) } }
    }

    // One-shot confirmation banners — "X is now active" / "Moved to X" — the
    // screen shows these as a Snackbar then clears them, same pattern as WorkspacesViewModel.
    private val _confirmationMessage = MutableStateFlow<String?>(null)
    val confirmationMessage: StateFlow<String?> = _confirmationMessage
    fun clearConfirmation() { _confirmationMessage.value = null }

    // Chat Screen — moving a chat to another workspace unfiles it from its current
    // folder (a folder must stay within one workspace); persona/model overrides are
    // left untouched so an explicit chat-level choice still wins, but a chat with no override
    // now inherits the destination workspace's persona (resolveEffectivePersona), matching
    // "use destination workspace defaults only where no chat-specific override exists".
    fun moveToWorkspace(workspaceId: String, workspaceName: String) {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let {
                db.chatDao().update(it.copy(workspaceId = workspaceId, folderId = null, updatedAt = System.currentTimeMillis()))
            }
            _confirmationMessage.value = "Moved to $workspaceName"
        }
    }

    // Chat Screen — "Set Chat X as active workspace" from the chat's own workspace
    // indicator, distinct from just opening/viewing it.
    fun setChatWorkspaceActive() {
        viewModelScope.launch {
            workspace.value?.let {
                app.container.workspaceManager.setActive(it)
                _confirmationMessage.value = "${it.name} is now active"
            }
        }
    }

    // Chat Screen — an archived workspace stays viewable but blocks new messages
    // until restored; this is the "Restore and continue" affordance.
    fun restoreChatWorkspace() {
        viewModelScope.launch { workspace.value?.let { app.container.workspaceManager.restore(it) } }
    }

    // Chat Screen — clears every chat-specific override so the chat falls back to
    // its workspace's persona/defaults (resolveEffectivePersona, model/profile/thinking-mode
    // fallbacks already read null as "inherit"). Messages, branches, folder, and workspace are
    // untouched — this only resets configuration, never data.
    fun resetChatSettings() {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let {
                db.chatDao().update(
                    it.copy(
                        personaId = null, modelId = null, profile = "BALANCED", thinkingMode = null,
                        // Matches Chat's own constructor default — "reset" must land on the same
                        // state a brand-new chat starts in, not a stricter one.
                        sourceGrounded = false, toolsEnabled = true, knowledgeBaseIds = "",
                        temperature = null, topP = null, topK = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            _confirmationMessage.value = "Chat settings reset to workspace defaults"
        }
    }

    data class ChatStats(
        val totalMessages: Int,
        val userMessages: Int,
        val assistantMessages: Int,
        val attachments: Int,
        val branchPoints: Int,
        val createdAt: Long,
        val updatedAt: Long
    )

    // Chat Screen — kept to what's actually recorded today (no token/duration fields
    // exist anywhere in this app yet, see Message.kt); this omits token counts rather than
    // faking them.
    fun chatStats(): ChatStats {
        val rendered = messages.value
        val all = allMessages.value
        return ChatStats(
            totalMessages = rendered.count { it.role != MessageRole.SYSTEM },
            userMessages = rendered.count { it.role == MessageRole.USER },
            assistantMessages = rendered.count { it.role == MessageRole.ASSISTANT },
            attachments = rendered.count { it.imagePath != null || it.documentId != null || it.audioPath != null },
            branchPoints = all.groupBy { it.parentId }.values.count { it.size > 1 },
            createdAt = chat.value?.createdAt ?: 0L,
            updatedAt = chat.value?.updatedAt ?: 0L
        )
    }

    fun moveToTrash(onDone: () -> Unit) {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(deletedAt = System.currentTimeMillis())) }
            onDone()
        }
    }

    fun closeEmptyDraft(onDone: () -> Unit) {
        if (!leaveCleanupStarted.compareAndSet(false, true)) { onDone(); return }
        viewModelScope.launch {
            cleanupChatOnLeave()
            onDone()
        }
    }

    private suspend fun cleanupChatOnLeave() {
        val chat = db.chatDao().getChat(chatId) ?: return
        val hasNoConversation = db.messageDao().getMessages(chatId).isEmpty() && chat.draft.isBlank()
        if (hasNoConversation || chat.isTemporary) purgeTemporaryChat(chat)
    }

    /** Incognito mode — hard-deletes everything scoped to a temporary chat on close
     * (not soft-deleted to the recycle bin, unlike a normal chat): messages, tool-audit rows,
     * and any per-chat attachment knowledge bases created via document
     * import (same document cleanup [com.vervan.chat.model.WorkspaceManager.delete] uses for a
     * deleted workspace's documents, scoped here to just this chat's KBs instead). Also run as
     * a cold-start sweep in VervanApp.onCreate, in case the process died before this ran. */
    private suspend fun purgeTemporaryChat(chat: com.vervan.chat.data.db.entities.Chat) {
        chat.kbIdList().forEach { kbId ->
            db.documentDao().getForKb(kbId).forEach { app.container.documentImportManager.delete(it) }
            db.knowledgeBaseDao().get(kbId)?.let { db.knowledgeBaseDao().delete(it) }
        }
        MessageAttachmentCleanup.deleteOrphanedFiles(db, chat.id)
        db.messageDao().deleteForChat(chat.id)
        db.toolAuditDao().deleteForChat(chat.id)
        db.chatDao().delete(chat)
    }

    fun duplicate(onDone: () -> Unit) {
        viewModelScope.launch {
            val original = db.chatDao().getChat(chatId) ?: return@launch
            db.withTransaction {
                val allMsgs = db.messageDao().getMessages(chatId)
                val ids = allMsgs.associate { it.id to java.util.UUID.randomUUID().toString() }
                val copy = original.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "${original.title} copy",
                    activeLeafId = original.activeLeafId?.let(ids::get),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    deletedAt = null
                )
                db.chatDao().upsert(copy)
                allMsgs.forEach { message ->
                    db.messageDao().upsert(
                        message.copy(id = ids.getValue(message.id), chatId = copy.id, parentId = message.parentId?.let(ids::get))
                    )
                }
            }
            onDone()
        }
    }

    val knowledgeBases = db.knowledgeBaseDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Wires this chat's transcript into a knowledge base. */
    fun addToKnowledgeBase(kbId: String) {
        viewModelScope.launch {
            val name = db.chatDao().getChat(chatId)?.title ?: "Chat"
            app.container.documentImportManager.importRawText(kbId, name, exportText())
        }
    }

    /**
     * What one message contributes to an export/share, or null if it contributes nothing.
     *
     * Reasoning (`<think>`) and `<tool_call>` markup are internal mechanics, not transcript: they
     * were previously exported verbatim, so sharing a chat leaked the model's private chain of
     * thought and raw tool JSON into whatever the user sent it to. SYSTEM turns are dropped for the
     * same reason — they're prompt plumbing the user never wrote or saw.
     */
    private fun exportBody(message: Message): String? {
        if (message.role == MessageRole.SYSTEM) return null
        val withoutThinking = com.vervan.chat.llm.ThinkingParser.parse(message.content).answer
        val withoutTools = ToolCallParser.stripForDisplay(withoutThinking)
        val clarified = com.vervan.chat.llm.ClarificationParser.parse(withoutTools)
        val text = clarified.answer.ifBlank { clarified.request?.question.orEmpty() }.trim()
        return text.ifBlank { null }
    }

    /** The messages an export covers: the active branch only, oldest first. [getAllMessages] spans
     *  every branch, so an export used to include abandoned regenerations the user had moved on
     *  from, interleaved by timestamp with the conversation they actually kept. */
    private suspend fun exportableMessages(): List<Message> {
        val chatRow = db.chatDao().getChat(chatId)
        return BranchUtil.pathTo(getAllMessages(), chatRow?.activeLeafId)
    }

    suspend fun exportText(): String {
        val chatRow = db.chatDao().getChat(chatId)
        val messages = exportableMessages()
        return buildString {
            appendLine("# ${chatRow?.title ?: "Chat"}")
            messages.forEach { message ->
                val body = exportBody(message) ?: return@forEach
                appendLine()
                appendLine("${message.role.name.lowercase().replaceFirstChar(Char::uppercase)}:")
                appendLine(body)
            }
        }
    }

    /** Markdown transcript written to a private file for sharing (chat menu → "Export as
     * file"). Message bodies are already Markdown (that's how they render in-app), so role
     * headings + separators are all the structure needed; a file share survives length limits
     * that clip long transcripts when stuffed into a plain-text share intent. */
    suspend fun exportMarkdownFile(): java.io.File {
        val chatRow = db.chatDao().getChat(chatId)
        val title = chatRow?.title?.takeIf { it.isNotBlank() } ?: "Chat"
        val body = buildString {
            appendLine("# $title")
            appendLine()
            appendLine(
                "_Exported from Vervan on ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}_"
            )
            exportableMessages().forEach { message ->
                val text = exportBody(message) ?: return@forEach
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**${message.role.name.lowercase().replaceFirstChar(Char::uppercase)}:**")
                appendLine()
                appendLine(text.trimEnd())
            }
        }
        val dir = java.io.File(app.filesDir, "exports").apply { mkdirs() }
        val safeName = title.replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().ifEmpty { "chat" }.take(60)
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        return java.io.File(dir, "$safeName-$stamp.md").apply { writeText(body) }
    }

    /** PDF transcript for sharing/printing outside the app — same content as
     * [exportMarkdownFile], run through [ChatPdfExporter] instead. Runs on IO since PDFBox does
     * blocking file I/O while writing the document. */
    suspend fun exportPdfFile(): java.io.File = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val chatRow = db.chatDao().getChat(chatId)
        val title = chatRow?.title?.takeIf { it.isNotBlank() } ?: "Chat"
        val subtitle = "Exported from Vervan on ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}"
        val entries = exportableMessages().mapNotNull { message ->
            val label = message.role.name.lowercase().replaceFirstChar(Char::uppercase)
            val body = exportBody(message) ?: return@mapNotNull null
            // Strip the Markdown emphasis/heading markers PDFBox has no renderer for, so the
            // PDF reads as plain prose instead of showing literal **/#/` characters.
            val plain = body
                .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "$1")
                .replace(Regex("`(.+?)`"), "$1")
            com.vervan.chat.model.PdfTranscriptEntry(label, plain)
        }
        val dir = java.io.File(app.filesDir, "exports").apply { mkdirs() }
        val safeName = title.replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().ifEmpty { "chat" }.take(60)
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val file = java.io.File(dir, "$safeName-$stamp.pdf")
        com.vervan.chat.model.ChatPdfExporter.write(file, title, subtitle, entries)
        file
    }

    /** Runs the rule-based detector over a just-sent user message and enqueues
     * any hits as pending [com.vervan.chat.data.db.entities.MemorySuggestion] rows — never a
     * real [com.vervan.chat.data.db.entities.Memory] directly. Skips a candidate if an
     * enabled global memory already carries the same key, or a suggestion for that key is
     * already pending, so repeating yourself doesn't spam the inbox. */
    private fun enqueueMemorySuggestions(userText: String) {
        viewModelScope.launch {
            val candidates = com.vervan.chat.data.repo.MemorySuggestionDetector.detect(userText)
            val blockedKeys = app.container.settingsRepository.blockedMemorySuggestionKeys.first()
            for (candidate in candidates) {
                if (candidate.key != null) {
                    if (candidate.key in blockedKeys) continue
                    val existing = db.memoryDao().getEnabled()
                        .firstOrNull { it.key == candidate.key }
                    if (existing != null) continue
                    if (db.memorySuggestionDao().getPendingByKey(candidate.key) != null) continue
                }
                db.memorySuggestionDao().upsert(
                    com.vervan.chat.data.db.entities.MemorySuggestion(
                        text = candidate.text,
                        key = candidate.key,
                        scope = com.vervan.chat.data.db.entities.MemoryScope.GLOBAL,
                        sourceChatId = chatId
                    )
                )
            }
        }
    }

    fun send(
        text: String,
        imagePath: String? = null,
        audioPath: String? = null,
        documentId: String? = null,
        inputModality: String = "TEXT",
        transcriptMetadataJson: String? = null,
        voiceRecordingPath: String? = null
    ) {
        if (text.isBlank() && imagePath == null && audioPath == null && documentId == null) return
        if (text.length > InputLimits.CHAT_TEXT_CHARS) {
            _error.value = "Message is too long (maximum ${InputLimits.CHAT_TEXT_CHARS} characters)"
            return
        }
        Log.i(TAG, "[$chatId] send() textLen=${text.length}, hasImage=${imagePath != null}, hasAudio=${audioPath != null}")
        draftSaveJob?.cancel()
        launchGeneration {
            val expandedText = expandSlashCommand(text)
            val chatRow = db.chatDao().getChat(chatId) ?: return@launchGeneration null
            // Auto-title from the first real message ("Generate a new title" —
            // this is the automatic version) instead of leaving every chat labeled "New chat"
            // in the list forever. Only fires once: a chat the user already renamed, or one
            // past its first message, is left alone.
            val autoTitle = if (chatRow.title == "New chat" && getAllMessages().isEmpty() && expandedText.isNotBlank()) {
                expandedText.trim().take(60).let { if (expandedText.trim().length > 60) "$it…" else it }
            } else chatRow.title
            db.chatDao().update(chatRow.copy(title = autoTitle, draft = "", updatedAt = System.currentTimeMillis()))

            val userMessage = Message(
                chatId = chatId,
                parentId = chatRow.activeLeafId,
                role = MessageRole.USER,
                content = expandedText,
                imagePath = imagePath,
                documentId = documentId,
                audioPath = audioPath,
                voiceRecordingPath = voiceRecordingPath,
                inputModality = inputModality,
                transcriptMetadataJson = transcriptMetadataJson
            )
            db.messageDao().upsert(userMessage)
            setActiveLeaf(userMessage.id)
            // Incognito mode — nothing learned from a temporary chat persists past it.
            if (!chatRow.isTemporary) enqueueMemorySuggestions(expandedText)

            GenerationRequest(expandedText, imagePath, audioPath)
        }
    }

    /**
     * Hands-free voice entry point. It deliberately reuses [send], so a spoken turn has the
     * same parent/branch, context, attachments, retrieval, tools, model and persistence behavior
     * as a typed turn. Updates are forwarded from the Room-backed message stream so TTS can begin
     * sentence-by-sentence while the ordinary assistant bubble is still streaming.
     */
    suspend fun sendVoiceAndAwait(
        text: String,
        imagePath: String? = null,
        audioPath: String? = null,
        documentId: String? = null,
        inputModality: String = "HANDS_FREE",
        outputModalities: String = "TEXT,SPEECH",
        voiceRecordingPath: String? = null,
        sttLabel: String? = null,
        durationMs: Int? = null,
        onAssistantUpdate: (String) -> Unit
    ): String {
        if (_isGenerating.value) return ""
        val assistantIdsBefore = messages.value.asSequence()
            .filter { it.role == MessageRole.ASSISTANT }
            .map { it.id }
            .toSet()
        send(
            text = text,
            imagePath = imagePath,
            audioPath = audioPath,
            documentId = documentId,
            inputModality = inputModality,
            transcriptMetadataJson = org.json.JSONObject()
                .put("originalTranscript", text)
                .put("submittedText", text)
                .put("edited", false)
                .put("sttModel", sttLabel)
                .put("durationMs", durationMs)
                .put("audioReference", voiceRecordingPath)
                .toString(),
            voiceRecordingPath = voiceRecordingPath
        )
        if (!_isGenerating.value) return ""

        val completed = combine(messages, isGenerating) { current, generating ->
            val response = current.lastOrNull {
                it.role == MessageRole.ASSISTANT && it.id !in assistantIdsBefore
            }
            response to generating
        }
            .map { snapshot ->
                snapshot.first?.content?.let(onAssistantUpdate)
                snapshot
            }
            .filter { (response, generating) ->
                !generating || response?.state in setOf(
                    MessageState.COMPLETE,
                    MessageState.CANCELLED,
                    MessageState.INTERRUPTED,
                    MessageState.FAILED
                )
            }
            .first()

        completed.first?.let { response ->
            if (response.outputModalities != outputModalities) {
                db.messageDao().update(response.copy(outputModalities = outputModalities))
            }
        }
        return completed.first?.content.orEmpty()
    }

    /**
     * Edits [messageId] (a USER message) by creating a sibling with new text — the
     * original stays in the tree, this becomes a new branch — and regenerates from there.
     */
    fun editAndResend(messageId: String, newText: String) {
        if (newText.isBlank()) return
        Log.i(TAG, "[$chatId] editAndResend() messageId=$messageId")
        launchGeneration {
            val original = getAllMessages().find { it.id == messageId && it.role == MessageRole.USER }
                ?: return@launchGeneration null
            val expandedText = expandSlashCommand(newText)
            val branchMessage = Message(
                chatId = chatId,
                parentId = original.parentId,
                role = MessageRole.USER,
                content = expandedText,
                imagePath = original.imagePath,
                documentId = original.documentId,
                audioPath = original.audioPath,
                // An edit is a new manually-authored text turn. Do not inherit the original
                // voice provenance: doing so incorrectly rendered ordinary edits as
                // "Typed + dictated" and kept the old transcription badge on the new branch.
                voiceRecordingPath = null,
                inputModality = "TEXT",
                transcriptMetadataJson = null
            )
            db.messageDao().upsert(branchMessage)
            setActiveLeaf(branchMessage.id)

            GenerationRequest(expandedText, branchMessage.imagePath, branchMessage.audioPath)
        }
    }

    /**
     * Duplicates this chat's history from the root up to and including [messageId] into a new
     * chat (same workspace/persona/model/settings), so the user can branch a fresh conversation
     * off any point without disturbing the original. Returns the new chat's id.
     */
    suspend fun forkChat(messageId: String): String {
        val chatRow = db.chatDao().getChat(chatId) ?: return chatId
        val path = BranchUtil.pathTo(getAllMessages(), messageId)
        // Forking a fork must not stack suffixes ("title (fork) (fork) (fork)…") — strip any
        // existing "(fork N)" first, then number against the base title so repeated forks (of
        // the original or of each other) read "Title (fork 1)", "Title (fork 2)", ... in order
        // instead of colliding on the same name.
        val forkSuffix = Regex("""^(.*) \(fork (\d+)\)$""")
        val baseTitle = forkSuffix.matchEntire(chatRow.title)?.groupValues?.get(1) ?: chatRow.title
        val existingForkNumbers = db.chatDao().search(baseTitle)
            .mapNotNull { forkSuffix.matchEntire(it.title)?.groupValues?.get(2)?.toIntOrNull() }
        val nextForkNumber = (existingForkNumbers.maxOrNull() ?: 0) + 1
        val forked = chatRow.copy(
            id = java.util.UUID.randomUUID().toString(),
            title = "$baseTitle (fork $nextForkNumber)",
            titleIsCustom = true,
            previousTitle = null,
            activeLeafId = null,
            scrollAnchorMessageId = null,
            scrollAnchorOffsetPx = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            deletedAt = null
        )
        db.chatDao().upsert(forked)
        var lastId: String? = null
        for (message in path) {
            val copy = Message(
                chatId = forked.id,
                parentId = lastId,
                role = message.role,
                content = message.content,
                state = message.state,
                imagePath = message.imagePath,
                documentId = message.documentId,
                audioPath = message.audioPath,
                voiceRecordingPath = message.voiceRecordingPath,
                inputModality = message.inputModality,
                transcriptMetadataJson = message.transcriptMetadataJson,
                outputModalities = message.outputModalities,
                playbackMetadataJson = message.playbackMetadataJson,
                sourcesJson = message.sourcesJson,
                memoryActivityJson = message.memoryActivityJson,
                toolResultJson = message.toolResultJson,
                createdAt = message.createdAt,
                generationMs = message.generationMs,
                tokenCount = message.tokenCount
            )
            db.messageDao().upsert(copy)
            lastId = copy.id
        }
        if (lastId != null) db.chatDao().upsert(forked.copy(activeLeafId = lastId))
        Log.i(TAG, "[$chatId] forkChat() -> ${forked.id} with ${path.size} messages")
        return forked.id
    }

    /** Regenerates from [messageId] (an ASSISTANT message) — a new sibling response, same parent. */
    fun regenerate(messageId: String) {
        Log.i(TAG, "[$chatId] regenerate() messageId=$messageId")
        launchGeneration {
            val all = getAllMessages()
            val original = all.find { it.id == messageId } ?: return@launchGeneration null
            val parent = original.parentId?.let { pid -> all.find { it.id == pid } }
            setActiveLeaf(original.parentId)

            val triggerText = if (parent?.role == MessageRole.USER) parent.content else ""
            GenerationRequest(triggerText, parent?.imagePath, parent?.audioPath)
        }
    }

    /** Small-model recovery action (see [beginGeneration]'s `forceGrounding` param): re-answers
     * [messageId] with source grounding forced on for just this one retry, even if the chat's own
     * grounding toggle is off or a prior attempt already came back with no matching passages —
     * the user asked for this specific retry, so it's worth trying again rather than repeating
     * the same "no evidence" result silently. Does not flip the chat's persisted grounding
     * setting; later turns in this chat are unaffected. */
    fun retryWithSources(messageId: String) {
        Log.i(TAG, "[$chatId] retryWithSources() messageId=$messageId")
        launchGeneration {
            val all = getAllMessages()
            val original = all.find { it.id == messageId } ?: return@launchGeneration null
            val parent = original.parentId?.let { pid -> all.find { it.id == pid } }
            setActiveLeaf(original.parentId)
            val triggerText = if (parent?.role == MessageRole.USER) parent.content else ""
            GenerationRequest(triggerText, parent?.imagePath, parent?.audioPath, forceGrounding = true)
        }
    }

    /** Small-model recovery action: re-answers [messageId] with the profile forced to QUALITY
     * for just this one retry, regardless of the chat's own profile — a one-tap "try harder"
     * without permanently switching the whole chat to a slower/heavier mode. */
    fun retryWithQuality(messageId: String) {
        Log.i(TAG, "[$chatId] retryWithQuality() messageId=$messageId")
        launchGeneration {
            val all = getAllMessages()
            val original = all.find { it.id == messageId } ?: return@launchGeneration null
            val parent = original.parentId?.let { pid -> all.find { it.id == pid } }
            setActiveLeaf(original.parentId)
            val triggerText = if (parent?.role == MessageRole.USER) parent.content else ""
            GenerationRequest(triggerText, parent?.imagePath, parent?.audioPath, profileOverride = com.vervan.chat.llm.ModelProfileType.QUALITY)
        }
    }

    /**
     * Every entry point into generation routes through here — sets [_isGenerating], always
     * clears it in `finally` even if [beginGeneration] throws, and catches everything
     * (not just [Exception]) so a native MediaPipe failure can't leave the chat silently
     * stuck with [_isGenerating] permanently true (which made every future [send] a no-op —
     * the exact "imported a model, still can't chat" bug this fixes).
     */
    private fun launchGeneration(prepare: suspend () -> GenerationRequest?) {
        // Synchronous CAS closes the small window where two fast taps could both enqueue work.
        if (!_isGenerating.compareAndSet(expect = false, update = true)) return
        // Preempt any background title/summary generation from the previous turn — both hold the
        // generation mutex for their whole decode, and interactive work must not wait on them.
        if (titleJob?.isActive == true) titleRetryPending = true
        titleJob?.cancel()
        summaryJob?.cancel()
        generationJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            var responseCompleted = false
            // B16: foreground service keeps the process alive if the app is backgrounded
            // mid-generation, instead of the OS being free to kill it outright.
            com.vervan.chat.system.GenerationService.start(app)
            try {
                _error.value = null
                val request = prepare() ?: return@launch
                Log.i(TAG, "[$chatId] generation start: triggerLen=${request.triggerText.length}, hasImage=${request.imagePath != null}, hasAudio=${request.audioPath != null}")
                beginGeneration(request.triggerText, request.imagePath, request.audioPath, request.forceGrounding, request.profileOverride)
                responseCompleted = true
                Log.i(TAG, "[$chatId] generation finished in ${System.currentTimeMillis() - startedAt}ms")
            // Runs after the response completes, on its own coroutine (never
                // interrupts interactive generation).
                maybeAutoGenerateTitle()
                // Same fire-and-forget shape as the title generator above — folds turns that
                // are about to fall out of the context budget into a running summary instead
                // of just letting trimHistoryToBudget silently drop them next turn.
                maybeSummarizeOlderHistory()
            } catch (cancelled: CancellationException) {
                // Room's observed list may lag a just-inserted streaming row, so cancellation
                // updates by query instead of consulting UI state. NonCancellable guarantees
                // the cleanup survives the cancellation that brought us here.
                withContext(NonCancellable + Dispatchers.IO) {
                    db.messageDao().cancelStreamingForChat(chatId, System.currentTimeMillis() - startedAt)
                }
                throw cancelled
            } catch (t: Throwable) {
                com.vervan.chat.system.rethrowCancellation(t)
                Log.e(TAG, "[$chatId] generation FAILED after ${System.currentTimeMillis() - startedAt}ms: ${t::class.simpleName}: ${t.message}", t)
                _error.value = "Generation failed: ${t.toUserMessage()}"
            } finally {
                if (responseCompleted) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        db.messageDao().completeStreamingForChat(chatId)
                    }
                }
                _isGenerating.value = false
                _liveGenStats.value = null
                com.vervan.chat.system.GenerationService.stop(app)
            }
        }
    }

    /** Switches the active branch to whichever leaf is under [siblingId]'s subtree. */
    fun switchBranch(siblingId: String) {
        viewModelScope.launch {
            setActiveLeaf(BranchUtil.deepestTip(getAllMessages(), siblingId))
        }
    }

    /** Jumps the active leaf to exactly [messageId] — used by the branch-tree view, where the
     * user picks a precise node rather than "the deepest tip under this subtree". */
    fun jumpTo(messageId: String) {
        viewModelScope.launch { setActiveLeaf(messageId) }
    }

    private suspend fun setActiveLeaf(leafId: String?) {
        db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(activeLeafId = leafId, updatedAt = System.currentTimeMillis())) }
    }

    /** Shared tail of send/editAndResend/regenerate: load model, retrieve sources, generate.
     * [forceGrounding]/[profileOverride] are one-shot recovery-retry overrides (see
     * retryWithSources/retryWithQuality) — they never touch the chat's persisted settings. */
    private suspend fun beginGeneration(
        triggerText: String,
        imagePath: String?,
        audioPath: String? = null,
        forceGrounding: Boolean = false,
        profileOverride: com.vervan.chat.llm.ModelProfileType? = null
    ) {
        val chatRow = db.chatDao().getChat(chatId) ?: return
        val model = resolveGenerationModelForTurn(chatRow, triggerText, imagePath, audioPath)
        if (model == null) {
            Log.w(TAG, "[$chatId] beginGeneration() no generation model resolved (chatRow.modelId=${chatRow.modelId})")
            _error.value = "No model selected. Open Settings → AI models, then import or activate one."
            return
        }
        val activeEngine = activeEngineFor(model)
        Log.i(TAG, "[$chatId] beginGeneration() resolved model=${model.displayName} (engine=${model.engine}), loadedModelPath=${activeEngine.loadedModelPath}")
        // Only compute (and force) the profile-scaled context on a genuinely fresh load. If
        // this exact model is already resident — e.g. just loaded manually from Model Manager
        // at its full context — passing a smaller override here would change the coordinator's
        // load config fingerprint and trigger a pointless full unload+reload to shrink an
        // already-sufficient context, right before generating with it.
        val alreadyResident = app.container.modelLoadCoordinator.state.value[ModelRole.GENERATION]?.currentModelId == model.id
        val adjustedContext = if (alreadyResident) null else computeAdjustedContext(chatRow.profile, model)
        val loadResult = app.container.modelLoadCoordinator.ensureLoaded(model, LoadTrigger.CHAT_SEND, adjustedContext)
        if (!loadResult.success) {
            _error.value = "Could not load ${model.displayName}: ${loadResult.errorMessage ?: "unknown error"}"
            return
        }
        // Model-level capability, not activeEngine's own property: activeEngineFor falls back to
        // the shared LiteRT-LM engine object for anything that isn't llama.cpp, so a REMOTE_API
        // model reading activeEngine.visionEnabled was actually reading whatever unrelated local
        // model that engine last happened to load — never this model's own declared capability.
        // AppContainer.visionEnabled/audioEnabled already resolve correctly per engine (see
        // EngineTraits.capabilitiesUserDeclared).
        val canSeeImages = app.container.visionEnabled(model)
        val canHearAudio = app.container.audioEnabled(model)
        if (imagePath != null && audioPath != null && (!canSeeImages || !canHearAudio)) {
            val missing = buildList {
                if (!canSeeImages) add("images")
                if (!canHearAudio) add("audio")
            }.joinToString(" and ")
            val assistantMessage = Message(
                chatId = chatId,
                parentId = chatRow.activeLeafId,
                role = MessageRole.ASSISTANT,
                content = "This model cannot process image and audio together because it does not support $missing. Choose a multimodal model or remove one attachment and resend.",
                state = MessageState.COMPLETE
            )
            db.messageDao().upsert(assistantMessage)
            setActiveLeaf(assistantMessage.id)
            return
        }
        if (audioPath != null && !canHearAudio) {
            val assistantMessage = Message(
                chatId = chatId,
                parentId = chatRow.activeLeafId,
                role = MessageRole.ASSISTANT,
                content = "This model cannot use audio. Load an audio-capable model and resend.",
                state = MessageState.COMPLETE
            )
            db.messageDao().upsert(assistantMessage)
            setActiveLeaf(assistantMessage.id)
            return
        }
        // Mirrors the audio guard above — without it, an image was silently packed into
        // Content.ImageBytes and sent to an engine loaded at Capability.TEXT_ONLY (no
        // visionBackend), which the native runtime just drops with no error surfaced
        // anywhere: the attachment looked sent but the model never actually saw it.
        //
        // scan_qr_code is the one exception: it decodes the attached image itself (ZXing, not
        // the model's own vision), so a text-only model can still use it. Blocking the turn
        // outright here — as this used to do unconditionally — meant that tool could never be
        // reached at all: generation stopped before the model ever got a hop to call it. When
        // the tool is available, let the turn through with a system note instead of the image
        // itself (still gated by sendImageToModel below, same "never silently drop it" guarantee
        // as the block this replaces).
        if (imagePath != null && !canSeeImages) {
            val qrToolAvailable = chatRow.toolsEnabled && model.supportsTools != false && "scan_qr_code" in effectiveToolIds(chatRow)
            if (!qrToolAvailable) {
                val assistantMessage = Message(
                    chatId = chatId,
                    parentId = chatRow.activeLeafId,
                    role = MessageRole.ASSISTANT,
                    content = "This model cannot view images. Load a vision model, or turn on the scan_qr_code tool to decode a QR code/barcode from it, and resend.",
                    state = MessageState.COMPLETE
                )
                db.messageDao().upsert(assistantMessage)
                setActiveLeaf(assistantMessage.id)
                return
            }
            val hintMessage = Message(
                chatId = chatId, parentId = chatRow.activeLeafId, role = MessageRole.SYSTEM,
                content = "An image is attached to this message. This model has no vision and cannot see it directly — " +
                    "call scan_qr_code to decode any QR code or barcode it might contain."
            )
            db.messageDao().upsert(hintMessage)
            setActiveLeaf(hintMessage.id)
        }

        val effectiveProfileType = profileOverride ?: com.vervan.chat.system.DeviceAwareProfile.resolve(
            app,
            ModelProfileType.fromId(chatRow.profile),
        )
        val profile = ModelProfiles.resolve(effectiveProfileType)
        val groundingRequested = (forceGrounding || chatRow.sourceGrounded) && chatRow.kbIdList().isNotEmpty() && profile.retrievalTopK > 0
        // retrieveSources and recallMemories don't depend on each other's output (memory recall
        // only needs chatRow/triggerText), but used to run one after the other — each doing its
        // own embedding call plus a DB scan, serially, before generation could even start. That
        // was a real chunk of the "10-15s before anything happens" delay on a grounded send.
        // Run them concurrently instead; wall-clock cost becomes whichever one is slower, not
        // both added together.
        val (passages, memoryRecall) = coroutineScope {
            val passagesDeferred = async {
                if (groundingRequested) retrieveSources(chatRow.kbIdList(), triggerText, profile.retrievalTopK, queryModel = model) else emptyList()
            }
            val memoryDeferred = async { recallMemories(chatRow, triggerText) }
            passagesDeferred.await() to memoryDeferred.await()
        }
        // Grounding was on but nothing matched — abstain/weak-evidence signal (B6)
        // instead of silently answering as if grounding were off.
        val noEvidenceFound = groundingRequested && passages.isEmpty()
        runGenerationLoop(
            chatRow.toolsEnabled && model.supportsTools != false,
            imagePath,
            audioPath,
            passages,
            profile,
            effectiveProfileType.id,
            noEvidenceFound,
            memoryRecall,
            sendImageToModel = canSeeImages
        )
    }

    /**
     * Generates one assistant message as a child of the current active leaf; if tools are
     * enabled and the model asks for a read-only one, executes it and loops (capped) so
     * the model can use the result. A reversible-write tool call stops the loop and waits
     * for [confirmToolCall]. Every message created here immediately becomes the new active
     * leaf, so the hop chain — and any later branch off of it — stays a proper tree.
     * sequential, single-tool-per-turn, hard cap of [MAX_TOOL_HOPS] — no
     * parallel calls, no multi-tool-per-response parsing (fuller loop
     * protection — per-tool rate limits, result-size caps — isn't built).
     */
    private suspend fun runGenerationLoop(
        toolsEnabled: Boolean,
        imagePath: String?,
        audioPath: String?,
        passages: List<SourcePassage>,
        profile: com.vervan.chat.llm.ResolvedProfile,
        profileId: String,
        noEvidenceFound: Boolean = false,
        memoryRecall: com.vervan.chat.data.repo.MemoryRecall,
        // false when the model itself can't see images but [imagePath] was let through anyway
        // because a tool (scan_qr_code) can still use it — see beginGeneration's vision gate.
        // [imagePath] otherwise stays available below for that tool's `_imagePath` context
        // regardless of this flag; it only controls what reaches the engine's own vision input.
        sendImageToModel: Boolean = true
    ) {
        var hop = 0
        while (hop < MAX_TOOL_HOPS) {
            hop++
            Log.i(TAG, "[$chatId] runGenerationLoop() hop=$hop/$MAX_TOOL_HOPS toolsEnabled=$toolsEnabled")
            val chatRow = db.chatDao().getChat(chatId) ?: return
            val model = resolveGenerationModelForChat(chatRow)
            val persona: Persona? = resolveEffectivePersona(chatRow)
            val projectInstructions = chatRow.projectId?.let { db.projectDao().get(it)?.instructions }
            val memories = memoryRecall.matches.map { it.memory }
            val fullHistory = BranchUtil.pathTo(getAllMessages(), chatRow.activeLeafId)
            val requestedThinkingMode = ThinkingPolicy.effectiveThinkingMode(chatRow.thinkingMode, model?.defaultThinkingMode, model?.supportsThinking)
            val contextLimitTokens = effectiveContextLimitTokens(model, profile, requestedThinkingMode)
            val (postSummaryHistory, earlierSummary) = ChatFormatting.historyAfterSummary(fullHistory, chatRow)
            val includePastThinking = app.container.settingsRepository.includePastThinkingInContext.first()
            val history = ChatFormatting.trimHistoryToBudget(
                postSummaryHistory,
                contextLimitTokens,
                includePastThinking
            )
            val promptPassages = ChatFormatting.trimPassagesToBudget(passages, contextLimitTokens)
            val enabledToolIds = if (toolsEnabled) effectiveToolIds(chatRow) else emptySet()
            // A grounded turn floods the prompt with retrieved source passages — DEEP/BALANCED
            // reasoning over that much material is exactly what was producing "thought for Ns,
            // never answers" (the model spends its whole visible-time budget digesting sources
            // and never reaches the answer, especially on a small/quantized model). Cap to FAST
            // whenever this turn actually has grounded content in it; a turn with nothing
            // retrieved (grounding off, or noEvidenceFound) keeps the user's chosen mode as-is —
            // this is about the prompt being unusually large, not a blanket downgrade.
            val effectiveThinkingMode = if (promptPassages.isNotEmpty() && requestedThinkingMode in setOf("BALANCED", "DEEP")) "FAST" else requestedThinkingMode
            val modelEngine = model?.engine ?: com.vervan.chat.data.db.entities.ModelEngine.LITERT_LM
            // A native reasoner (supportsThinking) told OFF must have its reasoning actively
            // suppressed and stripped — see suppressReasoning below. Non-reasoning models pay no
            // extra prompt for OFF (the instruction stays empty).
            val isReasoningModel = model?.supportsThinking == true
            // Strip any <think>/<thinking> block from the persisted/displayed answer whenever the
            // user turned thinking OFF — not only for models we've *flagged* as reasoners. A model
            // whose supportsThinking is still null (never probed) or false can still emit a think
            // block, and gating the strip on isReasoningModel is exactly why "OFF still shows it
            // thinking" on LiteRT: the block leaked through unstripped. ThinkingParser is a no-op
            // on text with no tags, so this is safe for genuinely non-reasoning models.
            val suppressReasoning = effectiveThinkingMode == "OFF"
            val promptBuild = buildPrompt(
                persona, projectInstructions, memories, history, promptPassages, toolsEnabled,
                stylePreferenceText(profile.maxOutputHint),
                ThinkingPolicy.reasoningInstruction(effectiveThinkingMode, modelEngine, isReasoningModel),
                app.container.settingsRepository.userProfilePrompt(),
                noEvidenceFound,
                historyTrimmed = history.size < postSummaryHistory.size,
                enabledToolIds = enabledToolIds,
                earlierSummary = earlierSummary,
                includePastThinking = includePastThinking
            )
            val baseSystemPrompt = promptBuild.systemPrompt
            val prompt = promptBuild.flatPrompt
            val thinkingSpec = ThinkingSpec.forModel(model)
            val systemPrompt = ThinkingPolicy.withModelThinkingActivation(
                baseSystemPrompt, model, effectiveThinkingMode
            )
            val generationMessages = promptBuild.messages.mapIndexed { index, message ->
                if (index == 0 && message.first == "system") "system" to systemPrompt else message
            }
            val assistantPrefill = ThinkingPolicy.assistantPrefillFor(
                effectiveThinkingMode, modelEngine, isReasoningModel, thinkingSpec
            )
            // llama.cpp only: hard cap on reasoning tokens before </think> is force-injected
            // natively (see nativeGenerate). -1 when not applicable (LiteRT, non-reasoning model,
            // or OFF — where the prefill already closed the block).
            val reasoningBudget = ThinkingPolicy.reasoningBudgetFor(
                effectiveThinkingMode, modelEngine, isReasoningModel, thinkingSpec
            )

            val assistantMessage = Message(
                chatId = chatId, parentId = chatRow.activeLeafId, role = MessageRole.ASSISTANT, content = "", state = MessageState.STREAMING,
                // "[]" (not null) when grounding was requested but found nothing, so the UI can
                // show "no matching sources" instead of no signal at all (B6).
                sourcesJson = if (hop == 1 && (promptPassages.isNotEmpty() || noEvidenceFound)) ChatFormatting.sourcesToJson(promptPassages) else null,
                memoryActivityJson = if (hop == 1) ChatFormatting.memoryRecallToJson(memoryRecall) else null,
                modelId = model?.id,
                modelName = model?.displayName,
                // An off-device model never runs doLoadGeneration (see ModelLoadCoordinator's
                // runsOnDevice short-circuit), so lastWorkingBackend on it never advances past its
                // UNVERIFIED default — showing that label under a reply from a working remote
                // model reads as a warning about a model that's actually fine.
                backend = if (model?.traits?.runsOnDevice == false) REMOTE_BACKEND_LABEL else model?.lastWorkingBackend?.name,
                profile = profileId,
                thinkingMode = effectiveThinkingMode,
            )
            db.messageDao().upsert(assistantMessage)
            setActiveLeaf(assistantMessage.id)

            val accumulated = StringBuilder()
            // The forced-open "<think>\n" prefill (see assistantPrefillFor) never reaches this
            // flow's token callback — it was appended to the native prompt, not generated — so it
            // has to be seeded here for ThinkingParser to find a matching open tag, and for the
            // persisted message content to read the same as if the model had opened the tag itself.
            if (effectiveThinkingMode != "OFF") assistantPrefill?.let { accumulated.append(it) }
            // Hard guarantee for thinking OFF on a native reasoner: the model may keep emitting a
            // <think> block no matter what the prompt says, so strip it from everything we persist
            // and display. While the model is still inside an unclosed <think> the answer is empty,
            // so the bubble simply shows the "Thinking" indicator until the real answer begins.
            fun persistContent(raw: String): String =
                if (suppressReasoning) com.vervan.chat.llm.ThinkingParser.parse(raw).answer else raw
            var failed = false
            var lastStreamPersistAt = 0L
            var lastLiveStatsAt = 0L
            val settings = app.container.settingsRepository
            val genStartedAt = android.os.SystemClock.elapsedRealtime()
            val resolvedGenParams = com.vervan.chat.llm.resolveGenerationParams(
                model, settings, chatRow.temperature, chatRow.topP, chatRow.topK,
                personaTemperature = com.vervan.chat.data.repo.PersonaTraits.temperatureFor(persona, settings.temperature.first())
            )
            // llama.cpp gets a native reasoning-token budget (ThinkingPolicy.reasoningBudgetFor)
            // that force-injects </think> once spent, so its maxOutputTokens only ever has to cover
            // the answer that follows. LiteRT-LM and REMOTE_API have no such hook — the model's own
            // reasoning shares the exact same maxOutputTokens budget as its answer, so a non-trivial
            // BALANCED/DEEP reasoning pass can exhaust the cap before ever reaching the answer,
            // completing with a full <think> block and nothing after it. That used to look like a
            // normal COMPLETE message with unreadable content; the blank-answer guard below now
            // catches it, but the actual fix is giving reasoning its own headroom on top of the
            // configured answer budget so it has room to actually finish, same size budget
            // llama.cpp's native cap already uses per mode.
            val genParams = if (isReasoningModel && effectiveThinkingMode != "OFF" && modelEngine != com.vervan.chat.data.db.entities.ModelEngine.LLAMA_CPP) {
                val reasoningHeadroom = when (effectiveThinkingMode) { "FAST" -> 256; "BALANCED" -> 1024; "DEEP" -> 4096; else -> 0 }
                resolvedGenParams.copy(maxOutputTokens = resolvedGenParams.maxOutputTokens + reasoningHeadroom)
            } else resolvedGenParams
            // Routes to whichever engine `model` actually needs (LiteRT-LM or llama.cpp) — model
            // can be null if its row vanished mid-loop (rare; beginGeneration already validated
            // one existed), in which case fall back to the LiteRT-LM engine directly, same as
            // this function's other `model?.` reads tolerate a missing model.
            (if (model != null) {
                app.container.generate(
                    model, prompt, imagePath.takeIf { hop == 1 && sendImageToModel }, audioPath.takeIf { hop == 1 },
                    genParams.temperature, genParams.topP, genParams.topK, genParams.seed,
                    genParams.minP, genParams.repetitionPenalty, genParams.maxOutputTokens, genParams.stopSequences,
                    assistantPrefill = assistantPrefill, systemPrompt = systemPrompt, reasoningBudget = reasoningBudget,
                    messages = generationMessages,
                    remoteOptions = RemoteRequestOptions(
                        tools = if (model.engine == com.vervan.chat.data.db.entities.ModelEngine.REMOTE_API && toolsEnabled) {
                            remoteToolDefinitions(enabledToolIds)
                        } else emptyList(),
                        toolChoice = if (model.engine == com.vervan.chat.data.db.entities.ModelEngine.REMOTE_API && toolsEnabled) "auto" else null,
                        thinkingMode = effectiveThinkingMode,
                        supportsThinking = isReasoningModel,
                        thinkingParameter = ThinkingSpec.forModel(model).remoteParameter,
                        seed = genParams.seed,
                        minP = model.minP,
                        repetitionPenalty = model.repetitionPenalty
                    )
                )
            } else {
                engine.generate(
                    prompt, imagePath.takeIf { hop == 1 && sendImageToModel }, audioPath.takeIf { hop == 1 },
                    genParams.temperature, genParams.topP, genParams.topK, genParams.seed
                )
            })
                .catch { e ->
                    failed = true
                    Log.e(TAG, "[$chatId] runGenerationLoop() hop=$hop generate() FAILED after ${accumulated.length} chars: ${e::class.simpleName}: ${e.message}", e)
                    db.messageDao().update(assistantMessage.copy(content = accumulated.toString(), state = MessageState.FAILED))
                    _error.value = "Generation failed: ${e.toUserMessage()}"
                }
                .collect { chunk ->
                    accumulated.append(chunk)
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastStreamPersistAt >= STREAM_PERSIST_INTERVAL_MS) {
                        db.messageDao().update(assistantMessage.copy(content = persistContent(accumulated.toString()), state = MessageState.STREAMING))
                        lastStreamPersistAt = now
                    }
                    if (now - lastLiveStatsAt >= LIVE_STATS_INTERVAL_MS) {
                        lastLiveStatsAt = now
                        val elapsedS = (now - genStartedAt) / 1000f
                        val tokens = com.vervan.chat.llm.estimateTokens(accumulated.toString())
                        val mem = android.app.ActivityManager.MemoryInfo().also {
                            (app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(it)
                        }
                        _liveGenStats.value = LiveGenStats(
                            tokens = tokens,
                            tokensPerSecond = if (elapsedS > 0f) tokens / elapsedS else 0f,
                            availMemMb = mem.availMem / (1024 * 1024),
                            totalMemMb = mem.totalMem / (1024 * 1024)
                        )
                    }
                }
            if (failed) return
            Log.i(TAG, "[$chatId] runGenerationLoop() hop=$hop generate() complete: ${accumulated.length} chars")
            val output = accumulated.toString()

            val parseResult = if (toolsEnabled) ToolCallParser.parseAll(output) else ToolCallParser.ParseResult(emptyList(), emptyList())
            val toolCall = parseResult.calls.firstOrNull()
            if (toolCall == null && parseResult.malformed.isEmpty()) {
                val generationMs = android.os.SystemClock.elapsedRealtime() - genStartedAt
                val visible = persistContent(output)
                // The engine can complete its flow having emitted nothing usable — immediate EOS,
                // an unclosed <think> block that eats the whole output, or (thinking left ON) a
                // <think> block that closed but was never followed by an actual answer — with no
                // exception thrown, so the .catch{} above never fires. Checking only
                // `visible.isBlank()` caught the suppressReasoning case (visible IS the parsed
                // answer there) but missed the other two whenever thinking was ON: `visible` is the
                // *raw* unstripped output in that branch, so a message that was 100% reasoning and
                // 0% answer still read as "non-blank" and got silently saved as a COMPLETE bubble
                // with nothing readable in it — the "model gets stuck thinking, never responds"
                // reports. Parsing out the real answer regardless of suppressReasoning catches both.
                if (com.vervan.chat.llm.ThinkingParser.parse(output).answer.isBlank()) {
                    Log.w(TAG, "[$chatId] runGenerationLoop() hop=$hop generate() produced no content after ${generationMs}ms")
                    db.messageDao().update(assistantMessage.copy(content = "", state = MessageState.FAILED))
                    _error.value = "The model didn't return a response. Try regenerating, or check that it's still loaded in Settings → AI models."
                    return
                }
                db.messageDao().update(
                    assistantMessage.copy(
                        content = visible,
                        state = MessageState.COMPLETE,
                        generationMs = generationMs,
                        tokenCount = com.vervan.chat.llm.estimateTokens(output)
                    )
                )
                return
            }
            // Strip every matched block (the executed call, any extra ones only the app-side
            // loop's one-tool-per-turn rule leaves unexecuted, and malformed ones) so nothing
            // raw is left dangling in the visible, persisted message.
            val visibleContent = ToolCallParser.stripAll(output, parseResult.calls.map { it.rawBlock } + parseResult.malformed)
            if (toolCall == null) {
                // Tag matched but the body inside wasn't valid `{"tool": ..., "params": ...}`
                // JSON — tell the model instead of silently dropping its attempt (it previously
                // just vanished with no signal to the model or the user).
                Log.w(TAG, "[$chatId] runGenerationLoop() hop=$hop tool_call block(s) failed to parse: ${parseResult.malformed.size}")
                db.messageDao().update(assistantMessage.copy(content = visibleContent, state = MessageState.COMPLETE))
                if (appendSystemAndContinue(
                        assistantMessage.id,
                        "Your <tool_call> block could not be parsed. Reply again using exactly this format, with valid JSON and no extra text inside the tags: <tool_call>{\"tool\": \"tool_name\", \"params\": {...}}</tool_call>",
                        hop
                    )
                ) return
                continue
            }
            Log.i(TAG, "[$chatId] runGenerationLoop() hop=$hop model requested tool '${toolCall.name}'")

            val tool = ToolRegistry.find(toolCall.name)?.takeIf { toolCall.name in enabledToolIds }
            if (tool == null) {
                // Also hit if the model somehow calls a tool that's disabled (globally or by
                // this chat's override) but wasn't advertised in its own catalog — the catalog
                // filter is a hint, not the only enforcement; this is the actual gate.
                Log.w(TAG, "[$chatId] runGenerationLoop() hop=$hop unknown/disabled tool '${toolCall.name}' requested")
                db.messageDao().update(
                    assistantMessage.copy(
                        content = "$visibleContent\n\n(Asked for tool \"${toolCall.name}\", which isn't available — ignored.)",
                        state = MessageState.COMPLETE
                    )
                )
                return
            }

            // A model can be configured to auto-approve reversible-write (or all) tool calls
            // instead of always stopping the loop for a tap — only meaningful once Tools is on.
            val approvalMode = model?.toolApprovalMode ?: com.vervan.chat.data.db.entities.ToolApprovalMode.ALWAYS_ASK
            val needsConfirmation = when (tool.risk) {
                // "Always ask" now means always, read-only included. It previously hard-coded
                // `false` here, so a user who deliberately picked the strictest mode still had
                // read-only tools (list_tasks, search_notes, read_clipboard…) run with no prompt —
                // the setting silently did nothing for the majority of the catalog. The two
                // auto-approve modes keep skipping read-only, which is what they're for.
                ToolRisk.READ_ONLY -> approvalMode == com.vervan.chat.data.db.entities.ToolApprovalMode.ALWAYS_ASK
                ToolRisk.REVERSIBLE_WRITE -> approvalMode == com.vervan.chat.data.db.entities.ToolApprovalMode.ALWAYS_ASK
                ToolRisk.EXTERNAL_ACTION -> approvalMode != com.vervan.chat.data.db.entities.ToolApprovalMode.AUTO_APPROVE_ALL
            }
            if (needsConfirmation) {
                db.messageDao().update(
                    assistantMessage.copy(
                        content = visibleContent,
                        state = MessageState.AWAITING_CONFIRMATION,
                        // Risk tier travels with the pending call so the confirmation card can
                        // give EXTERNAL_ACTION extra friction instead of treating it the same
                        // as REVERSIBLE_WRITE (B4).
                        toolCallJson = JSONObject().put("tool", tool.name).put("params", toolCall.params).put("risk", tool.risk.name).toString()
                    )
                )
                return
            }

            // Read-only, or auto-approved by the model's tool approval mode: execute now,
            // record the result, then loop so the model can use it.
            val result = try {
                tool.execute(app, toolCall.params.withImagePathContext(imagePath))
            } catch (t: Throwable) {
                com.vervan.chat.system.rethrowCancellation(t)
                Log.e(TAG, "[$chatId] runGenerationLoop() hop=$hop tool '${tool.name}' threw", t)
                ToolResult(false, "Tool failed: ${t.toUserMessage()}")
            }
            Log.i(TAG, "[$chatId] runGenerationLoop() hop=$hop tool '${tool.name}' result: success=${result.success}, summary=${result.summary.take(200)}")
            recordToolAudit(tool.name, toolCall.params, result, tool.risk.name)
            db.messageDao().update(
                assistantMessage.copy(
                    content = visibleContent,
                    state = MessageState.COMPLETE,
                    // Kept (not nulled) so the completed card can show what was actually sent,
                    // not just the result — same as the confirm-then-execute path below.
                    toolCallJson = JSONObject().put("tool", tool.name).put("params", toolCall.params).put("risk", tool.risk.name).toString(),
                    toolResultJson = ChatFormatting.toolResultToJson(tool.name, result)
                )
            )
            if (appendSystemAndContinue(assistantMessage.id, "Tool ${tool.name} result: ${result.summary}", hop)) return
            // else: loop — next iteration's history includes the tool result.
        }
    }

    /**
     * Appends [content] as a SYSTEM message under [parentId] and makes it the active leaf, so
     * the next hop's history includes it — shared tail for every "loop the model again with
     * this extra context" case in [runGenerationLoop] (a tool result, or malformed-call
     * feedback). Returns true if this was the last allowed hop, in which case a final
     * assistant message explains the limit was hit instead of looping again; the caller
     * should `return` when this is true.
     */
    private suspend fun appendSystemAndContinue(parentId: String, content: String, hop: Int): Boolean {
        val systemMessage = Message(chatId = chatId, parentId = parentId, role = MessageRole.SYSTEM, content = content)
        db.messageDao().upsert(systemMessage)
        setActiveLeaf(systemMessage.id)
        if (hop == MAX_TOOL_HOPS) {
            val limitMessage = Message(
                chatId = chatId, parentId = systemMessage.id, role = MessageRole.ASSISTANT,
                content = "(Reached the tool-call limit for this turn.)", state = MessageState.COMPLETE
            )
            db.messageDao().upsert(limitMessage)
            setActiveLeaf(limitMessage.id)
            return true
        }
        return false
    }

    /** User approves or rejects a pending reversible-write tool call from [messageId]. */
    fun confirmToolCall(messageId: String, approve: Boolean) {
        Log.i(TAG, "[$chatId] confirmToolCall() messageId=$messageId, approve=$approve")
        if (!approve) {
            // Previously just marked CANCELLED and stopped — the model was never told the
            // user declined, so it couldn't recover (retry differently, answer without the
            // tool, or ask something else); the conversation just dead-ended until the user
            // typed a new message unprompted. Now it loops back in, same as the approve path.
            launchGeneration {
                val all = getAllMessages()
                val message = all.find { it.id == messageId && it.state == MessageState.AWAITING_CONFIRMATION }
                    ?: return@launchGeneration null
                db.messageDao().update(message.copy(state = MessageState.CANCELLED, toolCallJson = null))
                val declineMessage = Message(
                    chatId = chatId, parentId = message.id, role = MessageRole.SYSTEM,
                    content = "The user declined to run this tool. Continue without it, or ask a follow-up if you need different information."
                )
                db.messageDao().upsert(declineMessage)
                setActiveLeaf(declineMessage.id)
                GenerationRequest(ChatFormatting.nearestUserText(all, message), null, null)
            }
            return
        }
        launchGeneration {
            val all = getAllMessages()
            val message = all.find { it.id == messageId && it.state == MessageState.AWAITING_CONFIRMATION }
                ?: return@launchGeneration null
            val callJson = message.toolCallJson?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: return@launchGeneration null
            val toolName = callJson.optString("tool")
            val chatRow = db.chatDao().getChat(chatId)
            val tool = ToolRegistry.find(toolName)?.takeIf { chatRow != null && toolName in effectiveToolIds(chatRow) }
            if (tool == null) {
                db.messageDao().update(message.copy(state = MessageState.CANCELLED, toolCallJson = null))
                return@launchGeneration null
            }
            val params = callJson.optJSONObject("params") ?: JSONObject()
            val result = try {
                tool.execute(app, params.withImagePathContext(ChatFormatting.nearestImagePath(all, message)))
            } catch (t: Throwable) {
                com.vervan.chat.system.rethrowCancellation(t)
                ToolResult(false, "Tool failed: ${t.toUserMessage()}")
            }
            recordToolAudit(tool.name, params, result, tool.risk.name)
            db.messageDao().update(
                // toolCallJson kept (not nulled) so the completed card can show the request
                // (tool + params) alongside the result, not just the result.
                message.copy(state = MessageState.COMPLETE, toolResultJson = ChatFormatting.toolResultToJson(toolName, result))
            )
            val toolResultMessage = Message(
                chatId = chatId,
                parentId = message.id,
                role = MessageRole.SYSTEM,
                content = "Tool ${tool.name} result: ${result.summary}"
            )
            db.messageDao().upsert(toolResultMessage)
            setActiveLeaf(toolResultMessage.id)
            // Retrieval query for the next generation must be what the user actually asked,
            // not the tool result summary (e.g. "Created note \"Groceries\"." would otherwise
            // become the RAG query) — beginGeneration() only uses GenerationRequest.triggerText
            // for source retrieval, so this doesn't affect what the model is prompted with.
            GenerationRequest(ChatFormatting.nearestUserText(all, message), null, null)
        }
    }

    /** Walks parent links from [from] up to the nearest USER message and returns its content —
     * used where a retrieval query needs "what did the user actually ask" rather than whatever
     * text happens to be at the current leaf (e.g. a tool call or its result summary). */
    /** Records an executed tool call to the audit history. */
    private fun recordToolAudit(toolName: String, params: JSONObject, result: ToolResult, risk: String) {
        viewModelScope.launch {
            db.toolAuditDao().insert(
                ToolAudit(
                    toolName = toolName,
                    paramsJson = ToolAuditSanitizer.sanitize(params),
                    success = result.success,
                    // Redacted for the same reason paramsJson is — see sanitizeSummary's doc
                    // comment; a tool's summary is its output, which for read_clipboard/
                    // search_notes/search_documents/recall_memory is the sensitive payload itself.
                    summary = ToolAuditSanitizer.sanitizeSummary(result.summary),
                    risk = risk,
                    chatId = chatId
                )
            )
        }
    }

    private suspend fun expandSlashCommand(text: String): String {
        val match = Regex("^/(\\w+)(?:\\s+([\\s\\S]*))?$").find(text.trim()) ?: return text
        val (name, rest) = match.destructured
        val template = db.promptTemplateDao().findByName(name) ?: return text
        return template.expand(rest)
    }

    /** [queryModel], when supplied, is the already-resident generation model for this turn — the
     * only case query expansion (see [QueryExpander]) is worth attempting, since it reuses that
     * model rather than loading a different one just to rewrite the query. Callers with no
     * concrete resident model (e.g. the context-inspector preview) pass null and just skip it. */
    private suspend fun retrieveSources(kbIds: List<String>, query: String, topK: Int = 5, queryModel: ModelInfo? = null): List<SourcePassage> {
        if (retrievingCount.getAndIncrement() == 0) _isRetrieving.value = true
        try {
            return retrieveSourcesInner(kbIds, query, topK, queryModel)
        } finally {
            if (retrievingCount.decrementAndGet() == 0) _isRetrieving.value = false
        }
    }

    private suspend fun retrieveSourcesInner(kbIds: List<String>, query: String, topK: Int, queryModel: ModelInfo?): List<SourcePassage> {
        val embeddingModel = db.modelDao().getActiveModel(ModelRole.EMBEDDING)
        val mode = if (embeddingModel != null) {
            val result = app.container.modelLoadCoordinator.ensureLoaded(ModelRole.EMBEDDING, LoadTrigger.RAG_RETRIEVAL)
            if (!result.success) {
                // Falls back to keyword search below, but this used to be a silent failure — a
                // broken embedding model made RAG quietly degrade with no visible reason at all.
                // Surface it once per attempt instead.
                Log.w(TAG, "[$chatId] retrieveSources() embedding load failed, falling back to keyword: ${result.errorMessage}")
                _error.value = "Semantic search is unavailable. Using keyword search instead."
                RetrievalMode.KEYWORD
            } else {
                val preferred = app.container.settingsRepository.defaultRetrievalMode.first()
                runCatching { RetrievalMode.valueOf(preferred) }.getOrDefault(RetrievalMode.HYBRID)
            }
        } else RetrievalMode.KEYWORD

        val queries = if (queryModel != null && app.container.settingsRepository.queryExpansionEnabled.first()) {
            com.vervan.chat.system.runCatchingPreservingCancellation {
                com.vervan.chat.retrieval.QueryExpander.expand(app, queryModel, query)
            }.getOrDefault(listOf(query))
        } else listOf(query)

        // Called directly, NOT wrapped in app.container.withEmbedding{} — retrieve() already takes
        // embeddingMutex itself (see embedWith) for the one moment it actually needs it. Wrapping
        // the whole call in that same mutex again here used to self-deadlock: Mutex isn't
        // reentrant, so the inner lock attempt just hung forever waiting for the outer one (held
        // by this same coroutine) to release — every grounded query with a local embedding model
        // active hung with no error, no timeout, nothing.
        val results = if (queries.size == 1) {
            retrievalEngine.retrieve(kbIds, query, mode, topK)
        } else {
            // Merge each variant's results by chunk, keeping the best score any phrasing found
            // for it — a chunk that only one reformulation surfaced is still real evidence, not
            // noise.
            val best = LinkedHashMap<String, SourcePassage>()
            for (q in queries) {
                val variantResults = retrievalEngine.retrieve(kbIds, q, mode, topK)
                for (passage in variantResults) {
                    val existing = best[passage.chunkId]
                    if (existing == null || passage.score > existing.score) best[passage.chunkId] = passage
                }
            }
            best.values.sortedByDescending { it.score }.take(topK)
        }
        // A broad "describe/summarize this document" question naturally scores low against every
        // individual chunk — it isn't about any one passage — so normal retrieval empty-handed
        // doesn't mean there's nothing to say, just that nothing matched by relevance. See
        // retrieveOverviewFallback's own doc comment for why this only applies to a small
        // attached-document scope, not a broad knowledge base.
        return results.ifEmpty { retrievalEngine.retrieveOverviewFallback(kbIds, topK) }
    }

    private suspend fun recallMemories(chatRow: Chat, query: String): com.vervan.chat.data.repo.MemoryRecall {
        if (recallingMemoryCount.getAndIncrement() == 0) _isRecallingMemory.value = true
        return try {
            // Universal recall — memory is shared across every persona/project/model.
            app.container.memoryRepository.recall(query)
        } finally {
            if (recallingMemoryCount.decrementAndGet() == 0) _isRecallingMemory.value = false
        }
    }

    fun setReaction(messageId: String, reaction: String?) {
        viewModelScope.launch { db.messageDao().setReaction(messageId, reaction) }
    }

    /** Persists why the user thumbs-downed a response (see the reason-chip prompt shown after a
     * 👎 reaction) — kept alongside the message's own modelId/profile/backend snapshot so a
     * later diagnostics pass can surface "this model/preset keeps getting this reason" without
     * needing a separate feedback table. [reason] is one of a small fixed set of labels (e.g.
     * "Repetitive", "Factually wrong") chosen in the UI, not free text — enough to spot a
     * pattern, without ever needing this offline app to interpret arbitrary user prose. */
    fun setFeedbackReason(messageId: String, reason: String?) {
        viewModelScope.launch { db.messageDao().setFeedbackReason(messageId, reason) }
    }

    /** "Suggest a more capable installed model" (small-model recovery) — a strictly bigger
     * GENERATION model than the one that actually produced [message], or null if none is
     * installed (including when the model that answered is unknown, e.g. a message from before
     * the modelId snapshot existed). Same file-size proxy [com.vervan.chat.llm.AutoModelSelector]
     * uses elsewhere for "bigger" — no per-model quality benchmark exists at this layer. */
    suspend fun suggestBetterModel(message: Message): ModelInfo? {
        val respondedWith = message.modelId?.let { db.modelDao().get(it) } ?: return null
        val respondedSize = java.io.File(respondedWith.filePath).takeIf { it.isFile }?.length()
            ?.takeIf { it > 0 } ?: respondedWith.fileSizeBytes
        return db.modelDao().observeModels().first()
            .filter { it.role == ModelRole.GENERATION && it.id != respondedWith.id }
            .filter { (java.io.File(it.filePath).takeIf { f -> f.isFile }?.length()?.takeIf { s -> s > 0 } ?: it.fileSizeBytes) > respondedSize }
            .minByOrNull { java.io.File(it.filePath).takeIf { f -> f.isFile }?.length()?.takeIf { s -> s > 0 } ?: it.fileSizeBytes }
    }

    fun rememberMessage(messageId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val saved = app.container.memoryRepository.upsert(
                com.vervan.chat.data.db.entities.Memory(text = text.trim())
            )
            val message = db.messageDao().getMessages(chatId).firstOrNull { it.id == messageId } ?: return@launch
            val activity = message.memoryActivityJson
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: JSONObject().put("mode", "text").put("recalled", JSONArray())
            val savedItems = activity.optJSONArray("saved") ?: JSONArray().also { activity.put("saved", it) }
            savedItems.put(
                JSONObject()
                    .put("id", saved.memory.id)
                    .put("text", saved.memory.text)
                    .put("indexed", saved.indexed)
            )
            db.messageDao().update(message.copy(memoryActivityJson = activity.toString()))
            _confirmationMessage.value = if (saved.indexed) "Saved to memory and semantic index" else "Saved to memory"
        }
    }

    fun cancelGeneration() {
        Log.i(TAG, "[$chatId] cancelGeneration() requested")
        app.container.modelLoadCoordinator.cancelActiveGeneration()
        generationJob?.cancel()
    }

    /** Declared response-length/tone preference from Settings, formatted as a prompt
     * instruction — empty when both are left at their neutral defaults, so a user who never
     * touches this setting pays zero prompt cost for it ("declared, not inferred"
     * personalization). The active model profile's output hint is folded in too. */
    private suspend fun stylePreferenceText(profileOutputHint: String = ""): String {
        val length = app.container.settingsRepository.responseLength.first()
        val tone = app.container.settingsRepository.responseTone.first()
        val parts = mutableListOf<String>()
        when (length) {
            "CONCISE" -> parts += "Keep responses brief and to the point."
            "DETAILED" -> parts += "Give thorough, detailed responses."
        }
        // The profile hint only adds itself if the user hasn't explicitly set a length
        // preference (BALANCED) — explicit user choice wins over profile default.
        if (length == "BALANCED" && profileOutputHint.isNotBlank()) {
            parts += when (profileOutputHint) {
                "SHORT" -> "Keep responses brief and to the point."
                "DETAILED" -> "Give thorough, detailed responses."
                else -> ""
            }
        }
        when (tone) {
            "CASUAL" -> parts += "Use a casual, conversational tone."
            "FORMAL" -> parts += "Use a formal, professional tone."
        }
        return parts.filter { it.isNotBlank() }.joinToString(" ")
    }

    private suspend fun effectiveContextLimitTokens(
        model: com.vervan.chat.data.db.entities.ModelInfo?,
        profile: com.vervan.chat.llm.ResolvedProfile,
        thinkingMode: String = "OFF"
    ): Int {
        val base = model?.contextTokens ?: app.container.settingsRepository.contextTokenLimit.first()
        val outputTokens = model?.maxOutputTokens ?: app.container.settingsRepository.maxOutputTokens.first()
        val reasoningReserve = if (model?.supportsThinking == true) {
            when (thinkingMode) {
                "FAST" -> 256
                "BALANCED" -> 1024
                "DEEP" -> 4096
                else -> 0
            }
        } else 0
        // History/retrieval trimming used to reserve only 15% for system text while output and
        // thinking budgets were added later. That allowed the prompt plus max output to exceed a
        // local KV cache or a remote provider's context window. Keep a conservative structural
        // reserve and subtract the actual completion budget before trimming input.
        val structuralReserve = (base * 0.15f).toInt().coerceAtLeast(256)
        return (base * profile.contextFraction - outputTokens - reasoningReserve - structuralReserve)
            .toInt().coerceAtLeast(1024)
    }

    /** Tool ids this chat can actually call right now: the global Settings → Tools disable
     * list, with this chat's own per-tool overrides (Chat.toolOverrideMap()) taking priority —
     * lets a chat turn on a globally-disabled tool, or off a globally-enabled one, just for
     * itself. */
    private suspend fun effectiveToolIds(chatRow: Chat): Set<String> {
        val disabledGlobally = app.container.settingsRepository.disabledToolIds.first()
        val overrides = chatRow.toolOverrideMap()
        return ToolRegistry.tools.map { it.name }
            .filter { overrides[it] ?: (it !in disabledGlobally) }
            .toSet()
    }

    /** Converts the app's tool catalog into native OpenAI function definitions for remote models.
     * Local engines continue using the compact prompt protocol because their runtimes do not share
     * one stable function-calling API. Parameter types are conservative hints; ToolRegistry is
     * still the authoritative validator when the call reaches the device. */
    private fun remoteToolDefinitions(enabledIds: Set<String>): List<RemoteToolDefinition> =
        ToolRegistry.tools.filter { it.name in enabledIds }.map { tool ->
            val properties = JSONObject()
            tool.paramNames.forEach { name ->
                val type = when {
                    name.endsWith("Millis", ignoreCase = true) || name in setOf("seconds", "hour", "minute") -> "integer"
                    name in setOf("amount", "min", "max") -> "number"
                    else -> "string"
                }
                properties.put(name, JSONObject().put("type", type))
            }
            val required = JSONArray().apply { tool.paramNames.forEach(::put) }
            RemoteToolDefinition(
                name = tool.name,
                description = tool.description,
                parameters = JSONObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", required)
                    .put("additionalProperties", false)
            )
        }

    /** Current date/time for the prompt, or null if the user turned this off in Settings —
     * injected unconditionally (not gated behind tools being on) so the model always knows
     * "now", the same way it always knows who it's talking to via the persona/user profile. */
    private suspend fun currentDateTimeText(): String? {
        if (!app.container.settingsRepository.alwaysIncludeDateTime.first()) return null
        // Zone name + offset + IANA id, matching ToolRegistry's current_datetime — without a zone
        // the model can state the time but can't answer anything relative to another timezone.
        val zone = java.util.TimeZone.getDefault()
        val fmt = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy, h:mm a zzz (XXX)", java.util.Locale.getDefault())
            .apply { timeZone = zone }
        return "${fmt.format(java.util.Date())} — timezone ${zone.id}"
    }

    private data class PromptBuild(
        val systemPrompt: String,
        val flatPrompt: String,
        val messages: List<Pair<String, String>>
    )

    private suspend fun buildPrompt(
        persona: Persona?,
        projectInstructions: String?,
        memories: List<com.vervan.chat.data.db.entities.Memory>,
        history: List<Message>,
        passages: List<SourcePassage>,
        toolsEnabled: Boolean = false,
        stylePreference: String = "",
        reasoning: String = "",
        userProfile: String = "",
        noEvidenceFound: Boolean = false,
        historyTrimmed: Boolean = false,
        enabledToolIds: Set<String> = ToolRegistry.tools.map { it.name }.toSet(),
        earlierSummary: String? = null,
        includePastThinking: Boolean = false
    ): PromptBuild {
        val sections = buildPromptSections(persona, projectInstructions, memories, history, passages, toolsEnabled, stylePreference, reasoning, userProfile, noEvidenceFound, historyTrimmed, enabledToolIds, earlierSummary, includePastThinking)
        // Split into a real "system" turn (persona/instructions/tools/memory — content every chat
        // template treats as higher-trust, model-behavior-shaping context) and a "user" turn
        // (sources/history/the actual question) instead of flattening everything into one "user"
        // message. Squashing persona instructions into a user turn is a real chat-template
        // violation most instruction-tuned models were never trained on — RLHF gives system and
        // user content different trust/priority, and a model that never sees a system turn at all
        // can behave far worse here than in a dedicated llama.cpp front-end sending proper roles,
        // which is the most likely reason "models that work fine elsewhere" misbehave in this app.
        val system = sections.filter { it.first in SYSTEM_SECTION_LABELS }.joinToString("") { it.second }
        val user = sections.filter { it.first !in SYSTEM_SECTION_LABELS }.joinToString("") { it.second }
        val currentUser = sections
            .filter { it.first !in SYSTEM_SECTION_LABELS && it.first != "Conversation history" }
            .joinToString("") { it.second }
        val messages = buildList {
            if (system.isNotBlank()) add("system" to system)
            history.forEach { message ->
                if (message.role == MessageRole.ASSISTANT && message.state in CUT_OFF_STATES) return@forEach
                val content = historyMessageContent(message, includePastThinking)
                if (content.isBlank()) return@forEach
                add(
                    when (message.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        // Remote OpenAI endpoints understand a real tool role. The AppContainer
                        // maps it to ordinary user context for llama.cpp templates that lack one.
                        MessageRole.SYSTEM -> "tool"
                    } to content
                )
            }
            if (currentUser.isNotBlank()) add("user" to currentUser.removeSuffix("Assistant: ").trim())
        }
        return PromptBuild(system, user, messages)
    }

    private fun historyMessageContent(message: Message, includePastThinking: Boolean): String {
        return ChatFormatting.contextMessageContent(message, includePastThinking)
    }

    /**
     * Same inputs as [buildPrompt], but returned as labeled sections instead of one string —
     * lets [ContextBreakdown] show where the prompt's bulk comes from without re-deriving it.
     */
    private suspend fun buildPromptSections(
        persona: Persona?,
        projectInstructions: String?,
        memories: List<com.vervan.chat.data.db.entities.Memory>,
        history: List<Message>,
        passages: List<SourcePassage>,
        toolsEnabled: Boolean = false,
        stylePreference: String = "",
        reasoning: String = "",
        userProfile: String = "",
        noEvidenceFound: Boolean = false,
        historyTrimmed: Boolean = false,
        enabledToolIds: Set<String> = ToolRegistry.tools.map { it.name }.toSet(),
        earlierSummary: String? = null,
        includePastThinking: Boolean = false
    ): List<Pair<String, String>> {
        val sections = mutableListOf<Pair<String, String>>()
        sections += "Core behavior" to (PromptPolicy.CORE_SYSTEM + "\n\n")
        currentDateTimeText()?.let { sections += "Current date & time" to "Current date & time: $it\n\n" }
        if (persona != null) {
            val traits = com.vervan.chat.data.repo.PersonaTraits.instructionFor(persona)
            val text = buildString {
                appendLine("Additional persona instructions. Apply them only when they do not conflict with the core rules.")
                if (persona.systemInstruction.isNotBlank()) appendLine(persona.systemInstruction)
                if (traits.isNotBlank()) appendLine(traits)
                appendLine()
            }
            sections += "Persona" to text
        }
        if (!projectInstructions.isNullOrBlank()) sections += "Project instructions" to (projectInstructions + "\n\n")
        if (userProfile.isNotBlank()) sections += "User profile" to (userProfile + "\n\n")
        if (stylePreference.isNotBlank()) sections += "Style preference" to (stylePreference + "\n\n")
        sections += "Clarification" to (PromptPolicy.CLARIFICATION + "\n\n")
        val latestUserRequest = history.asReversed().firstOrNull { it.role == MessageRole.USER }?.content.orEmpty()
        PromptPolicy.formattingInstructions(latestUserRequest)
            .takeIf { it.isNotBlank() }
            ?.let { sections += "Formatting" to (it + "\n\n") }
        if (toolsEnabled) {
            val catalog = ToolRegistry.catalogDescription(enabledToolIds)
            if (catalog.isNotBlank()) sections += "Tool catalog" to (PromptPolicy.TOOLS + "\n" + catalog + "\n")
        }
        if (memories.isNotEmpty()) {
            val text = buildString {
                appendLine("What you know about the user:")
                memories.forEach { appendLine("- ${it.text}") }
                appendLine()
            }
            sections += "Memory" to text
        }
        if (passages.isNotEmpty()) {
            val text = buildString {
                if (passages.any { it.isOverview }) {
                    // Overview-fallback passages (see RetrievalEngine.retrieveOverviewFallback)
                    // are a positional skim of the document, not a relevance match for this
                    // specific question — telling the model to "cite sources / say if the answer
                    // isn't there" would wrongly nudge it to hedge on a plain "describe this
                    // document" request instead of just describing it.
                    appendLine("The following are excerpts sampled across the whole attached document " +
                        "(beginning to end, not matched to the question's exact wording). Use them to " +
                        "describe, summarize, or answer about the document as a whole.")
                } else {
                    appendLine("Use the following sources to answer the next question. Cite them as [1], [2], etc. " +
                        "If the sources don't contain the answer, say so instead of guessing.")
                }
                passages.forEachIndexed { i, p -> appendLine("[${i + 1}] (${p.documentName}) ${p.excerpt}") }
                appendLine()
            }
            sections += "Retrieved sources" to text
        } else if (noEvidenceFound) {
            sections += "Retrieved sources" to (
                "Source grounding is on, but no relevant passages were found in the selected knowledge bases " +
                    "for this question. Say plainly that you found nothing relevant in the sources rather than " +
                    "guessing or answering from general knowledge as if it were sourced.\n\n"
                )
        }
        if (!earlierSummary.isNullOrBlank()) {
            sections += "Earlier conversation summary" to (
                "Summary of earlier parts of this conversation (older turns were folded into " +
                    "this summary to save context space — treat it as established background, " +
                    "not something to re-explain):\n$earlierSummary\n\n"
                )
        }
        val historyText = buildString {
            // Context eviction dropped the oldest turns to fit the budget — say so
            // rather than silently presenting a partial history as if it were the whole thing.
            if (historyTrimmed) appendLine("[Earlier turns omitted to fit this model's context budget]")
            // A stopped/failed assistant turn is cut off mid-sentence — re-embedding it as a
            // normal completed turn left a dangling "Assistant: ..." right before the next
            // question, and since a turn here is just raw text (no chat-template turn tokens),
            // the model would resume completing that cut-off sentence instead of answering the
            // new one. Drop it from the text sent to the model; the UI still shows it as "Stopped".
            for (m in history) {
                if (m.role == MessageRole.ASSISTANT && m.state in CUT_OFF_STATES) continue
                val label = when (m.role) {
                    MessageRole.USER -> "User"
                    MessageRole.ASSISTANT -> "Assistant"
                    MessageRole.SYSTEM -> "Tool result"
                }
                val text = ChatFormatting.contextMessageContent(m, includePastThinking)
                appendLine("$label: $text")
            }
        }
        if (historyText.isNotBlank()) sections += "Conversation history" to historyText
        // Placed as close as possible to the final "Assistant:" marker, not up near the system
        // instructions — Qwen-family think/no-think detection keys off the tail end of the
        // user turn, so burying this directive under tools/memory/sources/history made it
        // effectively invisible to the model on any non-trivial prompt.
        if (reasoning.isNotBlank()) sections += "Reasoning mode" to (reasoning + "\n\n")
        sections += "Current request" to "Assistant: "
        return sections
    }

    /**
     * What would go into the prompt if [draftText] were sent right now — the context
     * inspector's data source. token estimate is chars/4 (no tokenizer exposed
     * for a cheap pre-count, same tradeoff as [com.vervan.chat.model.Chunker]) — close
     * enough to show relative weight, not exact enough to promise a hard limit.
     */
    suspend fun inspectContext(draftText: String): ContextBreakdown {
        val chatRow = db.chatDao().getChat(chatId)
        val profile = ModelProfiles.resolve(ModelProfileType.fromId(chatRow?.profile))
        val persona: Persona? = resolveEffectivePersona(chatRow)
        val projectInstructions = chatRow?.projectId?.let { db.projectDao().get(it)?.instructions }
        val memories = app.container.memoryRepository
            .recall(draftText)
            .matches.map { it.memory }
        val fullHistory = BranchUtil.pathTo(getAllMessages(), chatRow?.activeLeafId)
        val model = resolveGenerationModelForChat(chatRow)
        val thinkingMode = ThinkingPolicy.effectiveThinkingMode(chatRow?.thinkingMode, model?.defaultThinkingMode, model?.supportsThinking)
        val contextLimitTokens = effectiveContextLimitTokens(model, profile, thinkingMode)
        val (postSummaryHistory, earlierSummary) = ChatFormatting.historyAfterSummary(fullHistory, chatRow)
        val includePastThinking = app.container.settingsRepository.includePastThinkingInContext.first()
        val historyForPrompt = if (draftText.isBlank()) {
            postSummaryHistory
        } else {
            postSummaryHistory + Message(
                chatId = chatId,
                role = MessageRole.USER,
                content = draftText
            )
        }
        val history = ChatFormatting.trimHistoryToBudget(
            historyForPrompt,
            contextLimitTokens,
            includePastThinking
        )
        val passages = if (chatRow?.sourceGrounded == true && chatRow.kbIdList().isNotEmpty() && draftText.isNotBlank() && profile.retrievalTopK > 0) {
            retrieveSources(chatRow.kbIdList(), draftText, profile.retrievalTopK)
        } else emptyList()
        val promptPassages = ChatFormatting.trimPassagesToBudget(passages, contextLimitTokens)
        val sections = buildPromptSections(
            persona, projectInstructions, memories, history, promptPassages, chatRow?.toolsEnabled == true,
            stylePreferenceText(profile.maxOutputHint),
            ThinkingPolicy.reasoningInstruction(
                ThinkingPolicy.effectiveThinkingMode(chatRow?.thinkingMode, model?.defaultThinkingMode, model?.supportsThinking),
                model?.engine ?: com.vervan.chat.data.db.entities.ModelEngine.LITERT_LM,
                model?.supportsThinking == true
            ),
            app.container.settingsRepository.userProfilePrompt(),
            historyTrimmed = history.size < historyForPrompt.size,
            enabledToolIds = chatRow?.let { effectiveToolIds(it) } ?: emptySet(),
            earlierSummary = earlierSummary,
            includePastThinking = includePastThinking
        )
        val items = sections.map { (label, text) -> ContextItem(label, text.length, com.vervan.chat.llm.estimateTokens(text)) }
        return ContextBreakdown(items, items.sumOf { it.estimatedTokens }, contextLimitTokens)
    }

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MAX_TOOL_HOPS = 3
        /** Stands in for a hardware backend name on an engine that has none (see
         *  EngineTraits.runsOnDevice) — shown in the model-ready pill and per-message stats. */
        private const val REMOTE_BACKEND_LABEL = "Remote"
        private const val DRAFT_SAVE_DEBOUNCE_MS = 300L
        private const val STREAM_PERSIST_INTERVAL_MS = 80L
        // Memory readout is a binder call to ActivityManager — throttled separately (and
        // coarser) than the plain in-memory token-count/speed math above it.
        private const val LIVE_STATS_INTERVAL_MS = 500L
        // First assistant reply already gives enough topic signal; waiting longer just
        // delays the sidebar update for no benefit (see TitleGenerator's own min-context guard).
        private const val AUTO_TITLE_TRIGGER_REPLIES = 1
        // Long-chat context management (summarizeOlderHistoryIfNeeded): turns this far back
        // from the tip always stay raw, never folded into the summary.
        private const val KEEP_RAW_TURNS = 6
        // Trigger summarization once the un-summarized tail alone would already eat this share
        // of the history token budget.
        private const val SUMMARIZE_TRIGGER_FRACTION = 0.8
        private val CUT_OFF_STATES = setOf(MessageState.CANCELLED, MessageState.FAILED)
        // Section labels from buildPromptSections() that belong in the chat template's "system"
        // turn rather than the "user" turn — see buildPrompt().
        private val SYSTEM_SECTION_LABELS = setOf(
            "Core behavior", "Current date & time", "Persona", "Project instructions", "User profile",
            "Style preference", "Clarification", "Formatting", "Tool catalog", "Memory", "Earlier conversation summary"
        )
    }
}

data class ContextItem(val label: String, val characters: Int, val estimatedTokens: Int)
data class ContextBreakdown(val items: List<ContextItem>, val estimatedTotalTokens: Int, val recommendedLimit: Int)
