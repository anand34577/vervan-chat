package com.vervan.chat.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanTopAppBar as MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.PromptTemplate
import com.vervan.chat.data.db.entities.Workflow
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.common.setText
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.workflows.WorkflowListViewModel
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch

private val libTabs = listOf("Personas", "Templates", "Workflows", "Saved")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPersona: (String) -> Unit,
    onNewPersona: () -> Unit,
    onOpenWorkflow: (String) -> Unit = {},
    onNewWorkflow: () -> Unit = {},
    onEditWorkflow: (String) -> Unit = {},
    onOpenTemplate: (String) -> Unit = {},
    onNewTemplate: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as VervanApp
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val allOutputs by app.container.db.savedOutputDao().observeAll().collectAsState(initial = emptyList())
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selected.size,
                    allSelected = selected.size == allOutputs.size && allOutputs.isNotEmpty(),
                    onToggleSelectAll = { selected = if (selected.size == allOutputs.size && allOutputs.isNotEmpty()) emptySet() else allOutputs.map { it.id }.toSet() },
                    onExit = { selected = emptySet(); selectionMode = false },
                    onDelete = {
                        val count = selected.size
                        scope.launch {
                            val now = System.currentTimeMillis()
                            allOutputs.filter { it.id in selected }.forEach { app.container.db.savedOutputDao().upsert(it.copy(deletedAt = now)) }
                            snackbarHostState.showSnackbar("Deleted $count saved output${if (count == 1) "" else "s"}")
                        }
                        selected = emptySet()
                        selectionMode = false
                    },
                    deleteContentDescription = "Delete selected"
                )
            } else {
                MediumTopAppBar(
                    title = {
                        Column {
                            Text("Library")
                            Text("Reusable building blocks", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        if (tab == 0) IconButton(onClick = onNewPersona) { Icon(Icons.Filled.Add, contentDescription = "New persona") }
                        if (tab == 1) IconButton(onClick = onNewTemplate) { Icon(Icons.Filled.Add, contentDescription = "New template") }
                        if (tab == 2) IconButton(onClick = onNewWorkflow) { Icon(Icons.Filled.Add, contentDescription = "New workflow") }
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
            FeatureHero(
                icon = Icons.Filled.AutoAwesome,
                eyebrow = "Create once, reuse anywhere",
                title = libTabs[tab],
                body = when (tab) {
                    0 -> "Give chats a focused voice, expertise, and working style."
                    1 -> "Turn your best prompts into fast, consistent starting points."
                    2 -> "Chain repeatable local actions into transparent workflows."
                    else -> "Keep useful responses close for quick reuse."
                },
                modifier = Modifier.padding(top = Space.sm)
            )
            androidx.compose.material3.SecondaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
                libTabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index; selectionMode = false; selected = emptySet() },
                        text = { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    )
                }
            }
            VervanSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search ${libTabs[tab].lowercase()}",
                modifier = Modifier.padding(vertical = Space.sm)
            )
            when (tab) {
                0 -> PersonasTab(app, query, onOpenPersona, onNewPersona)
                1 -> TemplatesTab(app, query, onOpenTemplate)
                2 -> WorkflowsTab(app, query, onOpenWorkflow, onEditWorkflow)
                3 -> SavedTab(
                    app = app,
                    query = query,
                    outputs = allOutputs,
                    selectionMode = selectionMode,
                    selected = selected,
                    onToggleSelected = { id -> selected = if (id in selected) selected - id else selected + id },
                    onEnterSelection = { id -> selectionMode = true; selected = selected + id }
                )
            }
          }
        }
    }
}

