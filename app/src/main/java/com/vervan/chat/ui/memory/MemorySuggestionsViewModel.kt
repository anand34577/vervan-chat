package com.vervan.chat.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Memory
import com.vervan.chat.data.db.entities.MemoryScope
import com.vervan.chat.data.db.entities.MemorySuggestion
import com.vervan.chat.data.db.entities.MemorySuggestionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemorySuggestionsViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db

    val pending: StateFlow<List<MemorySuggestion>> = db.memorySuggestionDao().observePending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val existingMemories: StateFlow<List<Memory>> = db.memoryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Detects whether accepting this suggestion would conflict with an existing memory that
     * shares its canonical key (spec §27.5). Returns the conflicting memory if any. */
    fun conflictFor(suggestion: MemorySuggestion): Memory? {
        if (suggestion.key.isNullOrBlank()) return null
        return existingMemories.value.firstOrNull {
            it.key == suggestion.key && it.scope == suggestion.scope && it.scopeRefId == suggestion.scopeRefId
        }
    }

    fun accept(suggestion: MemorySuggestion, overwriteConflict: Boolean) {
        viewModelScope.launch {
            // If a same-key memory exists in the same scope and the user chose to overwrite,
            // replace it; otherwise add (canonical-key dedup — spec §27.4/§27.5).
            val conflict = conflictFor(suggestion)
            if (conflict != null && overwriteConflict) {
                db.memoryDao().delete(conflict)
            }
            db.memoryDao().upsert(
                Memory(
                    text = suggestion.text,
                    key = suggestion.key,
                    scope = suggestion.scope,
                    scopeRefId = suggestion.scopeRefId
                )
            )
            db.memorySuggestionDao().update(suggestion.copy(status = MemorySuggestionStatus.ACCEPTED))
        }
    }

    fun reject(suggestion: MemorySuggestion) {
        viewModelScope.launch { db.memorySuggestionDao().update(suggestion.copy(status = MemorySuggestionStatus.REJECTED)) }
    }

    /** Accepts [suggestion] with user-edited text/key instead of the detector's raw capture. */
    fun editAndAccept(suggestion: MemorySuggestion, editedText: String, editedKey: String?) {
        accept(suggestion.copy(text = editedText, key = editedKey), overwriteConflict = false)
    }

    /** §27.3 "never suggest this type" — blocks future suggestions carrying this key and
     * rejects every other pending suggestion that already shares it. */
    fun blockType(suggestion: MemorySuggestion) {
        viewModelScope.launch {
            reject(suggestion)
            val key = suggestion.key ?: return@launch
            app.container.settingsRepository.blockMemorySuggestionKey(key)
            pending.value.filter { it.key == key && it.id != suggestion.id }.forEach { reject(it) }
        }
    }
}
