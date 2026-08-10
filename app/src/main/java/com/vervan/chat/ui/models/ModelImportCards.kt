package com.vervan.chat.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.stringResource
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
import com.vervan.chat.data.db.entities.traits
import com.vervan.chat.llm.RemoteModelCatalog
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
import kotlinx.coroutines.delay
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
            Text(stringResource(R.string.model_recommended_setup), style = MaterialTheme.typography.titleMedium)
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
            Text(stringResource(R.string.model_set_up_for_me), modifier = Modifier.padding(start = Space.sm))
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
        label = stringResource(R.string.model_my_models),
                supportingLabel = installedCount.toString(),
                selected = !showingDiscover,
                onClick = onLibrary,
                modifier = Modifier.weight(1f)
            )
            ModelManagerSwitchItem(
        label = stringResource(R.string.model_discover),
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
            Text(stringResource(R.string.model_discover_models), modifier = Modifier.padding(start = Space.sm))
        }
        TextButton(onClick = onImport) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.model_import_device), modifier = Modifier.padding(start = Space.sm))
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
        title = { Text(stringResource(R.string.model_import_dialog)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    "Choose the format of your model file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Space.xs)
                )
                ImportChoiceCard(
            title = stringResource(R.string.model_litert_title),
            subtitle = stringResource(R.string.model_litert_subtitle),
                    icon = Icons.Filled.Bolt,
                    enabled = !importing,
                    horizontal = true,
                    onClick = { onImport(ModelRole.GENERATION) }
                )
                ImportChoiceCard(
            title = stringResource(R.string.model_llama_title),
            subtitle = stringResource(R.string.model_llama_subtitle),
                    icon = Icons.Filled.Bolt,
                    enabled = !importing,
                    horizontal = true,
                    onClick = onImportGguf
                )
                ImportChoiceCard(
            title = stringResource(R.string.model_embeddings_title),
            subtitle = stringResource(R.string.model_embeddings_subtitle),
                    icon = Icons.Outlined.Storage,
                    enabled = !importing,
                    horizontal = true,
                    onClick = { onImport(ModelRole.EMBEDDING) }
                )
                if (com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE) {
                    ImportChoiceCard(
            title = stringResource(R.string.model_whisper_title),
            subtitle = stringResource(R.string.model_whisper_subtitle),
                        icon = Icons.Filled.Mic,
                        enabled = !importing,
                        horizontal = true,
                        onClick = onImportWhisper
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = Space.xs))
                ImportChoiceCard(
            title = stringResource(R.string.model_remote_title),
            subtitle = stringResource(R.string.model_remote_subtitle),
                    icon = Icons.Filled.CloudDownload,
                    enabled = !importing,
                    horizontal = true,
                    onClick = onImportRemote
                )
            }
        },
        confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/**
 * Adds one or more external OpenAI-compatible [ModelInfo] rows (`engine == REMOTE_API`) — no file
 * picker, since there's nothing on disk: just where to send requests, which models to ask for, and
 * a bearer key. Editing an already-added remote model reuses [ModelEditDialog], the same "Configure
 * model" screen a local model gets, rather than this dialog — see `ModelManagerScreen.editModel()`.
 *
 * The model field is a search box over the endpoint's own catalog, fetched automatically once the
 * URL parses (debounced, since it runs while the URL is still being typed). That fetch doubles as
 * the connection test — it is the same `/models` request a separate "Test connection" button used
 * to make, so there no longer is one. Whatever is typed wins: an id absent from the dropdown, or a
 * self-hosted server that serves no usable catalog at all, is still added verbatim.
 *
 * Multi-select, because one endpoint routinely serves a dozen models and adding them one dialog at
 * a time is the same URL and key re-entered a dozen times. Each pick carries its own role and
 * capabilities — guessed per id by [RemoteModelCatalog] and overridable per row, since one endpoint
 * commonly mixes a vision chat model, a text-only one, and an embedding model together.
 */
