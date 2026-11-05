package com.vervan.chat.ui.home

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import com.vervan.chat.ui.theme.vervanWarning
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import com.vervan.chat.data.db.entities.traits
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
import com.vervan.chat.validation.InputLimits
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.ui.theme.VervanBreakpoints
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
    onOpenPrivacy: () -> Unit = {},
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
    val recentChats by vm.recentChats.collectAsStateWithLifecycle()
    val latestMessagesByChat by vm.latestMessagesByChat.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val recentNotes by vm.recentNotes.collectAsStateWithLifecycle()
    val recentToolRuns by vm.recentToolRuns.collectAsStateWithLifecycle()
    val activeModel by vm.activeModel.collectAsStateWithLifecycle()
    val indexingDocuments by vm.indexingDocuments.collectAsStateWithLifecycle()
    val activeWorkspaceName by vm.activeWorkspaceName.collectAsStateWithLifecycle()
    val thermalLevel by app.container.thermalMonitor.level.collectAsStateWithLifecycle()
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
                                // Decorative: the "Vervan" text beside it is the accessible name.
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        // The active workspace name already appears right below in the hero (with
                        // richer privacy context) — repeating it here duplicated the
                        // same fact twice on one screen for no added information.
                        Text(stringResource(R.string.home_vervan), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Space.md))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Filled.Search, stringResource(R.string.home_search_workspace)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.home_settings)) }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Use the shared window token so the split layout only appears when both
                // columns have room to stay calm and readable. A raw screen-width threshold
                // made the layout flip too early in split-screen and on compact tablets.
                val expanded = maxWidth >= VervanBreakpoints.expanded
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.sm, bottom = Space.xxl),
                    verticalArrangement = Arrangement.spacedBy(Space.xl)
                ) {
                    HomeHero(
                        workspaceName = activeWorkspaceName,
                        model = activeModel,
                        compact = recentChats.isNotEmpty(),
                        onAsk = ::askVervan,
                        onOpenModels = onOpenModels,
                        onOpenKnowledge = onOpenKnowledge
                    )

                    // The full privacy card only shows when something needs attention (no model, or
                    // a remote one); the on-device case is already stated by the hero's badge.
                    if (activeModel?.traits?.runsOnDevice == false) {
                        HomePrivacyStatus(model = activeModel, onOpenPrivacy = onOpenPrivacy)
                    }

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
                        // Phones: chat-first. Workspaces, projects and folders stay one tap away in
                        // Library rather than stacking another panel under the tools.
                        ToolsSection(onOpenVoiceChat, onOpenWritingAssistant, onOpenDocScanner, onOpenTranslate, onOpenAllTools)
                    }
                }
            }
        }
    }
}

/**
 * Makes the app's trust boundary visible in plain language. The hero lock is a useful identity
 * cue, but it is not enough on its own: users should be able to tell what is local and where to
 * review the exceptions without opening a manual or guessing what the icon means.
 */
