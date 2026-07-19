package com.vervan.chat.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.FlashcardSet
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.StudyCard
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

data class StudySetSummary(
    val name: String,
    val description: String,
    val cardCount: Int,
    val masteredCount: Int,
    val accuracyPercent: Int?,
    val lastStudiedAt: Long?
)

class StudyWorkspaceViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val engine = app.container.llmEngine

    val sets: StateFlow<List<StudySetSummary>> = combine(
        db.flashcardSetDao().observeAll(), db.studyCardDao().observeAll()
    ) { metadata, cards ->
        val cardsBySet = cards.groupBy { it.setName }
        val metadataByName = metadata.associateBy { it.name }
        (metadata.map { it.name } + cardsBySet.keys).distinct().map { name ->
            val setCards = cardsBySet[name].orEmpty()
            val attempts = setCards.sumOf { it.timesReviewed }
            val correct = setCards.sumOf { it.timesCorrect }
            StudySetSummary(
                name = name,
                description = metadataByName[name]?.description.orEmpty(),
                cardCount = setCards.size,
                masteredCount = setCards.count { it.timesReviewed > 0 && it.timesCorrect * 5 >= it.timesReviewed * 4 },
                accuracyPercent = if (attempts == 0) null else correct * 100 / attempts,
                lastStudiedAt = metadataByName[name]?.lastStudiedAt
            )
        }.sortedWith(compareByDescending<StudySetSummary> { it.lastStudiedAt ?: 0L }.thenBy { it.name.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating

    private val _generationStage = MutableStateFlow("Preparing your deck")
    val generationStage: StateFlow<String> = _generationStage

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun clearError() { _error.value = null }

    fun generateSet(
        setName: String,
        sourceText: String,
        cardCount: Int,
        focus: String,
        cardStyle: String,
        onDone: () -> Unit
    ) {
        if (setName.isBlank() || sourceText.isBlank() || _generating.value) return
        viewModelScope.launch {
            _generating.value = true
            _error.value = null
            _generationStage.value = "Preparing the local model"
            val model = db.modelDao().getActiveModel(ModelRole.GENERATION)
            if (model == null) {
                _error.value = "No chat model selected. Import or activate one in Models."
                _generating.value = false
                return@launch
            }
            val focusInstruction = focus.trim().takeIf { it.isNotBlank() }?.let { "Learning goal: $it\n" }.orEmpty()
            val prompt = "Create exactly $cardCount high-quality $cardStyle flashcards from the source below. " +
                "Each question must test one useful idea, cover the source broadly, avoid duplicates and trivial wording, " +
                "and never add facts that are not in the source. Keep answers concise but sufficient.\n" +
                focusInstruction +
                "Respond with ONLY a JSON array where each item is {\"q\":\"question\",\"a\":\"answer\"}.\n\nSource:\n$sourceText"
            var raw = ""
            try {
                _generationStage.value = "Creating $cardCount focused cards"
                com.vervan.chat.llm.OneShotLlm.stream(app, prompt)?.collect { raw += it }
            } catch (t: Throwable) {
                _error.value = "Generation failed: ${t.toUserMessage()}"
                _generating.value = false
                return@launch
            }
            val cards = parseCards(raw, setName)
            if (cards.isEmpty()) {
                    _error.value = "Could not create the cards. Shorten the text, then try again."
            } else {
                _generationStage.value = "Saving your deck"
                db.withTransaction {
                    db.studyCardDao().deleteSet(setName)
                    db.studyCardDao().insertAll(cards)
                    db.flashcardSetDao().upsert(
                        FlashcardSet(
                            name = setName,
                            description = focus.trim().ifBlank { "$cardStyle review · ${cards.size} cards" }
                        )
                    )
                }
                onDone()
            }
            _generating.value = false
        }
    }

    private fun parseCards(raw: String, setName: String): List<StudyCard> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return emptyList()
        return try {
            val array = JSONArray(raw.substring(start, end + 1))
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val question = item.optString("q").trim().ifBlank { return@mapNotNull null }
                val answer = item.optString("a").trim().ifBlank { return@mapNotNull null }
                StudyCard(setName = setName, question = question, answer = answer)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun deleteSet(setName: String) {
        viewModelScope.launch {
            db.withTransaction {
                db.studyCardDao().deleteSet(setName)
                db.flashcardSetDao().deleteByName(setName)
            }
        }
    }
}
