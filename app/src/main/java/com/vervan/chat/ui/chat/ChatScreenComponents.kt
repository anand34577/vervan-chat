package com.vervan.chat.ui.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.vervan.chat.ui.common.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.audio.WavRecorder
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.DatePill
import com.vervan.chat.ui.common.SectionCard
import com.vervan.chat.ui.common.SectionRow
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.MessageAction
import com.vervan.chat.ui.common.MessageActionsSheet
import com.vervan.chat.ui.common.QuickReply
import com.vervan.chat.ui.common.QuickReplyChips
import com.vervan.chat.ui.common.ReactionBadges
import com.vervan.chat.ui.common.MessageReaction
import com.vervan.chat.ui.common.ThinkingIndicator
import com.vervan.chat.ui.common.VoiceWaveform
import com.vervan.chat.ui.common.defaultQuickReplies
import com.vervan.chat.ui.common.formatRelativeDay
import com.vervan.chat.ui.common.setSensitiveText
import com.vervan.chat.ui.common.setText
import com.vervan.chat.ui.common.MarkdownLiteText
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.VervanAccent
import com.vervan.chat.ui.theme.VervanBreakpoints
import com.vervan.chat.ui.theme.VervanContentWidth
import com.vervan.chat.ui.theme.VervanMotion
import com.vervan.chat.ui.theme.vervanAccentFor
import com.vervan.chat.ui.theme.vervanBorder
import com.vervan.chat.ui.theme.vervanSuccess
import com.vervan.chat.ui.theme.vervanWarning
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.data.db.entities.Document
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.MessageState
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.Persona
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.data.db.entities.Workspace
import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Folder
import com.vervan.chat.ui.common.OverflowTooltipText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.json.JSONArray

/** Supporting composables for ChatScreen: dialogs, sheets, panels, empty/context UI. */


@Composable
internal fun SavedResponsesDialog(
    outputs: List<SavedOutput>,
    onDismiss: () -> Unit,
    onOpen: (SavedOutput) -> Unit,
    onRemove: (SavedOutput) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved responses") },
        text = {
            if (outputs.isEmpty()) {
                Text("Bookmarked responses appear here.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    items(outputs, key = { it.id }) { output ->
                        Card(
                            onClick = { onOpen(output) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = Space.md, top = Space.sm, bottom = Space.sm, end = Space.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    output.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onRemove(output) }) {
                                    Icon(
                                        Icons.Filled.Bookmark,
                                        contentDescription = "Remove bookmark",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
internal fun ModelReadinessPanel(
    state: ChatViewModel.ModelLoadState,
    // "not loaded"/"loading into memory" describes a local weights file — a REMOTE_API model has
    // none, so NotLoaded here just means "hasn't confirmed its endpoint/key yet", not that
    // anything is about to be pulled into RAM. Defaults true so a state reached before the model
    // resolves doesn't flip the copy for a beat.
    modelRunsOnDevice: Boolean = true,
    onLoad: () -> Unit,
    onOpenModels: () -> Unit
) {
    if (state is ChatViewModel.ModelLoadState.Ready) return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(
            Modifier.widthIn(max = VervanContentWidth.standard).fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs),
            colors = CardDefaults.cardColors(
                containerColor = when (state) {
                    is ChatViewModel.ModelLoadState.Failed -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Psychology,
                        contentDescription = null,
                        tint = if (state is ChatViewModel.ModelLoadState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Column(Modifier.weight(1f).padding(start = Space.md)) {
                        Text(
                            when (state) {
                                ChatViewModel.ModelLoadState.NoModel -> "A model is required"
                                is ChatViewModel.ModelLoadState.NotLoaded ->
                                    if (modelRunsOnDevice) "${state.modelName} is not loaded" else "${state.modelName} is not confirmed yet"
                                is ChatViewModel.ModelLoadState.Loading -> "Loading ${state.modelName}"
                                is ChatViewModel.ModelLoadState.Failed -> "Could not load ${state.modelName}"
                                is ChatViewModel.ModelLoadState.Ready -> "Ready"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            when (state) {
                                ChatViewModel.ModelLoadState.NoModel -> "Import or select a generation model to enable the composer."
                                is ChatViewModel.ModelLoadState.NotLoaded ->
                                    if (modelRunsOnDevice) "Load it now, or enable automatic loading in Generation settings."
                                    else "Confirm its endpoint now, or enable automatic loading in Generation settings."
                                is ChatViewModel.ModelLoadState.Loading -> state.stage
                                is ChatViewModel.ModelLoadState.Failed -> state.reason
                                is ChatViewModel.ModelLoadState.Ready -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (state is ChatViewModel.ModelLoadState.Loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Space.sm))
                }
                when (state) {
                    ChatViewModel.ModelLoadState.NoModel -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onOpenModels) { Text("Models") }
                        }
                    }
                    is ChatViewModel.ModelLoadState.NotLoaded,
                    is ChatViewModel.ModelLoadState.Failed -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onLoad) { Text("Load") }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

/** informational, not an error: the model is working correctly, just constrained by
 * device temperature. Uses tertiary (not error) container so it reads distinctly from
 * [ModelReadinessPanel]'s failed/unavailable states even at a glance. */
@Composable
internal fun ThermalNotice(severe: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(
            Modifier.widthIn(max = VervanContentWidth.standard).fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                Text(
                    if (severe) "Running much slower — device is very warm" else "Running slower due to device temperature",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(start = Space.sm)
                )
            }
        }
    }
}

/** Live tokens/sec + RAM readout while a response is streaming — the "Show generation stats"
 * setting's real-time counterpart to the per-message numbers ChatInfoScreen shows after the
 * fact. Local-LLM users watch this while it's happening, not just in the post-mortem. */
@Composable
internal fun LiveGenStatsChip(stats: ChatViewModel.LiveGenStats) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(
            Modifier.widthIn(max = VervanContentWidth.standard).padding(horizontal = Space.lg, vertical = Space.xs),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    "${String.format("%.1f", stats.tokensPerSecond)} tok/s · ${stats.tokens} tokens · ${stats.availMemMb}/${stats.totalMemMb} MB free",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.sm)
                )
            }
        }
    }
}

/**
 * The chat's "More options" bottom sheet. The top bar's overflow dropdown keeps only the everyday
 * actions (details, search, mode, pin, archive); every power-user action lives here, grouped under
 * labeled sections and scrollable — so the menu never runs off the screen the way a 19-item
 * dropdown did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatMoreOptionsSheet(
    hasAssistantReply: Boolean,
    canGenerateTitle: Boolean,
    hasPreviousTitle: Boolean,
    savedResponsesCount: Int,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onGenerateTitle: () -> Unit,
    onRestoreTitle: () -> Unit,
    onSavedResponses: () -> Unit,
    onBranchTree: () -> Unit,
    onContextInspector: () -> Unit,
    toolsAvailable: Boolean,
    onChatTools: () -> Unit,
    onAddToKnowledgeBase: () -> Unit,
    onManageFolders: () -> Unit,
    onDuplicate: () -> Unit,
    onExportShare: () -> Unit,
    onExportMarkdown: () -> Unit,
    onExportPdf: () -> Unit,
    onResetSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = VervanContentWidth.reading).fillMaxWidth().align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg).padding(bottom = Space.xxl)
        ) {
            Text("Chat options", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Manage this conversation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            MoreSheetSection("Title")
            MoreOptionRow(Icons.Filled.Edit, "Rename", onClick = onRename)
            MoreOptionRow(
                Icons.Filled.AutoAwesome,
                if (hasAssistantReply) "Regenerate title" else "Generate title with AI",
                enabled = canGenerateTitle,
                onClick = onGenerateTitle
            )
            if (hasPreviousTitle) MoreOptionRow(Icons.Filled.Restore, "Restore previous title", onClick = onRestoreTitle)

            MoreSheetSection("Explore")
            MoreOptionRow(
                Icons.Filled.Bookmark, "Saved responses",
                subtitle = if (savedResponsesCount > 0) "$savedResponsesCount saved" else "None yet",
                onClick = onSavedResponses
            )
            MoreOptionRow(Icons.Filled.AccountTree, "Branch tree", onClick = onBranchTree)
            MoreOptionRow(Icons.AutoMirrored.Filled.ManageSearch, "Context inspector", onClick = onContextInspector)
            // Tools are gated on the active model actually supporting them (supportsTools) —
            // surfacing a "Chat tools" entry for a non-tool-call model would be a dead end,
            // since runGenerationLoop already no-ops the catalog when supportsTools == false.
            if (toolsAvailable) {
                MoreOptionRow(Icons.Filled.Build, "Chat tools", onClick = onChatTools)
            }

            MoreSheetSection("Organize")
            MoreOptionRow(Icons.Filled.Add, "Add to knowledge base", onClick = onAddToKnowledgeBase)
            MoreOptionRow(Icons.Filled.Folder, "Manage folders", onClick = onManageFolders)
            MoreOptionRow(Icons.Filled.ContentCopy, "Duplicate", onClick = onDuplicate)

            MoreSheetSection("Export")
            MoreOptionRow(Icons.Filled.Share, "Share as text", onClick = onExportShare)
            MoreOptionRow(Icons.Filled.Description, "Export as Markdown (.md)", onClick = onExportMarkdown)
            MoreOptionRow(Icons.Filled.PictureAsPdf, "Export as PDF (.pdf)", onClick = onExportPdf)

            HorizontalDivider(Modifier.padding(vertical = Space.sm))
            MoreOptionRow(Icons.Filled.RestartAlt, "Reset chat settings", onClick = onResetSettings)
            MoreOptionRow(Icons.Filled.Delete, "Delete chat", danger = true, onClick = onDelete)
        }
    }
}

/** Uppercase group label inside the chat "More options" sheet. */
@Composable
internal fun MoreSheetSection(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = Space.lg, bottom = Space.xs)
    )
}

