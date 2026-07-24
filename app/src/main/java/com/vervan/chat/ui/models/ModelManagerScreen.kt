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
            // §7.4.1 readiness summary — the model manager used to open straight into the
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
                AvailableForDownloadSection(catalogStates, onDownload = { vm.downloadModel(it.modelId, it.version) })
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

@Composable
private fun SectionHeader(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Keeps the runtime choice explicit. LiteRT-LM and llama.cpp are peers, while embeddings are
 * supporting infrastructure; stacking these options on phones avoids unreadably narrow cards. */
@Composable
private fun ImportCard(importing: Boolean, onImport: (ModelRole) -> Unit, onImportGguf: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(Space.lg)) {
            Text("Add a local model", style = MaterialTheme.typography.titleSmall)
            Text(
                "Choose the runtime that matches your model file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs, bottom = Space.md)
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 600.dp
                val choiceWidth = if (compact) 1f else 0.31f
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    maxItemsInEachRow = if (compact) 1 else 3
                ) {
                    ImportChoiceCard(
                        title = "LiteRT-LM",
                        subtitle = "Android-optimized • .task / .litertlm",
                        icon = Icons.Filled.Bolt,
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth(choiceWidth),
                        horizontal = compact,
                        onClick = { onImport(ModelRole.GENERATION) }
                    )
                    ImportChoiceCard(
                        title = "llama.cpp",
                        subtitle = "Broad GGUF support • Vulkan / CPU",
                        icon = Icons.Filled.Bolt,
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth(choiceWidth),
                        horizontal = compact,
                        onClick = onImportGguf
                    )
                    ImportChoiceCard(
                        title = "Embeddings",
                        subtitle = "Semantic search • model + tokenizer",
                        icon = Icons.Outlined.Storage,
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth(choiceWidth),
                        horizontal = compact,
                        onClick = { onImport(ModelRole.EMBEDDING) }
                    )
                }
            }
        }
    }
}

/**
 * Step-by-step embedding import: pick the model file, then its SentencePiece tokenizer file,
 * then Import — the tokenizer is mandatory (a bare TFLite embedding graph has no tokenizer
 * bundled in), so Import refuses with an explicit warning instead of silently proceeding
 * without one.
 */
