package com.vervan.chat.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.data.db.entities.ToolRun
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async

/** Owns tool-run history reads and recoverable mutations so the history screen never treats a
 * database failure as an empty history or reports a delete/save as successful before it commits. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ToolRunHistoryViewModel(app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val reload = MutableStateFlow(0)
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val runs: StateFlow<List<ToolRun>> = reload
        .flatMapLatest { db.toolRunDao().observeAll() }
        .onStart { _isLoading.value = true }
        .onEach {
            _isLoading.value = false
            _error.value = null
        }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            _isLoading.value = false
            _error.value = throwable.toUserMessage()
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry() {
        _error.value = null
        reload.value += 1
    }

    fun save(run: ToolRun): Deferred<Result<Unit>> = viewModelScope.async {
        try {
            db.savedOutputDao().upsert(
                SavedOutput(content = run.output.ifBlank { run.input }, label = run.toolName)
            )
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun delete(run: ToolRun): Deferred<Result<Unit>> = viewModelScope.async {
        try {
            db.toolRunDao().softDelete(run.id)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