@Composable
private fun HomePrivacyStatus(model: ModelInfo?, onOpenPrivacy: () -> Unit) {
    val remote = model?.traits?.runsOnDevice == false
    Card(
        onClick = onOpenPrivacy,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = SurfaceRole.Card.cardColors(),
        border = SurfaceRole.Card.border(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAffordance(
                icon = if (remote) Icons.Filled.LockOpen else Icons.Filled.Lock,
                size = IconAffordanceSize.Compact,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
            Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                Text(
                    when {
                        remote -> stringResource(R.string.privacy_remote_title)
                        model == null -> stringResource(R.string.privacy_no_model_title)
                        else -> stringResource(R.string.privacy_local_title)
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    when {
                        remote -> stringResource(R.string.privacy_remote_body, model.displayName)
                        model == null -> stringResource(R.string.privacy_no_model_body)
                        else -> stringResource(R.string.privacy_local_body)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    StatusChip(
                        label = when {
                            remote -> stringResource(R.string.home_remote_model)
                            model == null -> stringResource(R.string.home_needs_setup)
                            else -> stringResource(R.string.home_on_device)
                        },
                        tone = when {
                            remote -> StatusTone.Info
                            model == null -> StatusTone.Warning
                            else -> StatusTone.Ready
                        },
                    )
                    Text(
                        stringResource(R.string.privacy_review),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.privacy_review),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
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
    val remoteModel = model?.traits?.runsOnDevice == false
    // Not remembered — an app left open across a time boundary should not greet
    // "Good morning" all evening. Recomputing on recomposition is trivially cheap.
    val greeting =
        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 0..4 -> stringResource(R.string.home_greeting_late)
            in 5..11 -> stringResource(R.string.home_greeting_morning)
            in 12..16 -> stringResource(R.string.home_greeting_afternoon)
            else -> stringResource(R.string.home_greeting_evening)
        }
    // Flat and quiet on purpose: the greeting and the ask field are the page's one primary
    // action, so they sit on the page itself rather than inside a loud coloured banner.
    Column(
        Modifier.fillMaxWidth().padding(top = if (compact) Space.sm else Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                greeting,
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (remoteModel) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = stringResource(
                        when {
                            remoteModel -> R.string.privacy_remote_icon_description
                            model == null -> R.string.privacy_no_model_icon_description
                            else -> R.string.privacy_local_icon_description
                        }
                    ),
                    tint = if (remoteModel) MaterialTheme.colorScheme.vervanWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                val label = workspaceName ?: stringResource(R.string.chat_info_workspace)
                Text(
                    when {
                        remoteModel -> stringResource(R.string.privacy_hero_remote_suffix, label)
                        model == null -> stringResource(R.string.privacy_hero_no_model_suffix, label)
                        else -> stringResource(R.string.privacy_hero_local_suffix, label)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Space.xs)
                )
            }
        }
        if (model != null) {
            QuickAskField(onAsk = onAsk)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                // Names the actual model instead of claiming "Model ready": installed is not the
                // same as loaded, and the model screen is where both are managed.
                HeroChip(Icons.Outlined.Memory, model.displayName, onOpenModels, Modifier.widthIn(max = 240.dp))
                // Opens the sources list (where documents are added and chatted with), so the
                // label says that rather than promising an answer straight away.
                HeroChip(Icons.Filled.Description, stringResource(R.string.home_your_documents), onOpenKnowledge)
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = SurfaceRole.Raised.cardColors(),
                border = SurfaceRole.Raised.border()
            ) {
                Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Text(
                        stringResource(R.string.home_local_model_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.Button(onClick = onOpenModels) {
                        Icon(Icons.Outlined.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Space.sm))
                        Text(stringResource(R.string.home_choose_local_model))
                    }
                }
            }
        }
    }
}

/** Single-line composer. Send (button or IME action) opens a new chat and immediately submits
 * this text — generation is already underway by the time the chat screen appears. Blank send
 * just opens an empty chat. Styled like the chat composer so the two read as the same control. */
@Composable
private fun QuickAskField(onAsk: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val askContentDescription = stringResource(R.string.home_ask_content_description)
    fun submit() {
        val t = text
        text = ""
        onAsk(t)
    }
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = VervanExtraShapes.composer,
        color = SurfaceRole.Floating.containerColor(),
        border = SurfaceRole.Floating.border()
    ) {
        Row(
            Modifier.padding(start = Space.lg, end = Space.xs).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(InputLimits.CHAT_TEXT_CHARS) },
                modifier = Modifier.weight(1f).padding(vertical = Space.md).semantics { contentDescription = askContentDescription },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(
                                stringResource(R.string.home_ask_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                }
            )
            Box(
                Modifier
                    .padding(start = Space.sm, top = Space.xs, bottom = Space.xs)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(scheme.onSurface)
                    .clickable(onClick = ::submit, role = Role.Button),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.home_start_chat_question),
                    tint = scheme.surface,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = VervanExtraShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = SurfaceRole.Card.border(),
        modifier = modifier.heightIn(min = 40.dp)
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HomeAlert(thermalLevel: ThermalLevel, indexingCount: Int, onOpenKnowledge: () -> Unit) {
    when {
        thermalLevel != ThermalLevel.NORMAL -> SystemStatusStrip(
            title = stringResource(if (thermalLevel == ThermalLevel.SEVERE) R.string.home_thermal_slowdown else R.string.home_device_warming),
            body = if (thermalLevel == ThermalLevel.SEVERE) {
                stringResource(R.string.home_thermal_severe_body)
            } else {
                stringResource(R.string.home_thermal_warning_body)
            },
            tone = StatusTone.Warning
        )
        indexingCount > 0 -> SystemStatusStrip(
            title = stringResource(R.string.home_preparing_knowledge),
            body = pluralStringResource(R.plurals.home_indexing_documents, indexingCount, indexingCount),
            tone = StatusTone.Running,
            actionLabel = stringResource(R.string.action_view),
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

/** Home is a launch pad, not an archive: the full history lives in Chats and Library. */
private const val HOME_CONTINUE_LIMIT = 4

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
    }.sortedByDescending { it.timestamp }.take(HOME_CONTINUE_LIMIT)

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
            // One grouped list (like Settings rows) instead of a stack of separately boxed cards:
            // fewer edges for the eye to parse, and the rows read as one "recent" set.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = SurfaceRole.Card.cardColors(),
                border = SurfaceRole.Card.border(),
            ) {
                recentItems.forEachIndexed { index, item ->
                    if (index > 0) androidx.compose.material3.HorizontalDivider(
                        Modifier.padding(start = 64.dp),
                        color = com.vervan.chat.ui.theme.vervanSubtleDividerColor()
                    )
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
    val openLabel = stringResource(R.string.home_open_item, title)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
            Box(Modifier.size(40.dp).background(accent.container, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = accent.onContainer)
            }
            Column(Modifier.weight(1f).padding(start = Space.md)) {
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
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.padding(start = Space.sm),
                    )
                }
                Text(
                    displayPreview.replace("\n", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
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
        VervanSectionHeader(stringResource(R.string.home_local_workspace), topPadding = 0.dp)
        SectionCard(items = listOf<@Composable () -> Unit>(
            {
                SectionRow(
                    icon = Icons.Outlined.Memory,
                    title = model?.displayName ?: stringResource(R.string.home_no_generation_model),
                    subtitle = model?.lastWorkingBackend?.label() ?: stringResource(R.string.home_choose_model),
                    onClick = onOpenModels,
                    trailing = { StatusChip(statusLabel(modelTone), modelTone) }
                )
            },
            {
                SectionRow(
                    icon = Icons.Filled.GridView,
                    title = workspaceName ?: stringResource(R.string.home_personal_workspace),
                    subtitle = if (indexingCount > 0) pluralStringResource(R.plurals.home_indexing_documents, indexingCount, indexingCount) else stringResource(R.string.home_up_to_date),
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
                    title = stringResource(R.string.home_projects),
                    subtitle = stringResource(R.string.home_browse_grouped_work),
                    onClick = onOpenProjects
                )
            },
            {
                SectionRow(
                    icon = Icons.Filled.Folder,
                    title = stringResource(R.string.home_folders),
                    subtitle = stringResource(R.string.home_browse_manual_filing),
                    onClick = onOpenFolders
                )
            }
        ))
    }
}

@androidx.compose.runtime.Composable
private fun statusLabel(tone: StatusTone): String = when (tone) {
    StatusTone.Ready -> stringResource(R.string.home_selected)
    StatusTone.Running -> stringResource(R.string.home_working)
    StatusTone.Warning -> stringResource(R.string.home_setup)
    else -> stringResource(R.string.action_open)
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
        "Common tasks" to listOf(
            ModernHomeTool(Icons.Filled.RecordVoiceOver, "Voice chat", "Talk naturally with your local model", onOpenVoiceChat),
            ModernHomeTool(Icons.Filled.EditNote, "Writing assistant", "Rewrite, refine, or change tone", onOpenWritingAssistant),
            ModernHomeTool(Icons.Filled.DocumentScanner, "Document scanner", "Capture pages and extract useful text", onOpenDocScanner),
            ModernHomeTool(Icons.Filled.Translate, "Translate", "Translate text or a photographed page", onOpenTranslate),
        ),
    )
    Column {
        VervanSectionHeader(
            "Quick tools",
            actionLabel = stringResource(R.string.home_see_all),
            onAction = onOpenAllTools,
            topPadding = 0.dp
        )
        // A single row of chips: these are shortcuts, not content, so they shouldn't take more
        // of the screen than the recent work above them. "See all" opens the full Tools tab.
        toolGroups.forEach { (_, tools) ->
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                tools.forEach { tool -> HeroChip(tool.icon, tool.label, tool.onClick) }
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

@androidx.compose.runtime.Composable
private fun ModelBackend.label(): String = when (this) {
    ModelBackend.NPU -> stringResource(R.string.home_npu_backend)
    ModelBackend.GPU -> stringResource(R.string.home_gpu_backend)
    ModelBackend.CPU -> stringResource(R.string.home_cpu_backend)
    ModelBackend.UNVERIFIED -> stringResource(R.string.home_setup_not_checked)
}
