package com.vervan.chat.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.StudyCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

/** Generates flashcard sets from pasted text (spec §24). ponytail: one-shot generation —
 * ask the model for a JSON array of {q,a} pairs, parse it, done. No streaming partial-card
 * UI, no per-card regeneration; regenerate the whole set if it comes out wrong. */
class StudyWorkspaceViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val engine = app.container.llmEngine

    val setNames: StateFlow<List<String>> = db.studyCardDao().observeSetNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun generateSet(setName: String, sourceText: String, cardCount: Int, onDone: () -> Unit) {
        if (setName.isBlank() || sourceText.isBlank() || _generating.value) return
        viewModelScope.launch {
            _generating.value = true
            _error.value = null
            val model = db.modelDao().getActiveModel(ModelRole.GENERATION)
            if (model == null) {
                _error.value = "No chat model selected. Import or activate one in Models."
                _generating.value = false
                return@launch
            }
            val prompt = "Generate exactly $cardCount flashcards from the following text. " +
                "Respond with ONLY a JSON array, no other text, where each item is " +
                "{\"q\": \"question\", \"a\": \"answer\"}.\n\nText:\n$sourceText"
            var raw = ""
            try {
                app.container.withLlm {
                    if (engine.loadedModelPath != model.filePath) engine.load(model.filePath)
                    engine.generate(prompt).collect { raw += it }
                }
            } catch (e: Exception) {
                _error.value = "Generation failed: ${e.message}"
                _generating.value = false
                return@launch
            }
            val cards = parseCards(raw, setName)
            if (cards.isEmpty()) {
                _error.value = "Could not read flashcards from the model's response — try shorter text or try again."
            } else {
                db.withTransaction {
                    db.studyCardDao().deleteSet(setName)
                    db.studyCardDao().insertAll(cards)
                // Record the set metadata (spec §55) — description and last-studied stamp.
                db.flashcardSetDao().upsert(
                    com.vervan.chat.data.db.entities.FlashcardSet(
                        name = setName,
                        description = "${cards.size} cards generated from source text"
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
            val arr = JSONArray(raw.substring(start, end + 1))
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val q = obj.optString("q").ifBlank { return@mapNotNull null }
                val a = obj.optString("a").ifBlank { return@mapNotNull null }
                StudyCard(setName = setName, question = q, answer = a)
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
