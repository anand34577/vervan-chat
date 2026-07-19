package com.vervan.chat.ui.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PersonaTestBenchViewModel(private val app: VervanApp, private val personaId: String) : ViewModel() {
    private val db = app.container.db
    private val engine = app.container.llmEngine

    val persona: StateFlow<Persona?> = db.personaDao().observePersonas()
        .map { list -> list.find { it.id == personaId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _samplePrompt = MutableStateFlow("Explain how recursion works, with a short example.")
    val samplePrompt: StateFlow<String> = _samplePrompt.asStateFlow()

    private val _response = MutableStateFlow<String?>(null)
    val response: StateFlow<String?> = _response.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setPrompt(v: String) { _samplePrompt.value = v }

    fun run() {
        val p = persona.value ?: return
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            _error.value = null
            _response.value = null
            try {
                val model = db.modelDao().getActiveModel(ModelRole.GENERATION)
                if (model == null) {
                    _error.value = "No active generation model."
                    return@launch
                }
                val prompt = "${p.systemInstruction}\n\nUser: ${_samplePrompt.value}\nAssistant:"
                val sb = StringBuilder()
                com.vervan.chat.llm.OneShotLlm.stream(app, prompt)?.collect { sb.append(it) }
                _response.value = sb.toString()
            } catch (t: Throwable) {
                _error.value = t.toUserMessage()
            } finally {
                _running.value = false
            }
        }
    }

    fun reset() {
        _response.value = null
        _error.value = null
    }
}
