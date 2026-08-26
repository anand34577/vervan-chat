package com.vervan.chat.ui.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.vervan.chat.ui.common.VervanFloatingActionButton as FloatingActionButton
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.ModernistListRow
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersListScreen(onBack: () -> Unit, onOpenFolder: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val vm: FoldersViewModel = viewModel(factory = viewModelFactory { initializer { FoldersViewModel(app) } })
    val folders by vm.folders.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selected.size,
                    allSelected = selected.size == folders.size && folders.isNotEmpty(),
                    onToggleSelectAll = { selected = if (selected.size == folders.size && folders.isNotEmpty()) emptySet() else folders.map { it.id }.toSet() },
                    onExit = { selected = emptySet(); selectionMode = false },
                    onDelete = { confirmBulkDelete = true },
                    deleteContentDescription = stringResource(R.string.folder_delete_selected_accessibility)
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.folder_list_title)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
                    // Long-press a row to enter selection mode — no separate top-bar entry
                    // point, matching every other list screen in the app.
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.folder_new)) }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
          Column(Modifier.fillMaxSize()) {
            FeatureHero(
                icon = Icons.Filled.Folder,
                eyebrow = stringResource(R.string.folder_hero_eyebrow),
                title = stringResource(R.string.folder_list_title),
                body = stringResource(R.string.folder_hero_body),
                modifier = Modifier.padding(top = Space.sm)
            )
          if (error != null) {
            OperationErrorCard(
                title = stringResource(R.string.folders_unavailable),
                message = error ?: stringResource(R.string.folders_unavailable_message),
                recovery = stringResource(R.string.folders_unavailable_recovery),
                modifier = Modifier.padding(top = Space.md),
                actionLabel = stringResource(R.string.action_retry),
                onAction = vm::retry
            )
          } else if (isLoading) {
            LoadingSkeletonList(rows = 5, modifier = Modifier.padding(top = Space.md))
          } else if (folders.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Folder,
                title = stringResource(R.string.folder_no_items),
                body = stringResource(R.string.folder_no_items_body),
                modifier = Modifier.fillMaxSize(),
                centered = true,
                actionLabel = stringResource(R.string.folder_create),
                onAction = { showCreate = true }
            )
          } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.sm),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm)
            ) {
                items(folders, key = { it.id }) { folder ->
                    val isSelected = folder.id in selected
                    ModernistListRow(
                        modifier = Modifier.fillMaxWidth()
                            .selectableItem(
                                selectionMode = selectionMode,
                                onClick = { onOpenFolder(folder.id) },
                                onToggleSelected = { selected = if (isSelected) selected - folder.id else selected + folder.id },
                                onEnterSelection = { selectionMode = true; selected = selected + folder.id }
                            ),
                        selected = isSelected
                    ) {
                        if (selectionMode) {
                            Checkbox(checked = isSelected, onCheckedChange = { selected = if (isSelected) selected - folder.id else selected + folder.id })
                        } else {
                            IconAffordance(Icons.Filled.Folder, size = IconAffordanceSize.Default)
                            androidx.compose.foundation.layout.Spacer(Modifier.width(Space.md))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(folder.name, style = MaterialTheme.typography.titleMedium)
                            val personaDefaultLabel = stringResource(R.string.folder_default_persona)
                            val modelDefaultLabel = stringResource(R.string.folder_default_model)
                            val sourceDefaultLabel = stringResource(R.string.folder_default_sources).lowercase()
                            val defaults = buildList {
                                if (folder.defaultPersonaId != null) add(personaDefaultLabel)
                                if (folder.defaultModelId != null) add(modelDefaultLabel)
                                if (folder.kbIdList().isNotEmpty()) add("${folder.kbIdList().size} $sourceDefaultLabel")
                            }
                            if (defaults.isNotEmpty()) {
                                Text(
                                    defaults.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = Space.xs),
                                )
                            }
                        }
                        if (!selectionMode) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        /*
                         * The row is intentionally a single action surface. Selection controls
                         * remain inline, while navigation is represented by the trailing arrow.
                         */
                        /* legacy inner Card content removed */
                        /*
                            if (selectionMode) {
                                Checkbox(checked = isSelected, onCheckedChange = { selected = if (isSelected) selected - folder.id else selected + folder.id })
                            } else {
                                IconAffordance(Icons.Filled.Folder, size = IconAffordanceSize.Default)
                                androidx.compose.foundation.layout.Spacer(Modifier.width(Space.md))
                            }
                                Column(Modifier.weight(1f)) {
                                Text(folder.name, style = MaterialTheme.typography.titleMedium)
                                val personaDefaultLabel = stringResource(R.string.folder_default_persona)
                                val modelDefaultLabel = stringResource(R.string.folder_default_model)
                                val sourceDefaultLabel = stringResource(R.string.folder_default_sources).lowercase()
                                val defaults = buildList {
                                    if (folder.defaultPersonaId != null) add(personaDefaultLabel)
                                    if (folder.defaultModelId != null) add(modelDefaultLabel)
                                    if (folder.kbIdList().isNotEmpty()) add("${folder.kbIdList().size} $sourceDefaultLabel")
                                }
                                if (defaults.isNotEmpty()) {
                                    Text(
                                        defaults.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = Space.xs),
                                    )
                                }
                            }
                        }
                        */
                    }
                }
            }
          }
          }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.folder_new)) },
            text = { BoundedTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.workspace_name), required = true, singleLine = true, maxLength = ValidationLimits.FOLDER_NAME) },
            confirmButton = { TextButton(onClick = {
                if (name.isNotBlank()) { vm.create(name.trim()); showCreate = false }
                else android.widget.Toast.makeText(context, "Folder name is required.", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.action_create)) } },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (confirmBulkDelete) {
        val count = selected.size
        val deletedMessage = stringResource(R.string.folder_deleted_many, count)
        ConfirmDialog(
            title = stringResource(R.string.folder_delete_many_title),
            body = stringResource(R.string.folder_delete_many_body, count),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmBulkDelete = false
                vm.deleteAll(selected)
                selected = emptySet()
                selectionMode = false
                scope.launch { snackbarHostState.showSnackbar(deletedMessage) }
            },
            onDismiss = { confirmBulkDelete = false }
        )
    }
}
