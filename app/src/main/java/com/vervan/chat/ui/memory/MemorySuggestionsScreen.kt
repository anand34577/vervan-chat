package com.vervan.chat.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import com.vervan.chat.ui.common.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.MemorySuggestion
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.VervanContentWidth
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySuggestionsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: MemorySuggestionsViewModel = viewModel(factory = viewModelFactory { initializer { MemorySuggestionsViewModel(app) } })
    val pending by vm.pending.collectAsState()
    var conflictDialog by remember { mutableStateOf<Pair<MemorySuggestion, com.vervan.chat.data.db.entities.Memory>?>(null) }
    var editDialog by remember { mutableStateOf<MemorySuggestion?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.memory_suggestions_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = VervanContentWidth.standard) {
        if (pending.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Check,
                title = stringResource(R.string.memory_caught_up),
                body = stringResource(R.string.memory_caught_up_body)
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                item {
                    FeatureHero(
                        icon = Icons.Filled.Lightbulb,
                        eyebrow = stringResource(R.string.memory_review_eyebrow),
                        title = stringResource(R.string.memory_suggested_title),
                        body = stringResource(R.string.memory_suggested_body)
                    )
                }
                items(pending, key = { it.id }) { suggestion ->
                    val conflict = vm.conflictFor(suggestion)
                    val dismissedMessage = stringResource(R.string.memory_dismissed)
                    val undoLabel = stringResource(R.string.memory_undo)
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = SurfaceRole.Card.cardColors(),
                        border = SurfaceRole.Card.border(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(Modifier.padding(Space.lg)) {
                            Text(suggestion.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Row(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                Text(suggestion.scope.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (suggestion.key != null) Text(stringResource(R.string.memory_key_label, suggestion.key.orEmpty()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            if (conflict != null) {
                                Row(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                    Text(stringResource(R.string.memory_conflict_label, conflict.text.take(60)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                            Row(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                IconButton(onClick = {
                                    if (conflict != null) conflictDialog = suggestion to conflict
                                    else vm.accept(suggestion, overwriteConflict = false)
                                }) { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.memory_accept), tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = {
                                    vm.reject(suggestion)
                                    scope.launch {
                                        if (snackbarHostState.showSnackbar(dismissedMessage, undoLabel) == SnackbarResult.ActionPerformed) {
                                            vm.unreject(suggestion)
                                        }
                                    }
                                }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.memory_reject)) }
                                IconButton(onClick = { editDialog = suggestion }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.memory_edit_accept)) }
                                IconButton(onClick = { menuFor = suggestion.id }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.memory_more)) }
                                DropdownMenu(expanded = menuFor == suggestion.id, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.memory_never_suggest)) },
                                        enabled = suggestion.key != null,
                                        onClick = { menuFor = null; vm.blockType(suggestion) }
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

    conflictDialog?.let { (suggestion, conflict) ->
        AlertDialog(
            onDismissRequest = { conflictDialog = null },
            title = { Text(stringResource(R.string.memory_conflict)) },
            text = {
                Column {
                    Text(stringResource(R.string.memory_conflict_exists, suggestion.key.orEmpty()))
                    Text("\"${conflict.text}\"", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = Space.sm))
                    Text(stringResource(R.string.memory_suggested_value))
                    Text("\"${suggestion.text}\"", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = Space.sm))
                    Text(stringResource(R.string.memory_conflict_choice))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.accept(suggestion, overwriteConflict = true)
                    conflictDialog = null
                }) { Text(stringResource(R.string.memory_replace)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.accept(suggestion, overwriteConflict = false)
                    conflictDialog = null
                }) { Text(stringResource(R.string.memory_keep_both)) }
            }
        )
    }

    editDialog?.let { suggestion ->
        var text by remember(suggestion.id) { mutableStateOf(suggestion.text) }
        var key by remember(suggestion.id) { mutableStateOf(suggestion.key ?: "") }
        AlertDialog(
            onDismissRequest = { editDialog = null },
            title = { Text(stringResource(R.string.memory_edit_accept)) },
            text = {
                Column {
                    BoundedTextField(value = text, onValueChange = { text = it }, maxLength = ValidationLimits.MEMORY_TEXT)
                    BoundedTextField(
                        value = key, onValueChange = { key = it }, singleLine = true,
                        placeholder = stringResource(R.string.memory_key_optional), maxLength = ValidationLimits.MEMORY_KEY,
                        modifier = Modifier.padding(top = Space.sm)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = { vm.editAndAccept(suggestion, text, key.trim().ifBlank { null }); editDialog = null }
                ) { Text(stringResource(R.string.memory_accept)) }
            },
            dismissButton = { TextButton(onClick = { editDialog = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
