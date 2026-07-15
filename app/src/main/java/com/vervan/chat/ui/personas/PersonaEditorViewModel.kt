package com.vervan.chat.ui.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Persona
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** [personaId] null creates a new persona; non-null edits (a built-in opened here saves
 * as a new custom copy, same pattern as [com.vervan.chat.ui.workflows.WorkflowEditorViewModel]). */
class PersonaEditorViewModel(private val app: VervanApp, private val personaId: String?) : ViewModel() {
    private val db = app.container.db

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description

    private val _systemInstruction = MutableStateFlow("")
    val systemInstruction: StateFlow<String> = _systemInstruction

    private val _isBuiltIn = MutableStateFlow(false)
    val isBuiltIn: StateFlow<Boolean> = _isBuiltIn

    private val _tone = MutableStateFlow("NEUTRAL")
    val tone: StateFlow<String> = _tone
    private val _formality = MutableStateFlow("NEUTRAL")
    val formality: StateFlow<String> = _formality
    private val _conciseness = MutableStateFlow("NORMAL")
    val conciseness: StateFlow<String> = _conciseness
    private val _creativity = MutableStateFlow(0.5f)
    val creativity: StateFlow<Float> = _creativity
    private val _responseLength = MutableStateFlow("BALANCED")
    val responseLength: StateFlow<String> = _responseLength
    private val _language = MutableStateFlow("")
    val language: StateFlow<String> = _language

    init {
        if (personaId != null) {
            viewModelScope.launch {
                db.personaDao().getPersona(personaId)?.let { persona ->
                    _name.value = persona.name
                    _description.value = persona.description
                    _systemInstruction.value = persona.systemInstruction
                    _isBuiltIn.value = persona.isBuiltIn
                    _tone.value = persona.tone
                    _formality.value = persona.formality
                    _conciseness.value = persona.conciseness
                    _creativity.value = persona.creativity
                    _responseLength.value = persona.responseLength
                    _language.value = persona.language
                }
            }
        }
    }

    fun setName(value: String) { _name.value = value }
    fun setDescription(value: String) { _description.value = value }
    fun setSystemInstruction(value: String) { _systemInstruction.value = value }
    fun setTone(value: String) { _tone.value = value }
    fun setFormality(value: String) { _formality.value = value }
    fun setConciseness(value: String) { _conciseness.value = value }
    fun setCreativity(value: Float) { _creativity.value = value }
    fun setResponseLength(value: String) { _responseLength.value = value }
    fun setLanguage(value: String) { _language.value = value }

    suspend fun save(): Boolean {
        if (_name.value.isBlank() || _systemInstruction.value.isBlank()) return false
        val editingCustom = personaId != null && !_isBuiltIn.value
        val persona = Persona(
            id = if (editingCustom) personaId!! else java.util.UUID.randomUUID().toString(),
            name = _name.value.trim(),
            description = _description.value.trim(),
            systemInstruction = _systemInstruction.value.trim(),
            isBuiltIn = false,
            tone = _tone.value,
            formality = _formality.value,
            conciseness = _conciseness.value,
            creativity = _creativity.value,
            responseLength = _responseLength.value,
            language = _language.value.trim()
        )
        db.personaDao().upsert(persona)
        return true
    }

    /** Soft delete (Phase 6, spec §34) — recoverable from the recycle bin instead of gone instantly. */
    fun delete() {
        if (personaId == null || _isBuiltIn.value) return
        viewModelScope.launch {
            db.personaDao().getPersona(personaId)?.let {
                db.chatDao().clearPersona(personaId)
                db.folderDao().clearDefaultPersona(personaId)
                db.projectDao().clearPersona(personaId)
                db.knowledgeBaseDao().clearDefaultPersona(personaId)
                db.personaDao().upsert(it.copy(deletedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun duplicate(): String {
        val copy = Persona(
            name = "${_name.value.trim()} copy",
            description = _description.value.trim(),
            systemInstruction = _systemInstruction.value.trim(),
            isBuiltIn = false,
            tone = _tone.value,
            formality = _formality.value,
            conciseness = _conciseness.value,
            creativity = _creativity.value,
            responseLength = _responseLength.value,
            language = _language.value.trim()
        )
        db.personaDao().upsert(copy)
        return copy.id
    }
}
