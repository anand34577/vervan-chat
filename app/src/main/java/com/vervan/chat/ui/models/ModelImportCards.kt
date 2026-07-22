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


/** Model import cards, dialogs, and the per-model ModelCard for the manager screen. */

@Composable
internal fun RecommendedSetupCard(model: ModelUiState, reason: String, onSetup: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = com.vervan.chat.ui.theme.vervanBorder(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    Text("Recommended setup", style = MaterialTheme.typography.titleMedium)
                    Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                }
            }
            Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = Space.sm))
            Text(
                "Downloads, verifies, imports, activates, loads, and tests the model. You can pause the download at any time.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = Space.xs),
            )
            Button(onClick = onSetup, modifier = Modifier.padding(top = Space.md)) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Set up for me", modifier = Modifier.padding(start = Space.sm))
            }
        }
    }
}

@Composable
internal fun SectionHeader(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
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
internal fun ImportCard(importing: Boolean, onImport: (ModelRole) -> Unit, onImportGguf: () -> Unit) {
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
internal fun EmbeddingImportDialog(
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
internal fun LlamaCppImportDialog(
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
internal fun EmbeddingImportStep(stepNumber: Int, label: String, fileName: String?, onPick: () -> Unit) {
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

internal fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
    }
    return uri.lastPathSegment
}

@Composable
internal fun ImportChoiceCard(
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
internal fun ModelCard(
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

