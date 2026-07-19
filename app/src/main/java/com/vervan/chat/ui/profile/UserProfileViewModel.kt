package com.vervan.chat.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UserProfileViewModel(app: VervanApp) : ViewModel() {
    private val repo = app.container.settingsRepository

    val name: StateFlow<String> = repo.userName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val occupation: StateFlow<String> = repo.userOccupation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val expertise: StateFlow<String> = repo.userExpertise.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val interests: StateFlow<String> = repo.userInterests.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val languages: StateFlow<Set<String>> = repo.userLanguages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val units: StateFlow<String> = repo.userUnits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "metric")
    val avoid: StateFlow<String> = repo.userTopicsAvoid.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val goals: StateFlow<String> = repo.userGoals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setName(v: String) { viewModelScope.launch { repo.setUserName(v) } }
    fun setOccupation(v: String) { viewModelScope.launch { repo.setUserOccupation(v) } }
    fun setExpertise(v: String) { viewModelScope.launch { repo.setUserExpertise(v) } }
    fun setInterests(v: String) { viewModelScope.launch { repo.setUserInterests(v) } }
    fun toggleLanguage(lang: String, current: Set<String>) { viewModelScope.launch { repo.setUserLanguages(if (lang in current) current - lang else current + lang) } }
    fun setUnits(v: String) { viewModelScope.launch { repo.setUserUnits(v) } }
    fun setAvoid(v: String) { viewModelScope.launch { repo.setUserTopicsAvoid(v) } }
    fun setGoals(v: String) { viewModelScope.launch { repo.setUserGoals(v) } }
}