@Composable
internal fun RemoteApiModelDialog(
    saving: Boolean,
    defaults: ModelDefaults,
    onDismiss: () -> Unit,
    onFetch: suspend (baseUrl: String, apiKey: String) -> Result<List<String>>,
    onSave: (
        baseUrl: String,
        apiKey: String,
        selections: List<ModelManagerViewModel.RemoteModelSelection>
    ) -> Unit
) {
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    // Search box AND free-text entry: an endpoint that serves no catalog (or serves one missing
    // the model you want) is still usable by typing the id and pressing Add.
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(emptyList<ModelManagerViewModel.RemoteModelSelection>()) }
    // Same override-or-default pattern ModelEditDialog uses for a local model — "unset" (the
    // switch off) means "use the app-wide Settings value", not "use whatever the slider happens
    // to show". Shared across the batch rather than per-row (unlike capabilities): a wrong
    // capability guess breaks the request outright, a shared sampling preference across a handful
    // of models you're adding together is just a starting point, refinable per model afterward via
    // ModelEditDialog once the row exists.
    var temperatureOn by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf(defaults.temperature) }
    var topPOn by remember { mutableStateOf(false) }
    var topP by remember { mutableStateOf(defaults.topP) }
    var topKOn by remember { mutableStateOf(false) }
    var topK by remember { mutableStateOf(defaults.topK.toFloat()) }
    var maxOutputTokensOn by remember { mutableStateOf(false) }
    var maxOutputTokens by remember { mutableStateOf(defaults.maxOutputTokens.toFloat()) }
    var contextOn by remember { mutableStateOf(false) }
    var context by remember { mutableStateOf(defaults.contextTokens.toFloat()) }
    var catalog by remember { mutableStateOf<List<String>>(emptyList()) }
    var fetching by remember { mutableStateOf(false) }
    var fetchNote by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val baseUrlError = com.vervan.chat.llm.RemoteOpenAiEngine.baseUrlError(baseUrl)
    // Only surfaced once the user has typed something, so an untouched field isn't pre-marked
    // invalid — but it gates Save either way (see `valid`).
    val shownBaseUrlError = baseUrlError?.takeIf { baseUrl.isNotBlank() }
    val cleartext = baseUrl.trim().startsWith("http://", ignoreCase = true)
    val valid = baseUrlError == null && picked.isNotEmpty()

    fun update(id: String, transform: (ModelManagerViewModel.RemoteModelSelection) -> ModelManagerViewModel.RemoteModelSelection) {
        picked = picked.map { if (it.remoteApiModelId == id) transform(it) else it }
    }

    fun toggle(modelId: String) {
        val id = modelId.trim()
        if (id.isEmpty()) return
        picked = if (picked.any { it.remoteApiModelId == id }) {
            picked.filterNot { it.remoteApiModelId == id }
        } else {
            // Display name defaults to the provider's id — that's what the user recognizes and what
            // their billing shows. Renameable afterwards from this same dialog (single) or the
            // model's own edit action. Tools/Thinking default ON — "on until proven otherwise", the
            // same convention ModelEditDialog uses for a local model's own toggles; both are pure
            // prompt-level behavior with nothing to break. Vision is guessed from the id
            // (RemoteModelCatalog.inferVision); Audio has no comparable naming convention to guess
            // from, so it stays off until set by hand.
            picked + ModelManagerViewModel.RemoteModelSelection(
                remoteApiModelId = id,
                displayName = id,
                role = RemoteModelCatalog.inferRole(id),
                capabilities = ModelManagerViewModel.RemoteCapabilities(
                    vision = RemoteModelCatalog.inferVision(id),
                    tools = true,
                    thinking = true
                )
            )
        }
    }

    LaunchedEffect(baseUrl, apiKey) {
        catalog = emptyList()
        fetchNote = null
        // Also resets a spinner left over from a fetch this restart just cancelled.
        fetching = false
        if (baseUrlError != null) return@LaunchedEffect
        delay(CATALOG_FETCH_DEBOUNCE_MS)
        fetching = true
        onFetch(baseUrl, apiKey).fold(
            onSuccess = { ids ->
                catalog = ids
                menuOpen = ids.isNotEmpty() && picked.isEmpty()
                fetchNote = "${ids.size} model${if (ids.size == 1) "" else "s"} available"
            },
            // Advisory, not blocking: plenty of self-hosted servers don't implement /models, and
            // typing the id by hand still works — so Save stays enabled either way.
            onFailure = { fetchNote = it.message ?: "Could not list models — type the id instead" }
        )
        fetching = false
    }

    // The field is the search box, so the list narrows as the user types.
    val suggestions = catalog.filter { query.isBlank() || it.contains(query.trim(), ignoreCase = true) }
    // Only offer "add as typed" for something the catalog genuinely doesn't have — otherwise it
    // duplicates the row directly below it.
    val typedIsNew = query.isNotBlank() && catalog.none { it.equals(query.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_add_remote)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                Text(
                    "Connects to any OpenAI-compatible endpoint — OpenAI itself, OpenRouter, or a " +
                        "server on your own network. Requests leave this device; the API key is " +
                        "stored encrypted and only ever sent to the URL below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it.take(512) },
        label = { Text(stringResource(R.string.model_base_url)) },
        placeholder = { Text(stringResource(R.string.model_api_placeholder)) },
                    isError = shownBaseUrlError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                shownBaseUrlError?.let { ValidationMessage(it) }
                if (cleartext) {
                    Text(
                        "http:// sends the API key unencrypted — fine for a server on your own " +
                            "network, but use https:// for anything over the internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.take(128) },
        label = { Text(stringResource(R.string.model_api_key)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                run {
                    Box {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.take(200); menuOpen = true },
        label = { Text(stringResource(R.string.model_models)) },
        placeholder = { Text(stringResource(R.string.model_search_ids)) },
                            singleLine = true,
                            trailingIcon = {
                                when {
                                    fetching -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    catalog.isNotEmpty() -> IconButton(onClick = { menuOpen = !menuOpen }) {
                                        Icon(
                                            if (menuOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = if (menuOpen) "Hide model list" else "Show model list"
                                        )
                                    }
                                }
                            },
                            supportingText = {
                                Text(
                                    when {
                                        fetching -> "Checking the endpoint…"
                                        fetchNote != null -> fetchNote!!
                                        else -> "Pick as many as you like."
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = menuOpen && (suggestions.isNotEmpty() || typedIsNew),
                            onDismissRequest = { menuOpen = false },
                            // Non-focusable so the keyboard stays up and the caret stays in the
                            // field while the list narrows and rows are ticked — the field is the
                            // search input, and closing on every pick would make multi-select
                            // require reopening the menu once per model.
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            if (typedIsNew) {
                                DropdownMenuItem(
        text = { Text(stringResource(R.string.model_add_named, query.trim()), style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = { toggle(query); query = "" }
                                )
                            }
                            suggestions.take(MAX_MODEL_SUGGESTIONS).forEach { id ->
                                val checked = picked.any { it.remoteApiModelId == id }
                                DropdownMenuItem(
                                    text = { Text(id, style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = { Checkbox(checked = checked, onCheckedChange = { toggle(id) }) },
                                    // Menu deliberately stays open — see PopupProperties above.
                                    onClick = { toggle(id) }
                                )
                            }
                            // Say so rather than silently hiding matches — providers like
                            // OpenRouter return hundreds of ids.
                            if (suggestions.size > MAX_MODEL_SUGGESTIONS) {
                                DropdownMenuItem(
                                    enabled = false,
                                    text = {
                                        Text(
                                            "${suggestions.size - MAX_MODEL_SUGGESTIONS} more — keep typing to narrow",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    onClick = {}
                                )
                            }
                        }
                    }

                    if (picked.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            "Selected (${picked.size})",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            // Both guessed from the id alone — RemoteModelCatalog.inferRole and
                            // .inferVision — since /models reports neither. A local server can
                            // serve anything under any name, so every guess renders as a changeable
                            // chip, never applied silently.
                            "Role and capabilities are guessed from each name — tap any chip to fix it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                picked.forEach { selection ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.xs)
                        ) {
                            Text(
                                selection.remoteApiModelId,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            VervanFilterChip(
                                selected = selection.role == ModelRole.EMBEDDING,
                                onClick = {
                                    val flipped = if (selection.role == ModelRole.EMBEDDING) ModelRole.GENERATION else ModelRole.EMBEDDING
                                    update(selection.remoteApiModelId) { it.copy(role = flipped) }
                                },
                                label = { Text(if (selection.role == ModelRole.EMBEDDING) "Embedding" else "Chat") }
                            )
                            IconButton(onClick = { toggle(selection.remoteApiModelId) }) {
    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.model_remove_remote, selection.remoteApiModelId), modifier = Modifier.size(18.dp))
                            }
                        }
                        // Chat-only concepts: an embedding model turns text into a vector and
                        // never sees a tool catalog, an image, or a reasoning instruction — same
                        // rule ModelEditDialog uses to hide its own capability section by role.
                        if (selection.role == ModelRole.GENERATION) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(start = Space.xl, bottom = Space.xs),
                                horizontalArrangement = Arrangement.spacedBy(Space.xs)
                            ) {
                                VervanFilterChip(
                                    selected = selection.capabilities.vision,
                                    onClick = { update(selection.remoteApiModelId) { it.copy(capabilities = it.capabilities.copy(vision = !it.capabilities.vision)) } },
            label = { Text(stringResource(R.string.model_vision)) }
                                )
                                VervanFilterChip(
                                    selected = selection.capabilities.audio,
                                    onClick = { update(selection.remoteApiModelId) { it.copy(capabilities = it.capabilities.copy(audio = !it.capabilities.audio)) } },
            label = { Text(stringResource(R.string.model_audio)) }
                                )
                                VervanFilterChip(
                                    selected = selection.capabilities.tools,
                                    onClick = { update(selection.remoteApiModelId) { it.copy(capabilities = it.capabilities.copy(tools = !it.capabilities.tools)) } },
            label = { Text(stringResource(R.string.model_tools)) }
                                )
                                VervanFilterChip(
                                    selected = selection.capabilities.thinking,
                                    onClick = { update(selection.remoteApiModelId) { it.copy(capabilities = it.capabilities.copy(thinking = !it.capabilities.thinking)) } },
            label = { Text(stringResource(R.string.model_thinking)) }
                                )
                            }
                        }
                    }
                }

                // Same per-model tuning a local model gets in Configure — temperature/top-p/top-k/
                // max output tokens/context length, each "off means use the app-wide Settings
                // value" exactly like ModelEditDialog's own OverrideSlider. Shown once the batch
                // includes at least one chat model, applied to all of them (see the shared-vs-
                // per-row reasoning on the state declarations above).
                if (picked.any { it.role == ModelRole.GENERATION }) {
                    HorizontalDivider()
        Text(stringResource(R.string.model_generation_settings), style = MaterialTheme.typography.labelLarge)
                    OverrideSlider("Temperature", temperatureOn, { temperatureOn = it }, temperature, { temperature = it }, defaults.temperature, "%.2f", 0f..2f)
                    OverrideSlider("Top-p", topPOn, { topPOn = it }, topP, { topP = it }, defaults.topP, "%.2f", 0.1f..1f)
                    OverrideSlider(
                        "Top-k", topKOn, { topKOn = it }, topK, { topK = it }, defaults.topK.toFloat(), "%.0f", 1f..64f
                    )
                    Text(
                        "Top-k isn't part of the OpenAI API — only sent if the endpoint happens to accept it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OverrideSlider("Max output tokens", maxOutputTokensOn, { maxOutputTokensOn = it }, maxOutputTokens, { maxOutputTokens = it }, defaults.maxOutputTokens.toFloat(), "%.0f", 64f..4096f, steps = 20)
                    OverrideSlider(
                        "Context length", contextOn, { contextOn = it }, context, { context = it }, defaults.contextTokens.toFloat(),
                        "%.0f", 1024f..131072f, steps = 30
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid && !saving,
                onClick = {
                    val generation = ModelManagerViewModel.RemoteGenerationOverrides(
                        temperature = temperature.takeIf { temperatureOn },
                        topP = topP.takeIf { topPOn },
                        topK = topK.toInt().takeIf { topKOn },
                        maxOutputTokens = maxOutputTokens.toInt().takeIf { maxOutputTokensOn },
                        contextTokens = context.toInt().takeIf { contextOn }
                    )
                    onSave(
                        baseUrl.trim(),
                        apiKey,
                        picked.map {
                            it.copy(
                                displayName = it.displayName.trim().ifBlank { it.remoteApiModelId },
                                generation = if (it.role == ModelRole.GENERATION) generation else it.generation
                            )
                        }
                    )
                }
            ) {
                Text(
                    when {
                        saving -> "Saving…"
                        picked.size <= 1 -> "Save"
                        else -> "Add ${picked.size} models"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** Long enough that typing a URL doesn't fire a request per keystroke, short enough that the list
 *  appears without the user hunting for a button. */
private const val CATALOG_FETCH_DEBOUNCE_MS = 700L
private const val MAX_MODEL_SUGGESTIONS = 50

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
            Text(stringResource(R.string.model_add_local), style = MaterialTheme.typography.titleSmall)
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
            title = stringResource(R.string.model_litert_title),
            subtitle = stringResource(R.string.model_litert_subtitle),
                        icon = Icons.Filled.Bolt,
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth(choiceWidth),
                        horizontal = compact,
                        onClick = { onImport(ModelRole.GENERATION) }
                    )
                    ImportChoiceCard(
            title = stringResource(R.string.model_llama_title),
            subtitle = stringResource(R.string.model_llama_subtitle),
                        icon = Icons.Filled.Bolt,
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth(choiceWidth),
                        horizontal = compact,
                        onClick = onImportGguf
                    )
                    ImportChoiceCard(
            title = stringResource(R.string.model_embeddings_title),
            subtitle = stringResource(R.string.model_embeddings_subtitle),
                        icon = Icons.Outlined.Storage,
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth(choiceWidth),
                        horizontal = compact,
                        onClick = { onImport(ModelRole.EMBEDDING) }
                    )
                    if (com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE) {
                        ImportChoiceCard(
            title = stringResource(R.string.model_whisper_title),
            subtitle = stringResource(R.string.model_whisper_subtitle),
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
        title = { Text(stringResource(R.string.model_import_embedding)) },
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
        label = stringResource(R.string.model_file),
                    fileName = modelUri?.let { queryDisplayName(context, it) },
                    onPick = { validationError = null; onPickModel() }
                )
                EmbeddingImportStep(
                    stepNumber = 2,
        label = stringResource(R.string.model_tokenizer_file),
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
        }) { Text(stringResource(R.string.action_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
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
        title = { Text(stringResource(R.string.model_import_gguf)) },
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
        label = stringResource(R.string.model_gguf_file),
                    fileName = modelUri?.let { queryDisplayName(context, it) },
                    onPick = { validationError = null; onPickModel() }
                )
                EmbeddingImportStep(
                    stepNumber = 2,
        label = stringResource(R.string.model_vision_projector),
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
        }) { Text(stringResource(R.string.action_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
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
        title = { Text(stringResource(R.string.model_import_whisper)) },
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
        label = stringResource(R.string.model_bin_file),
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
        }) { Text(stringResource(R.string.action_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
internal fun EmbeddingImportStep(stepNumber: Int, label: String, fileName: String?, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(stringResource(R.string.model_step, stepNumber, label), style = MaterialTheme.typography.labelMedium)
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
                            // No weights on disk means no size to show, and "0 B" reads like a
                            // broken import — show where it actually lives instead.
                            if (model.traits.storesWeightsLocally) {
                                formatModelSize(model.fileSizeBytes)
                            } else {
                                model.remoteBaseUrl
                                    ?.let { runCatching { java.net.URI(it).host }.getOrNull() }
                                    ?: model.traits.label
                            },
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
                        // A remote model never "loads" into memory — it's just the one requests
                        // currently go to. "Using"/"Use" instead of "Loaded"/"Load" says that
                        // truthfully instead of implying a local weights file just got read in.
                        if (isLoaded) SemanticChip(if (model.traits.runsOnDevice) "Loaded" else "Using", ChipTone.Neutral)
                        if (model.isActive) SemanticChip("Default", ChipTone.Neutral)
                        Box {
                            IconButton(onClick = { menuOpen = true }, enabled = !busy) {
    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.model_more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (showSetActive && !model.isActive) {
                                    DropdownMenuItem(
        text = { Text(stringResource(R.string.model_set_default)) },
                                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                                        onClick = { menuOpen = false; onSetActive() }
                                    )
                                }
                                DropdownMenuItem(
        text = { Text(stringResource(R.string.model_configure)) },
                                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                                    onClick = { menuOpen = false; onEdit() }
                                )
                                DropdownMenuItem(
        text = { Text(stringResource(R.string.model_benchmark)) },
                                    leadingIcon = { Icon(Icons.Filled.Speed, contentDescription = null) },
                                    onClick = { menuOpen = false; onBenchmark() }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
        text = { Text(stringResource(R.string.model_delete), color = MaterialTheme.colorScheme.error) },
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
                        Text(
                            if (model.traits.runsOnDevice) (if (isLoaded) "Unload" else "Load")
                            else (if (isLoaded) "Stop using" else "Use"),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
        title = stringResource(R.string.model_delete_title),
            body = "Remove \"${model.displayName}\" permanently?",
        confirmLabel = stringResource(R.string.action_delete_forever),
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false }
        )
    }
}

