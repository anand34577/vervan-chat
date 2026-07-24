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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.vervan.chat.data.db.entities.SavedOutput
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(outputs, key = { it.id }) { output ->
                        Card(
                            onClick = { onOpen(output) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
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
    onLoad: () -> Unit,
    onOpenModels: () -> Unit
) {
    if (state is ChatViewModel.ModelLoadState.Ready) return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(
            Modifier.fillMaxWidth().widthIn(max = 840.dp).padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (state) {
                    is ChatViewModel.ModelLoadState.Failed -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Psychology,
                        contentDescription = null,
                        tint = if (state is ChatViewModel.ModelLoadState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            when (state) {
                                ChatViewModel.ModelLoadState.NoModel -> "A local model is required"
                                is ChatViewModel.ModelLoadState.NotLoaded -> "${state.modelName} is not loaded"
                                is ChatViewModel.ModelLoadState.Loading -> "Loading ${state.modelName}"
                                is ChatViewModel.ModelLoadState.Failed -> "Could not load ${state.modelName}"
                                is ChatViewModel.ModelLoadState.Ready -> "Ready"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            when (state) {
                                ChatViewModel.ModelLoadState.NoModel -> "Import or select a generation model to enable the composer."
                                is ChatViewModel.ModelLoadState.NotLoaded -> "Load it now, or enable automatic loading in Generation settings."
                                is ChatViewModel.ModelLoadState.Loading -> state.stage
                                is ChatViewModel.ModelLoadState.Failed -> state.reason
                                is ChatViewModel.ModelLoadState.Ready -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    when (state) {
                        ChatViewModel.ModelLoadState.NoModel -> TextButton(onClick = onOpenModels) { Text("Models") }
                        is ChatViewModel.ModelLoadState.NotLoaded,
                        is ChatViewModel.ModelLoadState.Failed -> TextButton(onClick = onLoad) { Text("Load") }
                        else -> Unit
                    }
                }
                if (state is ChatViewModel.ModelLoadState.Loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
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
            Modifier.fillMaxWidth().widthIn(max = 840.dp).padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                Text(
                    if (severe) "Running much slower — device is very warm" else "Running slower due to device temperature",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(start = 10.dp)
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
            Modifier.widthIn(max = 840.dp).padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    "${String.format("%.1f", stats.tokensPerSecond)} tok/s · ${stats.tokens} tokens · ${stats.availMemMb}/${stats.totalMemMb} MB free",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp)
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
    isIncognito: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onGenerateTitle: () -> Unit,
    onRestoreTitle: () -> Unit,
    onSavedResponses: () -> Unit,
    onBranchTree: () -> Unit,
    onContextInspector: () -> Unit,
    toolsAvailable: Boolean,
    onChatTools: () -> Unit,
    onToggleIncognito: () -> Unit,
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
            Modifier.fillMaxWidth().widthIn(max = 720.dp).align(Alignment.CenterHorizontally)
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

            MoreSheetSection("Session")
            MoreOptionRow(
                Icons.Filled.VisibilityOff,
                if (isIncognito) "Turn incognito off" else "Turn incognito on",
                subtitle = if (isIncognito) "Deletes when you leave" else null,
                highlight = isIncognito,
                onClick = onToggleIncognito
            )

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
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs),
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
                label = { Text("Context high · ~$contextPercent%", color = warn) },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, tint = warn, modifier = Modifier.size(18.dp)) },
                border = BorderStroke(1.dp, warn.copy(alpha = 0.5f))
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
            Modifier.fillMaxWidth().widthIn(max = 720.dp).align(Alignment.CenterHorizontally)
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

/**
 * Chat Screen — an archived workspace remains viewable (history, branches,
 * sources all intact) but blocks new messages until the workspace is restored.
 */
@Composable
internal fun ArchivedWorkspaceBanner(onRestore: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.32f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VervanSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Find in conversation",
            modifier = Modifier.weight(1f)
        )
        if (matches.isNotEmpty()) {
            Text("${matchIndex + 1}/${matches.size}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp))
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
    Dialog(onDismissRequest = onDismiss) {
        // Adaptive layout (B5): stacked cards on a compact window, side-by-side on an
        // expanded one — measured locally via BoxWithConstraints rather than threading
        // WindowSizeClass down through ChatScreen's nav signature just for this dialog.
        androidx.compose.foundation.layout.BoxWithConstraints {
            val stacked = maxWidth < 600.dp
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Compare branches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    if (stacked) {
                        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                            siblings.forEachIndexed { index, sibling ->
                                CompareBranchCard(index, sibling, onUse, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 120.dp))
                            }
                        }
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            siblings.forEachIndexed { index, sibling ->
                                CompareBranchCard(index, sibling, onUse, modifier = Modifier.padding(end = 8.dp).size(width = 240.dp, height = 320.dp))
                            }
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) { Text("Close") }
                }
            }
        }
    }
}

@Composable
internal fun CompareBranchCard(index: Int, sibling: Message, onUse: (String) -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(8.dp)) {
            Text("Branch ${index + 1}", style = MaterialTheme.typography.labelMedium)
            Text(
                sibling.content.ifBlank { "(empty)" },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 4.dp)
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
    thinkingMode: String,
    thinkingAvailable: Boolean,
    currentProfile: String,
    onThinkingChange: (String) -> Unit,
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
                Text("Thinking", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.padding(top = 6.dp, bottom = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("OFF", "FAST", "BALANCED", "DEEP").forEach { mode ->
                        VervanFilterChip(
                            selected = thinkingMode == mode,
                            enabled = thinkingAvailable || mode == "OFF",
                            onClick = { onThinkingChange(mode) },
                            label = { Text(mode.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Text("Profile", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.padding(top = 6.dp, bottom = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.vervan.chat.llm.ModelProfileType.entries.forEach { p ->
                        VervanFilterChip(
                            selected = currentProfile == p.id,
                            onClick = { onProfileChange(p.id) },
                            label = { Text(p.label) }
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(bottom = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpenModelPicker() }.padding(vertical = 12.dp)) {
                    Text("Chat model", modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpenPersonaPicker() }.padding(vertical = 12.dp)) {
                    Text("Persona", modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label: ${format(value)}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (isOverridden) {
                TextButton(onClick = onReset, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) { Text("Reset") }
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
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
                Text(
                "Choose which tools this chat can use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                com.vervan.chat.tools.ToolRegistry.tools.forEach { tool ->
                    val override = overrides[tool.name]
                    val effectivelyOn = override ?: (tool.name !in globallyDisabled)
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(tool.name, style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.padding(top = 4.dp)) {
                            VervanFilterChip(
                                selected = override == null,
                                onClick = { onSetOverride(tool.name, null) },
                                label = { Text(if (tool.name in globallyDisabled) "Inherit (off)" else "Inherit (on)") }
                            )
                            VervanFilterChip(
                                selected = override == true,
                                onClick = { onSetOverride(tool.name, true) },
                                label = { Text("On") },
                                modifier = Modifier.padding(start = 6.dp)
                            )
                            VervanFilterChip(
                                selected = override == false,
                                onClick = { onSetOverride(tool.name, false) },
                                label = { Text("Off") },
                                modifier = Modifier.padding(start = 6.dp)
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


