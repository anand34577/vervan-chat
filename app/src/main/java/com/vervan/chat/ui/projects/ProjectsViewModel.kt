package com.vervan.chat.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsListViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db

    // Scoped to the active workspace — projects now live inside a workspace like chats and folders.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val projects: StateFlow<List<Project>> = app.container.settingsRepository.activeWorkspaceId
        .flatMapLatest { db.projectDao().observeForWorkspace(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createProject(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val workspaceId = app.container.settingsRepository.activeWorkspaceId.first()
            db.projectDao().upsert(Project(name = name, workspaceId = workspaceId))
        }
    }

    fun rename(project: Project, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { db.projectDao().upsert(project.copy(name = newName.trim())) }
    }

    fun delete(project: Project) {
        // Soft delete (Phase 6, spec §34) — recoverable from the recycle bin instead of gone instantly.
        viewModelScope.launch {
            db.withTransaction {
                db.chatDao().clearProject(project.id)
                db.noteDao().clearProject(project.id)
                db.knowledgeBaseDao().clearDefaultProject(project.id)
                db.projectDao().upsert(project.copy(deletedAt = System.currentTimeMillis()))
            }
        }
    }
}

class ProjectDashboardViewModel(private val app: VervanApp, private val projectId: String) : ViewModel() {
    private val db = app.container.db

    val project: StateFlow<Project?> = db.projectDao().observe(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chats: StateFlow<List<Chat>> = db.chatDao().observeForProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<Note>> = db.noteDao().observeForProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveInstructions(instructions: String) {
        viewModelScope.launch { project.value?.let { db.projectDao().upsert(it.copy(instructions = instructions)) } }
    }

    fun rename(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { project.value?.let { db.projectDao().upsert(it.copy(name = newName.trim())) } }
    }

    fun delete() {
        // Soft delete (Phase 6, spec §34) — recoverable from the recycle bin instead of gone instantly.
        viewModelScope.launch {
            project.value?.let { p ->
                db.withTransaction {
                    db.chatDao().clearProject(p.id)
                    db.noteDao().clearProject(p.id)
                    db.knowledgeBaseDao().clearDefaultProject(p.id)
                    db.projectDao().upsert(p.copy(deletedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    suspend fun createChat(): String {
        // A chat created in a project belongs to that project's workspace (falling back to the
        // active one only if the project row hasn't loaded yet) — keeps the project, its chats,
        // and its workspace consistent regardless of which workspace is currently active.
        val workspaceId = project.value?.workspaceId ?: app.container.settingsRepository.activeWorkspaceId.first()
        val chat = app.container.workspaceManager.applyDefaults(
            Chat(projectId = projectId, workspaceId = workspaceId)
        )
        db.chatDao().upsert(chat)
        return chat.id
    }

    suspend fun createNote(): String {
        val note = Note(projectId = projectId)
        db.noteDao().upsert(note)
        return note.id
    }
}
