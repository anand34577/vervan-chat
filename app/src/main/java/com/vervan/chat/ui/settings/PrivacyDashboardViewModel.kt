package com.vervan.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * Single state owner for the read-only privacy dashboard.
 *
 * The dashboard is a trust surface, so its counts and active-model identity must come from the
 * same long-lived flows as the rest of the app without making the composable reach into Room.
 * Keeping these observations here also gives the screen a stable place to add a refresh/error
 * state later without changing its navigation contract.
 */
class PrivacyDashboardViewModel(app: VervanApp) : ViewModel() {
    private val db = app.container.db

    val models = db.modelDao().observeModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeModel = db.modelDao().observeActiveModel(ModelRole.GENERATION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val documents = db.documentDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val chats = db.chatDao().observeAllChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val memories = db.memoryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val knowledgeBases = db.knowledgeBaseDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val totalChunks = db.chunkDao().observeTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val embeddedChunks = db.chunkDao().observeEmbeddedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val networkEntries = app.container.networkAuditLog.entries
}