@Composable
private fun EmbeddingImportDialog(
    modelUri: Uri?,
    tokenizerUri: Uri?,
    onPickModel: () -> Unit,
    onPickTokenizer: () -> Unit,
    onDismiss: () -> Unit,
    onImport: (Uri, Uri) -> Unit
) {
    val context = LocalContext.current
    var validationError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import embedding model") },
        text = {
            Column {
                Text(
                    "Choose the embedding model and its SentencePiece tokenizer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                EmbeddingImportStep(
                    stepNumber = 1,
                    label = "Model file",
                    fileName = modelUri?.let { queryDisplayName(context, it) },
                    onPick = { validationError = null; onPickModel() }
                )
                EmbeddingImportStep(
                    stepNumber = 2,
                    label = "Tokenizer file (sentencepiece.model)",
                    fileName = tokenizerUri?.let { queryDisplayName(context, it) },
                    onPick = { validationError = null; onPickTokenizer() }
                )
                validationError?.let {
                    ValidationMessage(it, modifier = Modifier.padding(top = 10.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                validationError = when {
                    modelUri == null -> "Select the embedding model file first."
                    tokenizerUri == null -> "Choose a SentencePiece tokenizer before importing."
                    else -> null
                }
                if (modelUri != null && tokenizerUri != null) onImport(modelUri, tokenizerUri)
            }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Model file required, mtmd projector optional (vision) — unlike the embedding pair, both
 * files share the .gguf extension so there's no way to auto-tell them apart by name. */
@Composable
private fun LlamaCppImportDialog(
    modelUri: Uri?,
    mmprojUri: Uri?,
    onPickModel: () -> Unit,
    onPickMmproj: () -> Unit,
    onDismiss: () -> Unit,
    onImport: (Uri, Uri?) -> Unit
) {
    val context = LocalContext.current
    var validationError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import GGUF model") },
        text = {
            Column {
                Text(
                    "Add an mmproj file only for vision models.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                EmbeddingImportStep(
                    stepNumber = 1,
                    label = "Model file (.gguf)",
                    fileName = modelUri?.let { queryDisplayName(context, it) },
                    onPick = { validationError = null; onPickModel() }
                )
                EmbeddingImportStep(
                    stepNumber = 2,
                    label = "Vision projector (optional)",
                    fileName = mmprojUri?.let { queryDisplayName(context, it) },
                    onPick = { validationError = null; onPickMmproj() }
                )
                validationError?.let {
                    ValidationMessage(it, modifier = Modifier.padding(top = 10.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (modelUri == null) {
                    validationError = "Select the GGUF model file first."
                } else {
                    onImport(modelUri, mmprojUri)
                }
            }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EmbeddingImportStep(stepNumber: Int, label: String, fileName: String?, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text("$stepNumber. $label", style = MaterialTheme.typography.labelMedium)
            Text(
                fileName ?: "Not selected",
                style = MaterialTheme.typography.bodySmall,
                color = if (fileName != null) MaterialTheme.colorScheme.vervanSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onPick) { Text(if (fileName != null) "Change" else "Choose") }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
    }
    return uri.lastPathSegment
}

@Composable
private fun ImportChoiceCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = vervanBorder()
    ) {
        if (horizontal) {
            Row(Modifier.fillMaxWidth().padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(Space.sm).size(22.dp)
                    )
                }
                Column(Modifier.padding(start = Space.md)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(Modifier.padding(Space.md)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Space.sm))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ModelCard(
    model: ModelInfo,
    isLoaded: Boolean,
    showSetActive: Boolean,
    onSetActive: () -> Unit,
    onToggleLoad: () -> Unit,
    onEdit: () -> Unit,
    onBenchmark: () -> Unit,
    onDelete: () -> Unit,
    busy: Boolean,
    busyLabel: String?,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).animateContentSize()
            .combinedClickable(onClick = { if (selectionMode) onToggleSelect() else onEdit() }, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                model.isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (model.isActive || selected) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else null
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (selectionMode) {
                        Checkbox(checked = selected, onCheckedChange = { onToggleSelect() }, modifier = Modifier.padding(end = 4.dp))
                    }
                    Column(Modifier.padding(end = 8.dp)) {
                        Text(model.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            formatModelSize(model.fileSizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = VervanMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (model.role == ModelRole.GENERATION) {
                            Text(
                                model.runtimeSummary(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = VervanMono,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (model.origin == com.vervan.chat.data.db.entities.ModelOrigin.DOWNLOADED) {
                            Text(
                                "Downloaded" + (model.catalogVersion?.let { " · v$it" } ?: "") +
                                    " · ${java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(model.importedAt))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (!selectionMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                        if (isLoaded) SemanticChip("Loaded", ChipTone.Neutral)
                        if (model.isActive) SemanticChip("Default", ChipTone.Neutral)
                        Box {
                            IconButton(onClick = { menuOpen = true }, enabled = !busy) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (showSetActive && !model.isActive) {
                                    DropdownMenuItem(
                                        text = { Text("Set as default") },
                                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                                        onClick = { menuOpen = false; onSetActive() }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Configure") },
                                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                                    onClick = { menuOpen = false; onEdit() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Benchmark") },
                                    leadingIcon = { Icon(Icons.Filled.Speed, contentDescription = null) },
                                    onClick = { menuOpen = false; onBenchmark() }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete model", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; confirmDelete = true }
                                )
                            }
                        }
                    }
                }
            }
            if (busy && busyLabel != null) {
                Text(
                    busyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (model.role == ModelRole.GENERATION) {
                Row(
                    Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    fun state(supported: Boolean?) = when (supported) {
                        true -> com.vervan.chat.ui.common.CapabilityState.Supported
                        false -> com.vervan.chat.ui.common.CapabilityState.Unsupported
                        null -> com.vervan.chat.ui.common.CapabilityState.Unknown
                    }
                    com.vervan.chat.ui.common.CapabilityBadge(com.vervan.chat.ui.common.Capability.Vision, state(model.supportsVision))
                    com.vervan.chat.ui.common.CapabilityBadge(com.vervan.chat.ui.common.Capability.Audio, state(model.supportsAudio))
                    com.vervan.chat.ui.common.CapabilityBadge(com.vervan.chat.ui.common.Capability.Tools, state(model.supportsTools))
                    com.vervan.chat.ui.common.CapabilityBadge(com.vervan.chat.ui.common.Capability.Thinking, state(model.supportsThinking))
                }
            }
            if (model.lastWorkingBackend != com.vervan.chat.data.db.entities.ModelBackend.UNVERIFIED) {
                // MTP (speculative decoding) only ever applies to the GPU backend — showing its
                // on/off status when the model actually ran on CPU/NPU last time would read as
                // "MTP on" despite MTP having no effect at all on that run.
                val mtpNote = if (model.mtpSupported == true && model.lastWorkingBackend == com.vervan.chat.data.db.entities.ModelBackend.GPU) {
                    " · MTP ${if (model.mtpEnabled) "on" else "off"}"
                } else ""
                Text(
                    "Last ran on ${model.lastWorkingBackend.displayName()}$mtpNote",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.vervanSuccess,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (!selectionMode) {
                ResponsiveActions(Modifier.padding(top = 12.dp)) {
                    Button(
                        onClick = onToggleLoad,
                        enabled = !busy,
                        colors = if (isLoaded) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) else ButtonDefaults.buttonColors()
                    ) {
                        Icon(if (isLoaded) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (isLoaded) "Unload" else "Load", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete model?",
            body = "Remove \"${model.displayName}\" permanently?",
            confirmLabel = "Delete forever",
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ModelEditDialog(
    model: ModelInfo,
    defaults: ModelDefaults,
    expertMode: Boolean,
    useMlockDefault: Boolean,
    flashAttentionModeDefault: String,
    kvCacheTypeDefault: String,
    onDismiss: () -> Unit,
    onSave: (ModelInfo) -> Unit
) {
    var displayName by remember(model.id) { mutableStateOf(model.displayName) }
    // True for GGUF/llama.cpp models — used both inside the Configure dialog body and in the
    // Save action (which lives in a separate scope), so declared at the top of the composable.
    val isLlamaCpp = model.engine == com.vervan.chat.data.db.entities.ModelEngine.LLAMA_CPP
    // Toggle instead of Auto/On/Off (user ask): a model's capability is simply on or off,
    // defaulting to on until the model actually proves otherwise (see reconcileCapabilities).
    // A toggle is only meaningful when the engine can physically deliver the capability —
    // llama.cpp needs an mmproj projector for vision (canSupportVision) and has no audio-input
    // JNI at all (canSupportAudio). When the prerequisite is missing the toggle stays off and
    // renders disabled, instead of letting the user turn on something that can never work.
    val visionSupported = remember(model.id, model.mmprojPath) { model.canSupportVision() }
    val audioSupported = remember(model.id, model.engine) { model.canSupportAudio() }
    var vision by remember(model.id) { mutableStateOf(visionSupported && model.supportsVision != false) }
    var audio by remember(model.id) { mutableStateOf(audioSupported && model.supportsAudio != false) }
    var tools by remember(model.id) { mutableStateOf(model.supportsTools != false) }
    var thinking by remember(model.id) { mutableStateOf(model.supportsThinking != false) }
    var mtpEnabled by remember(model.id) { mutableStateOf(model.mtpEnabled) }
    // llama.cpp has no NPU backend — a stale NPU choice persisted by an older build is shown
    // (and re-saved) as AUTO, which is what the load coordinator resolves it to anyway.
    var backend by remember(model.id) {
        mutableStateOf(
            if (model.engine == com.vervan.chat.data.db.entities.ModelEngine.LLAMA_CPP &&
                model.preferredBackend == BackendChoice.NPU
            ) BackendChoice.AUTO
            else model.preferredBackend
        )
    }
    var approvalMode by remember(model.id) { mutableStateOf(model.toolApprovalMode) }

    // Every generation-default field is "use the app-wide Settings value" until the user
    // flips its own override switch — that's the default-then-customize-per-model model the
    // user asked for, instead of every field silently pinning to whatever it showed on Save.
    var temperatureOn by remember(model.id) { mutableStateOf(model.temperature != null) }
    var temperature by remember(model.id) { mutableStateOf(model.temperature ?: defaults.temperature) }
    var topPOn by remember(model.id) { mutableStateOf(model.topP != null) }
    var topP by remember(model.id) { mutableStateOf(model.topP ?: defaults.topP) }
    var topKOn by remember(model.id) { mutableStateOf(model.topK != null) }
    var topK by remember(model.id) { mutableStateOf((model.topK ?: defaults.topK).toFloat()) }
    var maxImagesOn by remember(model.id) { mutableStateOf(model.maxNumImages != null) }
    var maxImages by remember(model.id) { mutableStateOf((model.maxNumImages ?: defaults.maxNumImages).toFloat()) }
    var contextOn by remember(model.id) { mutableStateOf(model.contextTokens != null) }
    var context by remember(model.id) { mutableStateOf((model.contextTokens ?: defaults.contextTokens).toFloat()) }
    var seedOn by remember(model.id) { mutableStateOf(model.seed != null) }
    var seed by remember(model.id) { mutableStateOf((model.seed ?: 0).toString()) }

    // Common (both engines) — always visible.
    var minPOn by remember(model.id) { mutableStateOf(model.minP != null) }
    var minP by remember(model.id) { mutableStateOf(model.minP ?: defaults.minP) }
    var repetitionPenaltyOn by remember(model.id) { mutableStateOf(model.repetitionPenalty != null) }
    var repetitionPenalty by remember(model.id) { mutableStateOf(model.repetitionPenalty ?: defaults.repetitionPenalty) }
    var maxOutputTokensOn by remember(model.id) { mutableStateOf(model.maxOutputTokens != null) }
    var maxOutputTokens by remember(model.id) { mutableStateOf((model.maxOutputTokens ?: defaults.maxOutputTokens).toFloat()) }
    var stopSequencesOn by remember(model.id) { mutableStateOf(model.stopSequences != null) }
    var stopSequences by remember(model.id) { mutableStateOf(model.stopSequences ?: "") }

    // llama.cpp-only, expert-tier.
    var gpuLayerCountOn by remember(model.id) { mutableStateOf(model.gpuLayerCount != null) }
    var gpuLayerCount by remember(model.id) { mutableStateOf((model.gpuLayerCount ?: (model.layerCount ?: 32)).toFloat()) }
    var cpuThreadsOn by remember(model.id) { mutableStateOf(model.cpuThreads != null) }
    var cpuThreads by remember(model.id) { mutableStateOf((model.cpuThreads ?: defaults.cpuThreads.takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors()).toFloat()) }
    var nBatchOn by remember(model.id) { mutableStateOf(model.nBatch != null) }
    var nBatch by remember(model.id) { mutableStateOf((model.nBatch ?: defaults.nBatch).toFloat()) }
    var nUbatchOn by remember(model.id) { mutableStateOf(model.nUbatch != null) }
    var nUbatch by remember(model.id) { mutableStateOf((model.nUbatch ?: defaults.nUbatch).toFloat()) }
    var useMlockOn by remember(model.id) { mutableStateOf(model.useMlock != null) }
    var useMlock by remember(model.id) { mutableStateOf(model.useMlock ?: useMlockDefault) }
    var flashAttentionOn by remember(model.id) { mutableStateOf(model.flashAttention != null) }
    var flashAttentionMode by remember(model.id) {
        mutableStateOf(model.flashAttention?.let { if (it) "On" else "Off" } ?: flashAttentionModeDefault.lowercase().replaceFirstChar(Char::uppercase))
    }
    var kvCacheTypeOn by remember(model.id) { mutableStateOf(model.kvCacheType != null) }
    var kvCacheType by remember(model.id) { mutableStateOf(model.kvCacheType ?: kvCacheTypeDefault) }
    var vulkanDeviceIndexOn by remember(model.id) { mutableStateOf(model.vulkanDeviceIndex != null) }
    var vulkanDeviceIndex by remember(model.id) { mutableStateOf((model.vulkanDeviceIndex ?: 0).toFloat()) }
    var ropeFreqBaseOn by remember(model.id) { mutableStateOf(model.ropeFreqBase != null) }
    var ropeFreqBase by remember(model.id) { mutableStateOf((model.ropeFreqBase ?: 0f).toString()) }
    var ropeFreqScaleOn by remember(model.id) { mutableStateOf(model.ropeFreqScale != null) }
    var ropeFreqScale by remember(model.id) { mutableStateOf((model.ropeFreqScale ?: 0f).toString()) }
    var chatTemplateOverrideOn by remember(model.id) { mutableStateOf(model.chatTemplateOverride != null) }
    var chatTemplateOverride by remember(model.id) { mutableStateOf(model.chatTemplateOverride ?: "") }
    var loraPath by remember(model.id) { mutableStateOf(model.loraPath) }
    var loraScaleOn by remember(model.id) { mutableStateOf(model.loraScale != null) }
    var loraScale by remember(model.id) { mutableStateOf(model.loraScale ?: 1.0f) }
    var loraError by remember(model.id) { mutableStateOf<String?>(null) }

    val loraApp = LocalContext.current.applicationContext as VervanApp
    val loraScope = rememberCoroutineScope()
    // Unlike LoRA/mmproj, a template is plain text stored in the DB — read the content, no import.
    val pickTemplateFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            loraScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val text = runCatching {
                    loraApp.contentResolver.openInputStream(it)?.use { s -> String(s.readBytes(), Charsets.UTF_8) }
                }.getOrNull()
                if (!text.isNullOrBlank() && text.length <= 128_000) {
                    chatTemplateOverride = text.trim()
                    chatTemplateOverrideOn = true
                }
            }
        }
    }
    // Copies the picked file into internal storage (same reasoning as the mmproj import flow —
    // a content:// Uri isn't a real filesystem path the native loader can fopen) rather than
    // storing the raw picked Uri.
    val pickLoraFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            loraScope.launch {
                when (val result = loraApp.container.modelImportManager.importLoraAdapter(model, it)) {
                    is com.vervan.chat.model.ImportResult.Success -> { loraPath = result.model.loraPath; loraError = null }
                    is com.vervan.chat.model.ImportResult.Rejected -> loraError = result.reason
                    is com.vervan.chat.model.ImportResult.Duplicate -> Unit
                }
            }
        }
    }

    val isGeneration = model.role == ModelRole.GENERATION

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Configure model", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                    },
                    actions = {
                        TextButton(onClick = {
                            onSave(
                                if (isGeneration) {
                                    model.copy(
                                        displayName = displayName.ifBlank { model.displayName }.trim(),
                                        supportsVision = vision,
                                        supportsAudio = audio,
                                        supportsTools = tools,
                                        supportsThinking = thinking,
                                        temperature = temperature.takeIf { temperatureOn },
                                        topP = topP.takeIf { topPOn },
                                        topK = topK.toInt().takeIf { topKOn },
                                        maxNumImages = maxImages.toInt().takeIf { maxImagesOn },
                                        contextTokens = context.toInt().takeIf { contextOn },
                                        mtpEnabled = mtpEnabled,
                                        preferredBackend = backend,
                                        seed = seed.toIntOrNull().takeIf { seedOn },
                                        toolApprovalMode = approvalMode,
                                        minP = minP.takeIf { minPOn },
                                        repetitionPenalty = repetitionPenalty.takeIf { repetitionPenaltyOn },
                                        maxOutputTokens = maxOutputTokens.toInt().takeIf { maxOutputTokensOn },
                                        stopSequences = stopSequences.takeIf { stopSequencesOn },
                                        gpuLayerCount = gpuLayerCount.toInt().takeIf { gpuLayerCountOn },
                                        cpuThreads = cpuThreads.toInt().takeIf { cpuThreadsOn },
                                        nBatch = nBatch.toInt().takeIf { nBatchOn },
                                        nUbatch = nUbatch.toInt().takeIf { nUbatchOn },
                                        useMlock = useMlock.takeIf { useMlockOn },
                                        flashAttention = (when (flashAttentionMode) { "On" -> true; "Off" -> false; else -> null }).takeIf { flashAttentionOn },
                                        kvCacheType = kvCacheType.takeIf { kvCacheTypeOn },
                                        vulkanDeviceIndex = vulkanDeviceIndex.toInt().takeIf { vulkanDeviceIndexOn },
                                        ropeFreqBase = ropeFreqBase.toFloatOrNull().takeIf { ropeFreqBaseOn },
                                        ropeFreqScale = ropeFreqScale.toFloatOrNull().takeIf { ropeFreqScaleOn },
                                        chatTemplateOverride = chatTemplateOverride.takeIf { chatTemplateOverrideOn && chatTemplateOverride.isNotBlank() },
                                        loraPath = loraPath,
                                        loraScale = loraScale.takeIf { loraScaleOn }
                                    )
                                } else {
                                    model.copy(displayName = displayName.ifBlank { model.displayName }.trim())
                                }
                            )
                        }) { Text("Save") }
                    }
                )
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    com.vervan.chat.ui.common.BoundedTextField(
                        value = displayName, onValueChange = { displayName = it }, label = "Display name", singleLine = true,
                        maxLength = com.vervan.chat.ui.common.ValidationLimits.MODEL_DISPLAY_NAME,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    )
                    Text(
                        "Storage: ${model.filePath}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = VervanMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    // Capabilities/generation defaults/tool approval are all properties of text
                    // generation — an embedding model only ever turns text into a vector, so
                    // none of these apply and showing them was pure confusion.
                    if (isGeneration) {
                        SectionLabel("Performance mode")
                        if (expertMode) {
                            // llama.cpp offloads via Vulkan and has no NPU backend, so GGUF
                            // models get Auto/GPU/CPU only.
                            val backendChoices = if (isLlamaCpp) listOf(
                                BackendChoice.AUTO to "Auto", BackendChoice.GPU to "GPU (Vulkan)",
                                BackendChoice.CPU to "CPU"
                            ) else listOf(
                                BackendChoice.AUTO to "Auto", BackendChoice.GPU to "GPU",
                                BackendChoice.CPU to "CPU", BackendChoice.NPU to "NPU"
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                backendChoices.forEach { (choice, label) ->
                                    VervanFilterChip(selected = backend == choice, onClick = { backend = choice }, label = { Text(label) })
                                }
                            }
                            Text(
                                when {
                                    backend == BackendChoice.AUTO && isLlamaCpp -> "Tries Vulkan GPU offload, then falls back to CPU."
                                    backend == BackendChoice.AUTO -> "Tries NPU, then GPU, then falls back to CPU."
                                    else -> "Strict: use ${backend.name} only, with no fallback."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        } else {
                            // Simplified view: a single on/off toggle instead of the full
                            // AUTO/GPU/CPU/NPU chip row — maps straight onto the same
                            // BackendChoice the expert row edits.
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Use GPU acceleration", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = backend != BackendChoice.CPU,
                                    onCheckedChange = { backend = if (it) BackendChoice.GPU else BackendChoice.CPU }
                                )
                            }
                        }
                        if (isLlamaCpp && model.modelDesc != null) {
                            Text(
                                buildString {
                                    append(model.modelDesc)
                                    model.layerCount?.let { append(" · $it layers") }
                                    model.nativeMaxContext?.let { append(" · ${it} native max context") }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        SectionDivider()
                        SectionLabel("Capabilities")
                        CapabilityToggle(
                            "Vision", vision, enabled = visionSupported,
                            disabledHint = if (!visionSupported)
                                "Needs an mmproj projector file — re-import this GGUF with one to enable vision."
                            else null
                        ) { vision = it }
                        CapabilityToggle(
                            "Audio", audio, enabled = audioSupported,
                            disabledHint = if (!audioSupported)
                                "llama.cpp has no audio input in this build."
                            else null
                        ) { audio = it }
                        CapabilityToggle("Tools", tools) { tools = it }
                        CapabilityToggle("Thinking", thinking) { thinking = it }
                        // A load that couldn't actually deliver a capability the user asked for
                        // auto-turns it off here (see reconcileCapabilities) instead of quietly
                        // pretending it still works — surfacing that as a plain fact, not an error.
                        Text(
                            "Turns off if the loaded model cannot support it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                        )

                        if (tools) {
                            SectionDivider()
                            SectionLabel("Tool approval")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    ToolApprovalMode.ALWAYS_ASK to "Always ask",
                                    ToolApprovalMode.AUTO_APPROVE_REVERSIBLE to "Auto (safe writes)",
                                    ToolApprovalMode.AUTO_APPROVE_ALL to "Auto (all)"
                                ).forEach { (mode, label) ->
                                    VervanFilterChip(selected = approvalMode == mode, onClick = { approvalMode = mode }, label = { Text(label) })
                                }
                            }
                        }

                        // No MTP equivalent wired up for llama.cpp in this pass.
                        if (!isLlamaCpp) {
                            SectionDivider()
                            SectionLabel("Speculative decoding (MTP)")
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when (model.mtpSupported) {
                                        false -> "Last attempt failed. Turn on to retry at the next load."
                                        true -> "Speeds up generation on GPU; no effect on CPU/NPU."
                                        null -> "Tried automatically on load; auto-disabled if unsupported."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Switch(checked = mtpEnabled, onCheckedChange = { mtpEnabled = it })
                            }
                        }

                        SectionDivider()
                        SectionLabel("Generation defaults")
                        if (expertMode) {
                        Text(
                            "Raw per-model overrides. Disabled values use the app default.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OverrideSlider("Temperature", temperatureOn, { temperatureOn = it }, temperature, { temperature = it }, defaults.temperature, "%.2f", 0f..2f)
                        OverrideSlider("Top-p", topPOn, { topPOn = it }, topP, { topP = it }, defaults.topP, "%.2f", 0.1f..1f)
                        OverrideSlider("Top-k", topKOn, { topKOn = it }, topK, { topK = it }, defaults.topK.toFloat(), "%.0f", 1f..64f)
                        OverrideSlider("Min-p", minPOn, { minPOn = it }, minP, { minP = it }, defaults.minP, "%.2f", 0f..1f)
                        OverrideSlider("Repetition penalty", repetitionPenaltyOn, { repetitionPenaltyOn = it }, repetitionPenalty, { repetitionPenalty = it }, defaults.repetitionPenalty, "%.2f", 1f..2f)
                        OverrideSlider("Max output tokens", maxOutputTokensOn, { maxOutputTokensOn = it }, maxOutputTokens, { maxOutputTokens = it }, defaults.maxOutputTokens.toFloat(), "%.0f", 64f..4096f, steps = 20)
                        OverrideSlider("Max images", maxImagesOn, { maxImagesOn = it }, maxImages, { maxImages = it }, defaults.maxNumImages.toFloat(), "%.0f", 1f..4f)
                        OverrideSlider(
                            "Context length", contextOn, { contextOn = it }, context, { context = it }, defaults.contextTokens.toFloat(),
                            "%.0f", 1024f..(model.nativeMaxContext?.toFloat() ?: 32768f), steps = 30
                        )
                        OverrideField(
                            "Stop sequences", stopSequencesOn, { stopSequencesOn = it }, stopSequences, { stopSequences = it },
                            "None", singleLine = false
                        )
                        OverrideField("Seed", seedOn, { seedOn = it }, seed, { seed = it.filter(Char::isDigit) }, "Random")
                        if (seedOn) {
                            TextButton(onClick = { seed = kotlin.random.Random.nextInt(0, Int.MAX_VALUE).toString() }) { Text("Randomize") }
                        }
                        } else {
                            Text(
                                "Simple controls for this model. Choose Default to follow the app settings.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text("Response style", style = MaterialTheme.typography.titleSmall)
                            val styleChoice = when {
                                !temperatureOn -> "DEFAULT"
                                temperature <= 0.45f -> "FOCUSED"
                                temperature >= 1.05f -> "CREATIVE"
                                else -> "BALANCED"
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("DEFAULT" to "Default", "FOCUSED" to "Focused", "BALANCED" to "Balanced", "CREATIVE" to "Creative").forEach { (id, label) ->
                                    VervanFilterChip(selected = styleChoice == id, onClick = {
                                        if (id == "DEFAULT") {
                                            temperatureOn = false; topPOn = false; topKOn = false; minPOn = false; repetitionPenaltyOn = false
                                        } else {
                                            temperatureOn = true; topPOn = true; topKOn = true; minPOn = true; repetitionPenaltyOn = true
                                            when (id) {
                                                "FOCUSED" -> { temperature = 0.3f; topP = 0.85f; topK = 24f; minP = 0.08f; repetitionPenalty = 1.12f }
                                                "CREATIVE" -> { temperature = 1.15f; topP = 0.98f; topK = 56f; minP = 0.03f; repetitionPenalty = 1.05f }
                                                else -> { temperature = 0.8f; topP = 0.95f; topK = 40f; minP = 0.05f; repetitionPenalty = 1.1f }
                                            }
                                        }
                                    }, label = { Text(label) })
                                }
                            }

                            Text("Response size", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
                            val sizeChoice = when {
                                !maxOutputTokensOn -> "DEFAULT"
                                maxOutputTokens <= 320f -> "SHORT"
                                maxOutputTokens >= 900f -> "LONG"
                                else -> "STANDARD"
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("DEFAULT" to "Default", "SHORT" to "Short", "STANDARD" to "Standard", "LONG" to "Long").forEach { (id, label) ->
                                    VervanFilterChip(selected = sizeChoice == id, onClick = {
                                        maxOutputTokensOn = id != "DEFAULT"
                                        maxOutputTokens = when (id) { "SHORT" -> 256f; "LONG" -> 1024f; else -> 512f }
                                    }, label = { Text(label) })
                                }
                            }

                            Text("Conversation memory", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
                            val memoryChoice = if (!contextOn) 0 else context.toInt()
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0 to "Default", 4096 to "Standard", 8192 to "More", 16384 to "Maximum").forEach { (tokens, label) ->
                                    VervanFilterChip(selected = memoryChoice == tokens, onClick = {
                                        contextOn = tokens != 0
                                        if (tokens != 0) context = tokens.toFloat().coerceAtMost(model.nativeMaxContext?.toFloat() ?: 32768f)
                                    }, label = { Text(label) })
                                }
                            }
                            Text(
                                "More memory keeps a longer conversation but uses more RAM.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        if (isLlamaCpp && expertMode) {
                            SectionDivider()
                            SectionLabel("Advanced (llama.cpp)")
                            // GPU layers: default (override off) = offload the whole model on
                            // GPU/Auto; 0 keeps this model on CPU even under Auto.
                            run {
                                val maxGpuLayers = ((model.layerCount ?: 32) + 1).toFloat()
                                OverrideSlider(
                                    "GPU layers (Vulkan)", gpuLayerCountOn, { gpuLayerCountOn = it },
                                    gpuLayerCount.coerceIn(0f, maxGpuLayers), { gpuLayerCount = it },
                                    maxGpuLayers, "%.0f", 0f..maxGpuLayers
                                )
                            }
                            OverrideSlider(
                                "Vulkan device index", vulkanDeviceIndexOn, { vulkanDeviceIndexOn = it },
                                vulkanDeviceIndex, { vulkanDeviceIndex = it }, 0f, "%.0f", 0f..3f, steps = 2
                            )
                            OverrideSlider(
                                "CPU threads", cpuThreadsOn, { cpuThreadsOn = it }, cpuThreads, { cpuThreads = it },
                                Runtime.getRuntime().availableProcessors().toFloat(), "%.0f", 1f..16f
                            )
                            OverrideSlider("Batch size (n_batch)", nBatchOn, { nBatchOn = it }, nBatch, { nBatch = it }, defaults.nBatch.toFloat(), "%.0f", 128f..4096f, steps = 30)
                            OverrideSlider("Physical batch size (n_ubatch)", nUbatchOn, { nUbatchOn = it }, nUbatch, { nUbatch = it }, defaults.nUbatch.toFloat(), "%.0f", 32f..2048f, steps = 30)
                            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Lock model in RAM (mlock)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    if (useMlockOn) (if (useMlock) "On" else "Off") else "Default (${if (useMlockDefault) "On" else "Off"})",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                                Switch(checked = useMlockOn, onCheckedChange = { useMlockOn = it })
                            }
                            if (useMlockOn) {
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Enabled", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Switch(checked = useMlock, onCheckedChange = { useMlock = it })
                                }
                            }
                            OverrideDropdown(
                                "Flash attention", flashAttentionOn, { flashAttentionOn = it }, flashAttentionMode,
                                { flashAttentionMode = it }, listOf("Auto", "On", "Off"),
                                defaultValue = flashAttentionModeDefault.lowercase().replaceFirstChar(Char::uppercase)
                            )
                            OverrideDropdown(
                                "KV cache type", kvCacheTypeOn, { kvCacheTypeOn = it }, kvCacheType,
                                { kvCacheType = it }, listOf("f16", "q8_0", "q4_0"), defaultValue = kvCacheTypeDefault
                            )
                            OverrideField("RoPE freq base", ropeFreqBaseOn, { ropeFreqBaseOn = it }, ropeFreqBase, { ropeFreqBase = it.filter { c -> c.isDigit() || c == '.' } }, "From model")
                            OverrideField("RoPE freq scale", ropeFreqScaleOn, { ropeFreqScaleOn = it }, ropeFreqScale, { ropeFreqScale = it.filter { c -> c.isDigit() || c == '.' } }, "From model")
                            OverrideField(
                                "Chat template override", chatTemplateOverrideOn, { chatTemplateOverrideOn = it }, chatTemplateOverride,
                                { chatTemplateOverride = it }, "From model (embedded)", singleLine = false
                            )
                            if (chatTemplateOverrideOn) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Tap a preset, paste Jinja text above, or load a template file.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f).padding(top = 2.dp)
                                    )
                                    TextButton(onClick = { pickTemplateFile.launch(arrayOf("*/*")) }) { Text("From file") }
                                }
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    com.vervan.chat.llm.LlamaCppEngine.builtinChatTemplates.forEach { name ->
                                        VervanFilterChip(
                                            selected = chatTemplateOverride == name,
                                            onClick = { chatTemplateOverride = name },
                                            label = { Text(name) }
                                        )
                                    }
                                }
                            }

                            SectionDivider()
                            SectionLabel("LoRA adapter")
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    loraPath?.let { File(it).name } ?: "None attached",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { pickLoraFile.launch(arrayOf("*/*")) }) { Text(if (loraPath != null) "Replace" else "Attach") }
                                if (loraPath != null) {
                                    TextButton(onClick = { loraPath = null }) { Text("Remove") }
                                }
                            }
                            loraError?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                            }
                            if (loraPath != null) {
                                OverrideSlider("LoRA scale", loraScaleOn, { loraScaleOn = it }, loraScale, { loraScale = it }, 1.0f, "%.2f", 0f..2f)
                            }
                        }
                    } else {
                        Text(
                            "Embedding models power semantic search and have no generation settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(top = 10.dp), color = vervanSubtleDividerColor())
}

@Composable
private fun CapabilityToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    disabledHint: String? = null,
    onChange: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
        }
        if (!enabled && disabledHint != null) {
            Text(
                disabledHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** A generation-default slider that's either "inherit the app-wide Settings value" (off, shown
 * disabled at the default) or "override for this model" (on, editable) — all such fields share
 * this exact label/value/switch layout so they read as one consistent, aligned group. */
@Composable
private fun OverrideSlider(
    label: String,
    override: Boolean,
    onOverrideChange: (Boolean) -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    defaultValue: Float,
    format: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (override) String.format(format, value) else "Default (${String.format(format, defaultValue)})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp)
            )
            Switch(checked = override, onCheckedChange = onOverrideChange)
        }
        val effectiveValue = if (override) value else defaultValue
        Slider(
            value = effectiveValue,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = override,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).semantics {
                contentDescription = "$label, ${String.format(format, effectiveValue)}"
            }
        )
    }
}

/** Same override pattern as [OverrideSlider] but for a free-form numeric field (seed has no
 * meaningful "scale" to slide). */
@Composable
private fun OverrideField(
    label: String,
    override: Boolean,
    onOverrideChange: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    defaultLabel: String,
    singleLine: Boolean = true
) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (!override) {
                Text(
                    defaultLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp)
                )
            }
            Switch(checked = override, onCheckedChange = onOverrideChange)
        }
        if (override) {
            OutlinedTextField(
                value, onValueChange, singleLine = singleLine,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}

/** Same override-switch header as [OverrideSlider]/[OverrideField], but for a fixed set of
 * string choices (KV cache type, flash-attention Auto/On/Off) instead of a numeric range. */
@Composable
private fun OverrideDropdown(
    label: String,
    override: Boolean,
    onOverrideChange: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    defaultValue: String
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (override) value else "Default ($defaultValue)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp)
            )
            Switch(checked = override, onCheckedChange = onOverrideChange)
        }
        if (override) {
            Box(Modifier.padding(top = 6.dp)) {
                TextButton(onClick = { expanded = true }) { Text(value) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { onValueChange(option); expanded = false })
                    }
                }
            }
        }
    }
}

private fun formatModelSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GiB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024 * 1024 -> String.format("%.1f MiB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format("%.0f KiB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun ModelInfo.runtimeSummary(): String {
    val runtime = when (engine) {
        ModelEngine.LITERT_LM -> "LiteRT-LM"
        ModelEngine.LLAMA_CPP -> "llama.cpp"
    }
    val hardware = when (preferredBackend) {
        BackendChoice.AUTO -> if (engine == ModelEngine.LLAMA_CPP) "Auto: Vulkan → CPU" else "Auto: NPU → GPU → CPU"
        BackendChoice.GPU -> if (engine == ModelEngine.LLAMA_CPP) "Vulkan GPU" else "GPU"
        BackendChoice.CPU -> "CPU"
        BackendChoice.NPU -> "NPU"
    }
    return "$runtime • $hardware"
}

/** "Available for Download" — collapsed by default, grouped by category, each category
 * independently expandable (spec §3.2). */
@Composable
private fun AvailableForDownloadSection(
    states: List<com.vervan.chat.modeldownload.ModelUiState>,
    onDownload: (com.vervan.chat.modeldownload.ModelUiState) -> Unit
) {
    var sectionExpanded by rememberSaveable("available_models") { mutableStateOf(false) }
    SectionHeader("Discover models", Icons.Filled.CloudDownload)
    Card(
        onClick = { sectionExpanded = !sectionExpanded },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
    ) {
        Row(Modifier.fillMaxWidth().padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.padding(Space.md).size(24.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                Text("Available for download", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${states.size} curated model${if (states.size == 1) "" else "s"} · Categories stay collapsed until you open them",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (sectionExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (sectionExpanded) "Collapse available models" else "Expand available models"
            )
        }
    }

    if (sectionExpanded) {
        states.groupBy { it.category }.forEach { (category, entries) ->
            var expanded by rememberSaveable("catalog_${category.name}") { mutableStateOf(false) }
            Card(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = vervanBorder()
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        categoryIcon(category),
                        contentDescription = null,
                        tint = categoryAccent(category)
                    )
                    Text(
                        categoryLabel(category),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f).padding(start = Space.md)
                    )
                    SemanticChip("${entries.size}", ChipTone.Neutral)
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse category" else "Expand category",
                        modifier = Modifier.padding(start = Space.sm)
                    )
                }
            }
            if (expanded) {
                entries.forEach { entry -> CatalogEntryCard(entry, onDownload = { onDownload(entry) }) }
            }
        }
    }
}

private fun categoryIcon(category: ModelRole) = when (category) {
    ModelRole.GENERATION -> Icons.Filled.Bolt
    ModelRole.EMBEDDING -> Icons.Outlined.Storage
    ModelRole.TTS_VOICE -> Icons.Filled.GraphicEq
    ModelRole.STT_MODEL -> Icons.Filled.Mic
}

private fun categoryLabel(category: ModelRole) = when (category) {
    ModelRole.GENERATION -> "Generation"
    ModelRole.EMBEDDING -> "Embedding"
    ModelRole.TTS_VOICE -> "Voice model"
    ModelRole.STT_MODEL -> "Speech-to-text"
}

@Composable
private fun categoryAccent(category: ModelRole) = when (category) {
    ModelRole.GENERATION -> MaterialTheme.colorScheme.primary
    ModelRole.EMBEDDING -> MaterialTheme.colorScheme.secondary
    ModelRole.TTS_VOICE, ModelRole.STT_MODEL -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun categoryAccentContainer(category: ModelRole) = when (category) {
    ModelRole.GENERATION -> MaterialTheme.colorScheme.primaryContainer
    ModelRole.EMBEDDING -> MaterialTheme.colorScheme.secondaryContainer
    ModelRole.TTS_VOICE, ModelRole.STT_MODEL -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun CatalogEntryCard(state: com.vervan.chat.modeldownload.ModelUiState, onDownload: () -> Unit) {
    val accent = categoryAccent(state.category)
    val accentContainer = categoryAccentContainer(state.category)
    Card(
        Modifier.fillMaxWidth().padding(top = Space.sm),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = accentContainer.copy(alpha = 0.18f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(Space.lg)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.large, color = accentContainer, contentColor = accent) {
                    Icon(
                        categoryIcon(state.category),
                        contentDescription = null,
                        modifier = Modifier.padding(Space.md).size(24.dp)
                    )
                }
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    Text(state.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "v${state.version} · ${categoryLabel(state.category)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                }
            }

            if (state.description.isNotBlank()) {
                Text(
                    state.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md)
                )
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(top = Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                state.totalBytes?.let { SemanticChip("~${formatModelSize(it)}", ChipTone.Neutral) }
                state.precision?.let { SemanticChip(it, ChipTone.Neutral) }
                state.minimumRamBytes?.let { SemanticChip("${formatModelSize(it)} RAM", ChipTone.Neutral) }
                SemanticChip("${state.totalFileCount} file${if (state.totalFileCount == 1) "" else "s"}", ChipTone.Neutral)
                if (state.requiresAuthToken) SemanticChip("Access token", ChipTone.Warning)
                if (state.requiresLicenseAcceptance) SemanticChip("License", ChipTone.Warning)
                state.capabilities.forEach { SemanticChip(it, ChipTone.Neutral) }
            }
            if (state.sourceName.isNotBlank()) {
                Text(
                    "Source: ${state.sourceName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md)
                )
            }
            ResponsiveActions(Modifier.padding(top = Space.lg)) {
                Button(onClick = onDownload) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Download", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** One card per actively downloading/paused/failed package — expands to per-file detail (spec
 * §20). Progress is always derived from bytes (never a stored percentage), and only rendered as
 * determinate once a total is actually known. */
@Composable
private fun DownloadPackageCard(
    state: com.vervan.chat.modeldownload.ModelUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(state.packageId) { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val tone = downloadStatusTone(state.status)
    val statusColor = downloadStatusColor(state.status)
    val progress = state.totalBytes?.takeIf { it > 0L }?.let {
        (state.downloadedBytes.toFloat() / it.toFloat()).coerceIn(0f, 1f)
    }

    Card(
        Modifier.fillMaxWidth().padding(bottom = Space.md),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.32f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(Space.lg)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = statusColor.copy(alpha = 0.14f),
                    contentColor = statusColor
                ) {
                    Icon(
                        downloadStatusIcon(state.status),
                        contentDescription = null,
                        modifier = Modifier.padding(Space.md).size(24.dp)
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                    Text(state.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "v${state.version} · ${categoryLabel(state.category)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.files.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Hide download details" else "Show download details"
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = Space.md),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f).padding(end = Space.md)) {
                    Text(statusLabel(state), style = MaterialTheme.typography.labelMedium, color = statusColor)
                    state.currentFileName?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs)
                        )
                    }
                }
                SemanticChip(statusChipLabel(state.status), tone)
            }

            if (state.status != ModelStatus.QUEUED) {
                if (state.status in setOf(ModelStatus.PREPARING, ModelStatus.DOWNLOADED, ModelStatus.VERIFYING, ModelStatus.IMPORTING, ModelStatus.PAUSING, ModelStatus.CANCELLING, ModelStatus.DELETING)) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                        color = statusColor,
                        trackColor = statusColor.copy(alpha = 0.12f)
                    )
                } else if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                        color = statusColor,
                        trackColor = statusColor.copy(alpha = 0.12f)
                    )
                } else if (state.status !in setOf(ModelStatus.PAUSED, ModelStatus.FAILED)) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                        color = statusColor,
                        trackColor = statusColor.copy(alpha = 0.12f)
                    )
                }
            }

            if (state.status == ModelStatus.READY && state.installedSizeBytes != null) {
                Column(Modifier.fillMaxWidth().padding(top = Space.sm)) {
                    DownloadFactRow("Installed size", formatModelSize(state.installedSizeBytes))
                }
            } else if (state.downloadedBytes > 0L || state.status == ModelStatus.DOWNLOADING || state.status == ModelStatus.PAUSED) {
                Column(Modifier.fillMaxWidth().padding(top = Space.sm)) {
                    DownloadFactRow(
                        "Downloaded",
                        buildString {
                            append(formatModelSize(state.downloadedBytes))
                            state.totalBytes?.let { append(" / ${formatModelSize(it)}") }
                        },
                        "Progress",
                        progress?.let { "${(it * 100).toInt()}%" } ?: "—"
                    )
                    if (state.status == ModelStatus.DOWNLOADING) {
                        DownloadFactRow(
                            "Speed",
                            state.speedBytesPerSecond?.takeIf { it > 0 }?.let { "${formatModelSize(it)}/s" } ?: "Calculating…",
                            "Time left",
                            state.estimatedRemainingSeconds?.let(::formatEta) ?: "Calculating…"
                        )
                    }
                    if (state.totalFileCount > 1) {
                        DownloadFactRow("Files", "${state.completedFileCount} / ${state.totalFileCount}")
                    }
                }
            }

            state.error?.let {
                ValidationMessage(it.message.ifBlank { it.code.name }.toUserMessage(), modifier = Modifier.padding(top = Space.md))
            }

            if (expanded) {
                Column(Modifier.padding(top = Space.md)) {
                    HorizontalDivider(color = vervanSubtleDividerColor())
                    Text(
                        "Package files · ${state.completedFileCount} of ${state.totalFileCount} complete",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = Space.md, bottom = Space.xs)
                    )
                    state.files.forEach { file ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (file.status == FileDownloadStatus.COMPLETED) Icons.Filled.CheckCircle else Icons.Filled.CloudDownload,
                                contentDescription = null,
                                tint = if (file.status == FileDownloadStatus.COMPLETED) MaterialTheme.colorScheme.vervanSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(Modifier.weight(1f).padding(start = Space.sm)) {
                                Text(file.fileName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    fileStatusLabel(file),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (file.status == FileDownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (state.status in setOf(ModelStatus.PAUSING, ModelStatus.CANCELLING, ModelStatus.DELETING)) {
                Row(Modifier.padding(top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = statusColor)
                    Text(
                        "Stopping the transfer — this finishes in a moment.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.sm)
                    )
                }
            }
            ResponsiveActions(Modifier.padding(top = Space.lg)) {
                if (ModelAction.PAUSE in state.allowedActions) {
                    TextButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Pause", modifier = Modifier.padding(start = Space.xs))
                    }
                }
                if (ModelAction.RESUME in state.allowedActions) {
                    Button(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Resume", modifier = Modifier.padding(start = Space.xs))
                    }
                }
                if (ModelAction.CANCEL in state.allowedActions) {
                    TextButton(onClick = { confirmStop = true }) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Stop", modifier = Modifier.padding(start = Space.xs))
                    }
                }
                if (ModelAction.DELETE in state.allowedActions) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Delete", modifier = Modifier.padding(start = Space.xs))
                    }
                }
            }
        }
    }

    if (confirmStop) {
        ConfirmDialog(
            title = "Stop downloading?",
            body = "This removes the download and partial file. Pause to resume later.",
            confirmLabel = "Stop",
            destructive = true,
            onConfirm = { confirmStop = false; onStop() },
            onDismiss = { confirmStop = false }
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = if (state.status == ModelStatus.READY) "Delete downloaded voice?" else "Delete partial download?",
            body = "Remove downloaded data for \"${state.displayName}\"?",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun DownloadFactRow(
    label: String,
    value: String,
    trailingLabel: String? = null,
    trailingValue: String? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.xs),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f).padding(end = Space.sm)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium, fontFamily = VervanMono)
        }
        if (trailingLabel != null && trailingValue != null) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(trailingLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(trailingValue, style = MaterialTheme.typography.labelMedium, fontFamily = VervanMono)
            }
        } else {
            Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun downloadStatusColor(status: ModelStatus): Color = when (status) {
    ModelStatus.FAILED -> MaterialTheme.colorScheme.error
    ModelStatus.PAUSED, ModelStatus.WAITING_FOR_NETWORK, ModelStatus.WAITING_FOR_WIFI, ModelStatus.WAITING_FOR_STORAGE ->
        MaterialTheme.colorScheme.tertiary
    ModelStatus.DOWNLOADED, ModelStatus.VERIFYING, ModelStatus.IMPORTING, ModelStatus.READY ->
        MaterialTheme.colorScheme.vervanSuccess
    else -> MaterialTheme.colorScheme.primary
}

private fun downloadStatusTone(status: ModelStatus): ChipTone = when (status) {
    ModelStatus.FAILED -> ChipTone.Error
    ModelStatus.PAUSED, ModelStatus.WAITING_FOR_NETWORK, ModelStatus.WAITING_FOR_WIFI, ModelStatus.WAITING_FOR_STORAGE -> ChipTone.Warning
    ModelStatus.DOWNLOADED, ModelStatus.VERIFYING, ModelStatus.IMPORTING, ModelStatus.READY -> ChipTone.Success
    else -> ChipTone.Neutral
}

private fun downloadStatusIcon(status: ModelStatus) = when (status) {
    ModelStatus.FAILED -> Icons.Filled.Warning
    ModelStatus.PAUSED -> Icons.Filled.Pause
    ModelStatus.DOWNLOADED, ModelStatus.VERIFYING, ModelStatus.IMPORTING, ModelStatus.READY -> Icons.Filled.CheckCircle
    else -> Icons.Filled.CloudDownload
}

private fun fileStatusLabel(file: com.vervan.chat.modeldownload.ModelFileUiState): String = when (file.status) {
    FileDownloadStatus.COMPLETED -> "Complete"
    FileDownloadStatus.DOWNLOADING -> "${formatModelSize(file.downloadedBytes)}${file.totalBytes?.let { " / ${formatModelSize(it)}" } ?: ""}"
    FileDownloadStatus.FAILED -> file.errorMessage?.ifBlank { null } ?: "Failed"
    FileDownloadStatus.PAUSED -> "Paused"
    FileDownloadStatus.WAITING_FOR_NETWORK -> "Waiting for network"
    FileDownloadStatus.NOT_STARTED -> "Waiting"
}

private fun statusChipLabel(status: ModelStatus): String = when (status) {
    ModelStatus.WAITING_FOR_NETWORK -> "No network"
    ModelStatus.WAITING_FOR_WIFI -> "Wi-Fi needed"
    ModelStatus.WAITING_FOR_STORAGE -> "Storage needed"
    ModelStatus.DOWNLOADED -> "Downloaded"
    ModelStatus.VERIFYING -> "Verifying"
    ModelStatus.IMPORTING -> "Installing"
    else -> status.name.lowercase().replaceFirstChar(Char::titlecase)
}

private fun statusLabel(state: ModelUiState): String = when (state.status) {
    ModelStatus.QUEUED -> "Queued"
    ModelStatus.PREPARING -> "Preparing…"
    ModelStatus.WAITING_FOR_NETWORK -> "Waiting for network"
    ModelStatus.WAITING_FOR_WIFI -> "Waiting for Wi-Fi"
    ModelStatus.WAITING_FOR_STORAGE -> "Waiting for storage"
    ModelStatus.DOWNLOADING -> if (state.totalFileCount > 1) {
        "Downloading file ${state.completedFileCount + 1} of ${state.totalFileCount}"
    } else "Downloading"
    ModelStatus.PAUSING -> "Pausing…"
    ModelStatus.PAUSED -> "Paused"
    ModelStatus.DOWNLOADED -> "Download complete — preparing verification"
    ModelStatus.VERIFYING -> "Verifying…"
    ModelStatus.IMPORTING -> "Importing…"
    ModelStatus.READY -> "Ready"
    ModelStatus.CANCELLING -> "Cancelling…"
    ModelStatus.CANCELLED -> "Cancelled"
    ModelStatus.FAILED -> "Failed"
    ModelStatus.DELETING -> "Deleting…"
    ModelStatus.NOT_DOWNLOADED -> "Not downloaded"
}

private fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "$seconds sec"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}

/** Navigation affordance for the curated Model Store. */
@Composable
private fun StoreEntryCard(onOpenStore: () -> Unit) {
    com.vervan.chat.ui.common.ContentCard {
        Column(Modifier.padding(Space.lg)) {
            Text("Model Store", style = MaterialTheme.typography.titleSmall)
            Box(Modifier.height(Space.xs))
            Text(
                "Browse the curated catalogue of models reviewed for this app. Weights download " +
                    "directly from their publisher.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(Modifier.height(Space.sm))
            Button(onClick = onOpenStore) { Text("Open Model Store") }
        }
    }
}
