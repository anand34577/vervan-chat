package com.vervan.chat.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.SavedOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import com.vervan.chat.system.toUserMessage

/** Owns Library's Room observations and recoverable delete operations. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LibraryViewModel(app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val reload = MutableStateFlow(0)
    private val loadingSources = MutableStateFlow(setOf("personas", "templates", "workflows", "saved"))
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    val isLoading: StateFlow<Boolean> = loadingSources
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val personas = observeList("personas") { db.personaDao().observePersonas() }
    val templates = observeList("templates") { db.promptTemplateDao().observeAll() }
    val workflows = observeList("workflows") { db.workflowDao().observeAll() }
    val savedOutputs = observeList("saved") { db.savedOutputDao().observeAll() }

    private fun <T> observeList(key: String, source: () -> Flow<List<T>>): StateFlow<List<T>> = reload
        .flatMapLatest {
            loadingSources.update { it + key }
            source()
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    loadingSources.update { it - key }
                    _error.value = throwable.toUserMessage()
                    emit(emptyList())
                }
                .onEach { loadingSources.update { it - key } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry() {
        _error.value = null
        loadingSources.value = setOf("personas", "templates", "workflows", "saved")
        reload.value += 1
    }

    /** Soft-deletes selected rows and, for personas, clears every known foreign-key reference in
     * the same Room transaction so a reusable item never leaves stale defaults behind. */
    fun deleteSelected(tab: Int, ids: Set<String>): Deferred<Result<Unit>> = viewModelScope.async {
        if (ids.isEmpty()) return@async Result.success(Unit)
        try {
            val now = System.currentTimeMillis()
            val currentPersonas = personas.first()
            val currentTemplates = templates.first()
            val currentWorkflows = workflows.first()
            val currentOutputs = savedOutputs.first()

            db.withTransaction {
                when (tab) {
                    0 -> currentPersonas.filter { it.id in ids && !it.isBuiltIn }.forEach { persona ->
                        db.chatDao().clearPersona(persona.id)
                        db.folderDao().clearDefaultPersona(persona.id)
                        db.projectDao().clearPersona(persona.id)
                        db.knowledgeBaseDao().clearDefaultPersona(persona.id)
                        db.personaDao().upsert(persona.copy(deletedAt = now))
                    }
                    1 -> currentTemplates.filter { it.id in ids && !it.isBuiltIn }
                        .forEach { db.promptTemplateDao().upsert(it.copy(deletedAt = now)) }
                    2 -> currentWorkflows.filter { it.id in ids && !it.isBuiltIn }
                        .forEach { db.workflowDao().upsert(it.copy(deletedAt = now)) }
                    else -> currentOutputs.filter { it.id in ids }
                        .forEach { db.savedOutputDao().upsert(it.copy(deletedAt = now)) }
                }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            com.vervan.chat.system.rethrowCancellation(t)
            Result.failure(t)
        }
    }

    fun deleteSaved(output: SavedOutput): Deferred<Result<Unit>> = viewModelScope.async {
        try {
            db.savedOutputDao().upsert(output.copy(deletedAt = System.currentTimeMillis()))
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            com.vervan.chat.system.rethrowCancellation(t)
            Result.failure(t)
        }
    }
}