/** One tappable action row in the chat "More options" sheet: leading icon, title, optional
 *  subtitle. `danger` tints it as destructive; `highlight` tints it as the active state. */
@Composable
internal fun MoreOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        danger -> MaterialTheme.colorScheme.error
        highlight -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = Space.md, horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ChatEmptyState(
    personaName: String?,
    modelName: String?,
    modifier: Modifier = Modifier,
    onSuggestion: (String) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
        Text(
            if (personaName != null) "How can $personaName help?" else "What can we work on?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = Space.xl)
        )
        Text(
            "Private on this device. Type, speak, or add a file.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp).padding(top = Space.sm)
        )
        val activeContext = listOfNotNull(personaName, modelName).joinToString(" · ")
        if (activeContext.isNotBlank()) {
            Text(
                activeContext,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.md)
            )
        }
        // Starter prompts as tappable rows in a grouped card (adopts SectionCard/SectionRow).
        // Each carries a distinct display line and an inserted prompt *stem* the user finishes
        // typing — so the composer opens with intent rather than a full canned sentence.
        val starters = listOf(
            ChatStarter(Icons.Filled.Lightbulb, "Think through an idea", "Brainstorm and pressure-test options", "Help me think through an idea: "),
            ChatStarter(Icons.Filled.Description, "Summarize a document", "Attach a file, get the key points", "Summarize the key points of this: "),
            ChatStarter(Icons.Filled.Edit, "Draft something", "A clear first version to refine", "Help me draft ")
        )
        SectionCard(
            modifier = Modifier.widthIn(max = 520.dp).padding(top = Space.xl),
            items = starters.map { starter ->
                @Composable {
                    SectionRow(
                        icon = starter.icon,
                        title = starter.title,
                        subtitle = starter.subtitle,
                        onClick = { onSuggestion(starter.prompt) }
                    )
                }
            }
        )
        Text(
            "Tip: tap a message for quick actions, hold it for reactions and more, or swipe right to quote.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp).padding(top = Space.md),
        )
    }
}

internal data class ChatStarter(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val prompt: String
)

