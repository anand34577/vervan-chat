package com.vervan.chat.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.ToolRun
import com.vervan.chat.system.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Owns the small amount of live data used by the tool directory. The catalog itself stays
 * static, while model readiness and recent usage remain lifecycle-aware and recoverable. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AllToolsViewModel(app: VervanApp) : ViewModel() {
    private val db = app.container.db
    private val reload = MutableStateFlow(0)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val activeModel: StateFlow<ModelInfo?> = reload
        .flatMapLatest { db.modelDao().observeActiveModel(ModelRole.GENERATION) }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            _error.value = throwable.toUserMessage()
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentRuns: StateFlow<List<ToolRun>> = reload
        .flatMapLatest { db.toolRunDao().observeRecent(12) }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            _error.value = throwable.toUserMessage()
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry() {
        _error.value = null
        reload.value += 1
    }
}
