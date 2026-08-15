package com.vervan.chat.ui.tools

import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.theme.Space
import androidx.compose.runtime.Composable
import com.vervan.chat.ui.common.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.traits
import com.vervan.chat.system.toUserMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class ModelCapabilityDashboardViewModel(private val app: VervanApp) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val models: StateFlow<List<ModelInfo>> = reload
        .flatMapLatest { app.container.db.modelDao().observeModels() }
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retry() {
        _error.value = null
        reload.value += 1
    }
}

/** Shows what each installed model declares support for — the same [ModelInfo] fields that
 * already gate the composer's photo/camera/voice buttons and the Tools/Reasoning toggles
 * (see ChatScreen), just surfaced directly instead of only being inferred from what's greyed out. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelCapabilityDashboardScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: ModelCapabilityDashboardViewModel = viewModel(factory = viewModelFactory { initializer { ModelCapabilityDashboardViewModel(app) } })
    val models by vm.models.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val loadError by vm.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_modelcapabilitydashboardscreen_100_model_capabilities)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.action_back)) } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        when {
            loadError != null -> OperationErrorCard(
                title = stringResource(R.string.ui_modelcapabilitydashboardscreen_108_model_capabilities_unavailable),
                message = loadError.orEmpty(),
                recovery = stringResource(R.string.ui_modelcapability_capability_recovery),
                actionLabel = stringResource(R.string.action_retry),
                onAction = vm::retry,
                modifier = Modifier.padding(Space.md)
            )
            isLoading -> LoadingSkeletonList(rows = 6, modifier = Modifier.padding(Space.md))
            models.isEmpty() -> {
            Column(Modifier.fillMaxSize().padding(Space.md)) {
                ToolIntro(
                    icon = Icons.Filled.Memory,
                    title = stringResource(R.string.ui_modelcapabilitydashboardscreen_120_know_what_each_model_can_do),
                    body = stringResource(R.string.ui_modelcapabilitydashboardscreen_121_compare_model_features_context_and_compatibl)
                )
                EmptyState(
                    icon = Icons.Filled.Memory,
                    title = stringResource(R.string.ui_modelcapabilitydashboardscreen_125_no_models_to_compare),
                    body = stringResource(R.string.ui_modelcapabilitydashboardscreen_126_import_a_model_to_see_its_features_and_devic),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    centered = true
                )
            }
            }
            else -> {
        LazyColumn(
            Modifier.fillMaxSize().padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            item {
                ToolIntro(
                    icon = Icons.Filled.Memory,
                    title = stringResource(R.string.ui_modelcapabilitydashboardscreen_140_know_what_each_model_can_do),
                    body = stringResource(R.string.ui_modelcapabilitydashboardscreen_141_compare_model_features_context_and_compatibl),
                    modifier = Modifier.padding(bottom = Space.md)
                )
            }
            items(models, key = { it.id }) { model ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.md)) {
                        Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            // No weights on disk for a remote model — same reasoning as ModelCard's
                            // own size line — and lastWorkingBackend never advances past its
                            // UNVERIFIED default for one either (see EngineTraits.runsOnDevice):
                            // it never runs the native load path that would move it off that value,
                            // so showing it verbatim reads as a warning about a model that's fine.
                            (if (model.traits.storesWeightsLocally) "${model.fileSizeBytes / (1024 * 1024)} MB on disk"
                             else model.traits.label) +
                                " · context ${model.contextTokens ?: "—"} tokens" +
                                (if (model.isActive) " · Active" else ""),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            if (model.role == ModelRole.EMBEDDING) {
                                // An embedding model only ever turns text into a vector — it never
                                // sees an image, a tool catalog, or a reasoning instruction, so the
                                // generation-only badges below are meaningless for it (same rule
                                // ModelEditDialog/ModelCard use to hide those sections by role).
                                CapBadge("Embedding", true)
                            } else {
                                CapBadge("Text", true)
                                CapBadge("Vision", model.supportsVision)
                                CapBadge("Audio", model.supportsAudio)
                                CapBadge("Tools", model.supportsTools)
                                CapBadge("Thinking", model.supportsThinking)
                            }
                            CapBadge(
                                if (model.traits.runsOnDevice) "Backend: ${model.lastWorkingBackend.name}" else "Backend: Remote",
                                null, neutral = true
                            )
                        }
                    }
                }
            }
        }
        }
        }
        }
    }
}

@Composable
private fun CapBadge(label: String, supported: Boolean?, neutral: Boolean = false) {
    val color = when {
        neutral -> MaterialTheme.colorScheme.surfaceContainer
        supported == true -> MaterialTheme.colorScheme.primaryContainer
        supported == false -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val text = when {
        neutral -> label
        supported == true -> "$label ✓"
        supported == false -> "$label ✗"
        else -> "$label ?"
    }
    Surface(shape = MaterialTheme.shapes.small, color = color) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs))
    }
}
