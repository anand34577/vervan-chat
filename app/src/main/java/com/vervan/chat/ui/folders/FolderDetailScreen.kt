package com.vervan.chat.ui.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import com.vervan.chat.ui.common.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.ResponsiveActions
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.SectionCard
import com.vervan.chat.ui.common.SectionRow
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FolderDetailScreen(folderId: String, onBack: () -> Unit, onOpenChat: (String) -> Unit, onOpenNote: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: FolderDetailViewModel = viewModel(factory = viewModelFactory { initializer { FolderDetailViewModel(app, folderId) } })
    val folder by vm.folder.collectAsState()
    val chats by vm.chats.collectAsState()
    val notes by vm.notes.collectAsState()
    val personas by vm.personas.collectAsState()
    val models by vm.models.collectAsState()
    val knowledgeBases by vm.knowledgeBases.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { OverflowTooltipText(folder?.name ?: stringResource(R.string.folder_list_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { showActions = true }, enabled = folder != null) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.workspace_actions))
                    }
                    DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            onClick = { showActions = false; renaming = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                            onClick = { showActions = false; showDeleteConfirm = true }
                        )
                    }
                }
            )
        }
    ) { padding ->
        ScrollablePage(contentPadding = padding) {
            when {
                error != null -> OperationErrorCard(
                    title = stringResource(R.string.folder_unavailable),
                    message = error ?: stringResource(R.string.folder_unavailable_message),
                    recovery = stringResource(R.string.folder_unavailable_recovery),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::retry
                )
                isLoading -> LoadingSkeletonList(rows = 6)
                folder == null -> EmptyState(
                    icon = Icons.Filled.Folder,
                    title = stringResource(R.string.folder_not_found),
                    body = stringResource(R.string.folder_not_found_body),
                    modifier = Modifier.fillMaxSize(),
                    centered = true,
                    actionLabel = stringResource(R.string.action_back),
                    onAction = onBack
                )
                else -> Column(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.folder_defaults), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.folder_defaults_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = Space.sm))

            Text(stringResource(R.string.folder_default_persona), style = MaterialTheme.typography.labelMedium)
            Text(
                if (folder?.defaultPersonaId == null) stringResource(R.string.folder_inherited_persona) else stringResource(R.string.folder_set_persona),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowChips(
                options = listOf(stringResource(R.string.folder_none) to null) + personas.map { it.name to it.id },
                selected = folder?.defaultPersonaId,
                onSelect = { vm.setDefaultPersona(it) }
            )

            Text(stringResource(R.string.folder_default_model), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Space.sm))
            FlowChips(
                options = listOf(stringResource(R.string.folder_none) to null) + models.filter { it.role == ModelRole.GENERATION }.map { it.displayName to it.id },
                selected = folder?.defaultModelId,
                onSelect = { vm.setDefaultModel(it) }
            )

            Text(stringResource(R.string.folder_default_sources), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Space.sm))
            MultiSelectChips(
                options = knowledgeBases.map { it.name to it.id },
                selected = folder?.kbIdList() ?: emptyList(),
                onToggle = { ids -> vm.setDefaultKbs(ids) }
            )

            ResponsiveActions(Modifier.padding(top = Space.md)) {
                OutlinedButton(onClick = { scope.launch { onOpenChat(vm.createChat()) } }) { Text(stringResource(R.string.folder_new_chat)) }
                OutlinedButton(onClick = { scope.launch { onOpenNote(vm.createNote()) } }) { Text(stringResource(R.string.folder_new_note)) }
            }

            HorizontalDivider(Modifier.padding(vertical = Space.md))

            VervanSectionHeader(stringResource(R.string.folder_chats), count = chats.size, topPadding = Space.xs)
            if (chats.isNotEmpty()) {
                SectionCard(
                    items = chats.map { chat ->
                        {
                            SectionRow(title = chat.title, onClick = { onOpenChat(chat.id) })
                        }
                    }
                )
            }
            VervanSectionHeader(stringResource(R.string.folder_notes), count = notes.size)
            if (notes.isNotEmpty()) {
                SectionCard(
                    items = notes.map { note ->
                        {
                            SectionRow(title = note.title, onClick = { onOpenNote(note.id) })
                        }
                    }
                )
            }
                }
            }
        }
    }

    folder?.let { current ->
        if (renaming) {
            var name by remember(current.id) { mutableStateOf(current.name) }
            AlertDialog(
                onDismissRequest = { renaming = false },
                title = { Text(stringResource(R.string.folder_rename_title)) },
                text = { BoundedTextField(value = name, onValueChange = { name = it }, singleLine = true, maxLength = ValidationLimits.FOLDER_NAME) },
                confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { vm.rename(name.trim()); renaming = false } }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
                dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } }
            )
        }
        if (showDeleteConfirm) {
            ConfirmDialog(
                title = stringResource(R.string.folder_delete_title),
                body = stringResource(R.string.folder_delete_body, current.name),
                confirmLabel = stringResource(R.string.action_delete),
                destructive = true,
                onConfirm = { showDeleteConfirm = false; vm.delete(current); onBack() },
                onDismiss = { showDeleteConfirm = false }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(options: List<Pair<String, String?>>, selected: String?, onSelect: (String?) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        options.forEach { (label, id) ->
            VervanFilterChip(selected = selected == id, onClick = { onSelect(id) }, label = { Text(label, maxLines = 1) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSelectChips(options: List<Pair<String, String>>, selected: List<String>, onToggle: (List<String>) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        options.forEach { (label, id) ->
            val isSelected = id in selected
            VervanFilterChip(
                selected = isSelected,
                onClick = { onToggle(if (isSelected) selected - id else selected + id) },
                label = { Text(label, maxLines = 1) }
            )
        }
    }
}
