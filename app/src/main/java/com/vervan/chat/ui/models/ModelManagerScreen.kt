package com.vervan.chat.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.vervan.chat.ui.common.VervanFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.theme.vervanBorder
import com.vervan.chat.ui.theme.vervanSubtleDividerColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
import com.vervan.chat.data.db.entities.BackendChoice
import com.vervan.chat.data.db.entities.FileDownloadStatus
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelEngine
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.ModelStatus
import com.vervan.chat.data.db.entities.ToolApprovalMode
import com.vervan.chat.data.db.entities.canSupportAudio
import com.vervan.chat.data.db.entities.canSupportVision
import com.vervan.chat.data.db.entities.displayName
import com.vervan.chat.data.db.entities.traits
import com.vervan.chat.modeldownload.ModelAction
import com.vervan.chat.modeldownload.ModelUiState
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.ChipTone
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.SectionLabel
import com.vervan.chat.ui.common.SemanticChip
import com.vervan.chat.ui.common.ValidationMessage
import com.vervan.chat.ui.common.ModernistMetricStrip
import com.vervan.chat.ui.common.ModernistScreenHeader
import com.vervan.chat.ui.common.ModernistTag
import com.vervan.chat.ui.theme.VervanMono
import com.vervan.chat.ui.theme.vervanSuccess
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelManagerScreen(
    onBack: () -> Unit = {},
    onOpenCalculator: () -> Unit = {},
    onOpenStore: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: ModelManagerViewModel = viewModel(factory = viewModelFactory {
        initializer { ModelManagerViewModel(app) }
    })
    val models by vm.models.collectAsStateWithLifecycle()
    val modelsLoaded by vm.modelsLoaded.collectAsStateWithLifecycle()
    val defaults by vm.defaults.collectAsStateWithLifecycle()
    val expertMode by vm.expertMode.collectAsStateWithLifecycle()
    val useMlockDefault by vm.useMlockDefault.collectAsStateWithLifecycle()
    val flashAttentionModeDefault by vm.flashAttentionModeDefault.collectAsStateWithLifecycle()
    val kvCacheTypeDefault by vm.kvCacheTypeDefault.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val busyModelId by vm.busyModelId.collectAsStateWithLifecycle()
    val busyLabel by vm.busyLabel.collectAsStateWithLifecycle()
    val pendingAcknowledgment by vm.pendingAcknowledgment.collectAsStateWithLifecycle()
    val pendingMigration by vm.pendingMigration.collectAsStateWithLifecycle()
    // Sourced from ModelLoadCoordinator, not local engine polling — updates live regardless of
    // whether the load/unload was triggered from this screen, Chat, or Voice, so no
    // resume-tick refresh call is needed anymore (the coordinator's StateFlow already reflects
    // reality the moment anything changes).
    val generationLoadInfo by vm.generationLoadInfo.collectAsStateWithLifecycle()
    val embeddingLoadInfo by vm.embeddingLoadInfo.collectAsStateWithLifecycle()
    val downloadStates by vm.downloadStates.collectAsStateWithLifecycle()
    var editingModel by remember { mutableStateOf<ModelInfo?>(null) }
    // Model Calculator's "Browse models that fit" hands off its computed budget once (see
    // PendingModelBrowseFilter) — consumed exactly once per fresh navigation into this screen,
    // so returning to Model Manager later from anywhere else shows the plain, unfiltered list.
    val browseBudgetBytes = remember { com.vervan.chat.modeldownload.PendingModelBrowseFilter.consume() }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importModel(it, ModelRole.GENERATION) }
    }

    // Embedding models always need two files — the model itself and its SentencePiece
    // tokenizer, since a bare TFLite graph (this app's primary embedding target) has no
    // tokenizer bundled in. A dedicated step-by-step dialog (model file, then tokenizer file,
    // then Import) makes that requirement explicit instead of a single ambiguous multi-pick.
    var showEmbeddingImportDialog by remember { mutableStateOf(false) }
    var pendingEmbeddingModelUri by remember { mutableStateOf<Uri?>(null) }
    var pendingTokenizerUri by remember { mutableStateOf<Uri?>(null) }
    val pickEmbeddingModelFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingEmbeddingModelUri = it }
    }
    val pickTokenizerFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingTokenizerUri = it }
    }

    // GGUF (llama.cpp) import — model file required, mtmd projector optional.
    var showLlamaCppImportDialog by remember { mutableStateOf(false) }
    var pendingLlamaCppModelUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMmprojUri by remember { mutableStateOf<Uri?>(null) }
    val pickLlamaCppModelFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingLlamaCppModelUri = it }
    }
    val pickMmprojFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingMmprojUri = it }
    }

    // whisper.cpp local import — a single ggml (.bin) or GGUF model file, no catalog/network.
    var showWhisperImportDialog by remember { mutableStateOf(false) }
    var pendingWhisperUri by remember { mutableStateOf<Uri?>(null) }
    val pickWhisperFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingWhisperUri = it }
    }

    // Remote OpenAI-compatible API model — no file picker, just endpoint/key/model-id fields.
    // This dialog is add-only (see RemoteApiModelDialog's doc comment); editing an already-added
    // row reuses ModelEditDialog, the same "Configure model" screen a local model gets.
    var showRemoteApiDialog by remember { mutableStateOf(false) }

    // One routing decision for every model row in every section. Both local and remote models
    // share the same Configure screen now (ModelEditDialog branches internally on
    // model.traits.runsOnDevice) — a remote model just gets connection fields instead of
    // hardware/tuning sections.
    fun editModel(model: ModelInfo) {
        editingModel = model
    }

    // Press-and-hold selects one or more models for bulk delete; tapping a card while in
    // selection mode toggles it instead of doing its normal action.
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var showImportOptions by rememberSaveable { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    val generationModels = models.filter { it.role == ModelRole.GENERATION }
    val embeddingModels = models.filter { it.role == ModelRole.EMBEDDING }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selection_selected_count, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_modelmanagerscreen_229_cancel_selection)) }
                    },
                    actions = {
                        IconButton(onClick = { confirmBulkDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.library_delete_selected)) }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.ui_modelmanagerscreen_237_model_manager)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                    },
                    actions = {
                        IconButton(onClick = { showImportOptions = true }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ui_modelmanagerscreen_243_import_a_model))
                        }
                        IconButton(onClick = onOpenCalculator) { Icon(Icons.Filled.Calculate, contentDescription = stringResource(R.string.model_calculator_title)) }
                    }
                )
            }
        }
    ) { padding ->
      PageContainer(Modifier.padding(padding)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.sm)) {
            ModernistScreenHeader(
                eyebrow = stringResource(R.string.ui_modelmanagerscreen_254_runtime),
                title = stringResource(R.string.ui_modelmanagerscreen_255_make_the_workspace_ready),
                body = stringResource(R.string.ui_modelmanagerscreen_256_choose_a_runtime_load_what_you_need_and_see),
                trailing = {
                    ModernistTag(
                        if (generationLoadInfo.currentModelId != null) "LOADED" else "SETUP",
                        active = generationLoadInfo.currentModelId != null,
                    )
                },
            )
            ModernistMetricStrip(
                listOf(
                    "Generation" to generationModels.size.toString(),
                    "Embedding" to embeddingModels.size.toString(),
                    "Loaded" to listOfNotNull(generationLoadInfo.currentModelId, embeddingLoadInfo.currentModelId).size.toString(),
                    "Downloads" to downloadStates.count { it.status !in setOf(ModelStatus.NOT_DOWNLOADED, ModelStatus.READY) }.toString(),
                ),
                modifier = Modifier.padding(top = Space.lg, bottom = Space.lg),
            )
            // readiness summary — the model manager used to open straight into the
            // import card with no at-a-glance answer to "is anything actually usable right now."
            run {
                val generationReady = generationLoadInfo.currentModelId != null
                val embeddingReady = embeddingLoadInfo.currentModelId != null
                val (tone, title, body) = when {
                    generationReady && embeddingReady -> Triple(
                        com.vervan.chat.ui.common.StatusTone.Ready, "Ready",
                        "A generation model and an embedding model are both loaded."
                    )
                    generationReady -> Triple(
                        com.vervan.chat.ui.common.StatusTone.Ready, "Chat ready",
                        "No embedding model loaded. Search is using keywords."
                    )
                    generationModels.isEmpty() -> Triple(
                        com.vervan.chat.ui.common.StatusTone.Warning, "No generation model",
                        "Import a generation model below before starting a chat."
                    )
                    else -> Triple(
                        com.vervan.chat.ui.common.StatusTone.Info, "Not loaded",
                        "${generationModels.size} generation model(s) installed. Load one to chat."
                    )
                }
                com.vervan.chat.ui.common.SystemStatusStrip(
                    title = title,
                    body = body,
                    tone = tone,
                    modifier = Modifier.padding(bottom = Space.lg)
                )
            }
            // Persistent load-failure banners — the same ModelLoadCoordinator error Chat/Voice
            // read from, so this screen can never show a different story about why loading a
            // model didn't work.
            generationLoadInfo.error?.let { err ->
                val stuck = err.errorCategory == com.vervan.chat.modelload.ModelLoadErrorCategory.ENGINE_UNAVAILABLE
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = stringResource(R.string.ui_modelmanagerscreen_309_generation_model_load_failed),
                    message = err.errorMessage.toUserMessage(),
                    recovery = if (stuck) "" else "Retry from the model card, or use a smaller model or another runtime.",
                    // A foreground service (chat generation / the local API server) often keeps the
                    // process alive through a Recents swipe, so that alone won't clear the stuck
                    // engine this error means — only an actual process kill does (see
                    // ModelLoadCoordinator.engineUnavailableResult).
                    actionLabel = if (stuck) "Restart app" else null,
                    onAction = if (stuck) { { android.os.Process.killProcess(android.os.Process.myPid()) } } else null,
                    modifier = Modifier.padding(bottom = Space.sm)
                )
            }
            embeddingLoadInfo.error?.let { err ->
                val stuck = err.errorCategory == com.vervan.chat.modelload.ModelLoadErrorCategory.ENGINE_UNAVAILABLE
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = stringResource(R.string.ui_modelmanagerscreen_324_embedding_model_load_failed),
                    message = err.errorMessage.toUserMessage(),
                    recovery = if (stuck) "" else "Retry the model. Keyword search still works without it.",
                    actionLabel = if (stuck) "Restart app" else null,
                    onAction = if (stuck) { { android.os.Process.killProcess(android.os.Process.myPid()) } } else null,
                    modifier = Modifier.padding(bottom = Space.sm)
                )
            }
            val downloadingStates = downloadStates.filter {
                it.status !in setOf(ModelStatus.NOT_DOWNLOADED, ModelStatus.READY)
            }
            val downloadedVoiceStates = downloadStates.filter {
                it.status == ModelStatus.READY && it.category in setOf(ModelRole.TTS_VOICE, ModelRole.STT_MODEL)
            }
            val catalogStates = downloadStates.filter { it.status == com.vervan.chat.data.db.entities.ModelStatus.NOT_DOWNLOADED }
            // A first-time user with nothing installed yet should land on the unified picker
            // (recommended setup + Model Store + catalog) instead of an empty Library list —
            // that picker IS the single "one entry point" this screen already provides, it just
            // wasn't the default tab.
            //
            // The default can't be decided on the very first frame: `models` starts out as
            // `vm.models`'s stateIn seed (empty) for at least one dispatch before Room's real
            // query result arrives, and rememberSaveable's initializer only ever runs once — it
            // used to latch onto that transient empty list and land on Discover even when the
            // user already had a generation model installed (e.g. tapping the model name on
            // Home). Wait for modelsLoaded before deciding, then decide exactly once.
            var showingDiscover by rememberSaveable { mutableStateOf(browseBudgetBytes != null) }
            var discoverDefaultDecided by rememberSaveable { mutableStateOf(browseBudgetBytes != null) }
            LaunchedEffect(modelsLoaded) {
                if (!discoverDefaultDecided && modelsLoaded) {
                    showingDiscover = generationModels.isEmpty()
                    discoverDefaultDecided = true
                }
            }

            if (downloadingStates.isNotEmpty()) {
                SectionHeader("Active downloads", Icons.Filled.CloudDownload)
                downloadingStates.forEach { state ->
                    DownloadPackageCard(
                        state = state,
                        onPause = { vm.pauseDownload(state.modelId, state.version) },
                        onResume = { vm.resumeDownload(state.modelId, state.version) },
                        onStop = { vm.cancelDownload(state.modelId, state.version, keepPartial = false) },
                        onDelete = { vm.deleteDownload(state.modelId, state.version) }
                    )
                }
            }

            ModelManagerSwitcher(
                showingDiscover = showingDiscover,
                installedCount = models.size + downloadedVoiceStates.size,
                onLibrary = { showingDiscover = false },
                onDiscover = { showingDiscover = true },
                modifier = Modifier.padding(top = Space.md, bottom = Space.sm)
            )

            if (showingDiscover) {
                if (generationModels.isEmpty()) {
                    val memory = remember {
                        android.app.ActivityManager.MemoryInfo().also {
                            (app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(it)
                        }
                    }
                    val recommendation = remember(memory.totalMem) { com.vervan.chat.ui.onboarding.recommendModel(memory.totalMem) }
                    val recommendedState = recommendation?.let { rec -> catalogStates.firstOrNull { it.modelId == rec.model.modelId } }
                    if (recommendation != null && recommendedState != null) {
                        RecommendedSetupCard(
                            model = recommendedState,
                            reason = if (recommendation.reasonArg != null) stringResource(recommendation.reasonRes, recommendation.reasonArg) else stringResource(recommendation.reasonRes),
                            onSetup = { vm.setupRecommendedModel(recommendedState.modelId, recommendedState.version) },
                        )
                        Box(Modifier.height(Space.md))
                    }
                }

                // The signed store and the lightweight built-in catalogue are separate sources,
                // but live together here because both answer the same user intent: find a model.
                StoreEntryCard(onOpenStore)
                Box(Modifier.height(Space.md))
                if (catalogStates.isNotEmpty()) {
                    AvailableForDownloadSection(
                        catalogStates,
                        onDownload = { vm.downloadModel(it.modelId, it.version) },
                        highlightBudgetBytes = browseBudgetBytes
                    )
                    Box(Modifier.height(Space.lg))
                }
            } else {
                if (importing) {
                    com.vervan.chat.ui.common.OperationProgressCard(
                        title = busyLabel ?: "Importing model",
                        body = stringResource(R.string.ui_modelmanagerscreen_415_copying_and_checking_the_model_keep_the_app),
                        modifier = Modifier.padding(top = Space.sm)
                    )
                }
                status?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Space.sm))
                }

                if (!modelsLoaded) {
                    // Cold-start gap between compose-in and Room's first emission — the previous
                    // behavior showed "no models installed" here for a frame even with models
                    // present, same class of bug ChatListScreen's isLoading gate already fixed.
                    Box(Modifier.fillMaxWidth().padding(top = Space.xl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (generationModels.isEmpty() && embeddingModels.isEmpty() && downloadedVoiceStates.isEmpty()) {
                    EmptyModelLibrary(
                        onDiscover = { showingDiscover = true },
                        onImport = { showImportOptions = true }
                    )
                } else {
                    if (generationModels.isNotEmpty()) {
                        SectionHeader("For chat", Icons.Filled.Bolt)
                        generationModels.forEach { model ->
                            ModelCard(
                                model,
                                isLoaded = generationLoadInfo.currentModelId == model.id,
                                showSetActive = generationModels.size > 1,
                                onSetActive = { vm.setActive(model) },
                                onToggleLoad = { if (generationLoadInfo.currentModelId == model.id) vm.unload(model) else vm.load(model) },
                                onEdit = { editModel(model) },
                                onBenchmark = { vm.benchmark(model) },
                                onDelete = { vm.delete(model) },
                                busy = busyModelId == model.id,
                                busyLabel = busyLabel,
                                selectionMode = selectionMode,
                                selected = model.id in selectedIds,
                                onToggleSelect = { selectedIds = if (model.id in selectedIds) selectedIds - model.id else selectedIds + model.id },
                                onLongPress = { selectedIds = selectedIds + model.id }
                            )
                        }
                    }

                    if (embeddingModels.isNotEmpty()) {
                        SectionHeader("For search", Icons.Outlined.Storage)
                        Text(
                            "Used automatically for semantic search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Space.sm)
                        )
                        embeddingModels.forEach { model ->
                            ModelCard(
                                model,
                                isLoaded = embeddingLoadInfo.currentModelId == model.id,
                                showSetActive = embeddingModels.size > 1,
                                onSetActive = { vm.setActive(model) },
                                onToggleLoad = { if (embeddingLoadInfo.currentModelId == model.id) vm.unload(model) else vm.load(model) },
                                onEdit = { editModel(model) },
                                onBenchmark = { vm.benchmark(model) },
                                onDelete = { vm.delete(model) },
                                busy = busyModelId == model.id,
                                busyLabel = busyLabel,
                                selectionMode = selectionMode,
                                selected = model.id in selectedIds,
                                onToggleSelect = { selectedIds = if (model.id in selectedIds) selectedIds - model.id else selectedIds + model.id },
                                onLongPress = { selectedIds = selectedIds + model.id }
                            )
                        }
                    }

                    if (downloadedVoiceStates.isNotEmpty()) {
                        SectionHeader("Voice & speech", Icons.Filled.GraphicEq)
                        downloadedVoiceStates.forEach { state ->
                            DownloadPackageCard(
                                state = state,
                                onPause = {},
                                onResume = {},
                                onStop = {},
                                onDelete = { vm.deleteDownload(state.modelId, state.version) }
                            )
                        }
                    }
                }
            }

        }
      }
    }

    editingModel?.let { model ->
        ModelEditDialog(
            model = model,
            defaults = defaults,
            expertMode = expertMode,
            useMlockDefault = useMlockDefault,
            flashAttentionModeDefault = flashAttentionModeDefault,
            kvCacheTypeDefault = kvCacheTypeDefault,
            onDismiss = { editingModel = null },
            onSave = { updated, apiKey ->
                if (updated.traits.runsOnDevice) vm.update(updated) else vm.updateRemoteApiModel(updated, apiKey)
                editingModel = null
            }
        )
    }

    if (showEmbeddingImportDialog) {
        EmbeddingImportDialog(
            modelUri = pendingEmbeddingModelUri,
            tokenizerUri = pendingTokenizerUri,
            onPickModel = { pickEmbeddingModelFile.launch(arrayOf("*/*")) },
            onPickTokenizer = { pickTokenizerFile.launch(arrayOf("*/*")) },
            onDismiss = { showEmbeddingImportDialog = false },
            onImport = { modelUri, tokenizerUri ->
                vm.importEmbeddingPair(modelUri, tokenizerUri)
                showEmbeddingImportDialog = false
            }
        )
    }

    if (showImportOptions) {
        ImportModelDialog(
            importing = importing,
            onDismiss = { showImportOptions = false },
            onImport = { role ->
                showImportOptions = false
                if (role == ModelRole.GENERATION) {
                    pickFile.launch(arrayOf("*/*"))
                } else {
                    pendingEmbeddingModelUri = null
                    pendingTokenizerUri = null
                    showEmbeddingImportDialog = true
                }
            },
            onImportGguf = {
                showImportOptions = false
                pendingLlamaCppModelUri = null
                pendingMmprojUri = null
                showLlamaCppImportDialog = true
            },
            onImportWhisper = {
                showImportOptions = false
                pendingWhisperUri = null
                showWhisperImportDialog = true
            },
            onImportRemote = {
                showImportOptions = false
                showRemoteApiDialog = true
            }
        )
    }

    if (showRemoteApiDialog) {
        RemoteApiModelDialog(
            saving = importing,
            defaults = defaults,
            onDismiss = { showRemoteApiDialog = false },
            onFetch = { baseUrl, apiKey ->
                // This leaves the device carrying the user's bearer key, so it belongs in the
                // audit log like every other outbound request (model downloads, store catalogue,
                // remote generation). Host only, never the full URL — a base URL a user pasted
                // could carry a token in its query string, and the audit log is user-visible.
                app.container.networkAuditLog.record(
                    "Remote API model list: ${runCatching { java.net.URI(baseUrl).host }.getOrNull() ?: "invalid URL"}"
                )
                vm.fetchRemoteModels(baseUrl, apiKey)
            },
            onSave = { baseUrl, apiKey, selections ->
                vm.addRemoteApiModels(baseUrl, apiKey, selections)
                showRemoteApiDialog = false
            }
        )
    }

    if (showLlamaCppImportDialog) {
        LlamaCppImportDialog(
            modelUri = pendingLlamaCppModelUri,
            mmprojUri = pendingMmprojUri,
            onPickModel = { pickLlamaCppModelFile.launch(arrayOf("*/*")) },
            onPickMmproj = { pickMmprojFile.launch(arrayOf("*/*")) },
            onDismiss = { showLlamaCppImportDialog = false },
            onImport = { modelUri, mmprojUri ->
                vm.importLlamaCppModel(modelUri, mmprojUri)
                showLlamaCppImportDialog = false
            }
        )
    }

    if (showWhisperImportDialog) {
        WhisperCppImportDialog(
            modelUri = pendingWhisperUri,
            onPickModel = { pickWhisperFile.launch(arrayOf("*/*")) },
            onDismiss = { showWhisperImportDialog = false },
            onImport = { modelUri ->
                vm.importWhisperCppModel(modelUri)
                showWhisperImportDialog = false
            }
        )
    }

    if (confirmBulkDelete) {
        ConfirmDialog(
            title = stringResource(R.string.ui_modelmanagerscreen_617_delete_selected_models),
            body = stringResource(R.string.ui_modelmanagerscreen_remove_models, selectedIds.size),
            confirmLabel = stringResource(R.string.action_delete_forever),
            destructive = true,
            onConfirm = {
                    models.filter { it.id in selectedIds }.forEach { vm.delete(it) }
                    selectedIds = emptySet()
                    confirmBulkDelete = false
            },
            onDismiss = { confirmBulkDelete = false }
        )
    }

    pendingAcknowledgment?.let { model ->
        AlertDialog(
            onDismissRequest = { vm.dismissAcknowledgment() },
            title = { Text(stringResource(R.string.ui_modelmanagerscreen_633_before_you_activate_this_model)) },
            text = {
                Text(
                    "This model came from your file, so Vervan cannot verify its license. " +
                        "Check the publisher's terms before using it."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.acknowledgeAndActivate(model) }) { Text(stringResource(R.string.ui_modelmanagerscreen_641_i_understand_activate)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissAcknowledgment() }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    pendingMigration?.let { (newModel, previous) ->
        AlertDialog(
            onDismissRequest = { vm.dismissMigration() },
            title = { Text(stringResource(R.string.ui_modelmanagerscreen_652_new_version_detected)) },
            text = {
                Text(
                    "Use \"${newModel.displayName}\" as the default and update folders using the old default? " +
                        "Existing chats and \"${previous.displayName}\" stay unchanged."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.relinkToNewVersion(newModel, previous) }) { Text(stringResource(R.string.ui_modelmanagerscreen_660_use_new_version)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissMigration() }) { Text(stringResource(R.string.ui_modelmanagerscreen_663_keep_old_one)) }
            }
        )
    }
}
