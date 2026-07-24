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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.PromptTemplate
import com.vervan.chat.data.db.entities.Workflow
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SelectionTopBar
import com.vervan.chat.ui.common.selectableItem
import com.vervan.chat.ui.common.setText
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.ui.theme.Space
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val allPersonas by app.container.db.personaDao().observePersonas().collectAsState(initial = emptyList())
    val allTemplates by app.container.db.promptTemplateDao().observeAll().collectAsState(initial = emptyList())
    val allWorkflows by app.container.db.workflowDao().observeAll().collectAsState(initial = emptyList())
    val allOutputs by app.container.db.savedOutputDao().observeAll().collectAsState(initial = emptyList())
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectableIds = remember(tab, query, allPersonas, allTemplates, allWorkflows, allOutputs) {
        when (tab) {
            0 -> allPersonas.filter { !it.isBuiltIn && it.name.contains(query, ignoreCase = true) }.map { it.id }.toSet()
            1 -> allTemplates.filter { !it.isBuiltIn && it.name.contains(query, ignoreCase = true) }.map { it.id }.toSet()
            2 -> allWorkflows.filter { !it.isBuiltIn && it.name.contains(query, ignoreCase = true) }.map { it.id }.toSet()
            else -> allOutputs.filter { it.content.contains(query, ignoreCase = true) }.map { it.id }.toSet()
        }
    }

    Scaffold(
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
                            val now = System.currentTimeMillis()
                            when (targetTab) {
                                0 -> allPersonas.filter { it.id in ids && !it.isBuiltIn }.forEach { persona ->
                                    app.container.db.chatDao().clearPersona(persona.id)
                                    app.container.db.folderDao().clearDefaultPersona(persona.id)
                                    app.container.db.projectDao().clearPersona(persona.id)
                                    app.container.db.knowledgeBaseDao().clearDefaultPersona(persona.id)
                                    app.container.db.personaDao().upsert(persona.copy(deletedAt = now))
                                }
                                1 -> allTemplates.filter { it.id in ids && !it.isBuiltIn }.forEach { app.container.db.promptTemplateDao().upsert(it.copy(deletedAt = now)) }
                                2 -> allWorkflows.filter { it.id in ids && !it.isBuiltIn }.forEach { app.container.db.workflowDao().upsert(it.copy(deletedAt = now)) }
                                else -> allOutputs.filter { it.id in ids }.forEach { app.container.db.savedOutputDao().upsert(it.copy(deletedAt = now)) }
                            }
                            snackbarHostState.showSnackbar("Moved $count item${if (count == 1) "" else "s"} to the recycle bin")
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
            androidx.compose.material3.SecondaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
                libTabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index; query = ""; selectionMode = false; selected = emptySet() },
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
                0 -> PersonasTab(allPersonas, query, onOpenPersona, onNewPersona, selectionMode, selected, { id -> selected = if (id in selected) selected - id else selected + id }, { id -> selectionMode = true; selected = selected + id })
                1 -> TemplatesTab(allTemplates, query, onOpenTemplate, selectionMode, selected, { id -> selected = if (id in selected) selected - id else selected + id }, { id -> selectionMode = true; selected = selected + id })
                2 -> WorkflowsTab(allWorkflows, query, onOpenWorkflow, onEditWorkflow, selectionMode, selected, { id -> selected = if (id in selected) selected - id else selected + id }, { id -> selectionMode = true; selected = selected + id })
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
            if (query.isBlank()) "No personas yet" else "No matching personas",
            if (query.isBlank()) "Create a persona to give chats a reusable voice and working style." else "Try another name or clear your search.",
            actionLabel = if (query.isBlank()) "New persona" else null,
            onAction = if (query.isBlank()) onNewPersona else null
        )
        return
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(220.dp), modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.md)) {
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
private fun PersonaCard(
    persona: Persona,
    onClick: () -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit
) {
    Card(
        modifier = Modifier.padding(6.dp).fillMaxWidth().selectableItem(
            selectionMode = selectionMode,
            onClick = onClick,
            onToggleSelected = onToggleSelected,
            onEnterSelection = onEnterSelection,
            selectable = !persona.isBuiltIn
        ),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null
    ) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode && !persona.isBuiltIn) Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            val avatar = remember(persona.avatarPath) {
                persona.avatarPath?.let { com.vervan.chat.model.ImageUtils.decodeThumbnail(it, 128) }
            }
            Box(
                Modifier.size(32.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (avatar != null) {
                    androidx.compose.foundation.Image(
                        bitmap = avatar.asImageBitmap(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(MaterialTheme.shapes.small)
                    )
                } else {
                    Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
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
            if (query.isBlank()) "No templates yet" else "No matching templates",
            if (query.isBlank()) "Create a reusable prompt from the add button above." else "Try another search."
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.md)) {
        items(templates, key = { it.id }) { template ->
            TemplateCard(template, { onOpenTemplate(template.id) }, template.id in selected, selectionMode, { onToggleSelected(template.id) }, { onEnterSelection(template.id) })
        }
    }
}

@Composable
private fun TemplateCard(template: PromptTemplate, onClick: () -> Unit, selected: Boolean, selectionMode: Boolean, onToggleSelected: () -> Unit, onEnterSelection: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs).selectableItem(selectionMode, onClick, onToggleSelected, onEnterSelection, selectable = !template.isBuiltIn),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null
    ) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode && !template.isBuiltIn) Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            Column(Modifier.weight(1f)) {
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
            if (query.isBlank()) "No workflows yet" else "No matching workflows",
            if (query.isBlank()) "Build a repeatable sequence from the add button above." else "Try another search."
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.md)) {
        items(workflows, key = { it.id }) { workflow ->
            WorkflowCard(workflow, { onOpenWorkflow(workflow.id) }, { onEditWorkflow(workflow.id) }, workflow.id in selected, selectionMode, { onToggleSelected(workflow.id) }, { onEnterSelection(workflow.id) })
        }
    }
}

