package com.vervan.chat.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.Project
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db

    // "recent" = full list ordered pinned-then-recent; callers slice further if they only
    // want a preview (see HomeScreen). Incognito mode excludes temporary chats,
    // same as the main chat list and search.
    val recentChats: StateFlow<List<Chat>> = db.chatDao().observeChats()
        .map { it.filterNot { c -> c.isTemporary } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestMessagesByChat: StateFlow<Map<String, Message>> = db.messageDao().observeLatestPerChat()
        .map { messages -> messages.associateBy { it.chatId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val projects: StateFlow<List<Project>> = db.projectDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeModel: StateFlow<ModelInfo?> = db.modelDao().observeActiveModel(ModelRole.GENERATION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val indexingDocuments: StateFlow<List<Document>> = db.documentDao().observeIndexing()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Home top bar — active workspace name, so switching workspaces is visible without
    // opening Workspaces first.
    val activeWorkspaceName: StateFlow<String?> = app.container.settingsRepository.activeWorkspaceId
        .flatMapLatest { id -> db.workspaceDao().observe(id) }
        .map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun createChat(): String {
        val chat = app.container.workspaceManager.applyDefaults(
            Chat(workspaceId = app.container.settingsRepository.activeWorkspaceId.first())
        )
        db.chatDao().upsert(chat)
        return chat.id
    }

    /** Home's "Ask anything" quick-ask: creates the chat and stashes [text] via
     * [com.vervan.chat.model.PendingChatSend] so the chat screen sends it the instant it opens,
     * instead of leaving it sitting as an unsent draft the user has to tap Send on again — that
     * second tap read as the first one having failed. See [PendingChatSend]'s doc for why this
     * isn't just `Chat.draft`. */
    suspend fun createChatAndSend(text: String): String {
        val chatId = createChat()
        com.vervan.chat.model.PendingChatSend.stash(chatId, text.trim())
        return chatId
    }
}
