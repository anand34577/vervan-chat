package com.vervan.chat.ui.personas

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PersonaTestBenchViewModel(private val app: VervanApp, private val personaId: String) : ViewModel() {
    private val db = app.container.db
    private val engine = app.container.llmEngine
    private val reload = MutableStateFlow(0)
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    val persona: StateFlow<Persona?> = reload
        .flatMapLatest { db.personaDao().observePersonas() }
        .map { list -> list.find { it.id == personaId } }
        .onStart { _isLoading.value = true }
        .onEach {
            _isLoading.value = false
            _loadError.value = null
        }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            _isLoading.value = false
            _loadError.value = throwable.toUserMessage()
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun retryLoad() {
        _loadError.value = null
        reload.value += 1
    }

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
                    _error.value = "No model is ready. Open Settings → AI models and load one."
                    return@launch
                }
                val prompt = "${p.systemInstruction}\n\nUser: ${_samplePrompt.value}\nAssistant:"
                val sb = StringBuilder()
                com.vervan.chat.llm.OneShotLlm.stream(app, prompt)?.collect { sb.append(it) }
                _response.value = sb.toString()
            } catch (t: Throwable) {
                Log.e(TAG, "run() failed for persona=$personaId", t)
                _error.value = t.toUserMessage()
            } finally {
                _running.value = false
            }
        }
    }

    companion object {
        private const val TAG = "PersonaTestBenchViewModel"
    }

    fun reset() {
        _response.value = null
        _error.value = null
    }
}