@Composable
private fun PersonasTab(app: VervanApp, query: String, onOpenPersona: (String) -> Unit, onNewPersona: () -> Unit) {
    val allPersonas by app.container.db.personaDao().observePersonas().collectAsState(initial = emptyList())
    val personas = remember(allPersonas, query) { allPersonas.filter { it.name.contains(query, ignoreCase = true) } }
    if (personas.isEmpty() && query.isNotBlank()) {
        EmptyState(Icons.Outlined.Person, "No matching personas", "Try another name or clear your search.")
        return
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(220.dp), modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        items(personas, key = { it.id }) { persona -> PersonaCard(persona, onClick = { onOpenPersona(persona.id) }) }
        item {
            Card(
                onClick = onNewPersona,
                modifier = Modifier.padding(6.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("New persona", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun PersonaCard(persona: Persona, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.padding(6.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(persona.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (persona.description.isNotBlank()) {
                    Text(
                        persona.description, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplatesTab(app: VervanApp, query: String, onOpenTemplate: (String) -> Unit) {
    val allTemplates by app.container.db.promptTemplateDao().observeAll().collectAsState(initial = emptyList())
    val templates = remember(allTemplates, query) { allTemplates.filter { it.name.contains(query, ignoreCase = true) } }
    if (templates.isEmpty()) {
        EmptyState(
            Icons.Outlined.Description,
            if (query.isBlank()) "No templates yet" else "No matching templates",
            if (query.isBlank()) "Create a reusable prompt from the add button above." else "Try another search."
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        items(templates, key = { it.id }) { template -> TemplateCard(template, onClick = { onOpenTemplate(template.id) }) }
    }
}

@Composable
private fun TemplateCard(template: PromptTemplate, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(Space.lg)) {
            Text("/${template.name}", style = MaterialTheme.typography.titleSmall)
            if (template.description.isNotBlank()) {
                Text(
                    template.description, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun WorkflowsTab(app: VervanApp, query: String, onOpenWorkflow: (String) -> Unit, onEditWorkflow: (String) -> Unit) {
    val vm: WorkflowListViewModel = viewModel(factory = viewModelFactory { initializer { WorkflowListViewModel(app) } })
    val allWorkflows by vm.workflows.collectAsState()
    val workflows = remember(allWorkflows, query) { allWorkflows.filter { it.name.contains(query, ignoreCase = true) } }
    if (workflows.isEmpty()) {
        EmptyState(
            Icons.Outlined.AccountTree,
            if (query.isBlank()) "No workflows yet" else "No matching workflows",
            if (query.isBlank()) "Build a repeatable sequence from the add button above." else "Try another search."
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        items(workflows, key = { it.id }) { workflow -> WorkflowCard(workflow, onClick = { onOpenWorkflow(workflow.id) }, onEdit = { onEditWorkflow(workflow.id) }) }
    }
}

@Composable
private fun WorkflowCard(workflow: Workflow, onClick: () -> Unit, onEdit: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(workflow.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${workflow.steps.size} step(s)" + if (workflow.description.isNotBlank()) " · ${workflow.description}" else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onEdit) { Text("Edit") }
        }
    }
}

@Composable
private fun SavedTab(
    app: VervanApp,
    query: String,
    outputs: List<SavedOutput>,
    selectionMode: Boolean,
    selected: Set<String>,
    onToggleSelected: (String) -> Unit,
    onEnterSelection: (String) -> Unit
) {
    val filtered = remember(outputs, query) { outputs.filter { it.content.contains(query, ignoreCase = true) } }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    if (filtered.isEmpty()) {
        EmptyState(
            Icons.Outlined.BookmarkBorder,
            if (query.isBlank()) "No saved outputs yet" else "No matching saved outputs",
            if (query.isBlank()) "Save a response from chat and it will appear here for reuse." else "Try another search."
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        items(filtered, key = { it.id }) { output ->
            val isSelected = output.id in selected
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .selectableItem(
                        selectionMode = selectionMode,
                        onClick = {},
                        onToggleSelected = { onToggleSelected(output.id) },
                        onEnterSelection = { onEnterSelection(output.id) }
                    ),
                colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)) else null
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelected(output.id) },
                            colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.outline)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(output.content.take(300), style = MaterialTheme.typography.bodyMedium)
                        if (!selectionMode) {
                            Row {
                                TextButton(onClick = { clipboard.setText(output.content, scope) }) { Text("Copy") }
                                TextButton(onClick = { scope.launch { app.container.db.savedOutputDao().upsert(output.copy(deletedAt = System.currentTimeMillis())) } }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}
