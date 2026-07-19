package com.vervan.chat.ui.dev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class DevAction(val label: String, val instruction: String) {
    EXPLAIN("Explain", "Explain what the following code does, section by section:"),
    REVIEW("Review", "Review the following code for bugs, edge cases, and style issues. List concrete findings:"),
    TESTS("Generate tests", "Write unit tests for the following code, covering the main cases and edge cases:"),
    FIND_BUG("Find the bug", "The following code has a bug. Identify it and explain the fix:"),
    DOCUMENT("Add docs", "Add concise documentation comments to the following code without changing its logic:"),
    REFACTOR("Refactor", "Refactor the following code for readability and maintainability without changing its behavior. Explain each change briefly:"),
    EXPLAIN_STACK_TRACE("Explain stack trace", "The following is a stack trace or error output. Explain what went wrong, likely root cause, and where to look in the code:")
}

/** Standalone code assistant (spec §23) — paste code, run one action, get a streamed
 * response. No project/file awareness, no execution sandbox — just generation over
 * whatever text is pasted in, same runtime as everything else. */
class DevWorkspaceViewModel(private val app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val engine = app.container.llmEngine

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun run(action: DevAction, code: String) {
        if (code.isBlank() || _running.value) return
        viewModelScope.launch {
            _running.value = true
            _error.value = null
            _output.value = ""
            val model = db.modelDao().getActiveModel(ModelRole.GENERATION)
            if (model == null) {
                _error.value = "No chat model selected. Import or activate one in Models."
                _running.value = false
                return@launch
            }
            try {
                com.vervan.chat.llm.OneShotLlm.stream(app, "${action.instruction}\n\n```\n$code\n```")
                    ?.collect { chunk -> _output.value += chunk }
            } catch (t: Throwable) {
                _error.value = "Generation failed: ${t.toUserMessage()}"
            }
            _running.value = false
        }
    }

    fun saveToLibrary() {
        val content = _output.value
        if (content.isBlank()) return
        viewModelScope.launch { db.savedOutputDao().upsert(SavedOutput(content = content, label = "Developer workspace")) }
    }

    fun saveAsNote(title: String) {
        val content = _output.value
        if (content.isBlank()) return
        viewModelScope.launch {
            db.noteDao().upsert(com.vervan.chat.data.db.entities.Note(title = title.ifBlank { "Developer workspace" }, content = content))
        }
    }
}
