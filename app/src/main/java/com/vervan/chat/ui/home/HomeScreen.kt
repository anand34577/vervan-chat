package com.vervan.chat.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
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
import com.vervan.chat.ui.common.EnterMotion
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
import com.vervan.chat.ui.theme.VervanBreakpoints
import com.vervan.chat.ui.theme.VervanExtraShapes
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.vervan.chat.system.toUserMessage

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
    suspend fun askVervan(text: String) {
        onOpenChat(if (text.isBlank()) vm.createChat() else vm.createChatAndSend(text))
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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.shapes.small
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Keep the shell independent from launcher artwork. The mark stays
                            // legible when the app icon is replaced, the user changes theme, or
                            // the display uses a large font scale.
                            Text(
                                text = "V",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        // The active workspace name already appears right below in the hero (with
                        // richer privacy context) — repeating it here duplicated the
                        // same fact twice on one screen for no added information.
                        Text(stringResource(R.string.role_vervan), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Space.md))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, stringResource(R.string.shortcut_search))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.shortcut_settings))
                    }
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

                    HomePrivacyStatus(model = activeModel, onOpenPrivacy = onOpenPrivacy)

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
 * Makes the app's trust boundary visible in plain language. The hero lock is a useful identity
 * cue, but it is not enough on its own: users should be able to tell what is local and where to
 * review the exceptions without opening a manual or guessing what the icon means.
 */
@Composable
private fun HomePrivacyStatus(model: ModelInfo?, onOpenPrivacy: () -> Unit) {
    val remote = model?.traits?.runsOnDevice == false
    val statusIcon = when {
        remote -> Icons.Filled.Cloud
        model == null -> Icons.Outlined.Memory
        else -> Icons.Filled.Lock
    }
    val statusIconTint = when {
        remote -> MaterialTheme.colorScheme.onSecondaryContainer
        model == null -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val statusIconContainer = when {
        remote -> MaterialTheme.colorScheme.secondaryContainer
        model == null -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val statusIconDescription = when {
        remote -> stringResource(R.string.privacy_remote_icon_description)
        model == null -> stringResource(R.string.privacy_no_model_icon_description)
        else -> stringResource(R.string.privacy_local_icon_description)
    }
    Surface(
        onClick = onOpenPrivacy,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAffordance(
                icon = statusIcon,
                size = IconAffordanceSize.Compact,
                tint = statusIconTint,
                containerColor = statusIconContainer,
                contentDescription = statusIconDescription,
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
                        maxLines = 1,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.privacy_review),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The one hero surface on Home: greeting, runtime metrics, privacy state, and a *working* quick-ask
 * composer on a ruled surface. Typing here and hitting Send opens a new chat with the text already
 * in its composer, so the thought that started on Home finishes in the chat without retyping.
 * With no model installed the composer gives way to the setup CTA — nothing else on the screen
 * pretends chat works before a model exists.
 */
@Composable
private fun HomeHero(
    workspaceName: String?,
    model: ModelInfo?,
    compact: Boolean,
    onAsk: suspend (String) -> Unit,
    onOpenModels: () -> Unit,
    onOpenKnowledge: () -> Unit
) {
    val heroFg = MaterialTheme.colorScheme.onSurface
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
    EnterMotion {
        // The home hero is the product's signature surface. Keep the readable content on a
        // semantic surface role and reserve the accent gradient for a small visual signal. A
        // primary/secondary container gradient is not guaranteed to have one safe foreground
        // color across dynamic, high-contrast, light, and dark schemes.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = VervanExtraShapes.hero,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column(
                Modifier.fillMaxWidth().animateContentSize()
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                )
                            )
                        )
                )
                Column(
                    Modifier.fillMaxWidth().padding(Space.xl)
                ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_local_workspace),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        greeting,
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        color = heroFg,
                    )
                    Text(
                        when {
                            remoteModel -> stringResource(
                                R.string.home_hero_remote_model,
                                workspaceName ?: stringResource(R.string.home_personal_workspace),
                            )
                            model == null -> stringResource(
                                R.string.home_hero_no_model,
                                workspaceName ?: stringResource(R.string.home_personal_workspace),
                            )
                            else -> stringResource(
                                R.string.home_hero_local_model,
                                workspaceName ?: stringResource(R.string.home_personal_workspace),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = heroFg.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Space.xs)
                    )
                }
                // A glyph in a flat circle ("✓"/"—"/"↗") doesn't scale with display font, doesn't
                // mirror for RTL, and reads as placeholder text rather than a designed status
                // badge — replaced with real icons on the same soft two-tone gradient the rest of
                // the app's Feature-size icon badges use (see IconAffordance), so this is the
                // first thing on screen matching that language rather than a one-off circle.
                val readyTint = MaterialTheme.colorScheme.primary
                Box(
                    Modifier
                        .size(36.dp)
                        .shadow(if (model != null) 3.dp else 0.dp, CircleShape, clip = false)
                        .background(
                            if (model == null) Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHighest)
                            )
                            else Brush.linearGradient(
                                listOf(readyTint, lerp(readyTint, MaterialTheme.colorScheme.tertiary, 0.35f))
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when {
                            remoteModel -> Icons.Filled.Cloud
                            model == null -> Icons.Outlined.RadioButtonUnchecked
                            else -> Icons.Filled.CheckCircle
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (model == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(top = Space.lg),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                stringResource(R.string.home_start_prompt),
                style = MaterialTheme.typography.labelMedium,
                color = heroFg.copy(alpha = 0.78f),
                modifier = Modifier.padding(top = Space.lg, bottom = Space.sm)
            )
            if (model != null) {
                QuickAskField(onAsk = onAsk)
                Spacer(Modifier.height(Space.md))
                ResponsiveActions {
                    HeroChip(Icons.Outlined.Memory, stringResource(R.string.home_model_ready), onOpenModels)
                    HeroChip(Icons.Filled.Description, stringResource(R.string.home_ask_documents), onOpenKnowledge)
                }
            } else {
                Surface(
                    onClick = onOpenModels,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Row(
                        Modifier.padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        Icon(Icons.Outlined.Memory, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.home_choose_local_model), style = MaterialTheme.typography.titleSmall)
                    }
                }
                Text(
                    stringResource(R.string.home_local_model_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = heroFg.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = Space.sm)
                )
            }
                }
            }
        }
    }
}

