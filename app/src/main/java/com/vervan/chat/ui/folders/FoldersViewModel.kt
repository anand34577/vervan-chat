package com.vervan.chat.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Folder
import com.vervan.chat.data.db.entities.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoldersViewModel(app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val settingsRepository = app.container.settingsRepository

    val folders: StateFlow<List<Folder>> = db.folderDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personas = db.personaDao().observePersonas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val models = db.modelDao().observeModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val workspaceId = settingsRepository.activeWorkspaceId.first()
            db.folderDao().upsert(Folder(name = name.trim(), workspaceId = workspaceId))
        }
    }

    fun update(folder: Folder) {
        viewModelScope.launch { db.folderDao().update(folder) }
    }

    fun delete(folder: Folder) {
        // Soft-delete the folder; unfile its chats/notes rather than deleting them.
        viewModelScope.launch {
            db.withTransaction {
                db.chatDao().clearFolder(folder.id)
                db.noteDao().clearFolder(folder.id)
                db.folderDao().update(folder.copy(deletedAt = System.currentTimeMillis()))
            }
        }
    }

    /** Bulk soft-delete for selection-mode delete — same reversible move-to-recycle-bin as
     * [delete], no confirmation needed (matches ChatListScreen's pattern). */
    fun deleteAll(ids: Set<String>) {
        viewModelScope.launch {
            db.withTransaction {
                val now = System.currentTimeMillis()
                folders.value.filter { it.id in ids }.forEach { folder ->
                    db.chatDao().clearFolder(folder.id)
                    db.noteDao().clearFolder(folder.id)
                    db.folderDao().update(folder.copy(deletedAt = now))
                }
            }
        }
    }
}

class FolderDetailViewModel(private val app: VervanApp, private val folderId: String) : ViewModel() {
    private val db = app.container.db

    val folder: StateFlow<Folder?> = db.folderDao().observeAll()
        .map { list -> list.find { it.id == folderId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chats: StateFlow<List<Chat>> = db.chatDao().observeForFolder(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<Note>> = db.noteDao().observeForFolder(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personas = db.personaDao().observePersonas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val models = db.modelDao().observeModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDefaultPersona(personaId: String?) {
        viewModelScope.launch { folder.value?.let { db.folderDao().update(it.copy(defaultPersonaId = personaId)) } }
    }

    fun setDefaultModel(modelId: String?) {
        viewModelScope.launch { folder.value?.let { db.folderDao().update(it.copy(defaultModelId = modelId)) } }
    }

    fun setDefaultKbs(kbIds: List<String>) {
        viewModelScope.launch { folder.value?.let { db.folderDao().update(it.copy(defaultKnowledgeBaseIds = kbIds.joinToString(","))) } }
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { folder.value?.let { db.folderDao().update(it.copy(name = name.trim())) } }
    }

    fun delete(folder: Folder) {
        viewModelScope.launch {
            db.withTransaction {
                db.chatDao().clearFolder(folder.id)
                db.noteDao().clearFolder(folder.id)
                db.folderDao().update(folder.copy(deletedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun createChat(): String {
        val f = folder.value
        // Persona and model are left unset so they inherit live (chat → folder → workspace →
        // default) through ChatDefaults — changing a folder's default now propagates to the chats
        // already in it, instead of being frozen at whatever it was when each chat was created.
        // KBs stay stamped: grounding is per-chat editable state, not a live-inherited default.
        val chat = app.container.workspaceManager.applyDefaults(
            Chat(
                folderId = folderId,
                // : a folder's chats must share its workspace.
                workspaceId = f?.workspaceId ?: com.vervan.chat.data.db.entities.Workspace.DEFAULT_WORKSPACE_ID,
                knowledgeBaseIds = f?.defaultKnowledgeBaseIds ?: "",
                sourceGrounded = !f?.kbIdList().isNullOrEmpty()
            )
        )
        db.chatDao().upsert(chat)
        return chat.id
    }

    suspend fun createNote(): String {
        val note = Note(folderId = folderId)
        db.noteDao().upsert(note)
        return note.id
    }
}
