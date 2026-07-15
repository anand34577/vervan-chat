package com.vervan.chat.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.PromptTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** [templateId] null creates a new template; editing a built-in saves as a new custom copy,
 * same pattern as [com.vervan.chat.ui.personas.PersonaEditorViewModel]. */
class TemplateEditorViewModel(private val app: VervanApp, private val templateId: String?) : ViewModel() {
    private val db = app.container.db

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description

    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body

    private val _isBuiltIn = MutableStateFlow(false)
    val isBuiltIn: StateFlow<Boolean> = _isBuiltIn

    init {
        if (templateId != null) {
            viewModelScope.launch {
                db.promptTemplateDao().get(templateId)?.let { t ->
                    _name.value = t.name
                    _description.value = t.description
                    _body.value = t.body
                    _isBuiltIn.value = t.isBuiltIn
                }
            }
        }
    }

    fun setName(value: String) { _name.value = value.removePrefix("/").trim() }
    fun setDescription(value: String) { _description.value = value }
    fun setBody(value: String) { _body.value = value }

    suspend fun save(): Boolean {
        if (_name.value.isBlank() || _body.value.isBlank()) return false
        val editingCustom = templateId != null && !_isBuiltIn.value
        val template = PromptTemplate(
            id = if (editingCustom) templateId!! else java.util.UUID.randomUUID().toString(),
            name = _name.value.trim(),
            description = _description.value.trim(),
            body = _body.value.trim(),
            isBuiltIn = false
        )
        db.promptTemplateDao().upsert(template)
        return true
    }

    fun delete() {
        if (templateId == null || _isBuiltIn.value) return
        // Soft delete (Phase 6, spec §34) — recoverable from the recycle bin instead of gone instantly.
        viewModelScope.launch { db.promptTemplateDao().get(templateId)?.let { db.promptTemplateDao().upsert(it.copy(deletedAt = System.currentTimeMillis())) } }
    }
}
