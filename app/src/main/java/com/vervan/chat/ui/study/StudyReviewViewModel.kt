package com.vervan.chat.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.StudyCard
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class StudyReviewViewModel(app: VervanApp, private val setName: String) : ViewModel() {
    private val db = app.container.db
    private val reload = MutableStateFlow(0)
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val allCards: StateFlow<List<StudyCard>> = reload
        .flatMapLatest { db.studyCardDao().observeSet(setName) }
        .onStart { _isLoading.value = true }
        .onEach {
            _isLoading.value = false
            _error.value = null
        }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            _isLoading.value = false
            _error.value = throwable.toUserMessage()
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retry() {
        _error.value = null
        reload.value += 1
    }

    /** "review missed cards only" mode: a card counts as missed if it's
     * ever been answered wrong more times than right, not just "wrong last time" (no
     * per-attempt history is stored, only running correct/reviewed counts). */
    private val _missedOnly = MutableStateFlow(false)
    val missedOnly: StateFlow<Boolean> = _missedOnly
    fun setMissedOnly(value: Boolean) { _missedOnly.value = value }

    val cards: StateFlow<List<StudyCard>> = combine(allCards, _missedOnly) { all, missedOnly ->
        if (missedOnly) all.filter { it.timesReviewed > 0 && it.timesCorrect < it.timesReviewed } else all
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markResult(card: StudyCard, correct: Boolean) {
        viewModelScope.launch {
            db.studyCardDao().update(
                card.copy(timesReviewed = card.timesReviewed + 1, timesCorrect = card.timesCorrect + if (correct) 1 else 0)
            )
        }
    }

    /** Records the study session timestamp on the set metadata. */
    fun recordSession() {
        viewModelScope.launch {
            db.flashcardSetDao().findByName(setName)?.let {
                db.flashcardSetDao().update(it.copy(lastStudiedAt = System.currentTimeMillis()))
            }
        }
    }
}
