package com.vervan.chat.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
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
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.ui.theme.vervanAccentFor
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

    fun startNewChat() {
        scope.launch { onOpenChat(vm.createChat()) }
    }

    // Hero quick-ask: the typed text lands in the new chat's composer, so the thought started
    // on Home finishes in the chat without retyping. Blank text just opens an empty chat.
    fun askVervan(text: String) {
        scope.launch { onOpenChat(vm.createChat(text)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
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
                    IconButton(onClick = onOpenProfile) { Icon(Icons.Outlined.Person, "Profile") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= 760.dp
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.xs, bottom = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    HomeHero(
                        workspaceName = activeWorkspaceName,
                        model = activeModel,
                        onAsk = ::askVervan,
                        onOpenModels = onOpenModels,
                        onOpenKnowledge = onOpenKnowledge
                    )

                    HomeAlert(
                        thermalLevel = thermalLevel,
                        indexingCount = indexingDocuments.size,
                        onOpenKnowledge = onOpenKnowledge
                    )

                    if (expanded) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.xxl),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                ContinueCarousel(recentChats, projects, onOpenChat, onOpenProject, ::startNewChat, onOpenChats)
                                QuickStartSection(onOpenKnowledge, onOpenNotes, onOpenProjects, onOpenLibrary)
                            }
                            Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                ToolsSection(onOpenVoiceChat, onOpenWritingAssistant, onOpenDocScanner, onOpenTranslate, onOpenAllTools)
                            }
                        }
                    } else {
                        ContinueCarousel(recentChats, projects, onOpenChat, onOpenProject, ::startNewChat, onOpenChats)
                        QuickStartSection(onOpenKnowledge, onOpenNotes, onOpenProjects, onOpenLibrary)
                        ToolsSection(onOpenVoiceChat, onOpenWritingAssistant, onOpenDocScanner, onOpenTranslate, onOpenAllTools)
                    }
                }
            }
        }
    }
}

/**
 * The one hero surface on Home: greeting, privacy badge, and a *working* quick-ask composer on a
 * primary→secondary gradient. Typing here and hitting Send opens a new chat with the text already
 * in its composer, so the thought that started on Home finishes in the chat without retyping.
 * With no model installed the composer gives way to the setup CTA — nothing else on the screen
 * pretends chat works before a model exists.
 */
@Composable
private fun HomeHero(
    workspaceName: String?,
    model: ModelInfo?,
    onAsk: (String) -> Unit,
    onOpenModels: () -> Unit,
    onOpenKnowledge: () -> Unit
) {
    val heroFg = MaterialTheme.colorScheme.onPrimary
    // Not remembered — an app left open across a time boundary should not greet
    // "Good morning" all evening. Recomputing on recomposition is trivially cheap.
    val greeting =
        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 0..4 -> "Working late"
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(VervanExtraShapes.hero)
            .background(com.vervan.chat.ui.theme.vervanBrandGradient())
            .padding(Space.lg)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.headlineMedium, color = heroFg)
                    Text(
                        workspaceName?.let { "$it · fully offline" } ?: "Private · everything stays on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = heroFg.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Space.xs)
                    )
                }
                // Privacy badge — the visual anchor for the app's core promise.
                Box(
                    Modifier.size(40.dp).background(heroFg.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "Offline and private", tint = heroFg, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(Space.md))
            if (model != null) {
                QuickAskField(fg = heroFg, onAsk = onAsk)
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    HeroChip(Icons.Outlined.Memory, model.displayName, heroFg, onOpenModels, Modifier.weight(1f, fill = false))
                    HeroChip(Icons.Filled.Description, "Ask documents", heroFg, onOpenKnowledge)
                }
            } else {
                Surface(
                    onClick = onOpenModels,
                    shape = VervanExtraShapes.pill,
                    color = heroFg,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        Modifier.padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        Icon(Icons.Outlined.Memory, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("Choose a local model", style = MaterialTheme.typography.titleSmall)
                    }
                }
                Text(
                    "Pick a model once — chat, tools, and documents all unlock, fully offline.",
                    style = MaterialTheme.typography.labelSmall,
                    color = heroFg.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = Space.sm)
                )
            }
        }
    }
}

/** Frosted single-line composer on the hero. Send (button or IME action) opens a new chat with
 * this text as its draft; blank send just opens an empty chat. */
