package com.vervan.chat.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Owns document lookup for the PDF page viewer so a missing row or a database failure is not
 * mistaken for a still-loading PDF in the composable. */
class PdfPageViewerViewModel(private val app: VervanApp, private val documentId: String) : ViewModel() {
    private val db = app.container.db

    private val _document = MutableStateFlow<Document?>(null)
    val document: StateFlow<Document?> = _document

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _document.value = db.documentDao().get(documentId)
            } catch (t: Throwable) {
                com.vervan.chat.system.rethrowCancellation(t)
                if (t is CancellationException) throw t
                _error.value = t.toUserMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
