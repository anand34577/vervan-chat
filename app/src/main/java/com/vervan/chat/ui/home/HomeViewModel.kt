package com.vervan.chat.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.Project
import com.vervan.chat.data.db.entities.ToolRun
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db

    // Home only renders a small continuation preview, so keep Room's observed result bounded
    // instead of waking the screen with every row from growing chat/note/project tables.
    val recentChats: StateFlow<List<Chat>> = db.chatDao().observeRecent(HOME_CHAT_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestMessagesByChat: StateFlow<Map<String, Message>> = recentChats
        .flatMapLatest { chats ->
            if (chats.isEmpty()) flowOf(emptyList())
            else db.messageDao().observeLatestForChats(chats.map { it.id })
        }
        .map { messages: List<Message> -> messages.associateBy { it.chatId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val projects: StateFlow<List<Project>> = db.projectDao().observeRecent(HOME_SINGLE_ITEM_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentNotes: StateFlow<List<Note>> = db.noteDao().observeRecent(HOME_SINGLE_ITEM_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentToolRuns: StateFlow<List<ToolRun>> = db.toolRunDao().observeRecent(HOME_SINGLE_ITEM_LIMIT)
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

    private companion object {
        const val HOME_CHAT_LIMIT = 3
        const val HOME_SINGLE_ITEM_LIMIT = 1
    }
}
