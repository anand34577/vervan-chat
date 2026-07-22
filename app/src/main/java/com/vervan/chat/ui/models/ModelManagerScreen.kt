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
import androidx.compose.material3.Button
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
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
import com.vervan.chat.modeldownload.ModelAction
import com.vervan.chat.modeldownload.ModelUiState
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.ChipTone
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.SectionLabel
import com.vervan.chat.ui.common.SemanticChip
import com.vervan.chat.ui.common.ValidationMessage
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

    // Press-and-hold selects one or more models for bulk delete; tapping a card while in
    // selection mode toggles it instead of doing its normal action.
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    val generationModels = models.filter { it.role == ModelRole.GENERATION }
    val embeddingModels = models.filter { it.role == ModelRole.EMBEDDING }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Filled.Close, contentDescription = "Cancel selection") }
                    },
                    actions = {
                        IconButton(onClick = { confirmBulkDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete selected") }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Model manager") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        IconButton(onClick = onOpenCalculator) { Icon(Icons.Filled.Calculate, contentDescription = "Model calculator") }
                    }
                )
            }
        }
    ) { padding ->
      PageContainer(Modifier.padding(padding)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.sm)) {
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
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            // Persistent load-failure banners — the same ModelLoadCoordinator error Chat/Voice
            // read from, so this screen can never show a different story about why loading a
            // model didn't work.
            generationLoadInfo.error?.let { err ->
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = "Generation model load failed",
                    message = err.errorMessage.toUserMessage(),
                    recovery = "Retry from the model card, or use a smaller model or another runtime.",
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            embeddingLoadInfo.error?.let { err ->
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = "Embedding model load failed",
                    message = err.errorMessage.toUserMessage(),
                    recovery = "Retry the model. Keyword search still works without it.",
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            val downloadingStates = downloadStates.filter {
                it.status !in setOf(ModelStatus.NOT_DOWNLOADED, ModelStatus.READY)
            }
            val downloadedVoiceStates = downloadStates.filter {
                it.status == ModelStatus.READY && it.category in setOf(ModelRole.TTS_VOICE, ModelRole.STT_MODEL)
            }
            val catalogStates = downloadStates.filter { it.status == com.vervan.chat.data.db.entities.ModelStatus.NOT_DOWNLOADED }

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
                        reason = recommendation.reason,
                        onSetup = { vm.setupRecommendedModel(recommendedState.modelId, recommendedState.version) },
                    )
                    Box(Modifier.height(Space.md))
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

            if (downloadedVoiceStates.isNotEmpty()) {
                SectionHeader("Downloaded voice & speech models", Icons.Filled.GraphicEq)
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

            if (catalogStates.isNotEmpty()) {
                AvailableForDownloadSection(
                    catalogStates,
                    onDownload = { vm.downloadModel(it.modelId, it.version) },
                    highlightBudgetBytes = browseBudgetBytes
                )
                Box(Modifier.height(Space.lg))
            }

            // Entry point to the curated Model Store (com.vervan.chat.store). Kept as a distinct
            // destination rather than merged into the list above: the store's catalogue is signed
            // and fetched remotely, its entries have per-runtime variants and per-device
            // eligibility, and it installs through its own content-addressed pipeline.
            StoreEntryCard(onOpenStore)
            Box(Modifier.height(Space.lg))

            ImportCard(
                importing = importing,
                onImport = { role ->
                    if (role == ModelRole.GENERATION) {
                        pickFile.launch(arrayOf("*/*"))
                    } else {
                        pendingEmbeddingModelUri = null
                        pendingTokenizerUri = null
                        showEmbeddingImportDialog = true
                    }
                },
                onImportGguf = {
                    pendingLlamaCppModelUri = null
                    pendingMmprojUri = null
                    showLlamaCppImportDialog = true
                }
            )

            if (importing) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = busyLabel ?: "Importing model",
                    body = "Copying and checking the model. Keep the app open.",
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }

            if (generationModels.isNotEmpty()) {
                SectionHeader("Generation models", Icons.Filled.Bolt)
                generationModels.forEach { model ->
                    ModelCard(
                        model,
                        isLoaded = generationLoadInfo.currentModelId == model.id,
                        showSetActive = generationModels.size > 1,
                        onSetActive = { vm.setActive(model) },
                        onToggleLoad = { if (generationLoadInfo.currentModelId == model.id) vm.unload(model) else vm.load(model) },
                        onEdit = { editingModel = model },
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
                SectionHeader("Embedding models", Icons.Outlined.Storage)
                Text(
                    "Used automatically for semantic search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                embeddingModels.forEach { model ->
                    ModelCard(
                        model,
                        isLoaded = embeddingLoadInfo.currentModelId == model.id,
                        showSetActive = embeddingModels.size > 1,
                        onSetActive = { vm.setActive(model) },
                        onToggleLoad = { if (embeddingLoadInfo.currentModelId == model.id) vm.unload(model) else vm.load(model) },
                        onEdit = { editingModel = model },
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
            onSave = { vm.update(it); editingModel = null }
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

    if (confirmBulkDelete) {
        ConfirmDialog(
            title = "Delete selected models?",
            body = "Remove ${selectedIds.size} model file${if (selectedIds.size == 1) "" else "s"} permanently?",
            confirmLabel = "Delete forever",
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
            title = { Text("Before you activate this model") },
            text = {
                Text(
                    "This model came from your file, so Vervan cannot verify its license. " +
                        "Check the publisher's terms before using it."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.acknowledgeAndActivate(model) }) { Text("I understand, activate") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissAcknowledgment() }) { Text("Cancel") }
            }
        )
    }

    pendingMigration?.let { (newModel, previous) ->
        AlertDialog(
            onDismissRequest = { vm.dismissMigration() },
            title = { Text("New version detected") },
            text = {
                Text(
                    "Use \"${newModel.displayName}\" as the default and update folders using the old default? " +
                        "Existing chats and \"${previous.displayName}\" stay unchanged."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.relinkToNewVersion(newModel, previous) }) { Text("Use new version") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissMigration() }) { Text("Keep old one") }
            }
        )
    }
}
