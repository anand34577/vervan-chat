package com.vervan.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/** Read-only state for the Settings landing page. */
class SettingsOverviewViewModel(app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val settings = app.container.settingsRepository

    val models = db.modelDao().observeModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val memories = db.memoryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingSuggestions = db.memorySuggestionDao().observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val activeModel = db.modelDao().observeActiveModel(com.vervan.chat.data.db.entities.ModelRole.GENERATION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val userName = settings.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val userOccupation = settings.userOccupation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
}
