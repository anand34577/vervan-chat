package com.vervan.chat.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.StudyCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StudyReviewViewModel(app: VervanApp, private val setName: String) : ViewModel() {
    private val db = app.container.db

    val allCards: StateFlow<List<StudyCard>> = db.studyCardDao().observeSet(setName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Phase 4, spec §24 — "review missed cards only" mode: a card counts as missed if it's
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

    /** Records the study session timestamp on the set metadata (spec §55). */
    fun recordSession() {
        viewModelScope.launch {
            db.flashcardSetDao().findByName(setName)?.let {
                db.flashcardSetDao().update(it.copy(lastStudiedAt = System.currentTimeMillis()))
            }
        }
    }
}