/** Ruled single-line composer on the hero. Send (button or IME action) opens a new chat and
 * immediately submits this text — generation is already underway by the time the chat screen
 * appears. Blank send just opens an empty chat. */
@Composable
private fun QuickAskField(onAsk: suspend (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var submitting by rememberSaveable { mutableStateOf(false) }
    var submitError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val askContentDescription = stringResource(R.string.home_ask_content_description)
    val sendContentDescription = stringResource(R.string.home_start_chat_question)
    fun submit() {
        // The navigation happens asynchronously, so a fast double tap could otherwise create
        // two chats from one question before the destination replaces Home.
        if (submitting) return
        submitting = true
        val t = text
        submitError = null
        scope.launch {
            try {
                onAsk(t)
                text = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                // Preserve the question so a transient database or navigation failure does not
                // force the user to type it again, and keep the failure inside the UI scope.
                submitError = failure.toUserMessage()
            } finally {
                submitting = false
            }
        }
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        tonalElevation = 1.dp
    ) {
        Row(
            Modifier.padding(start = Space.lg, end = Space.xs).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(InputLimits.CHAT_TEXT_CHARS) },
                modifier = Modifier.weight(1f).semantics { contentDescription = askContentDescription },
                 textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                 cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                             Text(stringResource(R.string.home_ask_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                }
            )
            Surface(
                onClick = ::submit,
                enabled = !submitting,
                modifier = Modifier
                    .padding(vertical = Space.xs)
                    .size(48.dp)
                    .semantics { contentDescription = sendContentDescription },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (submitting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
    submitError?.let { error ->
        com.vervan.chat.ui.common.ValidationMessage(error, modifier = Modifier.padding(top = Space.sm))
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
        // These are compact task controls, not badges. Use the same rectangular control geometry
        // as the rest of the redesigned app so the first frame cannot flash a legacy pill style.
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
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
        // A moderate reading is common during launch and short bursts of work. Keep the home
        // surface quiet until Android reports a sustained, actionable severe state.
        thermalLevel == ThermalLevel.SEVERE -> SystemStatusStrip(
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
                recentItems.forEach { item ->
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
                            onClick = { onOpenChat(item.value.id) }
                        )
                        is HomeRecentItem.ProjectItem -> ContinueRow(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            eyebrow = stringResource(R.string.entity_project),
                            title = item.value.name,
                            preview = item.value.instructions,
                            timeLabel = relativeTime(item.timestamp),
                            onClick = { onOpenProject(item.value.id) }
                        )
                        is HomeRecentItem.NoteItem -> ContinueRow(
                            icon = Icons.Filled.NoteAlt,
                            eyebrow = stringResource(R.string.entity_note),
                            title = item.value.title,
                            preview = item.value.content,
                            timeLabel = relativeTime(item.timestamp),
                            onClick = { onOpenNote(item.value.id) }
                        )
                        is HomeRecentItem.ToolRunItem -> ContinueRow(
                            icon = Icons.Filled.History,
                            eyebrow = stringResource(R.string.entity_tool_result),
                            title = item.value.toolName,
                            preview = item.value.output.ifBlank { item.value.input },
                            timeLabel = relativeTime(item.timestamp),
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
    onClick: () -> Unit,
) {
    val displayPreview = if (preview.isBlank()) {
        stringResource(R.string.home_ready_to_continue, eyebrow)
    } else {
        preview
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.home_open_item, title),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
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

@Composable
private fun statusLabel(tone: StatusTone): String = when (tone) {
    StatusTone.Ready -> stringResource(R.string.home_status_selected)
    StatusTone.Running -> stringResource(R.string.home_status_working)
    StatusTone.Warning -> stringResource(R.string.home_status_setup)
    else -> stringResource(R.string.home_status_open)
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
        stringResource(R.string.home_tool_group_common) to listOf(
            ModernHomeTool(Icons.Filled.RecordVoiceOver, stringResource(R.string.home_tool_voice_title), stringResource(R.string.home_tool_voice_body), onOpenVoiceChat),
            ModernHomeTool(Icons.Filled.EditNote, stringResource(R.string.home_tool_writing_title), stringResource(R.string.home_tool_writing_body), onOpenWritingAssistant),
            ModernHomeTool(Icons.Filled.DocumentScanner, stringResource(R.string.home_tool_scanner_title), stringResource(R.string.home_tool_scanner_body), onOpenDocScanner),
            ModernHomeTool(Icons.Filled.Translate, stringResource(R.string.home_tool_translate_title), stringResource(R.string.home_tool_translate_body), onOpenTranslate),
        ),
    )
    Column {
        VervanSectionHeader(
            stringResource(R.string.home_quick_tools),
            actionLabel = stringResource(R.string.ui_homescreen_899_see_all),
            onAction = onOpenAllTools,
            topPadding = 0.dp
        )
        Text(
            stringResource(R.string.home_quick_tools_body),
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
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
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

@Composable
private fun ModelBackend.label(): String = when (this) {
    ModelBackend.NPU -> stringResource(R.string.home_backend_npu)
    ModelBackend.GPU -> stringResource(R.string.home_backend_gpu)
    ModelBackend.CPU -> stringResource(R.string.home_backend_cpu)
    ModelBackend.UNVERIFIED -> stringResource(R.string.home_backend_unverified)
}
