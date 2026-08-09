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
import androidx.compose.material.icons.filled.Add
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

@Composable
internal fun ModelManagerSwitcher(
    showingDiscover: Boolean,
    installedCount: Int,
    onLibrary: () -> Unit,
    onDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(Modifier.padding(4.dp)) {
            ModelManagerSwitchItem(
                label = "My models",
                supportingLabel = installedCount.toString(),
                selected = !showingDiscover,
                onClick = onLibrary,
                modifier = Modifier.weight(1f)
            )
            ModelManagerSwitchItem(
                label = "Discover",
                supportingLabel = null,
                selected = showingDiscover,
                onClick = onDiscover,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModelManagerSwitchItem(
    label: String,
    supportingLabel: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (supportingLabel != null) {
                Surface(
                    modifier = Modifier.padding(start = Space.sm),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        supportingLabel,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyModelLibrary(onDiscover: () -> Unit, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(Space.lg).size(28.dp)
            )
        }
        Text(
            "Your models will live here",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Space.lg)
        )
        Text(
            "Download a verified model or bring one you already have.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs, bottom = Space.lg)
        )
        Button(onClick = onDiscover) {
            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Discover models", modifier = Modifier.padding(start = Space.sm))
        }
        TextButton(onClick = onImport) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Import from device", modifier = Modifier.padding(start = Space.sm))
        }
    }
}

@Composable
internal fun ImportModelDialog(
    importing: Boolean,
    onDismiss: () -> Unit,
    onImport: (ModelRole) -> Unit,
    onImportGguf: () -> Unit,
    onImportWhisper: () -> Unit,
    onImportRemote: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import from device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    "Choose the format of your model file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Space.xs)
                )
                ImportChoiceCard(
                    title = "LiteRT-LM",
                    subtitle = "Android-optimized · .task / .litertlm",
                    icon = Icons.Filled.Bolt,
                    enabled = !importing,
                    horizontal = true,
                    onClick = { onImport(ModelRole.GENERATION) }
                )
                ImportChoiceCard(
                    title = "llama.cpp",
                    subtitle = "GGUF · Vulkan / CPU",
                    icon = Icons.Filled.Bolt,
                    enabled = !importing,
                    horizontal = true,
                    onClick = onImportGguf
                )
                ImportChoiceCard(
                    title = "Embeddings",
                    subtitle = "Model + tokenizer",
                    icon = Icons.Outlined.Storage,
                    enabled = !importing,
                    horizontal = true,
                    onClick = { onImport(ModelRole.EMBEDDING) }
                )
                if (com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE) {
                    ImportChoiceCard(
                        title = "Whisper",
                        subtitle = "Offline speech-to-text · .bin / .gguf",
                        icon = Icons.Filled.Mic,
                        enabled = !importing,
                        horizontal = true,
                        onClick = onImportWhisper
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = Space.xs))
                ImportChoiceCard(
                    title = "Remote API",
                    subtitle = "OpenAI-compatible endpoint · your own key",
                    icon = Icons.Filled.CloudDownload,
                    enabled = !importing,
                    horizontal = true,
                    onClick = onImportRemote
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Add/edit an external OpenAI-compatible [ModelInfo] (`engine == REMOTE_API`) — no file picker,
 * since there's nothing on disk: just where to send requests, the provider's own model name, and
 * a bearer key. [initial] non-null means editing an existing row (fields prefilled, API key field
 * left blank — see its own supporting text); null means adding a new one. [onTestConnection]
 * hits the endpoint's `/models` list before the user commits, so a typo'd URL or bad key is
 * caught here rather than on first chat send.
 */
@Composable
internal fun RemoteApiModelDialog(
    initial: ModelInfo?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onTestConnection: suspend (baseUrl: String, apiKey: String) -> Result<Unit>,
    onSave: (displayName: String, baseUrl: String, apiKey: String, remoteApiModelId: String) -> Unit
) {
    var displayName by remember { mutableStateOf(initial?.displayName ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.remoteBaseUrl ?: "") }
    var apiKey by remember { mutableStateOf("") }
    var remoteApiModelId by remember { mutableStateOf(initial?.remoteApiModelId ?: "") }
    var testResult by remember { mutableStateOf<Result<Unit>?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Only surfaced once the user has typed something, so an untouched field isn't pre-marked
    // invalid — but it gates Save/Test either way (see `valid` below).
    val baseUrlError = com.vervan.chat.llm.RemoteOpenAiEngine.baseUrlError(baseUrl)
    val shownBaseUrlError = baseUrlError?.takeIf { baseUrl.isNotBlank() }
    val valid = displayName.isNotBlank() && baseUrlError == null && remoteApiModelId.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add remote API model" else "Edit remote API model") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                Text(
                    "Connects to any OpenAI-compatible /v1/chat/completions endpoint — OpenAI itself, " +
                        "OpenRouter, or a self-hosted server. Generation leaves this device; the API key " +
                        "is stored encrypted and only ever sent to the URL below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    placeholder = { Text("e.g. GPT-4o mini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; testResult = null },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    isError = shownBaseUrlError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                shownBaseUrlError?.let { ValidationMessage(it) }
                OutlinedTextField(
                    value = remoteApiModelId,
                    onValueChange = { remoteApiModelId = it },
                    label = { Text("Model id") },
                    placeholder = { Text("gpt-4o-mini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; testResult = null },
                    label = { Text("API key") },
                    placeholder = { Text(if (initial != null) "Leave blank to keep the current key" else "") },
                    supportingText = if (initial != null) {
                        { Text("Leave blank to keep the existing key") }
                    } else null,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        // Same URL gate as Save — no point spending a round trip on a URL the
                        // platform would refuse to connect to anyway.
                        enabled = baseUrlError == null && !testing,
                        onClick = {
                            testing = true
                            testResult = null
                            scope.launch {
                                testResult = onTestConnection(baseUrl, apiKey)
                                testing = false
                            }
                        }
                    ) { Text(if (testing) "Testing…" else "Test connection") }
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
                testResult?.let { result ->
                    result.fold(
                        onSuccess = {
                            Text(
                                "Connection OK",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        },
                        onFailure = { ValidationMessage(it.message ?: "Connection failed") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid && !saving,
                onClick = { onSave(displayName.trim(), baseUrl.trim(), apiKey, remoteApiModelId.trim()) }
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
    )
}

/** Keeps the runtime choice explicit. LiteRT-LM and llama.cpp are peers, while embeddings are
 * supporting infrastructure; stacking these options on phones avoids unreadably narrow cards. */
@Composable
internal fun ImportCard(
    importing: Boolean,
    onImport: (ModelRole) -> Unit,
    onImportGguf: () -> Unit,
    onImportWhisper: () -> Unit
) {
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
                    if (com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE) {
                        ImportChoiceCard(
                            title = "Whisper (offline STT)",
                            subtitle = "Speech-to-text • .bin (ggml) / .gguf",
                            icon = Icons.Filled.Mic,
                            enabled = !importing,
                            modifier = Modifier.fillMaxWidth(choiceWidth),
                            horizontal = compact,
                            onClick = onImportWhisper
                        )
                    }
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

/** Single-file import — a whisper.cpp ggml (.bin) or GGUF model, brought in locally without
 * going through the catalog/network. Content-validated ([com.vervan.chat.model.ModelFileSniffer])
 * on Import, not here — this dialog is just the file picker. */
@Composable
internal fun WhisperCppImportDialog(
    modelUri: Uri?,
    onPickModel: () -> Unit,
    onDismiss: () -> Unit,
    onImport: (Uri) -> Unit
) {
    val context = LocalContext.current
    var validationError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import whisper.cpp model") },
        text = {
            Column {
                Text(
                    "Choose a whisper.cpp model file (.bin ggml, or .gguf).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                EmbeddingImportStep(
                    stepNumber = 1,
                    label = "Model file (.bin / .gguf)",
                    fileName = modelUri?.let { queryDisplayName(context, it) },
                    onPick = { validationError = null; onPickModel() }
                )
                validationError?.let {
                    ValidationMessage(it, modifier = Modifier.padding(top = 10.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (modelUri == null) {
                    validationError = "Select the whisper.cpp model file first."
                } else {
                    onImport(modelUri)
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
                        OverflowTooltipText(
                            text = model.displayName,
                            style = MaterialTheme.typography.titleSmall
                        )
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

