package com.vervan.chat.ui.writing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.SavedOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class WritingAction(val label: String, val instruction: String) {
    REWRITE("Rewrite", "Rewrite the following text, improving clarity and flow while preserving its meaning:"),
    SHORTEN("Shorten", "Shorten the following text as much as possible without losing its key meaning:"),
    EXPAND("Expand", "Expand the following text with more detail and supporting context, keeping the same meaning:"),
    FORMAL("More formal", "Rewrite the following text in a more formal, professional tone:"),
    CASUAL("More casual", "Rewrite the following text in a more casual, conversational tone:"),
    FIX_GRAMMAR("Fix grammar", "Correct grammar and spelling in the following text without changing its meaning or tone:"),
    SIMPLIFY("Simplify", "Rewrite the following text in simpler, plainer language for a general audience:"),
    ALTERNATIVES("Alternatives", "Give 3 alternative phrasings of the following text, each on its own numbered line:"),
    TRANSLATE("Translate", "Translate the following text into {LANGUAGE}, keeping the meaning and tone:")
}

/** Standalone rewrite/shorten/expand/tone tool (spec §22) — not tied to a chat or a note,
 * just original text in, revision out. Uses the same on-device generation as everything
 * else, one-shot per action (no multi-turn refinement loop). */
class WritingWorkspaceViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val engine = app.container.llmEngine

    private val _revision = MutableStateFlow("")
    val revision: StateFlow<String> = _revision

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun run(action: WritingAction, originalText: String, targetLanguage: String = "") {
        if (originalText.isBlank() || _running.value) return
        if (action == WritingAction.TRANSLATE && targetLanguage.isBlank()) {
            _error.value = "Enter a target language first"
            return
        }
        viewModelScope.launch {
            _running.value = true
            _error.value = null
            _revision.value = ""
            val model = db.modelDao().getActiveModel(ModelRole.GENERATION)
            if (model == null) {
                _error.value = "No chat model selected. Import or activate one in Models."
                _running.value = false
                return@launch
            }
            val instruction = if (action == WritingAction.TRANSLATE) action.instruction.replace("{LANGUAGE}", targetLanguage) else action.instruction
            try {
                app.container.withLlm {
                    if (engine.loadedModelPath != model.filePath) engine.load(model.filePath)
                    engine.generate("$instruction\n\n$originalText").collect { chunk -> _revision.value += chunk }
                }
            } catch (e: Exception) {
                _error.value = "Generation failed: ${e.message}"
            }
            _running.value = false
        }
    }

    fun saveAsNote(title: String) {
        val content = _revision.value
        if (content.isBlank()) return
        viewModelScope.launch { db.noteDao().upsert(Note(title = title.ifBlank { "Writing workspace result" }, content = content)) }
    }

    fun saveToLibrary() {
        val content = _revision.value
        if (content.isBlank()) return
        viewModelScope.launch { db.savedOutputDao().upsert(SavedOutput(content = content, label = "Writing workspace")) }
    }
}
