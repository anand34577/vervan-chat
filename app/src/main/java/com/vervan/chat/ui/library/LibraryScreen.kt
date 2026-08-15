package com.vervan.chat.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanTopAppBar as MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.PromptTemplate
import com.vervan.chat.data.db.entities.Workflow
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.ModernistScreenHeader
import com.vervan.chat.ui.common.ModernistTag
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.common.setText
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.common.rememberThumbnail
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.ModernistTokens
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R

private val libTabs = listOf(
    R.string.library_tab_personas,
    R.string.library_tab_templates,
    R.string.library_tab_workflows,
    R.string.library_tab_saved
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPersona: (String) -> Unit,
    onNewPersona: () -> Unit,
    onOpenWorkflow: (String) -> Unit = {},
    onNewWorkflow: () -> Unit = {},
    onEditWorkflow: (String) -> Unit = {},
    onOpenTemplate: (String) -> Unit = {},
    onNewTemplate: () -> Unit = {},
    onOpenNotes: () -> Unit = {}
) {
    val appContext = LocalContext.current.applicationContext as com.vervan.chat.VervanApp
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(appContext) } })
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val allPersonas by vm.personas.collectAsStateWithLifecycle()
    val allTemplates by vm.templates.collectAsStateWithLifecycle()
    val allWorkflows by vm.workflows.collectAsStateWithLifecycle()
    val allOutputs by vm.savedOutputs.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = tab, pageCount = { libTabs.size })
    val snackbarHostState = remember { SnackbarHostState() }
    val currentTabLabel = stringResource(libTabs[tab])
    val deleteSavedSuccess = stringResource(R.string.library_delete_saved_success)
    val deleteSavedError = stringResource(R.string.library_delete_saved_error)
    val selectableIds = remember(tab, query, allPersonas, allTemplates, allWorkflows, allOutputs) {
        when (tab) {
            0 -> allPersonas.filter { !it.isBuiltIn && it.name.contains(query, ignoreCase = true) }.map { it.id }.toSet()
            1 -> allTemplates.filter { !it.isBuiltIn && it.name.contains(query, ignoreCase = true) }.map { it.id }.toSet()
            2 -> allWorkflows.filter { !it.isBuiltIn && it.name.contains(query, ignoreCase = true) }.map { it.id }.toSet()
            else -> allOutputs.filter { it.content.contains(query, ignoreCase = true) }.map { it.id }.toSet()
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (tab != pagerState.currentPage) {
            tab = pagerState.currentPage
            query = ""
            selectionMode = false
            selected = emptySet()
        }
    }

    Scaffold(
        // The navigation shell already reserves the bottom navigation and gesture area.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selected.size,
                    allSelected = selectableIds.isNotEmpty() && selectableIds.all { it in selected },
                    onToggleSelectAll = { selected = if (selectableIds.isNotEmpty() && selectableIds.all { it in selected }) selected - selectableIds else selected + selectableIds },
                    onExit = { selected = emptySet(); selectionMode = false },
                    onDelete = {
                        val ids = selected
                        val count = ids.size
                        val targetTab = tab
                        scope.launch {
                            val result = vm.deleteSelected(targetTab, ids).await()
                            snackbarHostState.showSnackbar(
                                if (result.isSuccess) {
                                    "Moved $count item${if (count == 1) "" else "s"} to the recycle bin"
                                } else {
                                    "Could not move the selected item${if (count == 1) "" else "s"}. Try again."
                                }
                            )
                        }
                        selected = emptySet()
                        selectionMode = false
                    },
                    deleteContentDescription = stringResource(R.string.library_delete_selected)
                )
            } else {
                MediumTopAppBar(
                    title = { Text(stringResource(R.string.library_title)) },
                    actions = {
                        IconButton(onClick = onOpenNotes) { Icon(Icons.Outlined.NoteAlt, contentDescription = stringResource(R.string.library_open_notes)) }
                        if (tab == 0) IconButton(onClick = onNewPersona) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.library_new_persona)) }
                        if (tab == 1) IconButton(onClick = onNewTemplate) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.library_new_template)) }
                        if (tab == 2) IconButton(onClick = onNewWorkflow) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.library_new_workflow)) }
                    }
                    // Long-press a row (Saved tab) to enter selection mode — no separate
                    // top-bar entry point, matching every other list screen in the app.
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
          Column(Modifier.fillMaxSize()) {
            ModernistScreenHeader(
                eyebrow = stringResource(R.string.ui_libraryscreen_185_reusable_context),
                title = stringResource(R.string.ui_libraryscreen_186_your_building_blocks),
                body = stringResource(R.string.ui_libraryscreen_187_save_the_context_prompts_workflows_and_answe),
                trailing = { ModernistTag(currentTabLabel.uppercase(), active = true) }
            )
            androidx.compose.material3.SecondaryScrollableTabRow(selectedTabIndex = tab, edgePadding = Space.md) {
                libTabs.forEachIndexed { index, labelRes ->
                    Tab(
                        selected = tab == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { Text(stringResource(labelRes), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    )
                }
            }
            VervanSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.library_search_placeholder, currentTabLabel.lowercase()),
                modifier = Modifier.padding(vertical = Space.sm)
            )
            when {
                error != null -> OperationErrorCard(
                    title = stringResource(R.string.library_unavailable),
                    message = error ?: stringResource(R.string.library_unavailable_message),
                    recovery = stringResource(R.string.library_unavailable_recovery),
                    modifier = Modifier.padding(top = Space.md),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::retry
                )
                isLoading -> LoadingSkeletonList(rows = 6, modifier = Modifier.padding(top = Space.md))
                else -> HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> PersonasTab(allPersonas, query, onOpenPersona, onNewPersona, selectionMode, selected, { id -> selected = if (id in selected) selected - id else selected + id }, { id -> selectionMode = true; selected = selected + id })
                        1 -> TemplatesTab(allTemplates, query, onOpenTemplate, selectionMode, selected, { id -> selected = if (id in selected) selected - id else selected + id }, { id -> selectionMode = true; selected = selected + id })
                        2 -> WorkflowsTab(allWorkflows, query, onOpenWorkflow, onEditWorkflow, selectionMode, selected, { id -> selected = if (id in selected) selected - id else selected + id }, { id -> selectionMode = true; selected = selected + id })
                        else -> SavedTab(
                            query = query,
                            outputs = allOutputs,
                            selectionMode = selectionMode,
                            selected = selected,
                            onToggleSelected = { id -> selected = if (id in selected) selected - id else selected + id },
                            onEnterSelection = { id -> selectionMode = true; selected = selected + id },
                            onDelete = { output ->
                                scope.launch {
                                    val result = vm.deleteSaved(output).await()
                                    snackbarHostState.showSnackbar(
                                        if (result.isSuccess) deleteSavedSuccess else deleteSavedError
                                    )
                                }
                            }
                        )
                    }
                }
            }
          }
        }
    }
}

