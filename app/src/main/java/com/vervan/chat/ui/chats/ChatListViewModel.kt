package com.vervan.chat.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Folder
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChatFilter { ALL, PINNED, ARCHIVED }

class ChatListViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db

    private val allChats: StateFlow<List<Chat>> = db.chatDao().observeListableChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectNames: StateFlow<Map<String, String>> = db.projectDao().observeAll()
        .map { projects -> projects.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val folders: StateFlow<List<Folder>> = db.folderDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folderNames: StateFlow<Map<String, String>> = folders
        .map { folders -> folders.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Last message preview per chat — a single Flow from one DAO query rather than N per-row
    // queries. Used by the chat list row to show "what was the last thing said", the way every
    // other chat app does, instead of the previous pattern of showing the user's unsent draft.
    val lastMessageByChat: StateFlow<Map<String, Message>> = db.messageDao().observeLatestPerChat()
        .map { messages -> messages.associateBy { it.chatId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Model display name lookup — so the chat row can show a small badge identifying which
    // model this conversation used. Distinct from active-model state: a chat retains its model
    // identity even when the user later switches the global active model.
    val modelNames: StateFlow<Map<String, String>> = db.modelDao().observeModels()
        .map { models -> models.associate { it.id to it.displayName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _filter = MutableStateFlow(ChatFilter.ALL)
    val filter: StateFlow<ChatFilter> = _filter

    val chats: StateFlow<List<Chat>> = combine(allChats, _filter) { chats, filter ->
        // Incognito mode (Phase B) — a temporary chat never appears in the chat list; it's
        // only reachable by whatever navigated to it directly, and purges itself on close.
        val listable = chats.filterNot { it.isTemporary }
        when (filter) {
            ChatFilter.ALL -> listable.filter { !it.archived }
            ChatFilter.PINNED -> listable.filter { it.pinned && !it.archived }
            ChatFilter.ARCHIVED -> listable.filter { it.archived }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: ChatFilter) { _filter.value = filter }

    fun togglePin(chat: Chat) {
        viewModelScope.launch { db.chatDao().update(chat.copy(pinned = !chat.pinned)) }
    }

    fun toggleArchive(chat: Chat) {
        viewModelScope.launch { db.chatDao().update(chat.copy(archived = !chat.archived)) }
    }

    fun moveToTrash(chat: Chat) {
        viewModelScope.launch { db.chatDao().update(chat.copy(deletedAt = System.currentTimeMillis())) }
    }

    fun archive(ids: Set<String>) = updateSelected(ids) { it.copy(archived = true) }
    fun unarchive(ids: Set<String>) = updateSelected(ids) { it.copy(archived = false) }
    fun moveToTrash(ids: Set<String>) = updateSelected(ids) { it.copy(deletedAt = System.currentTimeMillis()) }
    fun moveToFolder(ids: Set<String>, folderId: String?) = updateSelected(ids) { it.copy(folderId = folderId) }

    private fun updateSelected(ids: Set<String>, transform: (Chat) -> Chat) {
        viewModelScope.launch { allChats.value.filter { it.id in ids }.forEach { db.chatDao().update(transform(it)) } }
    }

    fun rename(chat: Chat, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { db.chatDao().update(chat.copy(title = title.trim(), updatedAt = System.currentTimeMillis())) }
    }

    fun duplicate(chat: Chat) {
        viewModelScope.launch {
            db.withTransaction {
                val messages = db.messageDao().getMessages(chat.id)
                val ids = messages.associate { it.id to java.util.UUID.randomUUID().toString() }
                val copy = chat.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "${chat.title} copy",
                    activeLeafId = chat.activeLeafId?.let(ids::get),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    deletedAt = null
                )
                db.chatDao().upsert(copy)
                messages.forEach { message ->
                    db.messageDao().upsert(
                        message.copy(id = ids.getValue(message.id), chatId = copy.id, parentId = message.parentId?.let(ids::get))
                    )
                }
            }
        }
    }

    suspend fun exportText(chat: Chat): String = buildString {
        appendLine("# ${chat.title}")
        db.messageDao().getMessages(chat.id).forEach { message ->
            appendLine()
            appendLine("${message.role.name.lowercase().replaceFirstChar(Char::uppercase)}:")
            appendLine(message.content)
        }
    }

    suspend fun createChat(): String {
        val chat = app.container.workspaceManager.applyDefaults(
            Chat(workspaceId = app.container.settingsRepository.activeWorkspaceId.first())
        )
        db.chatDao().upsert(chat)
        return chat.id
    }
}
