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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.ModelStatus
import com.vervan.chat.data.db.entities.ToolApprovalMode
import com.vervan.chat.data.db.entities.displayName
import com.vervan.chat.modeldownload.ModelAction
import com.vervan.chat.modeldownload.ModelUiState
import com.vervan.chat.ui.common.ChipTone
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.SemanticChip
import com.vervan.chat.ui.common.ValidationMessage
import com.vervan.chat.ui.theme.VervanMono
import com.vervan.chat.ui.theme.vervanSuccess
import com.vervan.chat.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelManagerScreen(onBack: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: ModelManagerViewModel = viewModel(factory = viewModelFactory {
        initializer { ModelManagerViewModel(app) }
    })
    val models by vm.models.collectAsStateWithLifecycle()
    val defaults by vm.defaults.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val busyModelId by vm.busyModelId.collectAsStateWithLifecycle()
    val busyLabel by vm.busyLabel.collectAsStateWithLifecycle()
    val pendingAcknowledgment by vm.pendingAcknowledgment.collectAsStateWithLifecycle()
    val pendingMigration by vm.pendingMigration.collectAsStateWithLifecycle()
    val loadedGenerationPath by vm.loadedGenerationPath.collectAsStateWithLifecycle()
    val loadedEmbeddingPath by vm.loadedEmbeddingPath.collectAsStateWithLifecycle()
    val downloadStates by vm.downloadStates.collectAsStateWithLifecycle()
    var editingModel by remember { mutableStateOf<ModelInfo?>(null) }
    // LaunchedEffect(Unit) only fires once per composable instance — with save/restoreState
    // navigation (bottom-nav style), returning to this screen reuses that same instance, so a
    // model loaded/unloaded from Chat while this screen sat in the backstack never refreshed
    // here (root cause of "Models screen shows the wrong Load/Unload button"). Re-check on
    // every real return to the foreground instead of just the first-ever composition.
    val resumeTick = com.vervan.chat.ui.common.rememberOnResumeTick()
    LaunchedEffect(resumeTick) { vm.refreshLoadedState() }

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
                val generationReady = loadedGenerationPath != null
                val embeddingReady = loadedEmbeddingPath != null
                val (tone, title, body) = when {
                    generationReady && embeddingReady -> Triple(
                        com.vervan.chat.ui.common.StatusTone.Ready, "Ready",
                        "A generation model and an embedding model are both loaded."
                    )
                    generationReady -> Triple(
                        com.vervan.chat.ui.common.StatusTone.Ready, "Chat ready",
                        "A generation model is loaded. No embedding model loaded — semantic search falls back to keyword search."
                    )
                    generationModels.isEmpty() -> Triple(
                        com.vervan.chat.ui.common.StatusTone.Warning, "No generation model",
                        "Import a generation model below before starting a chat."
                    )
                    else -> Triple(
                        com.vervan.chat.ui.common.StatusTone.Info, "Not loaded",
                        "${generationModels.size} generation model(s) installed but none is loaded yet — load one to start chatting."
                    )
                }
                com.vervan.chat.ui.common.SystemStatusStrip(
                    title = title,
                    body = body,
                    tone = tone,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            val downloadingStates = downloadStates.filter { it.status != com.vervan.chat.data.db.entities.ModelStatus.NOT_DOWNLOADED }
            val catalogStates = downloadStates.filter { it.status == com.vervan.chat.data.db.entities.ModelStatus.NOT_DOWNLOADED }

            if (downloadingStates.isNotEmpty()) {
                SectionHeader("Active downloads", Icons.Filled.CloudDownload)
                downloadingStates.forEach { state ->
                    DownloadPackageCard(
                        state = state,
                        onPause = { vm.pauseDownload(state.modelId, state.version) },
                        onResume = { vm.resumeDownload(state.modelId, state.version) },
                        onRetry = { vm.retryDownload(state.modelId, state.version) },
                        onRemove = { vm.cancelDownload(state.modelId, state.version, keepPartial = false) },
                        onStopKeepPartial = { vm.cancelDownload(state.modelId, state.version, keepPartial = true) },
                        onStopDeletePartial = { vm.cancelDownload(state.modelId, state.version, keepPartial = false) }
                    )
                }
            }

            if (catalogStates.isNotEmpty()) {
                AvailableForDownloadSection(catalogStates, onDownload = { vm.downloadModel(it.modelId, it.version) })
            }

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
                }
            )

            if (importing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }

            if (generationModels.isNotEmpty()) {
                SectionHeader("Generation models", Icons.Filled.Bolt)
                generationModels.forEach { model ->
                    ModelCard(
                        model,
                        isLoaded = loadedGenerationPath == model.filePath,
                        showSetActive = generationModels.size > 1,
                        onSetActive = { vm.setActive(model) },
                        onToggleLoad = { if (loadedGenerationPath == model.filePath) vm.unload(model) else vm.load(model) },
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
                    "Loaded and unloaded automatically wherever semantic search is used — you don't need to load these by hand.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                embeddingModels.forEach { model ->
                    ModelCard(
                        model,
                        isLoaded = loadedEmbeddingPath == model.filePath,
                        showSetActive = embeddingModels.size > 1,
                        onSetActive = { vm.setActive(model) },
                        onToggleLoad = { if (loadedEmbeddingPath == model.filePath) vm.unload(model) else vm.load(model) },
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

    if (confirmBulkDelete) {
        ConfirmDialog(
            title = "Delete selected models forever?",
            body = "Permanently remove ${selectedIds.size} model file${if (selectedIds.size == 1) "" else "s"} from this device? This can't be undone.",
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
                    "\"${model.displayName}\" was brought in from a file you picked — this app didn't fetch or verify it, " +
                        "so it can't show you its actual license terms. Model weights are typically covered by their " +
                        "publisher's own license (for example Gemma's usage terms on Hugging Face or Kaggle). " +
                        "You're responsible for having the right to use this model under whatever terms apply to it."
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
                    "\"${newModel.displayName}\" looks like a new version of \"${previous.displayName}\". " +
                        "Make it the default and update folders that default to the old one? " +
                        "\"${previous.displayName}\" stays installed either way — existing chats keep using " +
                        "whichever model they already used."
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

/** Modernized entry point: two clearly-labeled cards instead of a plain button pair, with a
 * short explanation of what each model type is for right where the choice is made. */
@Composable
private fun ImportCard(importing: Boolean, onImport: (ModelRole) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Import a model", style = MaterialTheme.typography.titleSmall)
            Text(
                "Bring in a .task, .litertlm, or .litert package from your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ImportChoiceCard(
                    title = "Generation model",
                    subtitle = "Chat, write, reason",
                    icon = Icons.Filled.Bolt,
                    enabled = !importing,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onImport(ModelRole.GENERATION) }
                )
                ImportChoiceCard(
                    title = "Embedding model",
                    subtitle = "Select model + tokenizer file",
                    icon = Icons.Outlined.Storage,
                    enabled = !importing,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onImport(ModelRole.EMBEDDING) }
                )
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
                    "Two files are required: the embedding model, and its SentencePiece tokenizer " +
                        "(e.g. sentencepiece.model / tokenizer.model) — the model file alone can't be tokenized.",
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
                    tokenizerUri == null -> "The tokenizer file (sentencepiece.model) is required — select it before importing."
                    else -> null
                }
                if (modelUri != null && tokenizerUri != null) onImport(modelUri, tokenizerUri)
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
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .combinedClickable(onClick = { if (selectionMode) onToggleSelect() }, onLongClick = onLongPress),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatModelSize(model.fileSizeBytes), style = MaterialTheme.typography.labelSmall,
                                fontFamily = VervanMono, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (model.role == ModelRole.GENERATION) {
                                Text(
                                    " · ${model.preferredBackend.name}", style = MaterialTheme.typography.labelSmall,
                                    fontFamily = VervanMono, color = MaterialTheme.colorScheme.primary
                                )
                            }
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
            title = "Delete model forever?",
            body = "\"${model.displayName}\" will be permanently removed from this device. This can't be undone.",
            confirmLabel = "Delete forever",
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ModelEditDialog(model: ModelInfo, defaults: ModelDefaults, onDismiss: () -> Unit, onSave: (ModelInfo) -> Unit) {
    var displayName by remember(model.id) { mutableStateOf(model.displayName) }
    // Toggle instead of Auto/On/Off (user ask): a model's capability is simply on or off,
    // defaulting to on until the model actually proves otherwise (see reconcileCapabilities).
    var vision by remember(model.id) { mutableStateOf(model.supportsVision != false) }
    var audio by remember(model.id) { mutableStateOf(model.supportsAudio != false) }
    var tools by remember(model.id) { mutableStateOf(model.supportsTools != false) }
    var thinking by remember(model.id) { mutableStateOf(model.supportsThinking != false) }
    var mtpEnabled by remember(model.id) { mutableStateOf(model.mtpEnabled) }
    var backend by remember(model.id) { mutableStateOf(model.preferredBackend) }
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
                                        toolApprovalMode = approvalMode
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                BackendChoice.AUTO to "Auto", BackendChoice.GPU to "GPU",
                                BackendChoice.CPU to "CPU", BackendChoice.NPU to "NPU"
                            ).forEach { (choice, label) ->
                                FilterChip(selected = backend == choice, onClick = { backend = choice }, label = { Text(label) })
                            }
                        }
                        Text(
                            if (backend == BackendChoice.AUTO) "Tries NPU, then GPU, then falls back to CPU."
                            else "Strict: loads only on ${backend.name} — fails with an error instead of falling back.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )

                        SectionDivider()
                        SectionLabel("Capabilities")
                        CapabilityToggle("Vision", vision) { vision = it }
                        CapabilityToggle("Audio", audio) { audio = it }
                        CapabilityToggle("Tools", tools) { tools = it }
                        CapabilityToggle("Thinking", thinking) { thinking = it }
                        // A load that couldn't actually deliver a capability the user asked for
                        // auto-turns it off here (see reconcileCapabilities) instead of quietly
                        // pretending it still works — surfacing that as a plain fact, not an error.
                        Text(
                            "Turned off automatically if a load can't actually deliver it — you'll see a message when that happens.",
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
                                    FilterChip(selected = approvalMode == mode, onClick = { approvalMode = mode }, label = { Text(label) })
                                }
                            }
                        }

                        SectionDivider()
                        SectionLabel("Speculative decoding (MTP)")
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (model.mtpSupported) {
                                    false -> "Last attempt failed on this model/backend — turning it on will retry on next load."
                                    true -> "Speeds up generation on GPU; no effect on CPU/NPU."
                                    null -> "Tried automatically on load; auto-disabled if unsupported."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Switch(checked = mtpEnabled, onCheckedChange = { mtpEnabled = it })
                        }

                        SectionDivider()
                        SectionLabel("Generation defaults")
                        Text(
                            "Off uses the app-wide default from Settings; switch on to override it just for this model.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OverrideSlider("Temperature", temperatureOn, { temperatureOn = it }, temperature, { temperature = it }, defaults.temperature, "%.2f", 0f..2f)
                        OverrideSlider("Top-p", topPOn, { topPOn = it }, topP, { topP = it }, defaults.topP, "%.2f", 0.1f..1f)
                        OverrideSlider("Top-k", topKOn, { topKOn = it }, topK, { topK = it }, defaults.topK.toFloat(), "%.0f", 1f..64f)
                        OverrideSlider("Max images", maxImagesOn, { maxImagesOn = it }, maxImages, { maxImages = it }, defaults.maxNumImages.toFloat(), "%.0f", 1f..4f)
                        OverrideSlider(
                            "Context length", contextOn, { contextOn = it }, context, { context = it }, defaults.contextTokens.toFloat(),
                            "%.0f", 1024f..32768f, steps = 30
                        )
                        OverrideField("Seed", seedOn, { seedOn = it }, seed, { seed = it.filter(Char::isDigit) }, "Random")
                    } else {
                        Text(
                            "Embedding models only turn text into vectors for semantic search — they don't have capabilities or generation parameters to configure.",
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
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun CapabilityToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
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
        Slider(
            value = if (override) value else defaultValue,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = override,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
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
    defaultLabel: String
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
                value, onValueChange, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}

private fun formatModelSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GiB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024 * 1024 -> String.format("%.1f MiB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format("%.0f KiB", bytes / 1024.0)
    else -> "$bytes B"
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (category == ModelRole.GENERATION) Icons.Filled.Bolt else Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = if (category == ModelRole.GENERATION) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        if (category == ModelRole.GENERATION) "Generation" else "Embedding",
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

@Composable
private fun CatalogEntryCard(state: com.vervan.chat.modeldownload.ModelUiState, onDownload: () -> Unit) {
    val accent = if (state.category == ModelRole.GENERATION) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val accentContainer = if (state.category == ModelRole.GENERATION) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
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
                        if (state.category == ModelRole.GENERATION) Icons.Filled.Bolt else Icons.Outlined.Storage,
                        contentDescription = null,
                        modifier = Modifier.padding(Space.md).size(24.dp)
                    )
                }
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    Text(state.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "v${state.version} · ${if (state.category == ModelRole.GENERATION) "Generation" else "Embedding"}",
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
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onStopKeepPartial: () -> Unit,
    onStopDeletePartial: () -> Unit
) {
    var expanded by rememberSaveable(state.packageId) { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var confirmDeletePartial by remember { mutableStateOf(false) }
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
                        "v${state.version} · ${if (state.category == ModelRole.GENERATION) "Generation" else "Embedding"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SemanticChip(statusChipLabel(state.status), tone)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Hide download details" else "Show download details"
                    )
                }
            }

            Text(
                statusLabel(state),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                modifier = Modifier.padding(top = Space.md)
            )
            state.currentFileName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md)
                )
            }

            if (state.status !in setOf(ModelStatus.QUEUED, ModelStatus.CANCELLING, ModelStatus.DELETING)) {
                if (state.status in setOf(ModelStatus.PREPARING, ModelStatus.DOWNLOADED, ModelStatus.VERIFYING, ModelStatus.IMPORTING)) {
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

            if (state.downloadedBytes > 0L || state.status == ModelStatus.DOWNLOADING || state.status == ModelStatus.PAUSED) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    TransferMetric(
                        icon = Icons.Outlined.Storage,
                        label = "Downloaded",
                        value = buildString {
                            append(formatModelSize(state.downloadedBytes))
                            state.totalBytes?.let { append(" / ${formatModelSize(it)}") }
                        }
                    )
                    progress?.let {
                        TransferMetric(Icons.Filled.CloudDownload, "Progress", "${(it * 100).toInt()}%")
                    }
                    if (state.status == ModelStatus.DOWNLOADING) {
                        TransferMetric(
                            Icons.Filled.Speed,
                            "Speed",
                            state.speedBytesPerSecond?.takeIf { it > 0 }?.let { "${formatModelSize(it)}/s" } ?: "Calculating…"
                        )
                        TransferMetric(
                            Icons.Filled.Schedule,
                            "Time left",
                            state.estimatedRemainingSeconds?.let(::formatEta) ?: "Calculating…"
                        )
                    }
                    if (state.totalFileCount > 1) {
                        TransferMetric(Icons.Filled.CheckCircle, "Files", "${state.completedFileCount} / ${state.totalFileCount}")
                    }
                }
            }

            state.error?.let {
                ValidationMessage(it.message.ifBlank { it.code.name }, modifier = Modifier.padding(top = Space.md))
            }

            if (expanded) {
                Column(Modifier.padding(top = Space.md)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f))
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
                if (ModelAction.RETRY in state.allowedActions) {
                    Button(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Retry", modifier = Modifier.padding(start = Space.xs))
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
                        onClick = { confirmDeletePartial = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Delete partial", modifier = Modifier.padding(start = Space.xs))
                    }
                }
                if (ModelAction.REMOVE in state.allowedActions) {
                    TextButton(onClick = onRemove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Remove", modifier = Modifier.padding(start = Space.xs))
                    }
                }
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop downloading this model?") },
            text = { Text("Keep downloaded data to resume later, or remove all downloaded data.") },
            confirmButton = {
                ResponsiveActions {
                    TextButton(onClick = { confirmStop = false }) { Text("Continue") }
                    TextButton(onClick = { confirmStop = false; onStopKeepPartial() }) { Text("Keep partial") }
                    TextButton(
                        onClick = { confirmStop = false; onStopDeletePartial() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete partial") }
                }
            }
        )
    }
    if (confirmDeletePartial) {
        ConfirmDialog(
            title = "Delete partial download?",
            body = "Downloaded data for \"${state.displayName}\" will be removed. You can download it again later.",
            confirmLabel = "Delete partial",
            destructive = true,
            onConfirm = { confirmDeletePartial = false; onRemove() },
            onDismiss = { confirmDeletePartial = false }
        )
    }
}

@Composable
private fun TransferMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = Space.sm)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.labelMedium, fontFamily = VervanMono)
            }
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
