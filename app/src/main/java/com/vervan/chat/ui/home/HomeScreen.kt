package com.vervan.chat.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.ModelBackend
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.Project
import com.vervan.chat.system.ThermalLevel
import com.vervan.chat.ui.common.ActionTile
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SectionCard
import com.vervan.chat.ui.common.SectionRow
import com.vervan.chat.ui.common.StatusChip
import com.vervan.chat.ui.common.StatusTone
import com.vervan.chat.ui.common.SystemStatusStrip
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenChat: (String) -> Unit,
    onOpenModels: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenChats: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenProject: (String) -> Unit = { onOpenProjects() },
    onOpenKnowledge: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenWorkspaces: () -> Unit = {},
    onOpenDocScanner: () -> Unit = {},
    onOpenVoiceChat: () -> Unit = {},
    onOpenTranslate: () -> Unit = {},
    onOpenWritingAssistant: () -> Unit = {},
    onOpenAllTools: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(app) } })
    val recentChats by vm.recentChats.collectAsState()
    val projects by vm.projects.collectAsState()
    val activeModel by vm.activeModel.collectAsState()
    val indexingDocuments by vm.indexingDocuments.collectAsState()
    val activeWorkspaceName by vm.activeWorkspaceName.collectAsState()
    val thermalLevel by app.container.thermalMonitor.level.collectAsState()
    val scope = rememberCoroutineScope()
    var showAccountMenu by remember { mutableStateOf(false) }

    fun startNewChat() {
        scope.launch { onOpenChat(vm.createChat()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                            }
                        }
                        Column(Modifier.padding(start = Space.md)) {
                            Text("Vervan", style = MaterialTheme.typography.titleMedium)
                            Text(
                                activeWorkspaceName ?: "Private offline workspace",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Filled.Search, "Search workspace") }
                    Box {
                        IconButton(onClick = { showAccountMenu = true }) {
                            Icon(Icons.Filled.MoreVert, "Account menu")
                        }
                        DropdownMenu(
                            expanded = showAccountMenu,
                            onDismissRequest = { showAccountMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Profile") },
                                leadingIcon = { Icon(Icons.Outlined.Person, null) },
                                onClick = { showAccountMenu = false; onOpenProfile() }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                onClick = { showAccountMenu = false; onOpenSettings() }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= 760.dp
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.sm, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.lg)
                ) {
                    HomePrimaryAction(
                        model = activeModel,
                        onStartChat = ::startNewChat,
                        onOpenModels = onOpenModels,
                        onOpenKnowledge = onOpenKnowledge
                    )

                    HomeAlert(
                        thermalLevel = thermalLevel,
                        indexingCount = indexingDocuments.size,
                        onOpenKnowledge = onOpenKnowledge
                    )

                    WorkspaceSnapshot(
                        chatCount = recentChats.size,
                        projectCount = projects.size,
                        indexingCount = indexingDocuments.size,
                        onOpenChats = onOpenChats,
                        onOpenProjects = onOpenProjects,
                        onOpenKnowledge = onOpenKnowledge
                    )

                    if (expanded) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.xxl),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                ContinueSection(recentChats, projects, onOpenChat, onOpenProject, ::startNewChat)
                                QuickStartSection(onOpenKnowledge, onOpenNotes, onOpenProjects, onOpenLibrary)
                            }
                            Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                WorkspaceStatusSection(activeModel, activeWorkspaceName, indexingDocuments.size, onOpenModels, onOpenWorkspaces)
                                ToolsSection(onOpenVoiceChat, onOpenWritingAssistant, onOpenDocScanner, onOpenTranslate, onOpenAllTools)
                            }
                        }
                    } else {
                        ContinueSection(recentChats, projects, onOpenChat, onOpenProject, ::startNewChat)
                        QuickStartSection(onOpenKnowledge, onOpenNotes, onOpenProjects, onOpenLibrary)
                        ToolsSection(onOpenVoiceChat, onOpenWritingAssistant, onOpenDocScanner, onOpenTranslate, onOpenAllTools)
                        WorkspaceStatusSection(activeModel, activeWorkspaceName, indexingDocuments.size, onOpenModels, onOpenWorkspaces)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomePrimaryAction(
    model: ModelInfo?,
    onStartChat: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenKnowledge: () -> Unit
) {
    val modelSelected = model != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = SurfaceRole.Card.cardColors(),
        border = SurfaceRole.Card.border()
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.lg)) {
            Card(
                onClick = if (modelSelected) onStartChat else onOpenModels,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconAffordance(
                        if (modelSelected) Icons.Filled.AutoAwesome else Icons.Outlined.Memory,
                        size = IconAffordanceSize.Compact,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                    Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                        Text(if (modelSelected) "Ask Vervan" else "Choose a local model", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (modelSelected) "Start a new private conversation" else "Open Model Manager to finish setup",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = onOpenKnowledge, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Description, null)
                    Text("Ask documents", Modifier.padding(start = Space.sm))
                }
                if (modelSelected) {
                    TextButton(onClick = onOpenModels, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(model.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeAlert(thermalLevel: ThermalLevel, indexingCount: Int, onOpenKnowledge: () -> Unit) {
    when {
        thermalLevel != ThermalLevel.NORMAL -> SystemStatusStrip(
            title = if (thermalLevel == ThermalLevel.SEVERE) "Thermal slowdown" else "Device warming up",
            body = if (thermalLevel == ThermalLevel.SEVERE) {
                "Generation may pause until the device cools. Your work is already saved."
            } else {
                "Sustained work may slow down to protect battery and performance."
            },
            tone = StatusTone.Warning
        )
        indexingCount > 0 -> SystemStatusStrip(
            title = "Preparing your knowledge",
            body = "Indexing $indexingCount document${if (indexingCount == 1) "" else "s"} for search.",
            tone = StatusTone.Running,
            actionLabel = "View",
            onAction = onOpenKnowledge
        )
    }
}

@Composable
private fun WorkspaceSnapshot(
    chatCount: Int,
    projectCount: Int,
    indexingCount: Int,
    onOpenChats: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenKnowledge: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        SnapshotCard("Chats", chatCount.toString(), Icons.AutoMirrored.Filled.Chat, onOpenChats, Modifier.weight(1f))
        SnapshotCard("Projects", projectCount.toString(), Icons.AutoMirrored.Filled.MenuBook, onOpenProjects, Modifier.weight(1f))
        SnapshotCard(
            "Index",
            if (indexingCount == 0) "Ready" else "$indexingCount active",
            Icons.Filled.Description,
            onOpenKnowledge,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun SnapshotCard(label: String, value: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 80.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.md)) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Space.sm))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContinueSection(
    chats: List<Chat>,
    projects: List<Project>,
    onOpenChat: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onStartChat: () -> Unit
) {
    val chevron: @Composable () -> Unit = {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column {
        VervanSectionHeader("Continue where you left off", count = chats.take(2).size + projects.take(1).size)
        val rows = buildList<@Composable () -> Unit> {
            chats.take(2).forEach { chat ->
                add {
                    SectionRow(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        eyebrow = "Chat",
                        title = chat.title,
                        subtitle = chat.draft.ifBlank { "Continue the conversation" },
                        onClick = { onOpenChat(chat.id) },
                        trailing = chevron
                    )
                }
            }
            projects.take(1).forEach { project ->
                add {
                    SectionRow(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        eyebrow = "Project",
                        title = project.name,
                        subtitle = project.instructions.ifBlank { "Open project workspace" },
                        onClick = { onOpenProject(project.id) },
                        trailing = chevron
                    )
                }
            }
        }
        if (rows.isEmpty()) {
            SectionCard(items = listOf<@Composable () -> Unit>({
                Row(Modifier.fillMaxWidth().padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    IconAffordance(Icons.AutoMirrored.Filled.Chat, size = IconAffordanceSize.Default)
                    Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                        Text("A fresh workspace", style = MaterialTheme.typography.titleSmall)
                        Text("Recent chats and projects will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onStartChat) { Text("Start") }
                }
            }))
        } else {
            SectionCard(items = rows)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickStartSection(
    onOpenKnowledge: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    Column {
        VervanSectionHeader("Start something")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            maxItemsInEachRow = 2
        ) {
            val tile = Modifier.weight(1f).heightIn(min = 76.dp)
            ActionTile(
                Icons.Filled.Description,
                "Ask documents",
                "Search private sources with citations",
                onOpenKnowledge,
                tile,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.46f)
            )
            ActionTile(Icons.Filled.Edit, "Write a note", "Capture and shape an idea", onOpenNotes, tile)
            ActionTile(Icons.AutoMirrored.Filled.MenuBook, "Open projects", "Keep long-running work together", onOpenProjects, tile)
            ActionTile(Icons.AutoMirrored.Filled.LibraryBooks, "Browse library", "Personas, prompts, and saved work", onOpenLibrary, tile)
        }
    }
}

@Composable
private fun WorkspaceStatusSection(
    model: ModelInfo?,
    workspaceName: String?,
    indexingCount: Int,
    onOpenModels: () -> Unit,
    onOpenWorkspaces: () -> Unit
) {
    val modelTone = if (model == null) StatusTone.Warning else StatusTone.Ready
    val indexTone = if (indexingCount > 0) StatusTone.Running else StatusTone.Info
    Column {
        VervanSectionHeader("Local workspace")
        SectionCard(items = listOf<@Composable () -> Unit>(
            {
                SectionRow(
                    icon = Icons.Outlined.Memory,
                    title = model?.displayName ?: "No generation model",
                    subtitle = model?.lastWorkingBackend?.label() ?: "Choose a model to enable chat",
                    onClick = onOpenModels,
                    trailing = { StatusChip(statusLabel(modelTone), modelTone) }
                )
            },
            {
                SectionRow(
                    icon = Icons.Filled.GridView,
                    title = workspaceName ?: "Personal workspace",
                    subtitle = if (indexingCount > 0) "$indexingCount document${if (indexingCount == 1) "" else "s"} indexing" else "Everything is up to date",
                    onClick = onOpenWorkspaces,
                    trailing = { StatusChip(statusLabel(indexTone), indexTone) }
                )
            }
        ))
    }
}

private fun statusLabel(tone: StatusTone): String = when (tone) {
    StatusTone.Ready -> "Selected"
    StatusTone.Running -> "Working"
    StatusTone.Warning -> "Setup"
    else -> "Open"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolsSection(
    onOpenVoiceChat: () -> Unit,
    onOpenWritingAssistant: () -> Unit,
    onOpenDocScanner: () -> Unit,
    onOpenTranslate: () -> Unit,
    onOpenAllTools: () -> Unit,
) {
    val toolGroups = listOf(
        "Popular now" to listOf(
            ModernHomeTool(Icons.Filled.RecordVoiceOver, "Voice chat", "Talk naturally with your local model", onOpenVoiceChat),
            ModernHomeTool(Icons.Filled.EditNote, "Writing assistant", "Rewrite, refine, or change tone", onOpenWritingAssistant),
            ModernHomeTool(Icons.Filled.DocumentScanner, "Document scanner", "Capture pages and extract useful text", onOpenDocScanner),
            ModernHomeTool(Icons.Filled.Translate, "Translate", "Translate text or a photographed page", onOpenTranslate),
        ),
    )
    Column {
        VervanSectionHeader("Choose a mode", actionLabel = "See all", onAction = onOpenAllTools)
        Text(
                    "Choose a task below, or browse the full toolkit in Tools.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        toolGroups.forEach { (group, tools) ->
            Text(
                group,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.md, bottom = Space.sm).semantics { heading() },
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                maxItemsInEachRow = 2,
            ) {
                tools.forEach { tool ->
                    ActionTile(
                        icon = tool.icon,
                        title = tool.label,
                        body = tool.body,
                        onClick = tool.onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private data class ModernHomeTool(
    val icon: ImageVector,
    val label: String,
    val body: String,
    val onClick: () -> Unit,
)

private fun ModelBackend.label(): String = when (this) {
    ModelBackend.NPU -> "NPU backend"
    ModelBackend.GPU -> "GPU backend"
    ModelBackend.CPU -> "CPU backend"
    ModelBackend.UNVERIFIED -> "Backend not verified"
}
