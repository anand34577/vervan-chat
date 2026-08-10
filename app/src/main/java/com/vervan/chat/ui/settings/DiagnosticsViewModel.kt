package com.vervan.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.system.NetworkAuditEntry
import com.vervan.chat.system.ThermalLevel
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class DiagnosticsState(
    val models: List<ModelInfo> = emptyList(),
    val activeModel: ModelInfo? = null,
    val documents: List<Document> = emptyList(),
    val chats: List<Chat> = emptyList(),
    val notes: List<Note> = emptyList(),
    val thermal: ThermalLevel = ThermalLevel.NORMAL,
    val networkEntries: List<NetworkAuditEntry> = emptyList()
)

/** Provides one consistent snapshot for diagnostics so the report cannot mix values from
 * different database emissions or silently render empty counts while Room is still starting. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val reload = MutableStateFlow(0)
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val state: StateFlow<DiagnosticsState> = reload
        .flatMapLatest {
            val database = combine(
                db.modelDao().observeModels(),
                db.modelDao().observeActiveModel(ModelRole.GENERATION),
                db.documentDao().observeAll()
            ) { models, activeModel, documents -> Triple(models, activeModel, documents) }
            val content = combine(
                db.chatDao().observeAllChats(),
                db.noteDao().observeAll(),
                app.container.thermalMonitor.level
            ) { chats, notes, thermal -> Triple(chats, notes, thermal) }
            combine(database, content, app.container.networkAuditLog.entries) { databaseState, contentState, networkEntries ->
                DiagnosticsState(
                    models = databaseState.first,
                    activeModel = databaseState.second,
                    documents = databaseState.third,
                    chats = contentState.first,
                    notes = contentState.second,
                    thermal = contentState.third,
                    networkEntries = networkEntries
                )
            }
        }
        .onStart { _isLoading.value = true }
        .onEach {
            _isLoading.value = false
            _error.value = null
        }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            _isLoading.value = false
            _error.value = throwable.toUserMessage()
            emit(DiagnosticsState())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiagnosticsState())

    fun retry() {
        _error.value = null
        reload.value += 1
    }
}