@Composable
private fun WorkflowCard(workflow: Workflow, onClick: () -> Unit, onEdit: () -> Unit, selected: Boolean, selectionMode: Boolean, onToggleSelected: () -> Unit, onEnterSelection: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs).selectableItem(selectionMode, onClick, onToggleSelected, onEnterSelection, selectable = !workflow.isBuiltIn),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null
    ) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode && !workflow.isBuiltIn) Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            Column(Modifier.weight(1f)) {
                Text(workflow.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${workflow.steps.size} step(s)" + if (workflow.description.isNotBlank()) " · ${workflow.description}" else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (!selectionMode) TextButton(onClick = onEdit) { Text("Edit") }
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
    val filtered = remember(outputs, query) { outputs.filter { it.content.contains(query, ignoreCase = true) || it.label.contains(query, ignoreCase = true) } }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (filtered.isEmpty()) {
        EmptyState(
            Icons.Outlined.BookmarkBorder,
            if (query.isBlank()) "No saved outputs yet" else "No matching saved outputs",
            if (query.isBlank()) "Save a response from chat and it will appear here for reuse." else "Try another search."
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.md)) {
        items(filtered, key = { it.id }) { output ->
            val isSelected = output.id in selected
            var expanded by remember(output.id) { mutableStateOf(false) }
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .selectableItem(
                        selectionMode = selectionMode,
                        onClick = { expanded = !expanded },
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
                        Text(
                            output.label.takeIf { it.isNotBlank() && !it.contains('-') } ?: "Saved output",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            buildString {
                                append(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(output.createdAt)))
                                if (output.sourceChatId != null) append(" · From chat")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
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
                                TextButton(onClick = { clipboard.setText(output.content, scope) }) { Text("Copy") }
                                TextButton(onClick = {
                                    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, output.content)
                                    }, "Share saved output"))
                                }) { Text("Share") }
                                TextButton(onClick = { scope.launch { app.container.db.savedOutputDao().upsert(output.copy(deletedAt = System.currentTimeMillis())) } }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}