/**
 * Chat Screen — context strip. Previously up to six separate chips (workspace, folder,
 * persona, model+thinking, sources, context%) in a horizontally-scrolling row with no wrap, which
 * meant the model chip — arguably the most important one — could scroll off-screen entirely with
 * no indication anything was hidden. Now a single compact summary chip ("Default · Gemma · 2
 * sources") that opens the full breakdown in [ChatContextDetailsSheet] on tap; only genuinely
 * exceptional state (no model selected, context nearly full) stays inline next to it, since that's
 * the state a user needs to notice without tapping anything. Hidden entirely when there's nothing
 * useful to show (a brand new chat with no persona/sources/thinking mode set).
 */
@Composable
internal fun ChatContextStrip(
    workspaceName: String?,
    folderName: String?,
    personaName: String?,
    modelName: String?,
    thinkingMode: String?,
    sourceCount: Int?,
    contextTokens: Int,
    contextLimit: Int,
    contextPercent: Int,
    onWorkspaceClick: () -> Unit,
    onFolderClick: () -> Unit,
    onPersonaClick: () -> Unit,
    onModelClick: () -> Unit,
    onSourcesClick: () -> Unit,
    onContextClick: () -> Unit
) {
    if (workspaceName == null && folderName == null && personaName == null && modelName == null && sourceCount == null) return
    var showDetails by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val summary = listOfNotNull(
                folderName ?: workspaceName,
                modelName,
                sourceCount?.let { "$it source${if (it == 1) "" else "s"}" }
            ).joinToString(" · ").ifBlank { "Chat settings" }
            AssistChip(
                onClick = { showDetails = true },
                label = { Text(summary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f, fill = false)
            )
            // Exceptional state only — the normal case (a model is loaded, context has room) adds
            // nothing here; the summary chip above already covers it.
            if (modelName == null) {
                AssistChip(
                    onClick = onModelClick,
                    label = { Text("No model", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                )
            }
            if (contextPercent > 80) {
                val warn = MaterialTheme.colorScheme.vervanWarning
                AssistChip(
                    onClick = onContextClick,
                    label = { Text("Context nearly full · ~$contextPercent%", color = warn) },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, tint = warn, modifier = Modifier.size(18.dp)) },
                    border = BorderStroke(1.dp, warn.copy(alpha = 0.5f))
                )
            }
        }
        val contextColor = when {
            contextPercent >= 90 -> MaterialTheme.colorScheme.error
            contextPercent > 75 -> MaterialTheme.colorScheme.vervanWarning
            else -> MaterialTheme.colorScheme.primary
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.xs)
                .clickable(onClick = onContextClick)
                .semantics {
                    contentDescription =
                        "Estimated context: about $contextTokens of $contextLimit tokens, $contextPercent percent used"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Estimated context",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { (contextPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).padding(horizontal = Space.sm).height(4.dp),
                color = contextColor,
                trackColor = contextColor.copy(alpha = 0.16f)
            )
            Text(
                "~${compactTokenCount(contextTokens)} of ${compactTokenCount(contextLimit)} tokens · $contextPercent%",
                style = MaterialTheme.typography.labelSmall,
                color = contextColor
            )
        }
    }
    if (showDetails) {
        ChatContextDetailsSheet(
            workspaceName = workspaceName, folderName = folderName, personaName = personaName,
            modelName = modelName, thinkingMode = thinkingMode, sourceCount = sourceCount, contextPercent = contextPercent,
            onDismiss = { showDetails = false },
            onWorkspaceClick = { showDetails = false; onWorkspaceClick() },
            onFolderClick = { showDetails = false; onFolderClick() },
            onPersonaClick = { showDetails = false; onPersonaClick() },
            onModelClick = { showDetails = false; onModelClick() },
            onSourcesClick = { showDetails = false; onSourcesClick() },
            onContextClick = { showDetails = false; onContextClick() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatContextDetailsSheet(
    workspaceName: String?,
    folderName: String?,
    personaName: String?,
    modelName: String?,
    thinkingMode: String?,
    sourceCount: Int?,
    contextPercent: Int,
    onDismiss: () -> Unit,
    onWorkspaceClick: () -> Unit,
    onFolderClick: () -> Unit,
    onPersonaClick: () -> Unit,
    onModelClick: () -> Unit,
    onSourcesClick: () -> Unit,
    onContextClick: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = VervanContentWidth.reading).fillMaxWidth().align(Alignment.CenterHorizontally)
                .padding(horizontal = Space.lg).padding(bottom = Space.xxl)
        ) {
            Text("Chat context", style = MaterialTheme.typography.headlineSmall)
            Text(
                "What this chat is currently using",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.sm)
            )
            workspaceName?.let { MoreOptionRow(Icons.Filled.AccountTree, "Workspace", subtitle = it, onClick = onWorkspaceClick) }
            folderName?.let { MoreOptionRow(Icons.Filled.Folder, "Folder", subtitle = it, onClick = onFolderClick) }
            personaName?.let { MoreOptionRow(Icons.Filled.Psychology, "Persona", subtitle = it, onClick = onPersonaClick) }
            MoreOptionRow(
                Icons.Filled.Bolt, "Model",
                subtitle = modelName?.let {
                    if (thinkingMode != null) "$it · Thinking: ${thinkingMode.lowercase().replaceFirstChar { c -> c.uppercase() }}" else it
                } ?: "None selected — tap to choose one",
                danger = modelName == null,
                onClick = onModelClick
            )
            MoreOptionRow(
                Icons.AutoMirrored.Filled.MenuBook, "Sources",
                subtitle = sourceCount?.let { "$it source${if (it == 1) "" else "s"} grounding answers" } ?: "Not grounding answers to documents",
                onClick = onSourcesClick
            )
            MoreOptionRow(
                Icons.Filled.Info, "Context usage",
                subtitle = "~$contextPercent% of this model's context window used",
                danger = contextPercent > 80,
                onClick = onContextClick
            )
        }
    }
}

// A trailing ".0" (4.0k, 32.0k) is noise when the value is exact — only show the decimal when
// it's non-zero (4.2k), matching how anyone would actually write a round number by hand.
private fun compactTokenCount(tokens: Int): String = when {
    tokens >= 1_000_000 -> compactUnit(tokens, 1_000_000, "M")
    tokens >= 1_000 -> compactUnit(tokens, 1_000, "k")
    else -> tokens.toString()
}