@Composable
private fun QuickAskField(fg: androidx.compose.ui.graphics.Color, onAsk: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    fun submit() {
        val t = text
        text = ""
        onAsk(t)
    }
    Surface(shape = VervanExtraShapes.pill, color = fg.copy(alpha = 0.16f)) {
        Row(
            Modifier.padding(start = Space.lg, end = Space.xs).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Ask Vervan" },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg),
                cursorBrush = SolidColor(fg),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text("Ask anything…", style = MaterialTheme.typography.bodyLarge, color = fg.copy(alpha = 0.7f))
                        }
                        inner()
                    }
                }
            )
            Box(
                Modifier
                    .padding(vertical = Space.xs)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(fg)
                    .clickable(onClick = ::submit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Start a chat with this question",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun HeroChip(
    icon: ImageVector,
    label: String,
    fg: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(onClick = onClick, shape = VervanExtraShapes.pill, color = fg.copy(alpha = 0.14f), contentColor = fg, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        SnapshotCard("Chats", chatCount.toString(), Icons.AutoMirrored.Filled.Chat, vervanAccentFor(1), onOpenChats, Modifier.weight(1f))
        SnapshotCard("Projects", projectCount.toString(), Icons.AutoMirrored.Filled.MenuBook, vervanAccentFor(2), onOpenProjects, Modifier.weight(1f))
        SnapshotCard(
            "Index",
            if (indexingCount == 0) "Ready" else "$indexingCount active",
            Icons.Filled.Description,
            vervanAccentFor(0),
            onOpenKnowledge,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun SnapshotCard(label: String, value: String, icon: ImageVector, accent: com.vervan.chat.ui.theme.VervanAccent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 92.dp),
        colors = SurfaceRole.Raised.cardColors(),
        border = SurfaceRole.Raised.border()
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.md)) {
            Box(
                Modifier.size(32.dp).background(accent.container, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = accent.onContainer)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Space.sm))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Horizontally-scrolling "Continue" cards — recent chats plus the most recent project, each with
 * a relative timestamp. A carousel keeps five recents one flick away without costing the vertical
 * space five stacked rows did.
 */
@Composable
private fun ContinueCarousel(
    chats: List<Chat>,
    projects: List<Project>,
    onOpenChat: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onStartChat: () -> Unit,
    onOpenChats: () -> Unit
) {
    Column {
        VervanSectionHeader("Continue", actionLabel = "All chats", onAction = onOpenChats)
        val recentChats = chats.take(5)
        val recentProjects = projects.take(2)
        if (recentChats.isEmpty() && recentProjects.isEmpty()) {
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                itemsIndexed(recentChats, key = { _, c -> c.id }) { index, chat ->
                    ContinueCard(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        eyebrow = "Chat",
                        title = chat.title,
                        timeLabel = relativeTime(chat.updatedAt),
                        accent = vervanAccentFor(index),
                        onClick = { onOpenChat(chat.id) }
                    )
                }
                itemsIndexed(recentProjects, key = { _, p -> p.id }) { index, project ->
                    ContinueCard(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        eyebrow = "Project",
                        title = project.name,
                        timeLabel = "Workspace",
                        accent = vervanAccentFor(index + 5),
                        onClick = { onOpenProject(project.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(
    icon: ImageVector,
    eyebrow: String,
    title: String,
    timeLabel: String,
    accent: com.vervan.chat.ui.theme.VervanAccent,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(220.dp).heightIn(min = 128.dp),
        colors = SurfaceRole.Raised.cardColors(),
        border = SurfaceRole.Raised.border()
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).background(accent.container, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent.onContainer)
                }
                Text(
                    eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.sm)
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.sm).weight(1f, fill = false)
            )
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        diff < 7L * 86_400_000L -> "${diff / 86_400_000L}d ago"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(timestamp))
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
            val tile = Modifier.weight(1f).heightIn(min = 84.dp)
            val a0 = vervanAccentFor(0); val a1 = vervanAccentFor(1); val a2 = vervanAccentFor(2); val a3 = vervanAccentFor(3)
            ActionTile(Icons.Filled.Description, "Ask documents", "Search private sources with citations", onOpenKnowledge, tile, iconContainerColor = a0.container, iconTint = a0.onContainer)
            ActionTile(Icons.Filled.Edit, "Write a note", "Capture and shape an idea", onOpenNotes, tile, iconContainerColor = a1.container, iconTint = a1.onContainer)
            ActionTile(Icons.AutoMirrored.Filled.MenuBook, "Open projects", "Keep long-running work together", onOpenProjects, tile, iconContainerColor = a2.container, iconTint = a2.onContainer)
            ActionTile(Icons.AutoMirrored.Filled.LibraryBooks, "Browse library", "Personas, prompts, and saved work", onOpenLibrary, tile, iconContainerColor = a3.container, iconTint = a3.onContainer)
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
                tools.forEachIndexed { index, tool ->
                    val accent = vervanAccentFor(index + 3)
                    ActionTile(
                        icon = tool.icon,
                        title = tool.label,
                        body = tool.body,
                        onClick = tool.onClick,
                        modifier = Modifier.weight(1f),
                        iconContainerColor = accent.container,
                        iconTint = accent.onContainer,
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
