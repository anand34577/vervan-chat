package com.vervan.chat.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.ModelBackend
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.Project
import com.vervan.chat.data.db.entities.ToolRun
import com.vervan.chat.system.ThermalLevel
import com.vervan.chat.ui.common.ActionTile
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.ResponsiveActions
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
    onOpenChats: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenProject: (String) -> Unit = {},
    onOpenNote: (String) -> Unit = {},
    onOpenToolRun: (String) -> Unit = {},
    onOpenKnowledge: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenWorkspaces: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
    onOpenFolders: () -> Unit = {},
    onOpenDocScanner: () -> Unit = {},
    onOpenVoiceChat: () -> Unit = {},
    onOpenTranslate: () -> Unit = {},
    onOpenWritingAssistant: () -> Unit = {},
    onOpenAllTools: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(app) } })
    val recentChats by vm.recentChats.collectAsState()
    val latestMessagesByChat by vm.latestMessagesByChat.collectAsState()
    val projects by vm.projects.collectAsState()
    val recentNotes by vm.recentNotes.collectAsState()
    val recentToolRuns by vm.recentToolRuns.collectAsState()
    val activeModel by vm.activeModel.collectAsState()
    val indexingDocuments by vm.indexingDocuments.collectAsState()
    val activeWorkspaceName by vm.activeWorkspaceName.collectAsState()
    val thermalLevel by app.container.thermalMonitor.level.collectAsState()
    val scope = rememberCoroutineScope()

    fun startNewChat() {
        scope.launch { onOpenChat(vm.createChat()) }
    }

    // Hero quick-ask: tapping Send actually sends — the question is submitted and generation
    // starts the moment the chat opens, rather than landing as an unsent draft the user has to
    // tap Send on a second time (which read as the first tap having failed). Blank text just
    // opens an empty chat, same as "New chat".
    fun askVervan(text: String) {
        scope.launch {
            onOpenChat(if (text.isBlank()) vm.createChat() else vm.createChatAndSend(text))
        }
    }

    Scaffold(
        // The navigation shell already applies the bottom navigation/gesture inset. Keeping the
        // inner scaffold's default system-bar insets here creates a second blank strip above the
        // navbar. VervanTopAppBar still owns its own status-bar inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Keep the full launcher mark visible: the themed surface gives it a
                        // stable background while the foreground preserves the amber/periwinkle
                        // V, AI node, and diamond from the app icon. The surface adapts with the
                        // current light/dark theme without flattening the mark into one tint.
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = "Vervan",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        // The active workspace name already appears right below in the hero (with
                        // richer "· fully offline" context) — repeating it here duplicated the
                        // same fact twice on one screen for no added information.
                        Text("Vervan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Space.md))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Filled.Search, "Search workspace") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= 760.dp
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.sm, bottom = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    HomeHero(
                        workspaceName = activeWorkspaceName,
                        model = activeModel,
                        compact = recentChats.isNotEmpty(),
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
                            Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                                ContinueCarousel(
                                    recentChats, latestMessagesByChat, projects, recentNotes, recentToolRuns,
                                    onOpenChat, onOpenProject, onOpenNote, onOpenToolRun, ::startNewChat, onOpenChats
                                )
                            }
                            Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                                WorkspaceStatusSection(
                                    model = activeModel,
                                    workspaceName = activeWorkspaceName,
                                    indexingCount = indexingDocuments.size,
                                    onOpenModels = onOpenModels,
                                    onOpenWorkspaces = onOpenWorkspaces,
                                    onOpenProjects = onOpenProjects,
                                    onOpenFolders = onOpenFolders,
                                )
                            }
                        }
                        ToolsSection(onOpenVoiceChat, onOpenWritingAssistant, onOpenDocScanner, onOpenTranslate, onOpenAllTools)
                    } else {
                        ContinueCarousel(
                            recentChats, latestMessagesByChat, projects, recentNotes, recentToolRuns,
                            onOpenChat, onOpenProject, onOpenNote, onOpenToolRun, ::startNewChat, onOpenChats
                        )
                        ToolsSection(onOpenVoiceChat, onOpenWritingAssistant, onOpenDocScanner, onOpenTranslate, onOpenAllTools)
                        WorkspaceStatusSection(
                            model = activeModel,
                            workspaceName = activeWorkspaceName,
                            indexingCount = indexingDocuments.size,
                            onOpenModels = onOpenModels,
                            onOpenWorkspaces = onOpenWorkspaces,
                            onOpenProjects = onOpenProjects,
                            onOpenFolders = onOpenFolders,
                        )
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
    compact: Boolean,
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
            .padding(if (compact) Space.md else Space.lg)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        greeting,
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        color = heroFg,
                    )
                    Text(
                        workspaceName?.let { "$it · conversations stay on this device" }
                            ?: "Private · conversations stay on this device",
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
                    Icon(Icons.Filled.Lock, contentDescription = "Conversations stay on this device", tint = heroFg, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(if (compact) Space.sm else Space.md))
            if (model != null) {
                QuickAskField(fg = heroFg, onAsk = onAsk)
                Spacer(Modifier.height(Space.md))
                ResponsiveActions {
                    HeroChip(Icons.Outlined.Memory, model.displayName, heroFg, onOpenModels)
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
                    "Import a model or download one when connected. Conversations and inference stay on this device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = heroFg.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = Space.sm)
                )
            }
        }
    }
}

