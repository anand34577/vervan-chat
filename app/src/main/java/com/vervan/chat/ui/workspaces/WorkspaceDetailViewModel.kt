package com.vervan.chat.ui.workspaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Folder
import com.vervan.chat.data.db.entities.JobRecord
import com.vervan.chat.data.db.entities.JobState
import com.vervan.chat.data.db.entities.JobType
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.Workspace
import com.vervan.chat.llm.TitleGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkspaceDetailViewModel(private val app: VervanApp, private val workspaceId: String) : ViewModel() {
    private val db = app.container.db
    private val workspaceManager = app.container.workspaceManager
    private val settingsRepository = app.container.settingsRepository

    private val _confirmationMessage = MutableStateFlow<String?>(null)
    val confirmationMessage: StateFlow<String?> = _confirmationMessage
    fun clearConfirmation() { _confirmationMessage.value = null }

    val workspace: StateFlow<Workspace?> = db.workspaceDao().observe(workspaceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val personas: StateFlow<List<Persona>> = db.personaDao().observePersonas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeWorkspaceId: StateFlow<String> = settingsRepository.activeWorkspaceId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Workspace.DEFAULT_WORKSPACE_ID)

    val chats: StateFlow<List<Chat>> = db.chatDao().observeForWorkspace(workspaceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = db.folderDao().observeForWorkspace(workspaceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // §7 status summary — active/archived chat counts and document count, kept lean (total
    // chats/folders/docs, not the full token-usage breakdown spec §15 describes, which needs
    // per-message token accounting this app doesn't record yet).
    val activeChatCount: StateFlow<Int> = db.chatDao().observeActiveCountForWorkspace(workspaceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val archivedChatCount: StateFlow<Int> = db.chatDao().observeArchivedCountForWorkspace(workspaceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val folderCount: StateFlow<Int> = db.folderDao().observeCountForWorkspace(workspaceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val documentCount: StateFlow<Int> = db.documentDao().observeCountForWorkspace(workspaceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // personaId is left null (not copied from the workspace) so the chat inherits via the
    // effective-config fallback chain (spec §7) — if the workspace persona changes later,
    // this chat follows along instead of being pinned to whatever it was at creation time.
    suspend fun createChat(): String {
        val chat = workspaceManager.applyDefaults(Chat(workspaceId = workspaceId))
        db.chatDao().upsert(chat)
        return chat.id
    }

    fun rename(name: String, description: String) {
        val current = workspace.value ?: return
        if (current.isDefault || name.isBlank()) return
        viewModelScope.launch {
            db.workspaceDao().update(current.copy(name = name.trim(), description = description.trim(), updatedAt = System.currentTimeMillis()))
        }
    }

    fun setPersona(personaId: String) {
        val current = workspace.value ?: return
        if (current.isDefault) return
        viewModelScope.launch { db.workspaceDao().update(current.copy(personaId = personaId, updatedAt = System.currentTimeMillis())) }
    }

    // Chat Screen spec §20 — kept workspace-scoped, not global/per-chat: every chat in this
    // workspace either gets auto-generated titles or none do. Allowed even for the Default
    // Workspace (only identity/lifecycle fields are locked for it, not feature toggles).
    fun setAutoTitleGeneration(enabled: Boolean) {
        val current = workspace.value ?: return
        viewModelScope.launch { db.workspaceDao().update(current.copy(autoTitleGeneration = enabled, updatedAt = System.currentTimeMillis())) }
    }

    /** §Phase A — per-workspace lock. Only meaningful once app-lock credentials exist; the
     * screen is responsible for not offering this until [com.vervan.chat.security.AppLockManager.hasPin]
     * or biometric hardware is available, same as it gates showing the unlock prompt. */
    fun setLockEnabled(enabled: Boolean) {
        val current = workspace.value ?: return
        viewModelScope.launch { db.workspaceDao().update(current.copy(lockEnabled = enabled, updatedAt = System.currentTimeMillis())) }
    }

    /** Phase E — per-workspace defaults for new chats created inside it, see
     * [com.vervan.chat.model.WorkspaceManager.applyDefaults]. */
    fun setDefaultProfile(profileId: String?) {
        val current = workspace.value ?: return
        viewModelScope.launch { db.workspaceDao().update(current.copy(defaultProfile = profileId, updatedAt = System.currentTimeMillis())) }
    }

    fun setDefaultKnowledgeBaseIds(ids: Set<String>) {
        val current = workspace.value ?: return
        viewModelScope.launch { db.workspaceDao().update(current.copy(defaultKnowledgeBaseIds = ids.joinToString(","), updatedAt = System.currentTimeMillis())) }
    }

    fun setActive() {
        viewModelScope.launch {
            workspace.value?.let {
                workspaceManager.setActive(it)
                _confirmationMessage.value = "${it.name} is now active"
            }
        }
    }

    fun archive() {
        viewModelScope.launch { workspace.value?.let { workspaceManager.archive(it) } }
    }

    fun restore() {
        viewModelScope.launch { workspace.value?.let { workspaceManager.restore(it) } }
    }

    suspend fun delete() {
        workspace.value?.let { workspaceManager.delete(it) }
    }

    // Chat Screen spec §19 — batch AI title generation, launched from the workspace's chat
    // list. progress/pause/cancel work within this one run (an in-memory cursor and
    // a cancellable coroutine, checked between chats); a JobRecord row gives it visibility in
    // the Job Queue screen, but there's no true resume-after-process-death — cancelling and
    // starting over is the recovery path if the process is killed mid-batch.
    data class TitleBatchProgress(
        val total: Int,
        val completed: Int,
        val currentChatTitle: String?,
        val failed: Int,
        val skipped: Int,
        val done: Boolean
    )

    private var batchJob: Job? = null
    private var batchPaused = false
    private val _batchProgress = MutableStateFlow<TitleBatchProgress?>(null)
    val batchProgress: StateFlow<TitleBatchProgress?> = _batchProgress

    fun startTitleBatch(chatIds: List<String>, onlyUntitled: Boolean) {
        if (chatIds.isEmpty() || batchJob?.isActive == true) return
        batchPaused = false
        _batchProgress.value = TitleBatchProgress(chatIds.size, 0, null, 0, 0, false)
        batchJob = viewModelScope.launch {
            val jobRecord = JobRecord(type = JobType.BATCH_SUMMARIZE, label = "Generate chat titles", state = JobState.RUNNING)
            db.jobDao().upsert(jobRecord)
            var completed = 0
            var failed = 0
            var skipped = 0
            var cancelled = false
            try {
                for (id in chatIds) {
                    if (db.jobDao().get(jobRecord.id)?.state == JobState.CANCELLED) {
                        cancelled = true
                        break
                    }
                    while (batchPaused) delay(300)
                    val chatRow = db.chatDao().getChat(id)
                    if (chatRow == null || (onlyUntitled && chatRow.title != "New chat")) {
                        skipped++
                        _batchProgress.value = TitleBatchProgress(chatIds.size, completed, chatRow?.title, failed, skipped, false)
                        continue
                    }
                    _batchProgress.value = TitleBatchProgress(chatIds.size, completed, chatRow.title, failed, skipped, false)
                    val newTitle = runCatching { TitleGenerator.generate(app, id) }.getOrNull()
                    if (newTitle != null) {
                        db.chatDao().update(chatRow.copy(title = newTitle, previousTitle = chatRow.title, titleIsCustom = false, updatedAt = System.currentTimeMillis()))
                        completed++
                    } else {
                        failed++
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled = true
            }
            // A cancelled coroutine can't make further suspend calls on its own dispatcher —
            // NonCancellable lets this last bookkeeping write still land.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                db.jobDao().update(jobRecord.copy(state = if (cancelled) JobState.CANCELLED else JobState.COMPLETED, progress = 100, updatedAt = System.currentTimeMillis()))
            }
            if (!cancelled) _batchProgress.value = TitleBatchProgress(chatIds.size, completed, null, failed, skipped, true)
        }
    }

    fun pauseTitleBatch() { batchPaused = true }
    fun resumeTitleBatch() { batchPaused = false }
    fun cancelTitleBatch() { batchJob?.cancel(); _batchProgress.value = null }
    fun dismissBatchProgress() { _batchProgress.value = null }
}
