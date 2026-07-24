package com.vervan.chat.ui.workspaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkspacesViewModel(app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val workspaceManager = app.container.workspaceManager
    private val settingsRepository = app.container.settingsRepository

    // Workspace switch confirmation — a one-shot message the screen shows as a
    // Snackbar then clears; not a StateFlow of persistent UI state.
    private val _confirmationMessage = MutableStateFlow<String?>(null)
    val confirmationMessage: StateFlow<String?> = _confirmationMessage
    fun clearConfirmation() { _confirmationMessage.value = null }

    val workspaces: StateFlow<List<Workspace>> = db.workspaceDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedWorkspaces: StateFlow<List<Workspace>> = db.workspaceDao().observeArchived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeWorkspaceId: StateFlow<String> = settingsRepository.activeWorkspaceId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Workspace.DEFAULT_WORKSPACE_ID)

    val personas: StateFlow<List<Persona>> = db.personaDao().observePersonas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun chatCount(workspaceId: String): Flow<Int> = db.chatDao().observeActiveCountForWorkspace(workspaceId)

    fun create(name: String, description: String, personaId: String) {
        if (name.isBlank()) return
        viewModelScope.launch { workspaceManager.create(name, description, personaId) }
    }

    fun setActive(workspace: Workspace) {
        viewModelScope.launch {
            workspaceManager.setActive(workspace)
            _confirmationMessage.value = "${workspace.name} is now active"
        }
    }

    fun archive(workspace: Workspace) {
        viewModelScope.launch { workspaceManager.archive(workspace) }
    }

    fun restore(workspace: Workspace) {
        viewModelScope.launch { workspaceManager.restore(workspace) }
    }

    fun delete(workspace: Workspace) {
        viewModelScope.launch { workspaceManager.delete(workspace) }
    }

    /** Bulk hard-delete for selection-mode delete — workspaces have no recycle bin entry, so
     * this always goes through the same permanent-delete path as the single-item action, and
     * the screen gates it behind a [com.vervan.chat.ui.common.ConfirmDialog] first. */
    fun deleteAll(workspaces: List<Workspace>) {
        viewModelScope.launch { workspaces.forEach { workspaceManager.delete(it) } }
    }
}