/** Frosted single-line composer on the hero. Send (button or IME action) opens a new chat and
 * immediately submits this text — generation is already underway by the time the chat screen
 * appears. Blank send just opens an empty chat. */
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
                    .clickable(onClick = ::submit, role = Role.Button),
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

/** Chronological continuation items across chats, projects, notes, and durable tool results. */
private sealed interface HomeRecentItem {
    val timestamp: Long
    data class ChatItem(val value: Chat) : HomeRecentItem { override val timestamp = value.updatedAt }
    data class ProjectItem(val value: Project) : HomeRecentItem { override val timestamp = value.createdAt }
    data class NoteItem(val value: Note) : HomeRecentItem { override val timestamp = value.updatedAt }
    data class ToolRunItem(val value: ToolRun) : HomeRecentItem { override val timestamp = value.updatedAt }
}

@Composable
private fun ContinueCarousel(
    chats: List<Chat>,
    latestMessagesByChat: Map<String, Message>,
    projects: List<Project>,
    notes: List<Note>,
    toolRuns: List<ToolRun>,
    onOpenChat: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenToolRun: (String) -> Unit,
    onStartChat: () -> Unit,
    onOpenChats: () -> Unit
) {
    val recentItems = buildList<HomeRecentItem> {
        chats.forEach { add(HomeRecentItem.ChatItem(it)) }
        projects.forEach { add(HomeRecentItem.ProjectItem(it)) }
        notes.forEach { add(HomeRecentItem.NoteItem(it)) }
        toolRuns.forEach { add(HomeRecentItem.ToolRunItem(it)) }
    }.sortedByDescending { it.timestamp }

    Column {
        VervanSectionHeader(
            stringResource(R.string.home_continue),
            actionLabel = stringResource(R.string.home_all_chats),
            onAction = onOpenChats,
            topPadding = 0.dp
        )
        if (recentItems.isEmpty()) {
            SectionCard(items = listOf<@Composable () -> Unit>({
                Row(Modifier.fillMaxWidth().padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    IconAffordance(Icons.AutoMirrored.Filled.Chat, size = IconAffordanceSize.Default)
                    Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                        Text(stringResource(R.string.home_fresh_workspace), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.home_recent_content_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onStartChat) { Text(stringResource(R.string.action_start)) }
                }
            }))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                recentItems.forEachIndexed { index, item ->
                    when (item) {
                        is HomeRecentItem.ChatItem -> ContinueRow(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            eyebrow = stringResource(R.string.entity_chat),
                            title = item.value.title,
                            preview = latestMessagesByChat[item.value.id]?.let {
                                com.vervan.chat.ui.chat.chatPreviewText(
                                    it.content,
                                    it.role == com.vervan.chat.data.db.entities.MessageRole.USER
                                )
                            }.orEmpty(),
                            timeLabel = relativeTime(item.timestamp),
                            accent = vervanAccentFor(index),
                            onClick = { onOpenChat(item.value.id) }
                        )
                        is HomeRecentItem.ProjectItem -> ContinueRow(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            eyebrow = stringResource(R.string.entity_project),
                            title = item.value.name,
                            preview = item.value.instructions,
                            timeLabel = relativeTime(item.timestamp),
                            accent = vervanAccentFor(index),
                            onClick = { onOpenProject(item.value.id) }
                        )
                        is HomeRecentItem.NoteItem -> ContinueRow(
                            icon = Icons.Filled.NoteAlt,
                            eyebrow = stringResource(R.string.entity_note),
                            title = item.value.title,
                            preview = item.value.content,
                            timeLabel = relativeTime(item.timestamp),
                            accent = vervanAccentFor(index),
                            onClick = { onOpenNote(item.value.id) }
                        )
                        is HomeRecentItem.ToolRunItem -> ContinueRow(
                            icon = Icons.Filled.History,
                            eyebrow = stringResource(R.string.entity_tool_result),
                            title = item.value.toolName,
                            preview = item.value.output.ifBlank { item.value.input },
                            timeLabel = relativeTime(item.timestamp),
                            accent = vervanAccentFor(index),
                            onClick = { onOpenToolRun(item.value.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueRow(
    icon: ImageVector,
    eyebrow: String,
    title: String,
    preview: String,
    timeLabel: String,
    accent: com.vervan.chat.ui.theme.VervanAccent,
    onClick: () -> Unit,
) {
    val displayPreview = if (preview.isBlank()) {
        stringResource(R.string.home_ready_to_continue, eyebrow)
    } else {
        preview
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = SurfaceRole.Raised.cardColors(),
        border = SurfaceRole.Raised.border(),
    ) {
        Row(Modifier.fillMaxWidth().padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(accent.container, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = accent.onContainer)
            }
            Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OverflowTooltipText(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = Space.sm),
                    )
                }
                Text(
                    displayPreview.replace("\n", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open $title", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> stringResource(R.string.relative_just_now)
        diff < 3_600_000L -> {
            val minutes = (diff / 60_000L).toInt()
            pluralStringResource(R.plurals.relative_minutes_ago, minutes, minutes)
        }
        diff < 86_400_000L -> {
            val hours = (diff / 3_600_000L).toInt()
            pluralStringResource(R.plurals.relative_hours_ago, hours, hours)
        }
        diff < 7L * 86_400_000L -> {
            val days = (diff / 86_400_000L).toInt()
            pluralStringResource(R.plurals.relative_days_ago, days, days)
        }
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(timestamp))
    }
}

@Composable
private fun WorkspaceStatusSection(
    model: ModelInfo?,
    workspaceName: String?,
    indexingCount: Int,
    onOpenModels: () -> Unit,
    onOpenWorkspaces: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenFolders: () -> Unit
) {
    val modelTone = if (model == null) StatusTone.Warning else StatusTone.Ready
    val indexTone = if (indexingCount > 0) StatusTone.Running else StatusTone.Info
    Column {
        VervanSectionHeader("Local workspace", topPadding = 0.dp)
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
            },
            // Projects/Folders were previously only reachable via the Create sheet's secondary
            // "Organize" group — no bottom-nav or Home presence, unlike Workspaces above. Adding
            // them here as plain browse-all entries (same SectionRow pattern, no status chip
            // since there's no single "current" project/folder to reflect).
            {
                SectionRow(
                    icon = Icons.Filled.Workspaces,
                    title = "Projects",
                    subtitle = "Browse grouped work",
                    onClick = onOpenProjects
                )
            },
            {
                SectionRow(
                    icon = Icons.Filled.Folder,
                    title = "Folders",
                    subtitle = "Browse manual filing",
                    onClick = onOpenFolders
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
        VervanSectionHeader(
            "Choose a mode",
            actionLabel = "See all",
            onAction = onOpenAllTools,
            topPadding = 0.dp
        )
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
    ModelBackend.UNVERIFIED -> "Setup not checked"
}
