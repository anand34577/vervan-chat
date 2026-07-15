package com.vervan.chat.ui.chat

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.branch.BranchUtil
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.MessageState
import com.vervan.chat.data.db.entities.ModelBackend
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.Folder
import com.vervan.chat.data.db.entities.ToolAudit
import com.vervan.chat.data.db.entities.Workspace
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.data.db.entities.displayName
import com.vervan.chat.data.db.entities.reconcileCapabilities
import com.vervan.chat.model.DocumentImportOutcome
import com.vervan.chat.llm.LlmEngine
import com.vervan.chat.llm.ModelProfileType
import com.vervan.chat.llm.ModelProfiles
import com.vervan.chat.llm.TitleGenerator
import com.vervan.chat.retrieval.RetrievalMode
import com.vervan.chat.retrieval.SourcePassage
import com.vervan.chat.tools.ToolCallParser
import com.vervan.chat.tools.ToolRegistry
import com.vervan.chat.tools.ToolResult
import com.vervan.chat.tools.ToolRisk
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModel(private val app: VervanApp, private val chatId: String) : ViewModel() {

    private val db = app.container.db
    private val engine = app.container.llmEngine
    private val retrievalEngine = app.container.retrievalEngine

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

    // Chat Screen spec §4/§5 — workspace/folder indicators in the top bar.
    val workspace: StateFlow<Workspace?> = chat.flatMapLatest { chatRow ->
        if (chatRow == null) flowOf(null) else db.workspaceDao().observe(chatRow.workspaceId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val folder: StateFlow<Folder?> = chat.flatMapLatest { chatRow ->
        if (chatRow?.folderId == null) flowOf(null) else db.folderDao().observeAll().map { list -> list.find { it.id == chatRow.folderId } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Chat Screen spec §9/§23 — an archived workspace remains viewable but blocks new
    // messages until it (or the chat) is restored.
    val isWorkspaceArchived: StateFlow<Boolean> = workspace.map { it?.archived == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Chat's effective persona, if any (spec §7 priority chain: chat override → workspace
     * persona → Default Persona) — the top bar's "honesty layer" chip reads this, and
     * generation resolves the same chain via [resolveEffectivePersona] so the two can't drift.
     */
    val persona: StateFlow<Persona?> = combine(chat, workspace, db.personaDao().observePersonas()) { chatRow, ws, personaList ->
        val effectiveId = chatRow?.personaId ?: ws?.personaId ?: "builtin-general"
        personaList.find { it.id == effectiveId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeModelName: StateFlow<String?> = combine(chat, db.modelDao().observeModels()) { chatRow, models ->
        val model = chatRow?.modelId?.let { id -> models.find { it.id == id && it.role == ModelRole.GENERATION } }
            ?: models.firstOrNull { it.role == ModelRole.GENERATION && it.isActive }
        model?.let { "${it.displayName} · ${it.lastWorkingBackend.displayName()}" }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val generationModels = db.modelDao().observeModels()
        .map { models -> models.filter { it.role == ModelRole.GENERATION } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

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
     * not disproven). Set once a real load attempt has happened, per spec 2.2's declared
     * vs. tested distinction — we don't gate on a static per-model flag because the same
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
        data class Ready(val name: String, val grounded: Boolean) : DocumentAttachState()
        data class Failed(val name: String, val reason: String) : DocumentAttachState()
    }
    private val _pendingDocument = MutableStateFlow<DocumentAttachState?>(null)
    val pendingDocument: StateFlow<DocumentAttachState?> = _pendingDocument

    private var generationJob: Job? = null
    private var draftSaveJob: Job? = null

    init {
        viewModelScope.launch {
            combine(chat, generationModels, app.container.settingsRepository.autoLoadDefaultModel) { chatRow, models, autoLoad ->
                Triple(resolveGenerationModel(chatRow, models), chatRow?.profile, autoLoad)
            }.distinctUntilChanged().collect { (model, profile, autoLoad) ->
                when {
                    model == null -> _modelLoadState.value = ModelLoadState.NoModel
                    engine.loadedModelPath == model.filePath -> markModelReady(model)
                    autoLoad && profile != null -> preloadModel(profile, model)
                    !autoLoad -> _modelLoadState.value = ModelLoadState.NotLoaded(model.displayName)
                }
            }
        }
    }

    private fun resolveGenerationModel(chatRow: Chat?, models: List<ModelInfo>): ModelInfo? =
        chatRow?.modelId?.let { id -> models.find { it.id == id && it.role == ModelRole.GENERATION } }
            ?: models.firstOrNull { it.role == ModelRole.GENERATION && it.isActive }

    private fun markModelReady(model: ModelInfo) {
        _modelLoadState.value = ModelLoadState.Ready(
            model.displayName,
            engine.activeBackend.name.lowercase().replaceFirstChar { it.uppercase() }
        )
        _visionAvailable.value = model.supportsVision ?: engine.visionEnabled
        _audioAvailable.value = model.supportsAudio ?: engine.audioEnabled
    }

    private suspend fun preloadModel(profile: String, model: ModelInfo) {
        _modelLoadState.value = ModelLoadState.Loading(model.displayName, "Preparing local runtime")
        app.container.withLlm {
            ensureGenerationModelLoaded(profile, model)
        }
    }

    fun retryModelLoad() {
        if (_modelLoadState.value is ModelLoadState.Loading) return
        viewModelScope.launch {
            val chatRow = db.chatDao().getChat(chatId) ?: return@launch
            val model = chatRow.modelId?.let { db.modelDao().get(it)?.takeIf { item -> item.role == ModelRole.GENERATION } }
                ?: db.modelDao().getActiveModel(ModelRole.GENERATION)
            if (model == null) {
                _modelLoadState.value = ModelLoadState.NoModel
            } else {
                preloadModel(chatRow.profile, model)
            }
        }
    }

    private data class GenerationRequest(
        val triggerText: String,
        val imagePath: String?,
        val audioPath: String?
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
     * Effective-configuration priority for persona (spec §7): chat-specific override, then the
     * chat's workspace persona, then the Default Persona. Previously only `chatRow.personaId`
     * was consulted, so a chat with no override silently got no persona at all instead of
     * inheriting its workspace's — this is the single source of truth generation and the
     * context inspector both resolve through, so the fallback can't drift between the two.
     */
    private suspend fun resolveEffectivePersona(chatRow: Chat?): Persona? {
        if (chatRow == null) return null
        chatRow.personaId?.let { id -> db.personaDao().getPersona(id)?.let { return it } }
        db.workspaceDao().get(chatRow.workspaceId)?.personaId?.let { id -> db.personaDao().getPersona(id)?.let { return it } }
        return db.personaDao().getPersona("builtin-general")
    }

    /** A fresh on-disk path for a new voice-message recording — the caller (ChatScreen's
     * [com.vervan.chat.audio.WavRecorder]) writes directly here, no separate copy step
     * needed since audio never comes from a transient content:// Uri like images do. */
    fun newAudioFile(): java.io.File {
        val dir = java.io.File(app.filesDir, "audio").apply { mkdirs() }
        return java.io.File(dir, "${System.currentTimeMillis()}.wav")
    }

    /** Copies a picked image into app storage so it survives the source content:// Uri disappearing. */
    suspend fun copyImage(uri: Uri): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val dir = java.io.File(app.filesDir, "images").apply { mkdirs() }
        val dest = java.io.File(dir, "${System.currentTimeMillis()}.jpg")
        app.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext null
        // Orientation fix (Phase 7, spec §13) — bakes the EXIF rotation into the pixels so
        // the vision model (which has no EXIF awareness) doesn't see a sideways image.
        com.vervan.chat.model.ImageUtils.fixOrientation(dest)
        dest.absolutePath
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
            val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "document"
            _pendingDocument.value = DocumentAttachState.Importing(name)
            ensureEmbeddingModelLoaded()
            val kb = KnowledgeBase(name = "Attached: $name")
            db.knowledgeBaseDao().upsert(kb)
            try {
            when (val outcome = app.container.documentImportManager.import(kb.id, uri)) {
                is DocumentImportOutcome.Imported -> applyImportOutcome(outcome.document, kb.id)
                is DocumentImportOutcome.Duplicate -> _pendingDocument.value = DocumentAttachState.Failed(name, "Already attached to this chat")
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
                Log.e(TAG, "[$chatId] document attachment failed", t)
                db.knowledgeBaseDao().delete(kb)
                _pendingDocument.value = DocumentAttachState.Failed(name, t.message ?: "Import failed")
            }
        }
    }

    /** Camera "Scan document (OCR)" — runs the same on-device ML Kit recognizer already used
     * for scanned-PDF import ([com.vervan.chat.model.OcrExtractor]), then feeds the recognized
     * text through the normal chunk/embed pipeline via [DocumentImportManager.importRawText]. */
    fun attachScannedDocument(file: java.io.File) {
        viewModelScope.launch {
            val name = "Scan ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
            _pendingDocument.value = DocumentAttachState.Importing(name)
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.vervan.chat.model.OcrExtractor.extractFromImage(file) }.getOrDefault("")
            }
            file.delete()
            if (text.isBlank()) {
                _pendingDocument.value = DocumentAttachState.Failed(name, "No readable text found in scan")
                return@launch
            }
            ensureEmbeddingModelLoaded()
            val kb = KnowledgeBase(name = "Attached: $name")
            db.knowledgeBaseDao().upsert(kb)
            val document = app.container.documentImportManager.importRawText(kb.id, name, text)
            applyImportOutcome(document, kb.id)
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
        val grounded = app.container.embeddingEngine.isLoaded
        _pendingDocument.value = DocumentAttachState.Ready(document.displayName, grounded)
    }

    private suspend fun ensureEmbeddingModelLoaded() {
        val active = db.modelDao().getActiveModel(ModelRole.EMBEDDING) ?: return
        app.container.withEmbedding { engine ->
            if (engine.loadedModelPath != active.filePath) {
                runCatching { engine.load(active.filePath, active.tokenizerPath) }
            }
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

    fun setThinkingMode(mode: String) {
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

    // Per-chat sampler overrides (spec §6/§7) — null clears back to inherited (model, then app
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

    // Chat Screen spec §17 — persists the reading position (not just "was at the bottom") so
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
    // A manual rename permanently opts the chat out of auto-title generation (spec §18/§20:
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

    // Manual "Generate title" / "Regenerate title" (spec §18) — an AI-produced title stays
    // eligible for later auto-regeneration (titleIsCustom = false), unlike a hand-typed rename.
    fun generateTitle() {
        if (_titleGenerating.value) return
        viewModelScope.launch {
            _titleGenerating.value = true
            try {
                val newTitle = TitleGenerator.generate(app, chatId)
                if (newTitle != null) {
                    db.chatDao().getChat(chatId)?.let {
                        db.chatDao().update(it.copy(title = newTitle, previousTitle = it.title, titleIsCustom = false, updatedAt = System.currentTimeMillis()))
                    }
                    _confirmationMessage.value = "Title updated"
                } else {
                    _confirmationMessage.value = "Not enough conversation content to generate a title"
                }
            } catch (e: Exception) {
                _confirmationMessage.value = "Title generation failed: ${e.message}"
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

    // Auto title generation (spec §20), workspace-scoped: run once, only after the response
    // that pushes the assistant-message count to the trigger threshold, only while the title
    // is still non-custom, and only for workspaces that opted in. Fire-and-forget on its own
    // coroutine so it never blocks or interrupts the interactive response that triggered it.
    private fun maybeAutoGenerateTitle() {
        viewModelScope.launch {
            val chatRow = db.chatDao().getChat(chatId) ?: return@launch
            if (chatRow.titleIsCustom) return@launch
            val ws = db.workspaceDao().get(chatRow.workspaceId)
            if (ws?.autoTitleGeneration != true) return@launch
            val assistantReplies = getAllMessages().count { it.role == MessageRole.ASSISTANT && it.state == MessageState.COMPLETE }
            if (assistantReplies != AUTO_TITLE_TRIGGER_REPLIES) return@launch
            val newTitle = TitleGenerator.generate(app, chatId) ?: return@launch
            db.chatDao().getChat(chatId)?.let {
                if (!it.titleIsCustom) db.chatDao().update(it.copy(title = newTitle, previousTitle = it.title, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun togglePin() {
        viewModelScope.launch { db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(pinned = !it.pinned)) } }
    }

    /** Incognito mode (Phase B). Takes effect for messages sent from this point forward — see
     * [send]'s memory-suggestion skip and [com.vervan.chat.ui.chat.ChatScreen]'s hard-delete-on-
     * close, which only fires once this is true. */
    fun toggleTemporary() {
        viewModelScope.launch { db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(isTemporary = !it.isTemporary)) } }
    }

    fun toggleArchive() {
        viewModelScope.launch { db.chatDao().getChat(chatId)?.let { db.chatDao().update(it.copy(archived = !it.archived)) } }
    }

    // One-shot confirmation banners (spec §2, §5) — "X is now active" / "Moved to X" — the
    // screen shows these as a Snackbar then clears them, same pattern as WorkspacesViewModel.
    private val _confirmationMessage = MutableStateFlow<String?>(null)
    val confirmationMessage: StateFlow<String?> = _confirmationMessage
    fun clearConfirmation() { _confirmationMessage.value = null }

    // Chat Screen spec §4 — moving a chat to another workspace unfiles it from its current
    // folder (a folder must stay within one workspace, §6/§11); persona/model overrides are
    // left untouched so an explicit chat-level choice still wins, but a chat with no override
    // now inherits the destination workspace's persona (resolveEffectivePersona), matching
    // "use destination workspace defaults only where no chat-specific override exists" (§5).
    fun moveToWorkspace(workspaceId: String, workspaceName: String) {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let {
                db.chatDao().update(it.copy(workspaceId = workspaceId, folderId = null, updatedAt = System.currentTimeMillis()))
            }
            _confirmationMessage.value = "Moved to $workspaceName"
        }
    }

    // Chat Screen spec §4 — "Set Chat X as active workspace" from the chat's own workspace
    // indicator, distinct from just opening/viewing it.
    fun setChatWorkspaceActive() {
        viewModelScope.launch {
            workspace.value?.let {
                app.container.workspaceManager.setActive(it)
                _confirmationMessage.value = "${it.name} is now active"
            }
        }
    }

    // Chat Screen spec §9/§23 — an archived workspace stays viewable but blocks new messages
    // until restored; this is the "Restore and continue" affordance.
    fun restoreChatWorkspace() {
        viewModelScope.launch { workspace.value?.let { app.container.workspaceManager.restore(it) } }
    }

    // Chat Screen spec §10 — clears every chat-specific override so the chat falls back to
    // its workspace's persona/defaults (resolveEffectivePersona, model/profile/thinking-mode
    // fallbacks already read null as "inherit"). Messages, branches, folder, and workspace are
    // untouched — this only resets configuration, never data.
    fun resetChatSettings() {
        viewModelScope.launch {
            db.chatDao().getChat(chatId)?.let {
                db.chatDao().update(
                    it.copy(
                        personaId = null, modelId = null, profile = "BALANCED", thinkingMode = "OFF",
                        sourceGrounded = false, toolsEnabled = false, knowledgeBaseIds = "",
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

    // Chat Screen spec §26 — kept to what's actually recorded today (no token/duration fields
    // exist anywhere in this app yet, see Message.kt); this omits token counts rather than
    // faking them.
    fun chatStats(): ChatStats {
        val rendered = messages.value
        val all = allMessages.value
        return ChatStats(
            totalMessages = rendered.count { it.role != MessageRole.SYSTEM },
            userMessages = rendered.count { it.role == MessageRole.USER },
            assistantMessages = rendered.count { it.role == MessageRole.ASSISTANT },
            attachments = rendered.count { it.imagePath != null || it.audioPath != null },
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
        viewModelScope.launch {
            val chat = db.chatDao().getChat(chatId)
            val messages = db.messageDao().getMessages(chatId)
            if (chat != null && messages.isEmpty() && chat.draft.isBlank()) {
                db.chatDao().delete(chat)
            } else if (chat?.isTemporary == true) {
                purgeTemporaryChat(chat)
            }
            onDone()
        }
    }

    /** Incognito mode (Phase B) — hard-deletes everything scoped to a temporary chat on close
     * (not soft-deleted to the recycle bin, unlike a normal chat): messages, tool-audit rows,
     * and any per-chat attachment knowledge bases created via [attachScannedDocument]/document
     * import (same document cleanup [com.vervan.chat.model.WorkspaceManager.delete] uses for a
     * deleted workspace's documents, scoped here to just this chat's KBs instead). Also run as
     * a cold-start sweep in VervanApp.onCreate, in case the process died before this ran. */
    private suspend fun purgeTemporaryChat(chat: com.vervan.chat.data.db.entities.Chat) {
        chat.kbIdList().forEach { kbId ->
            db.documentDao().getForKb(kbId).forEach { app.container.documentImportManager.delete(it) }
            db.knowledgeBaseDao().get(kbId)?.let { db.knowledgeBaseDao().delete(it) }
        }
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

    /** Wires this chat's transcript into a knowledge base (Phase 3, spec §17). */
    fun addToKnowledgeBase(kbId: String) {
        viewModelScope.launch {
            val name = db.chatDao().getChat(chatId)?.title ?: "Chat"
            app.container.documentImportManager.importRawText(kbId, name, exportText())
        }
    }

    suspend fun exportText(): String {
        val chatRow = db.chatDao().getChat(chatId)
        return buildString {
            appendLine("# ${chatRow?.title ?: "Chat"}")
            getAllMessages().forEach { message ->
                appendLine()
                appendLine("${message.role.name.lowercase().replaceFirstChar(Char::uppercase)}:")
                appendLine(message.content)
            }
        }
    }

    /** Runs the rule-based detector (spec §27.3) over a just-sent user message and enqueues
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
                    val existing = db.memoryDao().getApplicable(null, null)
                        .firstOrNull { it.key == candidate.key && it.enabled }
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

    fun send(text: String, imagePath: String? = null, audioPath: String? = null) {
        if (text.isBlank() && imagePath == null && audioPath == null) return
        Log.i(TAG, "[$chatId] send() textLen=${text.length}, hasImage=${imagePath != null}, hasAudio=${audioPath != null}")
        draftSaveJob?.cancel()
        launchGeneration {
            val expandedText = expandSlashCommand(text)
            val chatRow = db.chatDao().getChat(chatId) ?: return@launchGeneration null
            // Auto-title from the first real message (spec §7.1's "Generate a new title" —
            // this is the automatic version) instead of leaving every chat labeled "New chat"
            // in the list forever. Only fires once: a chat the user already renamed, or one
            // past its first message, is left alone.
            val autoTitle = if (chatRow.title == "New chat" && getAllMessages().isEmpty() && expandedText.isNotBlank()) {
                expandedText.trim().take(60).let { if (expandedText.trim().length > 60) "$it…" else it }
            } else chatRow.title
            db.chatDao().update(chatRow.copy(title = autoTitle, draft = "", updatedAt = System.currentTimeMillis()))

            val userMessage = Message(chatId = chatId, parentId = chatRow.activeLeafId, role = MessageRole.USER, content = expandedText, imagePath = imagePath, audioPath = audioPath)
            db.messageDao().upsert(userMessage)
            setActiveLeaf(userMessage.id)
            // Incognito mode (Phase B) — nothing learned from a temporary chat persists past it.
            if (!chatRow.isTemporary) enqueueMemorySuggestions(expandedText)

            GenerationRequest(expandedText, imagePath, audioPath)
        }
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
            val branchMessage = Message(chatId = chatId, parentId = original.parentId, role = MessageRole.USER, content = expandedText, imagePath = original.imagePath, audioPath = original.audioPath)
            db.messageDao().upsert(branchMessage)
            setActiveLeaf(branchMessage.id)

            GenerationRequest(expandedText, branchMessage.imagePath, branchMessage.audioPath)
        }
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
        generationJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            // B16: foreground service keeps the process alive if the app is backgrounded
            // mid-generation, instead of the OS being free to kill it outright.
            com.vervan.chat.system.GenerationService.start(app)
            try {
                _error.value = null
                val request = prepare() ?: return@launch
                Log.i(TAG, "[$chatId] generation start: triggerLen=${request.triggerText.length}, hasImage=${request.imagePath != null}, hasAudio=${request.audioPath != null}")
                app.container.withLlm {
                    beginGeneration(request.triggerText, request.imagePath, request.audioPath)
                }
                Log.i(TAG, "[$chatId] generation finished in ${System.currentTimeMillis() - startedAt}ms")
            // Runs after the response completes, on its own coroutine (spec §20 — never
                // interrupts interactive generation).
                maybeAutoGenerateTitle()
            } catch (cancelled: CancellationException) {
                // Stop is a successful user action, not an error banner.
                throw cancelled
            } catch (t: Throwable) {
                Log.e(TAG, "[$chatId] generation FAILED after ${System.currentTimeMillis() - startedAt}ms: ${t::class.simpleName}: ${t.message}", t)
                _error.value = "Generation failed: ${t.message ?: t::class.simpleName}"
            } finally {
                _isGenerating.value = false
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

    /** Loads and verifies the selected generation model. Caller owns [app.container.withLlm]. */
    private suspend fun ensureGenerationModelLoaded(profileId: String, model: ModelInfo): ModelInfo? {
        if (engine.loadedModelPath == model.filePath) {
            markModelReady(model)
            return model
        }
        _modelLoadState.value = ModelLoadState.Loading(model.displayName, "Loading model into memory")
        return try {
            val settings = app.container.settingsRepository
            val requestedContext = model.contextTokens ?: settings.contextTokenLimit.first()
            val profile = ModelProfiles.resolve(ModelProfileType.fromId(profileId))
            val adjustedContext = (requestedContext * profile.contextFraction).toInt().coerceAtLeast(1024)
            val backendPreference = when (model.preferredBackend) {
                com.vervan.chat.data.db.entities.BackendChoice.GPU -> LlmEngine.BackendPreference.GPU_ONLY
                com.vervan.chat.data.db.entities.BackendChoice.CPU -> LlmEngine.BackendPreference.CPU_ONLY
                com.vervan.chat.data.db.entities.BackendChoice.NPU -> LlmEngine.BackendPreference.NPU_ONLY
                com.vervan.chat.data.db.entities.BackendChoice.AUTO -> when (settings.preferredBackend.first()) {
                    "GPU" -> LlmEngine.BackendPreference.GPU_ONLY
                    "CPU" -> LlmEngine.BackendPreference.CPU_ONLY
                    "NPU" -> LlmEngine.BackendPreference.NPU_ONLY
                    else -> LlmEngine.BackendPreference.AUTO
                }
            }
            val backendHint = when (model.lastWorkingBackend) {
                ModelBackend.GPU -> LlmEngine.ModelBackend.GPU
                ModelBackend.CPU -> LlmEngine.ModelBackend.CPU
                ModelBackend.NPU -> LlmEngine.ModelBackend.NPU
                else -> null
            }
            val maxNumImages = model.maxNumImages ?: settings.maxNumImages.first()
            val useMtp = model.mtpEnabled
            // engine.load() is a blocking native call (GPU/NPU delegate init can take 10s+) —
            // must run off the Main dispatcher or it ANRs the whole app (was previously called
            // directly inside viewModelScope.launch, unlike ModelManagerViewModel's equivalent).
            val result = withContext(Dispatchers.Default) {
                try {
                    engine.load(model.filePath, adjustedContext, maxNumImages, backendPreference, backendHint, useMtp)
                } catch (first: Throwable) {
                    if (adjustedContext <= 2048) throw first
                    _modelLoadState.value = ModelLoadState.Loading(model.displayName, "Retrying with a smaller context")
                    engine.load(model.filePath, 2048, maxNumImages, backendPreference, backendHint, useMtp)
                }
            }
            if (result.fellBackToCpu) {
                android.widget.Toast.makeText(app, "GPU unavailable — using CPU", android.widget.Toast.LENGTH_SHORT).show()
            }
            val dbBackend = when (result.backend) {
                LlmEngine.ModelBackend.GPU -> ModelBackend.GPU
                LlmEngine.ModelBackend.NPU -> ModelBackend.NPU
                else -> ModelBackend.CPU
            }
            val (reconciled, disabled) = model.reconcileCapabilities(
                dbBackend, engine.visionEnabled, engine.audioEnabled,
                mtpAttempted = useMtp, mtpActive = engine.speculativeDecodingActive
            )
            db.modelDao().upsert(reconciled)
            markModelReady(reconciled)
            if (disabled.isNotEmpty()) {
                android.widget.Toast.makeText(
                    app, "${disabled.joinToString(", ")} not supported on this model — disabled",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            reconciled
        } catch (t: Throwable) {
            Log.e(TAG, "[$chatId] model load failed for ${model.displayName}", t)
            val reason = t.message ?: t::class.simpleName ?: "Unknown error"
            _modelLoadState.value = ModelLoadState.Failed(model.displayName, reason)
            null
        }
    }

    /** Shared tail of send/editAndResend/regenerate: load model, retrieve sources, generate. */
    private suspend fun beginGeneration(triggerText: String, imagePath: String?, audioPath: String? = null) {
        val chatRow = db.chatDao().getChat(chatId) ?: return
        val model = chatRow.modelId?.let { db.modelDao().get(it)?.takeIf { m -> m.role == ModelRole.GENERATION } }
            ?: db.modelDao().getActiveModel(ModelRole.GENERATION)
        if (model == null) {
            Log.w(TAG, "[$chatId] beginGeneration() no generation model resolved (chatRow.modelId=${chatRow.modelId})")
            _error.value = "No model selected. Import or activate one in Models."
            return
        }
        Log.i(TAG, "[$chatId] beginGeneration() resolved model=${model.displayName}, engine.loadedModelPath=${engine.loadedModelPath}")
        if (ensureGenerationModelLoaded(chatRow.profile, model) == null) {
            val failure = _modelLoadState.value as? ModelLoadState.Failed
            _error.value = "Could not load ${model.displayName}: ${failure?.reason ?: "unknown error"}"
            return
        }
        if (audioPath != null && !engine.audioEnabled) {
            val assistantMessage = Message(
                chatId = chatId,
                parentId = chatRow.activeLeafId,
                role = MessageRole.ASSISTANT,
                content = "Voice message saved, but this model runtime does not support direct audio input on this device yet.",
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
        if (imagePath != null && !engine.visionEnabled) {
            val assistantMessage = Message(
                chatId = chatId,
                parentId = chatRow.activeLeafId,
                role = MessageRole.ASSISTANT,
                content = "Image attached, but this model does not support vision on this device — try a vision-capable model from Model Manager.",
                state = MessageState.COMPLETE
            )
            db.messageDao().upsert(assistantMessage)
            setActiveLeaf(assistantMessage.id)
            return
        }

        val profile = ModelProfiles.resolve(ModelProfileType.fromId(chatRow.profile))
        val groundingRequested = chatRow.sourceGrounded && chatRow.kbIdList().isNotEmpty() && profile.retrievalTopK > 0
        val passages = if (groundingRequested) retrieveSources(chatRow.kbIdList(), triggerText, profile.retrievalTopK) else emptyList()
        // Grounding was on but nothing matched — abstain/weak-evidence signal (B6, spec §19.5)
        // instead of silently answering as if grounding were off.
        val noEvidenceFound = groundingRequested && passages.isEmpty()

        runGenerationLoop(chatRow.toolsEnabled && model.supportsTools != false, imagePath, audioPath, passages, profile, noEvidenceFound)
    }

    /**
     * Generates one assistant message as a child of the current active leaf; if tools are
     * enabled and the model asks for a read-only one, executes it and loops (capped) so
     * the model can use the result. A reversible-write tool call stops the loop and waits
     * for [confirmToolCall]. Every message created here immediately becomes the new active
     * leaf, so the hop chain — and any later branch off of it — stays a proper tree.
     * ponytail: sequential, single-tool-per-turn, hard cap of [MAX_TOOL_HOPS] — no
     * parallel calls, no multi-tool-per-response parsing (spec 16.7's fuller loop
     * protection — per-tool rate limits, result-size caps — isn't built).
     */
    private suspend fun runGenerationLoop(toolsEnabled: Boolean, imagePath: String?, audioPath: String?, passages: List<SourcePassage>, profile: com.vervan.chat.llm.ResolvedProfile, noEvidenceFound: Boolean = false) {
        var hop = 0
        while (hop < MAX_TOOL_HOPS) {
            hop++
            Log.i(TAG, "[$chatId] runGenerationLoop() hop=$hop/$MAX_TOOL_HOPS toolsEnabled=$toolsEnabled")
            val chatRow = db.chatDao().getChat(chatId) ?: return
            val model = chatRow.modelId?.let { db.modelDao().get(it)?.takeIf { m -> m.role == ModelRole.GENERATION } }
                ?: db.modelDao().getActiveModel(ModelRole.GENERATION)
            val persona: Persona? = resolveEffectivePersona(chatRow)
            val projectInstructions = chatRow.projectId?.let { db.projectDao().get(it)?.instructions }
            val memories = db.memoryDao().getApplicable(chatRow.personaId, chatRow.projectId)
            val fullHistory = BranchUtil.pathTo(getAllMessages(), chatRow.activeLeafId)
            val contextLimitTokens = effectiveContextLimitTokens(model, profile)
            val history = trimHistoryToBudget(fullHistory, contextLimitTokens)
            val promptPassages = trimPassagesToBudget(passages, contextLimitTokens)
            val enabledToolIds = if (toolsEnabled) effectiveToolIds(chatRow) else emptySet()
            val prompt = buildPrompt(
                persona, projectInstructions, memories, history, promptPassages, toolsEnabled,
                stylePreferenceText(profile.maxOutputHint),
                reasoningInstruction(if (model?.supportsThinking == false) "OFF" else chatRow.thinkingMode),
                app.container.settingsRepository.userProfilePrompt(),
                noEvidenceFound,
                historyTrimmed = history.size < fullHistory.size,
                enabledToolIds = enabledToolIds
            )

            val assistantMessage = Message(
                chatId = chatId, parentId = chatRow.activeLeafId, role = MessageRole.ASSISTANT, content = "", state = MessageState.STREAMING,
                // "[]" (not null) when grounding was requested but found nothing, so the UI can
                // show "no matching sources" instead of no signal at all (B6).
                sourcesJson = if (hop == 1 && (promptPassages.isNotEmpty() || noEvidenceFound)) sourcesToJson(promptPassages) else null
            )
            db.messageDao().upsert(assistantMessage)
            setActiveLeaf(assistantMessage.id)

            val accumulated = StringBuilder()
            var failed = false
            var lastStreamPersistAt = 0L
            val settings = app.container.settingsRepository
            engine.generate(
                prompt,
                imagePath.takeIf { hop == 1 },
                audioPath.takeIf { hop == 1 },
                // Effective-config priority (spec §7): chat-specific override first, then
                // per-model override, then the app-global setting.
                temperature = chatRow.temperature ?: model?.temperature ?: com.vervan.chat.data.repo.PersonaTraits.temperatureFor(persona, settings.temperature.first()),
                topP = chatRow.topP ?: model?.topP ?: settings.topP.first(),
                topK = chatRow.topK ?: model?.topK ?: settings.topK.first(),
                randomSeed = model?.seed ?: settings.randomSeed.first().takeIf { it >= 0 }
            )
                .catch { e ->
                    failed = true
                    Log.e(TAG, "[$chatId] runGenerationLoop() hop=$hop generate() FAILED after ${accumulated.length} chars: ${e::class.simpleName}: ${e.message}", e)
                    db.messageDao().update(assistantMessage.copy(content = accumulated.toString(), state = MessageState.FAILED))
                    _error.value = "Generation failed: ${e.message}"
                }
                .collect { chunk ->
                    accumulated.append(chunk)
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastStreamPersistAt >= STREAM_PERSIST_INTERVAL_MS) {
                        db.messageDao().update(assistantMessage.copy(content = accumulated.toString(), state = MessageState.STREAMING))
                        lastStreamPersistAt = now
                    }
                }
            if (failed) return
            Log.i(TAG, "[$chatId] runGenerationLoop() hop=$hop generate() complete: ${accumulated.length} chars")
            val output = accumulated.toString()

            val toolCall = if (toolsEnabled) ToolCallParser.parse(output) else null
            if (toolCall == null) {
                db.messageDao().update(assistantMessage.copy(content = output, state = MessageState.COMPLETE))
                return
            }
            Log.i(TAG, "[$chatId] runGenerationLoop() hop=$hop model requested tool '${toolCall.name}'")

            val visibleContent = ToolCallParser.stripToolCall(output, toolCall)
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
                ToolRisk.READ_ONLY -> false
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
                tool.execute(app, toolCall.params)
            } catch (e: Exception) {
                Log.e(TAG, "[$chatId] runGenerationLoop() hop=$hop tool '${tool.name}' threw", e)
                ToolResult(false, "Tool failed: ${e.message}")
            }
            Log.i(TAG, "[$chatId] runGenerationLoop() hop=$hop tool '${tool.name}' result: success=${result.success}, summary=${result.summary.take(200)}")
            recordToolAudit(tool.name, toolCall.params, result, tool.risk.name)
            db.messageDao().update(
                assistantMessage.copy(
                    content = visibleContent,
                    state = MessageState.COMPLETE,
                    toolResultJson = toolResultToJson(tool.name, result)
                )
            )
            val toolResultMessage = Message(
                chatId = chatId, parentId = assistantMessage.id, role = MessageRole.SYSTEM,
                content = "Tool ${tool.name} result: ${result.summary}"
            )
            db.messageDao().upsert(toolResultMessage)
            setActiveLeaf(toolResultMessage.id)

            if (hop == MAX_TOOL_HOPS) {
                val limitMessage = Message(
                    chatId = chatId, parentId = toolResultMessage.id, role = MessageRole.ASSISTANT,
                    content = "(Reached the tool-call limit for this turn.)", state = MessageState.COMPLETE
                )
                db.messageDao().upsert(limitMessage)
                setActiveLeaf(limitMessage.id)
                return
            }
            // else: loop — next iteration's history includes the tool result.
        }
    }

    /** User approves or rejects a pending reversible-write tool call from [messageId]. */
    fun confirmToolCall(messageId: String, approve: Boolean) {
        Log.i(TAG, "[$chatId] confirmToolCall() messageId=$messageId, approve=$approve")
        if (!approve) {
            viewModelScope.launch {
                val message = getAllMessages().find { it.id == messageId && it.state == MessageState.AWAITING_CONFIRMATION }
                    ?: return@launch
                db.messageDao().update(message.copy(state = MessageState.CANCELLED, toolCallJson = null))
            }
            return
        }
        launchGeneration {
            val message = getAllMessages().find { it.id == messageId && it.state == MessageState.AWAITING_CONFIRMATION }
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
                tool.execute(app, params)
            } catch (e: Exception) {
                ToolResult(false, "Tool failed: ${e.message}")
            }
            recordToolAudit(tool.name, params, result, tool.risk.name)
            db.messageDao().update(
                message.copy(state = MessageState.COMPLETE, toolCallJson = null, toolResultJson = toolResultToJson(toolName, result))
            )
            val toolResultMessage = Message(
                chatId = chatId,
                parentId = message.id,
                role = MessageRole.SYSTEM,
                content = "Tool ${tool.name} result: ${result.summary}"
            )
            db.messageDao().upsert(toolResultMessage)
            setActiveLeaf(toolResultMessage.id)
            GenerationRequest(result.summary, null, null)
        }
    }

    private fun toolResultToJson(toolName: String, result: ToolResult): String =
        JSONObject().put("tool", toolName).put("success", result.success).put("summary", result.summary).toString()

    /** Records an executed tool call to the audit history (spec §16.3 / §2.6). */
    private fun recordToolAudit(toolName: String, params: JSONObject, result: ToolResult, risk: String) {
        viewModelScope.launch {
            db.toolAuditDao().insert(
                ToolAudit(
                    toolName = toolName,
                    paramsJson = params.toString(),
                    success = result.success,
                    summary = result.summary,
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

    private suspend fun retrieveSources(kbIds: List<String>, query: String, topK: Int = 5): List<SourcePassage> {
        return app.container.withEmbedding { embeddingEngine ->
            val embeddingModel = db.modelDao().getActiveModel(ModelRole.EMBEDDING)
            val mode = if (embeddingModel != null) {
                if (embeddingEngine.loadedModelPath != embeddingModel.filePath) {
                    try {
                        embeddingEngine.load(embeddingModel.filePath, embeddingModel.tokenizerPath)
                    } catch (e: Exception) {
                        // Falls back to keyword search below, but this used to be a silent
                        // catch — a broken embedding model made RAG quietly degrade with no
                        // visible reason at all. Surface it once per attempt instead.
                        Log.w(TAG, "[$chatId] retrieveSources() embedding load failed, falling back to keyword: ${e.message}", e)
                        _error.value = "Semantic search unavailable (${e.message ?: e::class.simpleName}) — using keyword search instead."
                    }
                }
                if (embeddingEngine.isLoaded) {
                    val preferred = app.container.settingsRepository.defaultRetrievalMode.first()
                    runCatching { RetrievalMode.valueOf(preferred) }.getOrDefault(RetrievalMode.HYBRID)
                } else RetrievalMode.KEYWORD
            } else RetrievalMode.KEYWORD
            retrievalEngine.retrieve(kbIds, query, mode, topK)
        }
    }

    private fun sourcesToJson(passages: List<SourcePassage>): String {
        val arr = JSONArray()
        passages.forEach { p ->
            arr.put(
                JSONObject()
                    .put("chunkId", p.chunkId)
                    .put("documentId", p.documentId)
                    .put("documentName", p.documentName)
                    .put("sectionPath", p.sectionPath)
                    .put("excerpt", p.excerpt.take(500))
                    .put("score", p.score)
            )
        }
        return arr.toString()
    }

    fun cancelGeneration() {
        Log.i(TAG, "[$chatId] cancelGeneration() requested")
        generationJob?.cancel()
        viewModelScope.launch {
            messages.value.lastOrNull { it.state == MessageState.STREAMING }?.let {
                db.messageDao().update(it.copy(state = MessageState.CANCELLED))
            }
        }
    }

    /** Declared response-length/tone preference from Settings, formatted as a prompt
     * instruction — empty when both are left at their neutral defaults, so a user who never
     * touches this setting pays zero prompt cost for it (spec §26's "declared, not inferred"
     * personalization). The active model profile's output hint (spec §11.9) is folded in too. */
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

    /**
     * Prompt-engineered "thinking mode" (spec §15) — `tasks-genai` exposes no native
     * reasoning-mode toggle, so this just asks the model to wrap its reasoning in
     * `<thinking>` tags before answering; [com.vervan.chat.llm.ThinkingParser] splits that
     * out for display. Empty for OFF, so a chat that never touches this pays no prompt cost.
     */
    private fun reasoningInstruction(mode: String): String = when (mode) {
        "FAST" -> "Before answering, briefly think through the problem in 1-2 sentences wrapped in <thinking></thinking> tags, then give your final answer outside the tags."
        "BALANCED" -> "Before answering, think through the problem step by step wrapped in <thinking></thinking> tags, then give your final answer outside the tags."
        "DEEP" -> "Before answering, think through the problem thoroughly, considering multiple angles and edge cases, wrapped in <thinking></thinking> tags, then give your final answer outside the tags."
        else -> ""
    }

    /**
     * Context eviction (Phase 2, spec §10) — drops the oldest turns first once the
     * conversation would blow past the model's usable context, instead of growing the
     * prompt unbounded (B9-adjacent). ponytail: char-budget proxy (same chars/4 estimate
     * used elsewhere), drop-oldest-first only — no pinning, no summarization-of-dropped-
     * turns, no 16-tier priority system. Always keeps at least the most recent turn.
     */
    private fun trimHistoryToBudget(history: List<Message>, contextLimitTokens: Int): List<Message> {
        if (history.size <= 1) return history
        // Reserve roughly 60% of the token budget for history — the rest covers persona,
        // memories, retrieved sources, and the model's own output.
        val budgetChars = (contextLimitTokens * 4 * 0.6).toInt().coerceAtLeast(200)
        var totalChars = history.sumOf { it.content.length }
        if (totalChars <= budgetChars) return history
        val trimmed = history.toMutableList()
        while (trimmed.size > 1 && totalChars > budgetChars) {
            totalChars -= trimmed.removeAt(0).content.length
        }
        return trimmed
    }

    private suspend fun effectiveContextLimitTokens(model: com.vervan.chat.data.db.entities.ModelInfo?, profile: com.vervan.chat.llm.ResolvedProfile): Int {
        val base = model?.contextTokens ?: app.container.settingsRepository.contextTokenLimit.first()
        return (base * profile.contextFraction).toInt().coerceAtLeast(1024)
    }

    private fun trimPassagesToBudget(passages: List<SourcePassage>, contextLimitTokens: Int): List<SourcePassage> {
        if (passages.isEmpty()) return passages
        val budgetChars = (contextLimitTokens * 4 * 0.25f).toInt().coerceAtLeast(800)
        val perPassageChars = (budgetChars / passages.size).coerceAtLeast(200)
        return passages.map { passage ->
            passage.copy(excerpt = passage.excerpt.take(perPassageChars))
        }
    }

    /** Tool ids this chat can actually call right now: the global Settings → Tools disable
     * list, with this chat's own per-tool overrides (Chat.toolOverrideMap()) taking priority —
     * lets a chat turn on a globally-disabled tool, or off a globally-enabled one, just for
     * itself. */
    private suspend fun effectiveToolIds(chatRow: Chat): Set<String> {
        val disabledGlobally = app.container.settingsRepository.disabledToolIds.first()
        val overrides = chatRow.toolOverrideMap()
        return ToolRegistry.tools.map { it.name }.filter { overrides[it] ?: (it !in disabledGlobally) }.toSet()
    }

    /** Current date/time for the prompt, or null if the user turned this off in Settings —
     * injected unconditionally (not gated behind tools being on) so the model always knows
     * "now", the same way it always knows who it's talking to via the persona/user profile. */
    private suspend fun currentDateTimeText(): String? {
        if (!app.container.settingsRepository.alwaysIncludeDateTime.first()) return null
        val fmt = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy, h:mm a", java.util.Locale.getDefault())
        return fmt.format(java.util.Date())
    }

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
        enabledToolIds: Set<String> = ToolRegistry.tools.map { it.name }.toSet()
    ): String = buildPromptSections(persona, projectInstructions, memories, history, passages, toolsEnabled, stylePreference, reasoning, userProfile, noEvidenceFound, historyTrimmed, enabledToolIds)
        .joinToString("") { it.second }

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
        enabledToolIds: Set<String> = ToolRegistry.tools.map { it.name }.toSet()
    ): List<Pair<String, String>> {
        val sections = mutableListOf<Pair<String, String>>()
        currentDateTimeText()?.let { sections += "Current date & time" to "Current date & time: $it\n\n" }
        if (persona != null) {
            val traits = com.vervan.chat.data.repo.PersonaTraits.instructionFor(persona)
            val text = if (traits.isNotBlank()) "${persona.systemInstruction}\n$traits\n\n" else "${persona.systemInstruction}\n\n"
            sections += "Persona" to text
        }
        if (!projectInstructions.isNullOrBlank()) sections += "Project instructions" to (projectInstructions + "\n\n")
        if (userProfile.isNotBlank()) sections += "User profile" to (userProfile + "\n\n")
        if (stylePreference.isNotBlank()) sections += "Style preference" to (stylePreference + "\n\n")
        if (reasoning.isNotBlank()) sections += "Reasoning mode" to (reasoning + "\n\n")
        if (toolsEnabled) {
            val catalog = ToolRegistry.catalogDescription(enabledToolIds)
            if (catalog.isNotBlank()) sections += "Tool catalog" to (catalog + "\n")
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
                appendLine("Use the following sources to answer the next question. Cite them as [1], [2], etc. " +
                    "If the sources don't contain the answer, say so instead of guessing.")
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
        val historyText = buildString {
            // Context eviction (Phase 2) dropped the oldest turns to fit the budget — say so
            // rather than silently presenting a partial history as if it were the whole thing.
            if (historyTrimmed) appendLine("[Earlier turns omitted to fit this model's context budget]")
            for (m in history) {
                val label = when (m.role) {
                    MessageRole.USER -> "User"
                    MessageRole.ASSISTANT -> "Assistant"
                    MessageRole.SYSTEM -> "Tool result"
                }
                // Strip any <thinking> block from prior assistant turns — the model's own
                // past reasoning doesn't need to keep re-entering context every future turn,
                // just its actual answer (spec §8.3-adjacent context hygiene for §15).
                val text = if (m.role == MessageRole.ASSISTANT) com.vervan.chat.llm.ThinkingParser.parse(m.content).answer else m.content
                appendLine("$label: $text")
            }
        }
        if (historyText.isNotBlank()) sections += "Conversation history" to historyText
        sections += "Current request" to "Assistant: "
        return sections
    }

    /**
     * What would go into the prompt if [draftText] were sent right now — the context
     * inspector's data source. ponytail: token estimate is chars/4 (no tokenizer exposed
     * for a cheap pre-count, same tradeoff as [com.vervan.chat.model.Chunker]) — close
     * enough to show relative weight, not exact enough to promise a hard limit.
     */
    suspend fun inspectContext(draftText: String): ContextBreakdown {
        val chatRow = db.chatDao().getChat(chatId)
        val profile = ModelProfiles.resolve(ModelProfileType.fromId(chatRow?.profile))
        val persona: Persona? = resolveEffectivePersona(chatRow)
        val projectInstructions = chatRow?.projectId?.let { db.projectDao().get(it)?.instructions }
        val memories = db.memoryDao().getApplicable(chatRow?.personaId, chatRow?.projectId)
        val fullHistory = BranchUtil.pathTo(getAllMessages(), chatRow?.activeLeafId)
        val model = chatRow?.modelId?.let { db.modelDao().get(it)?.takeIf { m -> m.role == ModelRole.GENERATION } }
            ?: db.modelDao().getActiveModel(ModelRole.GENERATION)
        val contextLimitTokens = effectiveContextLimitTokens(model, profile)
        val history = trimHistoryToBudget(fullHistory, contextLimitTokens)
        val passages = if (chatRow?.sourceGrounded == true && chatRow.kbIdList().isNotEmpty() && draftText.isNotBlank() && profile.retrievalTopK > 0) {
            retrieveSources(chatRow.kbIdList(), draftText, profile.retrievalTopK)
        } else emptyList()
        val promptPassages = trimPassagesToBudget(passages, contextLimitTokens)
        val sections = buildPromptSections(
            persona, projectInstructions, memories, history, promptPassages, chatRow?.toolsEnabled == true,
            stylePreferenceText(profile.maxOutputHint), reasoningInstruction(chatRow?.thinkingMode ?: "OFF"),
            app.container.settingsRepository.userProfilePrompt(),
            historyTrimmed = history.size < fullHistory.size,
            enabledToolIds = chatRow?.let { effectiveToolIds(it) } ?: emptySet()
        )
        val items = sections.map { (label, text) -> ContextItem(label, text.length, text.length / 4) }
        return ContextBreakdown(items, items.sumOf { it.estimatedTokens }, contextLimitTokens)
    }

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MAX_TOOL_HOPS = 3
        private const val DRAFT_SAVE_DEBOUNCE_MS = 300L
        private const val STREAM_PERSIST_INTERVAL_MS = 80L
        // Spec §20 — "two or three meaningful user-assistant exchanges."
        private const val AUTO_TITLE_TRIGGER_REPLIES = 2
    }
}

data class ContextItem(val label: String, val characters: Int, val estimatedTokens: Int)
data class ContextBreakdown(val items: List<ContextItem>, val estimatedTotalTokens: Int, val recommendedLimit: Int)