private fun compactUnit(tokens: Int, unit: Int, suffix: String): String {
    val whole = tokens / unit
    val tenth = (tokens / (unit / 10)) % 10
    return if (tenth == 0) "$whole$suffix" else "$whole.$tenth$suffix"
}

/**
 * Chat Screen — an archived workspace remains viewable (history, branches,
 * sources all intact) but blocks new messages until the workspace is restored.
 */
@Composable
internal fun ArchivedWorkspaceBanner(onRestore: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.32f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Archived Workspace", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Restore this workspace to send new messages.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRestore) { Text("Restore and continue") }
        }
    }
}

/**
 * Chat Screen — find-in-conversation, scoped to the currently rendered branch path
 * (not the app-wide SearchScreen, which spans every chat). no inline highlighting of
 * the matched substring, just prev/next navigation and a match count — jumping to the message
 * is the useful part, highlighting inside MarkdownLiteText would need its own span-aware path.
 */
@Composable
internal fun ConversationSearchBar(messages: List<Message>, onClose: () -> Unit, onJumpTo: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var matchIndex by remember { mutableStateOf(0) }
    val matches = remember(query, messages) {
        if (query.isBlank()) emptyList() else messages.withIndex().filter { (_, m) -> m.content.contains(query, ignoreCase = true) }.map { it.index }
    }
    LaunchedEffect(matches) {
        matchIndex = 0
        matches.firstOrNull()?.let(onJumpTo)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VervanSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Find in conversation",
            modifier = Modifier.weight(1f)
        )
        if (matches.isNotEmpty()) {
            Text("${matchIndex + 1}/${matches.size}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = Space.sm))
            IconButton(onClick = { matchIndex = (matchIndex - 1 + matches.size) % matches.size; onJumpTo(matches[matchIndex]) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous match")
            }
            IconButton(onClick = { matchIndex = (matchIndex + 1) % matches.size; onJumpTo(matches[matchIndex]) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next match")
            }
        }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close search") }
    }
}

/** Juxtaposes every sibling's full text side by side — no token-level diff
 * highlighting, just the raw outputs next to each other, "compare" not "diff". */
@Composable
internal fun CompareDialog(siblings: List<Message>, onDismiss: () -> Unit, onUse: (String) -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Adaptive layout (B5): stacked cards on a compact window, side-by-side on an
        // expanded one — measured locally via BoxWithConstraints rather than threading
        // WindowSizeClass down through ChatScreen's nav signature just for this dialog.
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .padding(horizontal = Space.lg)
                .widthIn(max = VervanContentWidth.standard)
                .fillMaxWidth()
        ) {
            val stacked = maxWidth < VervanBreakpoints.medium
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = SurfaceRole.Overlay.cardColors(),
                border = SurfaceRole.Overlay.border()
            ) {
                Column(Modifier.padding(Space.md)) {
                    Text(
                        "Compare branches",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = Space.sm).semantics { heading() }
                    )
                    if (stacked) {
                        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                            siblings.forEachIndexed { index, sibling ->
                                CompareBranchCard(index, sibling, onUse, modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm).height(180.dp))
                            }
                        }
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            siblings.forEachIndexed { index, sibling ->
                                CompareBranchCard(index, sibling, onUse, modifier = Modifier.padding(end = Space.sm).size(width = 240.dp, height = 320.dp))
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = Space.xs),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompareBranchCard(index: Int, sibling: Message, onUse: (String) -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.fillMaxSize().padding(Space.sm)) {
            Text("Branch ${index + 1}", style = MaterialTheme.typography.labelMedium)
            Text(
                sibling.content.ifBlank { "(empty)" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Space.xs)
            )
            TextButton(onClick = { onUse(sibling.id) }) { Text("Use this") }
        }
    }
}

/** Replaces the old cascading 10-item "Mode & model" dropdown (thinking mode and profile each
 * listed as separate DropdownMenuItems, checkmark for the selected one) with a compact chip
 * picker — the same choices, far less scanning to find the current selection. */
