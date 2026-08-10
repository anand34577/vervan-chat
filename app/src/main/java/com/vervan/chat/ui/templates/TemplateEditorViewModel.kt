package com.vervan.chat.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.PromptTemplate
import com.vervan.chat.data.repo.resolveEditId
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.ValidationLimits
import kotlinx.coroutines.CancellationException
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

    private val _isLoading = MutableStateFlow(templateId != null)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    private val _recordFound = MutableStateFlow(templateId == null)
    val recordFound: StateFlow<Boolean> = _recordFound

    init {
        load()
    }

    fun retryLoad() {
        load()
    }

    private fun load() {
        if (templateId == null) {
            _isLoading.value = false
            _recordFound.value = true
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _loadError.value = null
            try {
                val template = db.promptTemplateDao().get(templateId)
                _recordFound.value = template != null
                template?.let { t ->
                    _name.value = t.name
                    _description.value = t.description
                    _body.value = t.body
                    _isBuiltIn.value = t.isBuiltIn
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _recordFound.value = false
                _loadError.value = t.toUserMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setName(value: String) { _name.value = value.removePrefix("/").trim() }
    fun setDescription(value: String) { _description.value = value }
    fun setBody(value: String) { _body.value = value }

    suspend fun save(): Boolean {
        if (_name.value.isBlank() || _body.value.isBlank()) return false
        if (_name.value.length > ValidationLimits.TEMPLATE_TITLE ||
            _description.value.length > ValidationLimits.TEMPLATE_DESCRIPTION ||
            _body.value.length > ValidationLimits.TEMPLATE_BODY
        ) return false
        val template = PromptTemplate(
            id = resolveEditId(templateId, _isBuiltIn.value),
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
        // Soft delete — recoverable from the recycle bin instead of gone instantly.
        viewModelScope.launch { db.promptTemplateDao().get(templateId)?.let { db.promptTemplateDao().upsert(it.copy(deletedAt = System.currentTimeMillis())) } }
    }
}