@Composable
private fun PersonasTab(
    allPersonas: List<Persona>,
    query: String,
    onOpenPersona: (String) -> Unit,
    onNewPersona: () -> Unit,
    selectionMode: Boolean,
    selected: Set<String>,
    onToggleSelected: (String) -> Unit,
    onEnterSelection: (String) -> Unit
) {
    val personas = remember(allPersonas, query) { allPersonas.filter { it.name.contains(query, ignoreCase = true) } }
    if (personas.isEmpty()) {
        EmptyState(
            Icons.Outlined.Person,
            if (query.isBlank()) stringResource(R.string.library_personas_empty) else stringResource(R.string.library_personas_no_match),
                    if (query.isBlank()) stringResource(R.string.library_personas_empty_body) else stringResource(R.string.library_try_another_name),
            actionLabel = if (query.isBlank()) stringResource(R.string.library_new_persona) else null,
            onAction = if (query.isBlank()) onNewPersona else null,
            modifier = Modifier.fillMaxSize(),
            centered = true
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Space.sm, bottom = Space.md),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.md)
    ) {
        items(personas, key = { it.id }) { persona ->
            PersonaCard(
                persona = persona,
                onClick = { onOpenPersona(persona.id) },
                selected = persona.id in selected,
                selectionMode = selectionMode,
                onToggleSelected = { onToggleSelected(persona.id) },
                onEnterSelection = { onEnterSelection(persona.id) }
            )
        }
        if (!selectionMode) item {
            Surface(
                onClick = onNewPersona,
                modifier = Modifier.fillMaxWidth().heightIn(min = ModernistTokens.Layout.rowMinHeight),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconAffordance(
                        icon = Icons.Filled.Add,
                        size = IconAffordanceSize.Compact,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    Text(
                        stringResource(R.string.library_new_persona_card),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = Space.md)
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonaCard(
    persona: Persona,
    onClick: () -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = ModernistTokens.Layout.rowMinHeight).selectableItem(
            selectionMode = selectionMode,
            onClick = onClick,
            onToggleSelected = onToggleSelected,
            onEnterSelection = onEnterSelection,
            selectable = !persona.isBuiltIn
        ),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
        shape = MaterialTheme.shapes.small
    ) {
        Row(Modifier.padding(horizontal = Space.lg, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode && !persona.isBuiltIn) Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            val avatar = rememberThumbnail(
                persona.avatarPath?.takeUnless { it.startsWith("emoji:") }, 128
            )
            val emoji = persona.avatarPath?.takeIf { it.startsWith("emoji:") }?.removePrefix("emoji:")
            Box(
                Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(
                    if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer
                ),
                contentAlignment = Alignment.Center
            ) {
                if (avatar != null) {
                    androidx.compose.foundation.Image(
                        bitmap = avatar,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
                    )
                } else if (emoji != null) {
                    Text(emoji, style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(
                        persona.name.trim().firstOrNull()?.uppercase() ?: "P",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = Space.sm)) {
                OverflowTooltipText(
                    text = persona.name,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    persona.description.ifBlank { "Personal response style" },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }
        }
    }
}

@Composable
private fun TemplatesTab(
    allTemplates: List<PromptTemplate>,
    query: String,
    onOpenTemplate: (String) -> Unit,
    selectionMode: Boolean,
    selected: Set<String>,
    onToggleSelected: (String) -> Unit,
    onEnterSelection: (String) -> Unit
) {
    val templates = remember(allTemplates, query) { allTemplates.filter { it.name.contains(query, ignoreCase = true) } }
    if (templates.isEmpty()) {
        EmptyState(
            Icons.Outlined.Description,
            if (query.isBlank()) stringResource(R.string.library_templates_empty) else stringResource(R.string.library_templates_no_match),
            if (query.isBlank()) stringResource(R.string.library_templates_empty_body) else stringResource(R.string.library_try_another_search),
            modifier = Modifier.fillMaxSize(),
            centered = true
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Space.sm, bottom = Space.md), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.md)) {
        items(templates, key = { it.id }) { template ->
            TemplateCard(template, { onOpenTemplate(template.id) }, template.id in selected, selectionMode, { onToggleSelected(template.id) }, { onEnterSelection(template.id) })
        }
    }
}

@Composable
private fun TemplateCard(template: PromptTemplate, onClick: () -> Unit, selected: Boolean, selectionMode: Boolean, onToggleSelected: () -> Unit, onEnterSelection: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = ModernistTokens.Layout.rowMinHeight).selectableItem(selectionMode, onClick, onToggleSelected, onEnterSelection, selectable = !template.isBuiltIn),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
        shape = MaterialTheme.shapes.small
    ) {
        Row(Modifier.padding(horizontal = Space.lg, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode && !template.isBuiltIn) Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            IconAffordance(
                icon = Icons.Outlined.Description,
                size = IconAffordanceSize.Compact,
                tint = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer
            )
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text("/${template.name}", style = MaterialTheme.typography.titleSmall)
                if (template.description.isNotBlank()) {
                    Text(
                        template.description, style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Space.xs)
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkflowsTab(
    allWorkflows: List<Workflow>,
    query: String,
    onOpenWorkflow: (String) -> Unit,
    onEditWorkflow: (String) -> Unit,
    selectionMode: Boolean,
    selected: Set<String>,
    onToggleSelected: (String) -> Unit,
    onEnterSelection: (String) -> Unit
) {
    val workflows = remember(allWorkflows, query) { allWorkflows.filter { it.name.contains(query, ignoreCase = true) } }
    if (workflows.isEmpty()) {
        EmptyState(
            Icons.Outlined.AccountTree,
            if (query.isBlank()) stringResource(R.string.library_workflows_empty) else stringResource(R.string.library_workflows_no_match),
            if (query.isBlank()) stringResource(R.string.library_workflows_empty_body) else stringResource(R.string.library_try_another_search),
            modifier = Modifier.fillMaxSize(),
            centered = true
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Space.sm, bottom = Space.md), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.md)) {
        items(workflows, key = { it.id }) { workflow ->
            WorkflowCard(workflow, { onOpenWorkflow(workflow.id) }, { onEditWorkflow(workflow.id) }, workflow.id in selected, selectionMode, { onToggleSelected(workflow.id) }, { onEnterSelection(workflow.id) })
        }
    }
}

@Composable
private fun WorkflowCard(workflow: Workflow, onClick: () -> Unit, onEdit: () -> Unit, selected: Boolean, selectionMode: Boolean, onToggleSelected: () -> Unit, onEnterSelection: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = ModernistTokens.Layout.rowMinHeight).selectableItem(selectionMode, onClick, onToggleSelected, onEnterSelection, selectable = !workflow.isBuiltIn),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
        shape = MaterialTheme.shapes.small
    ) {
        Row(Modifier.padding(horizontal = Space.lg, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode && !workflow.isBuiltIn) Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            IconAffordance(
                icon = Icons.Outlined.AccountTree,
                size = IconAffordanceSize.Compact,
                tint = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer
            )
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text(workflow.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.library_workflow_steps, workflow.steps.size) + if (workflow.description.isNotBlank()) " · ${workflow.description}" else "",
                    style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }
            if (!selectionMode) TextButton(onClick = onEdit) { Text(stringResource(R.string.library_edit)) }
        }
    }
}

@Composable
private fun SavedTab(
    query: String,
    outputs: List<SavedOutput>,
    selectionMode: Boolean,
    selected: Set<String>,
    onToggleSelected: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onDelete: (SavedOutput) -> Unit
) {
    val filtered = remember(outputs, query) { outputs.filter { it.content.contains(query, ignoreCase = true) || it.label.contains(query, ignoreCase = true) } }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val shareSavedOutputLabel = stringResource(R.string.library_share)
    val fromChatLabel = stringResource(R.string.library_from_chat)

    if (filtered.isEmpty()) {
        EmptyState(
            Icons.Outlined.BookmarkBorder,
            if (query.isBlank()) stringResource(R.string.library_saved_empty) else stringResource(R.string.library_saved_no_match),
            if (query.isBlank()) stringResource(R.string.library_saved_empty_body) else stringResource(R.string.library_try_another_search),
            modifier = Modifier.fillMaxSize(),
            centered = true
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Space.sm, bottom = Space.md), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.md)) {
        items(filtered, key = { it.id }) { output ->
            val isSelected = output.id in selected
            var expanded by remember(output.id) { mutableStateOf(false) }
            Surface(
                Modifier.fillMaxWidth()
                    .heightIn(min = ModernistTokens.Layout.rowMinHeight)
                    .selectableItem(
                        selectionMode = selectionMode,
                        onClick = { expanded = !expanded },
                        onToggleSelected = { onToggleSelected(output.id) },
                        onEnterSelection = { onEnterSelection(output.id) }
                    ),
                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)) else null,
                shape = MaterialTheme.shapes.small
            ) {
                Row(Modifier.padding(horizontal = Space.lg, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelected(output.id) },
                            colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.outline)
                        )
                    }
                    IconAffordance(
                        icon = Icons.Outlined.BookmarkBorder,
                        size = IconAffordanceSize.Compact,
                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer
                    )
                    Column(Modifier.weight(1f).padding(start = Space.md)) {
                        Text(
                            output.label.takeIf { it.isNotBlank() && !it.contains('-') } ?: stringResource(R.string.library_saved_output),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            buildString {
                                append(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(output.createdAt)))
                                if (output.sourceChatId != null) append(" · ").append(fromChatLabel)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                        Text(
                            output.content,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) 20 else 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Space.sm),
                        )
                        if (!selectionMode) {
                            Row {
                                TextButton(onClick = { clipboard.setText(output.content, scope) }) { Text(stringResource(R.string.library_copy)) }
                                TextButton(onClick = {
                                    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, output.content)
                                    }, shareSavedOutputLabel))
                                }) { Text(stringResource(R.string.library_share)) }
                                TextButton(onClick = { onDelete(output) }) { Text(stringResource(R.string.library_delete)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