@Composable
internal fun ModeSettingsDialog(
    thinkingMode: String?,
    modelDefaultThinkingMode: String?,
    thinkingAvailable: Boolean,
    currentProfile: String,
    onThinkingChange: (String?) -> Unit,
    onProfileChange: (String) -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenPersonaPicker: () -> Unit,
    temperature: Float?,
    topP: Float?,
    topK: Int?,
    defaultTemperature: Float,
    defaultTopP: Float,
    defaultTopK: Int,
    onTemperatureChange: (Float?) -> Unit,
    onTopPChange: (Float?) -> Unit,
    onTopKChange: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mode & model") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Thinking — ${
                        if (thinkingMode == null)
                            "using model default (${(modelDefaultThinkingMode ?: "OFF").lowercase().replaceFirstChar { it.uppercase() }})"
                        else "override"
                    }",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(Modifier.padding(top = Space.sm, bottom = Space.lg).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    VervanFilterChip(
                        selected = thinkingMode == null,
                        enabled = true,
                        onClick = { onThinkingChange(null) },
                        label = { Text("Default") }
                    )
                    com.vervan.chat.llm.ThinkingPolicy.MODES.forEach { mode ->
                        VervanFilterChip(
                            selected = thinkingMode == mode,
                            enabled = thinkingAvailable || mode == "OFF",
                            onClick = { onThinkingChange(mode) },
                            label = { Text(mode.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Text("Profile", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.padding(top = Space.sm, bottom = Space.lg).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    com.vervan.chat.llm.ModelProfileType.entries.forEach { p ->
                        VervanFilterChip(
                            selected = currentProfile == p.id,
                            onClick = { onProfileChange(p.id) },
                            label = { Text(p.label) }
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(bottom = Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpenModelPicker() }.padding(vertical = Space.md)) {
                    Text("Chat model", modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpenPersonaPicker() }.padding(vertical = Space.md)) {
                    Text("Persona", modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
                HorizontalDivider(Modifier.padding(vertical = Space.sm))
                // Per-chat sampler overrides — the slider always shows the
                // effective value (override or inherited default); dragging it sets a
                // chat-specific override, Reset clears back to inherited.
                Text("Generation (this chat)", style = MaterialTheme.typography.labelLarge)
                SamplerOverrideRow(
                    label = "Temperature", value = temperature ?: defaultTemperature, isOverridden = temperature != null,
                    range = 0f..2f, format = { "%.2f".format(it) },
                    onChange = { onTemperatureChange(it) }, onReset = { onTemperatureChange(null) }
                )
                SamplerOverrideRow(
                    label = "Top-P", value = topP ?: defaultTopP, isOverridden = topP != null,
                    range = 0.1f..1f, format = { "%.2f".format(it) },
                    onChange = { onTopPChange(it) }, onReset = { onTopPChange(null) }
                )
                SamplerOverrideRow(
                    label = "Top-K", value = (topK ?: defaultTopK).toFloat(), isOverridden = topK != null,
                    range = 1f..64f, format = { it.roundToInt().toString() },
                    onChange = { onTopKChange(it.roundToInt()) }, onReset = { onTopKChange(null) }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
internal fun SamplerOverrideRow(
    label: String,
    value: Float,
    isOverridden: Boolean,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Column(Modifier.padding(top = Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label: ${format(value)}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (isOverridden) {
                TextButton(onClick = onReset, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.sm)) { Text("Reset") }
            }
        }
        androidx.compose.material3.Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
internal fun SourcePickerDialog(
    initiallyEnabled: Boolean,
    initiallySelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Set<String>) -> Unit
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val kbs by app.container.db.knowledgeBaseDao().observeAll().collectAsState(initial = emptyList())
    var enabled by remember { mutableStateOf(initiallyEnabled) }
    var selected by remember { mutableStateOf(initiallySelected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ground answers in sources") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use selected knowledge bases", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                HorizontalDivider(Modifier.padding(vertical = Space.sm))
                if (kbs.isEmpty()) {
                    Text("No knowledge bases yet. Import a document in Knowledge.", style = MaterialTheme.typography.bodySmall)
                }
                kbs.forEach { kb: KnowledgeBase ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selected.contains(kb.id),
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + kb.id else selected - kb.id
                            }
                        )
                        Text(kb.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(enabled, selected) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Per-chat override for each globally-known tool: Inherit (this chat follows Settings → Tools),
 * On (force-enabled here even if globally off), Off (force-disabled here even if globally on).
 * Mirrors [com.vervan.chat.ui.settings.ToolsScreen]'s list, but per-chat instead of global.
 */
@Composable
internal fun ChatToolsDialog(
    toolsEnabled: Boolean,
    onSetToolsEnabled: (Boolean) -> Unit,
    overrides: Map<String, Boolean>,
    globallyDisabled: Set<String>,
    onSetOverride: (String, Boolean?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat tools") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                // The per-tool overrides below only take effect once tools are actually turned
                // on for this chat — without this switch there was no way to flip that master
                // flag on at all, so every chat silently stayed toolless regardless of overrides.
                Row(Modifier.fillMaxWidth().padding(bottom = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tools for this chat", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "The model discovers tools itself (list_tools, then tool_details) instead of " +
                                "getting every description up front — see the tools below.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = toolsEnabled, onCheckedChange = onSetToolsEnabled)
                }
                HorizontalDivider(Modifier.padding(bottom = Space.sm))
                Text(
                "Choose which tools this chat can use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Space.sm)
                )
                com.vervan.chat.tools.ToolRegistry.tools.forEach { tool ->
                    val override = overrides[tool.name]
                    val effectivelyOn = override ?: (tool.name !in globallyDisabled)
                    Column(Modifier.padding(vertical = Space.sm)) {
                        Text(tool.name, style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.padding(top = Space.xs)) {
                            VervanFilterChip(
                                selected = override == null,
                                onClick = { onSetOverride(tool.name, null) },
                                label = { Text(if (tool.name in globallyDisabled) "Inherit (off)" else "Inherit (on)") }
                            )
                            VervanFilterChip(
                                selected = override == true,
                                onClick = { onSetOverride(tool.name, true) },
                                label = { Text("On") },
                                modifier = Modifier.padding(start = Space.sm)
                            )
                            VervanFilterChip(
                                selected = override == false,
                                onClick = { onSetOverride(tool.name, false) },
                                label = { Text("Off") },
                                modifier = Modifier.padding(start = Space.sm)
                            )
                        }
                        if (!effectivelyOn) {
                            Text(
                                "Disabled for this chat",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}



// The dialogs below were previously inlined directly in ChatScreen()'s body. That single
// composable had grown large enough (~2,450 lines) that ART's JIT refused to compile it at all
// (logcat: "Method exceeds compiler instruction limit"), so it ran interpreted on every
// recomposition — the measured cause of dropped frames every time a chat screen opened. Moving
// each self-contained `if (show...) { ... }` block out to its own composable, matching every
// other dialog already in this file, shrinks ChatScreen's own compiled method back toward that
// limit without changing any behavior — Compose runs an extracted composable identically to an
// inlined block.

@Composable
internal fun RenameChatDialog(initialTitle: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            BoundedTextField(value = title, onValueChange = { title = it }, maxLength = 120, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { onRename(title) }, enabled = title.trim().isNotBlank() && title.length <= 120) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatVoiceOptionsBottomSheet(
    speechOutputEnabled: Boolean,
    microphoneMuted: Boolean,
    immersiveEnabled: Boolean,
    pushToTalkEnabled: Boolean,
    onToggleSpeechOutput: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleImmersive: () -> Unit,
    onTogglePushToTalk: () -> Unit,
    onSwitchModel: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        VoiceSessionOptionsSheet(
            speechOutputEnabled = speechOutputEnabled,
            microphoneMuted = microphoneMuted,
            immersiveEnabled = immersiveEnabled,
            pushToTalkEnabled = pushToTalkEnabled,
            onToggleSpeechOutput = onToggleSpeechOutput,
            onToggleMute = onToggleMute,
            onToggleImmersive = onToggleImmersive,
            onTogglePushToTalk = onTogglePushToTalk,
            onSwitchModel = onSwitchModel,
            onOpenSettings = onOpenSettings,
            onDismiss = onDismiss
        )
    }
}

/** Fetches its own sibling-workspace list (same pattern as [SourcePickerDialog]'s own
 *  knowledge-base fetch) rather than taking it as a parameter — it's dialog-local data no other
 *  caller needs. */
@Composable
internal fun WorkspaceOptionsDialog(
    workspace: Workspace,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSetActive: () -> Unit,
    onMoveTo: (Workspace) -> Unit
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val otherWorkspaces by app.container.db.workspaceDao().observeActive().collectAsState(initial = emptyList())
    val activeWorkspaceId by app.container.settingsRepository.activeWorkspaceId.collectAsState(initial = "")
    val isChatWorkspaceActive = workspace.id == activeWorkspaceId
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(workspace.name) },
        text = {
            Column {
                Text("Open workspace", modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = Space.md))
                if (!isChatWorkspaceActive) {
                    Text(
                        "Set as active workspace",
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onSetActive).padding(vertical = Space.md)
                    )
                }
                HorizontalDivider()
                Text("Move to another workspace", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Space.sm))
                otherWorkspaces.filter { it.id != workspace.id }.forEach { ws ->
                    Text(
                        ws.name,
                        modifier = Modifier.fillMaxWidth().clickable { onMoveTo(ws) }.padding(vertical = Space.md)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
internal fun MoveToWorkspaceConfirmDialog(
    targetName: String,
    fromWorkspaceName: String,
    folderName: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to \"$targetName\"?") },
        text = {
            Column {
                Text("From: $fromWorkspaceName")
                Text("To: $targetName")
                if (folderName != null) Text("This chat will leave \"$folderName\" and become unfiled.")
                Text("Messages, branches, attachments, and history are kept.")
                Text("Chat-specific model and persona choices are also kept.")
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Move") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun ResetChatSettingsDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset chat settings?") },
        text = {
            Column {
                Text("Resets AI, source, tool, and knowledge settings to workspace defaults.")
                Text("Messages, attachments, workspace, and folder stay unchanged.", modifier = Modifier.padding(top = Space.sm))
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Reset") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun ChatStatsDialog(stats: ChatViewModel.ChatStats, onDismiss: () -> Unit) {
    val dateFormat = remember { java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat details") },
        text = {
            Column {
                Text("Messages: ${stats.totalMessages} (${stats.userMessages} user, ${stats.assistantMessages} assistant)")
                Text("Attachments: ${stats.attachments}")
                Text("Branches: ${stats.branchPoints}")
                Text("Created: ${dateFormat.format(java.util.Date(stats.createdAt))}")
                Text("Last updated: ${dateFormat.format(java.util.Date(stats.updatedAt))}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
internal fun AddToKnowledgeBaseDialog(knowledgeBases: List<KnowledgeBase>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to knowledge base") },
        text = {
            Column {
                if (knowledgeBases.isEmpty()) {
                    Text("No knowledge bases yet. Create one in Knowledge.")
                }
                knowledgeBases.forEach { kb ->
                    Text(
                        kb.name,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(kb.id) }.padding(vertical = Space.md)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun PersonaPickerDialog(personas: List<Persona>, selectedPersonaId: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Persona") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onSelect(null) }) {
                    androidx.compose.material3.RadioButton(selected = selectedPersonaId == null, onClick = null)
                    Text("No persona", modifier = Modifier.padding(start = Space.sm))
                }
                personas.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onSelect(option.id) }) {
                        androidx.compose.material3.RadioButton(selected = selectedPersonaId == option.id, onClick = null)
                        OverflowTooltipText(text = option.name, modifier = Modifier.weight(1f).padding(start = Space.sm))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
internal fun ChatModelPickerDialog(models: List<ModelInfo>, selectedModelId: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat model") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onSelect(null) }) {
                    androidx.compose.material3.RadioButton(selected = selectedModelId == null, onClick = null)
                    Text("Use active default", modifier = Modifier.padding(start = Space.sm))
                }
                models.forEach { model ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onSelect(model.id) }) {
                        androidx.compose.material3.RadioButton(selected = selectedModelId == model.id, onClick = null)
                        OverflowTooltipText(text = model.displayName, modifier = Modifier.weight(1f).padding(start = Space.sm))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
internal fun ContextBreakdownDialog(breakdown: ContextBreakdown, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Context for the next message") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                val palette = listOf(
                    MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.vervanSuccess,
                    MaterialTheme.colorScheme.vervanWarning, MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.error,
                    MaterialTheme.colorScheme.outline
                )
                com.vervan.chat.ui.common.ContextUsageBar(
                    usedTokens = breakdown.estimatedTotalTokens,
                    totalTokens = breakdown.recommendedLimit,
                    summary = "About ${breakdown.estimatedTotalTokens} of ${breakdown.recommendedLimit} recommended tokens used.",
                    slices = breakdown.items.mapIndexed { i, item ->
                        com.vervan.chat.ui.common.ContextSlice(item.label, item.estimatedTokens, palette[i % palette.size])
                    }
                )
                if (breakdown.estimatedTotalTokens > breakdown.recommendedLimit) {
                    Text(
                        "Over the recommended limit. Older context will be trimmed before sending.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Space.sm)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// The three bundles below fix the *real* cause of ChatScreen()'s oversized compiled method — see
// the dexdump analysis: not the dialog blocks (moved out above), but ~50 individual
// `collectAsState()`/`remember` calls sitting directly in ChatScreen's own body, each one costing
// its own Compose skip-check scaffolding (startReplaceGroup/rememberedValue/changed/
// endReplaceGroup) *inline in ChatScreen's own method* regardless of how small the read itself is.
// Bundling a group of reads into one `@Composable` helper moves that scaffolding into the helper's
// own compiled method — ChatScreen then pays for one call site instead of a dozen. Destructuring
// the result back into identically-named locals (`val (a, b, c) = rememberX(...)`) means none of
// the hundreds of existing usages elsewhere in ChatScreen need to change.

/** Every plain 1:1 [ChatViewModel] StateFlow read ChatScreen makes before it needs any of them for
 *  actual logic — no computation, just data. */
internal data class ChatVmState(
    val messages: List<Message>,
    val allMessages: List<Message>,
    val isGenerating: Boolean,
    val isRetrieving: Boolean,
    val isRecallingMemory: Boolean,
    val error: String?,
    val chat: Chat?,
    val workspace: Workspace?,
    val folder: Folder?,
    val isWorkspaceArchived: Boolean,
    val titleGenerating: Boolean,
    val confirmationMessage: String?,
    val pendingDocument: ChatViewModel.DocumentAttachState?,
    val attachEmbedProgress: com.vervan.chat.model.DocumentImportManager.EmbedProgress?,
    val documents: List<Document>,
    val savedOutputs: List<SavedOutput>,
    val persona: Persona?,
    val personas: List<Persona>,
    val activeModelName: String?,
    val selectedGenerationModel: ModelInfo?,
    val generationModels: List<ModelInfo>,
    val modelLoadState: ChatViewModel.ModelLoadState,
    val visionAvailable: Boolean?,
    val audioAvailable: Boolean?
)

@Composable
internal fun rememberChatVmState(vm: ChatViewModel, app: VervanApp): ChatVmState {
    val messages by vm.messages.collectAsState()
    val allMessages by vm.allMessages.collectAsState()
    val isGenerating by vm.isGenerating.collectAsState()
    val isRetrieving by vm.isRetrieving.collectAsState()
    val isRecallingMemory by vm.isRecallingMemory.collectAsState()
    val error by vm.error.collectAsState()
    val chat by vm.chat.collectAsState()
    val workspace by vm.workspace.collectAsState()
    val folder by vm.folder.collectAsState()
    val isWorkspaceArchived by vm.isWorkspaceArchived.collectAsState()
    val titleGenerating by vm.titleGenerating.collectAsState()
    val confirmationMessage by vm.confirmationMessage.collectAsState()
    val pendingDocument by vm.pendingDocument.collectAsState()
    val attachEmbedProgress by app.container.documentImportManager.embedProgress.collectAsState()
    val documents by app.container.db.documentDao().observeAll().collectAsState(initial = emptyList())
    val savedOutputs by app.container.db.savedOutputDao().observeAll().collectAsState(initial = emptyList())
    val persona by vm.persona.collectAsState()
    val personas by vm.personas.collectAsState()
    val activeModelName by vm.activeModelName.collectAsState()
    val selectedGenerationModel by vm.selectedGenerationModel.collectAsState()
    val generationModels by vm.generationModels.collectAsState()
    val modelLoadState by vm.modelLoadState.collectAsState()
    val visionAvailable by vm.visionAvailable.collectAsState()
    val audioAvailable by vm.audioAvailable.collectAsState()
    return ChatVmState(
        messages, allMessages, isGenerating, isRetrieving, isRecallingMemory, error, chat, workspace,
        folder, isWorkspaceArchived, titleGenerating, confirmationMessage, pendingDocument,
        attachEmbedProgress, documents, savedOutputs, persona, personas, activeModelName,
        selectedGenerationModel, generationModels, modelLoadState, visionAvailable, audioAvailable
    )
}

/** Settings-repository flags this screen reads once up front — mostly to feed
 *  [com.vervan.chat.voice.SttEnginePolicy.resolve] and gate voice/auto-read behavior later. */
internal data class ChatVoiceSettingsState(
    val hapticsEnabled: Boolean,
    val speechInputEnabled: Boolean,
    val modelAudioSttEnabled: Boolean,
    val whisperSttEnabled: Boolean,
    val androidSttEnabled: Boolean,
    val sttEnginePreference: String,
    val sttFallbackEnabled: Boolean,
    val installedVoiceModels: List<com.vervan.chat.data.db.entities.TtsVoiceModel>,
    val voiceReplyMode: String,
    val transcriptReviewEnabled: Boolean,
    val voicePushToTalkEnabled: Boolean,
    val autoReadAloud: Boolean
) {
    // ponytail: 12-field data class read back via component1..12 destructuring at the call
    // site — no arity limit in Kotlin, just a lot of positions; the alternative (field access
    // at every one of the ~15 scattered use sites) is the same line count with more diff noise.
}

@Composable
internal fun rememberChatVoiceSettingsState(app: VervanApp): ChatVoiceSettingsState {
    val hapticsEnabled by app.container.settingsRepository.hapticsEnabled.collectAsState(initial = true)
    val speechInputEnabled by app.container.settingsRepository.speechInputEnabled.collectAsState(initial = true)
    val modelAudioSttEnabled by app.container.settingsRepository.modelAudioSttEnabled.collectAsState(initial = true)
    val whisperSttEnabled by app.container.settingsRepository.inbuiltSttEnabled.collectAsState(initial = true)
    val androidSttEnabled by app.container.settingsRepository.androidSttEnabled.collectAsState(initial = true)
    val sttEnginePreference by app.container.settingsRepository.sttEnginePreference.collectAsState(initial = "AUTO")
    val sttFallbackEnabled by app.container.settingsRepository.sttFallbackEnabled.collectAsState(initial = true)
    val installedVoiceModels by app.container.db.ttsVoiceModelDao().observeAll().collectAsState(initial = emptyList())
    val voiceReplyMode by app.container.settingsRepository.voiceReplyMode.collectAsState(initial = "MANUAL")
    val transcriptReviewEnabled by app.container.settingsRepository.transcriptReviewEnabled.collectAsState(initial = true)
    val voicePushToTalkEnabled by app.container.settingsRepository.voicePushToTalkEnabled.collectAsState(initial = false)
    val autoReadAloud by app.container.settingsRepository.autoReadAloud.collectAsState(initial = false)
    return ChatVoiceSettingsState(
        hapticsEnabled, speechInputEnabled, modelAudioSttEnabled, whisperSttEnabled, androidSttEnabled,
        sttEnginePreference, sttFallbackEnabled, installedVoiceModels, voiceReplyMode,
        transcriptReviewEnabled, voicePushToTalkEnabled, autoReadAloud
    )
}

/** Every [com.vervan.chat.voice.RealtimeVoiceController] StateFlow ChatScreen reads to drive the
 *  hands-free/immersive voice UI — read here in one call instead of 15 separate ones. */
internal data class VoiceControllerUiState(
    val voiceState: com.vervan.chat.voice.VoiceControllerState,
    val voiceTurns: List<com.vervan.chat.voice.VoiceTurn>,
    val voiceWaveform: List<Float>,
    val voiceElapsedMs: Int,
    val voiceLiveTranscript: String,
    val voiceSttLabel: String,
    val voiceTtsLabel: String,
    val voiceHasEchoCancellation: Boolean,
    val voicePlaybackPaused: Boolean,
    val voiceMicrophoneMuted: Boolean,
    val voiceSpeechOutputEnabled: Boolean,
    val voiceModelLoadError: String?,
    val voiceSttUnavailable: Boolean,
    val voiceLoadingModelName: String?,
    val voicePushToTalkHeld: Boolean
)

@Composable
internal fun rememberVoiceControllerUiState(voiceController: com.vervan.chat.voice.RealtimeVoiceController): VoiceControllerUiState {
    val voiceState by voiceController.state.collectAsState()
    val voiceTurns by voiceController.turns.collectAsState()
    val voiceWaveform by voiceController.liveWaveform.collectAsState()
    val voiceElapsedMs by voiceController.liveElapsedMs.collectAsState()
    val voiceLiveTranscript by voiceController.liveTranscript.collectAsState()
    val voiceSttLabel by voiceController.sttLabel.collectAsState()
    val voiceTtsLabel by voiceController.ttsLabel.collectAsState()
    val voiceHasEchoCancellation by voiceController.hasEchoCancellation.collectAsState()
    val voicePlaybackPaused by voiceController.playbackPaused.collectAsState()
    val voiceMicrophoneMuted by voiceController.microphoneMuted.collectAsState()
    val voiceSpeechOutputEnabled by voiceController.speechOutputEnabled.collectAsState()
    val voiceModelLoadError by voiceController.modelLoadError.collectAsState()
    val voiceSttUnavailable by voiceController.sttUnavailable.collectAsState()
    val voiceLoadingModelName by voiceController.loadingModelName.collectAsState()
    val voicePushToTalkHeld by voiceController.pushToTalkHeld.collectAsState()
    return VoiceControllerUiState(
        voiceState, voiceTurns, voiceWaveform, voiceElapsedMs, voiceLiveTranscript, voiceSttLabel,
        voiceTtsLabel, voiceHasEchoCancellation, voicePlaybackPaused, voiceMicrophoneMuted,
        voiceSpeechOutputEnabled, voiceModelLoadError, voiceSttUnavailable, voiceLoadingModelName,
        voicePushToTalkHeld
    )
}

// Same rationale as the state bundles above, applied to attach-picker launchers: each
// rememberLauncherForActivityResult call (and its callback body) is another direct call site in
// ChatScreen's own compiled method. These take callback params instead of touching ChatScreen's
// mutable state directly, matching the pattern the dialog extraction above already established.

internal data class ImageAttachLaunchers(
    val pickImage: androidx.activity.compose.ManagedActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest, Uri?>,
    val requestCameraPermission: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>
)

@Composable
internal fun rememberImageAttachLaunchers(
    vm: ChatViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    onPreviewReady: (Boolean) -> Unit,
    onError: (String) -> Unit
): ImageAttachLaunchers {
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val copied = vm.copyImage(uri)
            vm.setPendingImage(copied)
            onPreviewReady(copied != null)
            if (copied == null) onError("Couldn’t prepare that image. Choose another photo and try again.")
        }
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingCameraFile?.let { com.vervan.chat.model.ImageUtils.fixOrientation(it) }
            pendingCameraFile?.absolutePath?.let { vm.setPendingImage(it) }
            onPreviewReady(pendingCameraFile != null)
        } else {
            pendingCameraFile?.delete()
        }
        pendingCameraFile = null
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (file, uri) = vm.newCameraImageFile()
            pendingCameraFile = file
            takePicture.launch(uri)
        } else {
            onError("Camera access is off. Choose a photo, or allow it in Android Settings → Apps → Vervan → Permissions.")
        }
    }
    return ImageAttachLaunchers(pickImage, requestCameraPermission)
}

internal data class OcrAttachLaunchers(
    val pickOcrImage: androidx.activity.compose.ManagedActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest, Uri?>,
    val requestOcrCameraPermission: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>
)

@Composable
internal fun rememberOcrAttachLaunchers(
    vm: ChatViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    onRunningChange: (Boolean) -> Unit,
    onOcrResult: (Result<ChatViewModel.OcrResult>) -> Unit,
    onError: (String) -> Unit
): OcrAttachLaunchers {
    var pendingOcrCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    val pickOcrImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            onRunningChange(true)
            scope.launch { onOcrResult(vm.extractOcr(uri)) }
        }
    }
    val takeOcrPicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingOcrCameraFile
        pendingOcrCameraFile = null
        if (success && file != null) {
            onRunningChange(true)
            scope.launch { onOcrResult(vm.extractOcrFromFile(file)) }
        } else {
            file?.delete()
        }
    }
    val requestOcrCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (file, uri) = vm.newCameraImageFile()
            pendingOcrCameraFile = file
            takeOcrPicture.launch(uri)
        } else {
            onError("Camera access is off. Choose an image, or allow it in Android Settings → Apps → Vervan → Permissions.")
        }
    }
    return OcrAttachLaunchers(pickOcrImage, requestOcrCameraPermission)
}

@Composable
internal fun rememberDocumentAttachLauncher(
    context: Context,
    onSelected: (PendingDocumentSelection) -> Unit
): androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Uri?> =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onSelected(inspectDocument(context, uri))
    }
