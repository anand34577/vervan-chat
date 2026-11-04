package com.vervan.chat.ui.models

import androidx.compose.ui.graphics.compositeOver
import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.vervan.chat.ui.common.OverflowTooltipText
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
    Card(
        onClick = { sectionExpanded = !sectionExpanded },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
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
                Text(stringResource(R.string.model_download_available_for_download), style = MaterialTheme.typography.titleMedium)
                Text(
                    androidx.compose.ui.res.pluralStringResource(com.vervan.chat.R.plurals.model_curated_count, states.size, states.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (sectionExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (sectionExpanded) stringResource(R.string.model_download_collapse_available_models) else stringResource(R.string.model_download_expand_available_models)
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
                        contentDescription = if (expanded) stringResource(R.string.model_download_collapse_category) else stringResource(R.string.model_download_expand_category),
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

@androidx.compose.runtime.Composable
internal fun categoryLabel(category: ModelRole) = when (category) {
    ModelRole.GENERATION -> stringResource(R.string.notification_generation_channel)
    ModelRole.EMBEDDING -> stringResource(R.string.model_download_embedding)
    ModelRole.TTS_VOICE -> stringResource(R.string.model_download_voice_model)
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
        shape = MaterialTheme.shapes.large,
        // Opaque on purpose: a translucent container over a shadow shows the shadow through it and
        // renders as a frame inside a frame.
        colors = CardDefaults.cardColors(containerColor = accentContainer.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surface)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                    Text(state.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
                    SemanticChip(if (it) stringResource(R.string.model_download_fits_your_device) else stringResource(R.string.model_download_may_be_tight), if (it) ChipTone.Success else ChipTone.Warning)
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
                    stringResource(R.string.model_download_source_arg1, state.sourceName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md)
                )
            }
            // A licence-gated model is only downloaded after the user agrees to its terms here.
            var confirmLicense by remember { mutableStateOf(false) }
            ResponsiveActions(Modifier.padding(top = Space.lg)) {
                Button(onClick = { if (state.requiresLicenseAcceptance) confirmLicense = true else onDownload() }) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.model_download_download), modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (confirmLicense) {
                val context = LocalContext.current
                val licenseName = state.licenseName ?: "the publisher's license"
                AlertDialog(
                    onDismissRequest = { confirmLicense = false },
                    title = { Text(stringResource(R.string.model_download_accept_the_license)) },
                    text = {
                        Text(stringResource(R.string.model_download_arg1_is_released_under_arg2, state.displayName, licenseName))
                    },
                    confirmButton = {
                        TextButton(onClick = { confirmLicense = false; onDownload() }) { Text(stringResource(R.string.model_download_accept_and_download)) }
                    },
                    dismissButton = {
                        Row {
                            state.licenseUrl?.let { url ->
                                TextButton(onClick = {
                                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))) }
                                }) { Text(stringResource(R.string.model_download_read_license)) }
                            }
                            TextButton(onClick = { confirmLicense = false }) { Text(stringResource(R.string.action_cancel)) }
                        }
                    }
                )
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
    if (state.status == ModelStatus.READY) {
        InstalledPackageCard(state = state, onDelete = onDelete)
        return
    }

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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.32f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                    Text(state.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
                            contentDescription = if (expanded) stringResource(R.string.model_download_hide_download_details) else stringResource(R.string.model_download_show_download_details)
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = Space.md),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f).padding(end = Space.md)) {
                    // The chip on the right already says "Queued"/"Paused"; only add the long label when it says more.
                    if (statusLabel(state) != statusChipLabel(state.status)) {
                        Text(statusLabel(state), style = MaterialTheme.typography.labelMedium, color = statusColor)
                    }
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

            // READY means the model is fully installed — there's nothing left to show progress
            // for, so the bar (previously falling into the indeterminate "else" branch below,
            // since READY isn't PAUSED/FAILED either) no longer renders once installed.
            // QUEUED can lag behind the first bytes: once bytes are moving, show the real bar.
            if (state.status != ModelStatus.READY && (state.status != ModelStatus.QUEUED || state.downloadedBytes > 0L)) {
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
                            state.speedBytesPerSecond?.takeIf { it > 0 }?.let { "${formatModelSize(it)}/s" } ?: stringResource(R.string.model_download_calculating),
                            "Time left",
                            state.estimatedRemainingSeconds?.let(::formatEta) ?: stringResource(R.string.model_download_calculating)
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
                        stringResource(R.string.model_download_package_files_arg1_of_arg2, state.completedFileCount, state.totalFileCount),
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
                                OverflowTooltipText(
                                    text = file.fileName,
                                    style = MaterialTheme.typography.labelMedium
                                )
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
                        stringResource(R.string.model_download_stopping_the_transfer_this_finishes),
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
                        Text(stringResource(R.string.action_pause), modifier = Modifier.padding(start = Space.xs))
                    }
                }
                if (ModelAction.RESUME in state.allowedActions) {
                    Button(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.workspace_resume), modifier = Modifier.padding(start = Space.xs))
                    }
                }
                if (ModelAction.CANCEL in state.allowedActions) {
                    TextButton(onClick = { confirmStop = true }) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.action_stop), modifier = Modifier.padding(start = Space.xs))
                    }
                }
                if (ModelAction.DELETE in state.allowedActions) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.library_delete), modifier = Modifier.padding(start = Space.xs))
                    }
                }
            }
        }
    }

    if (confirmStop) {
        ConfirmDialog(
            title = stringResource(R.string.model_download_stop_downloading),
            body = stringResource(R.string.model_download_this_removes_the_download_and),
            confirmLabel = stringResource(R.string.action_stop),
            destructive = true,
            onConfirm = { confirmStop = false; onStop() },
            onDismiss = { confirmStop = false }
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = if (state.status == ModelStatus.READY) "Delete downloaded voice?" else "Delete partial download?",
            body = stringResource(R.string.model_download_remove_downloaded_data_for_arg1, state.displayName),
            confirmLabel = stringResource(R.string.library_delete),
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun InstalledPackageCard(
    state: com.vervan.chat.modeldownload.ModelUiState,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, vervanSubtleDividerColor())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.md, top = Space.md, bottom = Space.md, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.vervanSuccess.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.vervanSuccess
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.model_download_installed),
                    modifier = Modifier.padding(10.dp).size(20.dp)
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                Text(
                    state.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(categoryLabel(state.category))
                        append(" · v${state.version}")
                        state.installedSizeBytes?.let { append(" · ${formatModelSize(it)}") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.model_download_delete_arg1, state.displayName),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.model_download_delete_downloaded_model),
            body = stringResource(R.string.model_download_remove_downloaded_data_for_arg1, state.displayName),
            confirmLabel = stringResource(R.string.library_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
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

@androidx.compose.runtime.Composable
internal fun fileStatusLabel(file: com.vervan.chat.modeldownload.ModelFileUiState): String = when (file.status) {
    FileDownloadStatus.COMPLETED -> stringResource(R.string.model_download_complete)
    FileDownloadStatus.DOWNLOADING -> "${formatModelSize(file.downloadedBytes)}${file.totalBytes?.let { " / ${formatModelSize(it)}" } ?: ""}"
    FileDownloadStatus.FAILED -> file.errorMessage?.ifBlank { null } ?: "Failed"
    FileDownloadStatus.PAUSED -> stringResource(R.string.model_download_paused)
    FileDownloadStatus.WAITING_FOR_NETWORK -> stringResource(R.string.model_download_waiting_for_network)
    FileDownloadStatus.NOT_STARTED -> stringResource(R.string.model_download_waiting)
}

@androidx.compose.runtime.Composable
internal fun statusChipLabel(status: ModelStatus): String = when (status) {
    ModelStatus.WAITING_FOR_NETWORK -> stringResource(R.string.model_download_no_network)
    ModelStatus.WAITING_FOR_WIFI -> stringResource(R.string.model_download_wi_fi_needed)
    ModelStatus.WAITING_FOR_STORAGE -> stringResource(R.string.model_download_storage_needed)
    ModelStatus.DOWNLOADED -> stringResource(R.string.model_download_downloaded)
    ModelStatus.VERIFYING -> stringResource(R.string.model_download_verifying)
    ModelStatus.IMPORTING -> stringResource(R.string.model_download_installing)
    else -> status.name.lowercase().replaceFirstChar(Char::titlecase)
}

@androidx.compose.runtime.Composable
internal fun statusLabel(state: ModelUiState): String = when (state.status) {
    ModelStatus.QUEUED -> stringResource(R.string.model_download_queued)
    ModelStatus.PREPARING -> stringResource(R.string.model_download_preparing)
    ModelStatus.WAITING_FOR_NETWORK -> stringResource(R.string.model_download_waiting_for_network)
    ModelStatus.WAITING_FOR_WIFI -> stringResource(R.string.model_download_waiting_for_wi_fi)
    ModelStatus.WAITING_FOR_STORAGE -> stringResource(R.string.model_download_waiting_for_storage)
    ModelStatus.DOWNLOADING -> if (state.totalFileCount > 1) {
        "Downloading file ${state.completedFileCount + 1} of ${state.totalFileCount}"
    } else "Downloading"
    ModelStatus.PAUSING -> stringResource(R.string.model_download_pausing)
    ModelStatus.PAUSED -> stringResource(R.string.model_download_paused)
    ModelStatus.DOWNLOADED -> stringResource(R.string.model_download_download_complete_preparing_verification)
    ModelStatus.VERIFYING -> stringResource(R.string.notification_verifying)
    ModelStatus.IMPORTING -> stringResource(R.string.notification_importing)
    ModelStatus.READY -> stringResource(R.string.chat_screen_ready)
    ModelStatus.CANCELLING -> stringResource(R.string.model_download_cancelling)
    ModelStatus.CANCELLED -> stringResource(R.string.model_download_cancelled)
    ModelStatus.FAILED -> stringResource(R.string.message_bubble_failed)
    ModelStatus.DELETING -> stringResource(R.string.model_download_deleting)
    ModelStatus.NOT_DOWNLOADED -> stringResource(R.string.voice_settings_not_downloaded)
}

internal fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "$seconds sec"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}

/** Navigation affordance for the curated Model Store. */
@Composable
internal fun StoreEntryCard(onOpenStore: () -> Unit) {
    Card(
        onClick = onOpenStore,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Filled.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.padding(Space.md).size(24.dp)
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                Text(stringResource(R.string.model_download_model_store), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.model_download_curated_models_with_device_aware),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
