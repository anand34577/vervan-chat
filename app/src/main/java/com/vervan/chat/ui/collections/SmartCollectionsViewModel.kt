package com.vervan.chat.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.MessageState
import com.vervan.chat.data.db.entities.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Dynamic collections — read-only filters, never move underlying content. */
enum class SmartCollection(val label: String, val description: String) {
    THIS_WEEK("This week", "Chats and notes from the last 7 days"),
    INTERRUPTED("Interrupted", "Responses cut off before finishing"),
    WITH_ATTACHMENTS("With attachments", "Chats containing images or audio"),
    PINNED("Pinned", "Everything you've pinned"),
    ARCHIVED("Archived", "Chats you've archived"),
    FAILED_DOCS("Failed imports", "Documents that didn't index")
}

data class CollectionContents(
    val chats: List<Chat> = emptyList(),
    val notes: List<Note> = emptyList(),
    val documents: List<Document> = emptyList()
) {
    val total: Int get() = chats.size + notes.size + documents.size
}

class SmartCollectionsViewModel(app: VervanApp) : ViewModel() {
    private val db = app.container.db

    // Incognito mode — a temporary chat never appears in a smart collection.
    private val allChats = db.chatDao().observeAllChats()
        .map { chats -> chats.filterNot { it.isTemporary } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val allNotes = db.noteDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val allDocs = db.documentDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val allMessages = db.messageDao().let { dao ->
        // Observe messages for interrupted/attachment detection — we can't observe *all*
        // messages across chats cheaply, so we read on demand in contents().
        null
    }

    fun contents(collection: SmartCollection): StateFlow<CollectionContents> {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return combine(allChats, allNotes, allDocs) { chats, notes, docs ->
            when (collection) {
                SmartCollection.THIS_WEEK -> CollectionContents(
                    chats = chats.filter { it.updatedAt >= weekAgo },
                    notes = notes.filter { it.updatedAt >= weekAgo }
                )
                SmartCollection.INTERRUPTED -> {
                    // Was chats.forEach { getMessages(c.id) } — an O(chats × messages) N+1 that
                    // loaded every message into memory on each recompute. Now one indexed query
                    // returning only the chatIds that actually contain an interrupted/failed reply.
                    val ids = db.messageDao().getChatIdsWithState(
                        listOf(MessageState.INTERRUPTED.name, MessageState.FAILED.name)
                    ).toHashSet()
                    CollectionContents(chats = chats.filter { it.id in ids })
                }
                SmartCollection.WITH_ATTACHMENTS -> {
                    val ids = db.messageDao().getChatIdsWithAttachments().toHashSet()
                    CollectionContents(chats = chats.filter { it.id in ids })
                }
                SmartCollection.PINNED -> CollectionContents(
                    chats = chats.filter { it.pinned },
                    notes = notes.filter { it.pinned }
                )
                SmartCollection.ARCHIVED -> CollectionContents(chats = chats.filter { it.archived })
                SmartCollection.FAILED_DOCS -> CollectionContents(documents = docs.filter { it.status == com.vervan.chat.data.db.entities.DocumentStatus.FAILED || it.status == com.vervan.chat.data.db.entities.DocumentStatus.UNSUPPORTED })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CollectionContents())
    }
}
