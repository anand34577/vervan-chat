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


/** "Available for Download" — collapsed by default, grouped by category, each category
 * independently expandable. [highlightBudgetBytes], when set (Model Calculator's
 * "Browse models that fit"), force-opens this section and the generation category, and sorts
 * entries within each category so ones that fit the budget surface first with a fit badge. */
@Composable
internal fun AvailableForDownloadSection(
    states: List<com.vervan.chat.modeldownload.ModelUiState>,
    onDownload: (com.vervan.chat.modeldownload.ModelUiState) -> Unit,
    highlightBudgetBytes: Long? = null
) {
    var sectionExpanded by rememberSaveable("available_models") { mutableStateOf(highlightBudgetBytes != null) }
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
        states.groupBy { it.category }.forEach { (category, entriesInCategory) ->
            val entries = if (highlightBudgetBytes != null) {
                entriesInCategory.sortedByDescending { it.fitsBudget(highlightBudgetBytes) }
            } else entriesInCategory
            var expanded by rememberSaveable("catalog_${category.name}") {
                mutableStateOf(highlightBudgetBytes != null && category == ModelRole.GENERATION)
            }
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
                entries.forEach { entry ->
                    CatalogEntryCard(
                        entry,
                        onDownload = { onDownload(entry) },
                        fitsBudget = highlightBudgetBytes?.let { entry.fitsBudget(it) }
                    )
                }
            }
        }
    }
}

internal fun categoryIcon(category: ModelRole) = when (category) {
    ModelRole.GENERATION -> Icons.Filled.Bolt
    ModelRole.EMBEDDING -> Icons.Outlined.Storage
    ModelRole.TTS_VOICE -> Icons.Filled.GraphicEq
    ModelRole.STT_MODEL -> Icons.Filled.Mic
}

internal fun categoryLabel(category: ModelRole) = when (category) {
    ModelRole.GENERATION -> "Generation"
    ModelRole.EMBEDDING -> "Embedding"
    ModelRole.TTS_VOICE -> "Voice model"
    ModelRole.STT_MODEL -> "Speech-to-text"
}

@Composable
internal fun categoryAccent(category: ModelRole) = when (category) {
    ModelRole.GENERATION -> MaterialTheme.colorScheme.primary
    ModelRole.EMBEDDING -> MaterialTheme.colorScheme.secondary
    ModelRole.TTS_VOICE, ModelRole.STT_MODEL -> MaterialTheme.colorScheme.tertiary
}

@Composable
internal fun categoryAccentContainer(category: ModelRole) = when (category) {
    ModelRole.GENERATION -> MaterialTheme.colorScheme.primaryContainer
    ModelRole.EMBEDDING -> MaterialTheme.colorScheme.secondaryContainer
    ModelRole.TTS_VOICE, ModelRole.STT_MODEL -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
internal fun CatalogEntryCard(
    state: com.vervan.chat.modeldownload.ModelUiState,
    onDownload: () -> Unit,
    // null = no budget in play (normal browsing); true/false = Model Calculator's handoff, see
    // AvailableForDownloadSection's highlightBudgetBytes.
    fitsBudget: Boolean? = null
) {
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
                fitsBudget?.let {
                    SemanticChip(if (it) "Fits your device" else "May be tight", if (it) ChipTone.Success else ChipTone.Warning)
                }
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
 *). Progress is always derived from bytes (never a stored percentage), and only rendered as
 * determinate once a total is actually known. */
@Composable
internal fun DownloadPackageCard(
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
internal fun DownloadFactRow(
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
internal fun downloadStatusColor(status: ModelStatus): Color = when (status) {
    ModelStatus.FAILED -> MaterialTheme.colorScheme.error
    ModelStatus.PAUSED, ModelStatus.WAITING_FOR_NETWORK, ModelStatus.WAITING_FOR_WIFI, ModelStatus.WAITING_FOR_STORAGE ->
        MaterialTheme.colorScheme.tertiary
    ModelStatus.DOWNLOADED, ModelStatus.VERIFYING, ModelStatus.IMPORTING, ModelStatus.READY ->
        MaterialTheme.colorScheme.vervanSuccess
    else -> MaterialTheme.colorScheme.primary
}

internal fun downloadStatusTone(status: ModelStatus): ChipTone = when (status) {
    ModelStatus.FAILED -> ChipTone.Error
    ModelStatus.PAUSED, ModelStatus.WAITING_FOR_NETWORK, ModelStatus.WAITING_FOR_WIFI, ModelStatus.WAITING_FOR_STORAGE -> ChipTone.Warning
    ModelStatus.DOWNLOADED, ModelStatus.VERIFYING, ModelStatus.IMPORTING, ModelStatus.READY -> ChipTone.Success
    else -> ChipTone.Neutral
}

internal fun downloadStatusIcon(status: ModelStatus) = when (status) {
    ModelStatus.FAILED -> Icons.Filled.Warning
    ModelStatus.PAUSED -> Icons.Filled.Pause
    ModelStatus.DOWNLOADED, ModelStatus.VERIFYING, ModelStatus.IMPORTING, ModelStatus.READY -> Icons.Filled.CheckCircle
    else -> Icons.Filled.CloudDownload
}

internal fun fileStatusLabel(file: com.vervan.chat.modeldownload.ModelFileUiState): String = when (file.status) {
    FileDownloadStatus.COMPLETED -> "Complete"
    FileDownloadStatus.DOWNLOADING -> "${formatModelSize(file.downloadedBytes)}${file.totalBytes?.let { " / ${formatModelSize(it)}" } ?: ""}"
    FileDownloadStatus.FAILED -> file.errorMessage?.ifBlank { null } ?: "Failed"
    FileDownloadStatus.PAUSED -> "Paused"
    FileDownloadStatus.WAITING_FOR_NETWORK -> "Waiting for network"
    FileDownloadStatus.NOT_STARTED -> "Waiting"
}

internal fun statusChipLabel(status: ModelStatus): String = when (status) {
    ModelStatus.WAITING_FOR_NETWORK -> "No network"
    ModelStatus.WAITING_FOR_WIFI -> "Wi-Fi needed"
    ModelStatus.WAITING_FOR_STORAGE -> "Storage needed"
    ModelStatus.DOWNLOADED -> "Downloaded"
    ModelStatus.VERIFYING -> "Verifying"
    ModelStatus.IMPORTING -> "Installing"
    else -> status.name.lowercase().replaceFirstChar(Char::titlecase)
}

internal fun statusLabel(state: ModelUiState): String = when (state.status) {
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

internal fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "$seconds sec"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}

/** Navigation affordance for the curated Model Store. */
@Composable
internal fun StoreEntryCard(onOpenStore: () -> Unit) {
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
