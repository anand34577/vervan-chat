package com.vervan.chat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.Workspace
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

data class ChatInfoState(
    val chat: Chat? = null,
    val messages: List<Message> = emptyList(),
    val documents: List<Document> = emptyList(),
    val personas: List<Persona> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val workspaces: List<Workspace> = emptyList(),
    val knowledgeBases: List<KnowledgeBase> = emptyList(),
    val activeModel: ModelInfo? = null
)

/** Keeps the chat-info screen from opening seven independent Room subscriptions in a
 * composable. A single state also gives the screen an honest loading/error boundary. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatInfoViewModel(private val app: VervanApp, private val chatId: String) : ViewModel() {
    private val db = app.container.db
    private val reload = MutableStateFlow(0)
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val state: StateFlow<ChatInfoState> = reload
        .flatMapLatest {
            val conversation = combine(
                db.chatDao().observeChat(chatId),
                db.messageDao().observeMessages(chatId),
                db.documentDao().observeAll()
            ) { chat, messages, documents -> Triple(chat, messages, documents) }
            val context = combine(
                db.personaDao().observePersonas(),
                db.modelDao().observeModels(),
                db.workspaceDao().observeAll()
            ) { personas, models, workspaces -> Triple(personas, models, workspaces) }
            val supporting = combine(
                db.knowledgeBaseDao().observeAll(),
                db.modelDao().observeActiveModel(ModelRole.GENERATION)
            ) { knowledgeBases, activeModel -> Pair(knowledgeBases, activeModel) }
            combine(conversation, context, supporting) { conversationState, contextState, supportingState ->
                ChatInfoState(
                    chat = conversationState.first,
                    messages = conversationState.second,
                    documents = conversationState.third,
                    personas = contextState.first,
                    models = contextState.second,
                    workspaces = contextState.third,
                    knowledgeBases = supportingState.first,
                    activeModel = supportingState.second
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
            emit(ChatInfoState())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatInfoState())

    fun retry() {
        _error.value = null
        reload.value += 1
    }
}
