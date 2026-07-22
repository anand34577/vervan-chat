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


@Composable
internal fun MessageBubble(
    message: Message,
    attachedDocument: Document? = null,
    savedOutput: SavedOutput?,
    onBookmarkChanged: (Boolean) -> Unit,
    onRemember: (String) -> Unit,
    onReaction: (String?) -> Unit,
    onReadAloud: (text: String, utteranceId: String) -> Unit,
    isGenerating: Boolean,
    siblingPosition: Pair<Int, Int>,
    onConfirmTool: (Boolean) -> Unit,
    onEditAndResend: (String) -> Unit,
    onRegenerate: () -> Unit,
    onSwitchBranch: (Int) -> Unit,
    onCompare: () -> Unit,
    onFork: () -> Unit = {},
    onOpenPassage: (String) -> Unit = {},
    onOpenDocument: (String) -> Unit = {},
    clarificationEnabled: Boolean = false,
    onClarificationReply: (String) -> Unit = {},
    isLastAssistant: Boolean = false,
    onQuickReply: (QuickReply) -> Unit = {},
    onQuote: (String) -> Unit = {},
    onRetryWithQuality: () -> Unit = {},
    betterModelName: String? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val displayedContent = rememberBatchedStreamingText(
        text = message.content,
        isStreaming = message.state == MessageState.STREAMING
    )
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val showGenerationStats by app.container.settingsRepository.showGenerationStats.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    var showRememberDialog by remember { mutableStateOf(false) }
    var showSaveAsPromptDialog by remember { mutableStateOf(false) }
    var showMessageMenu by remember { mutableStateOf(false) }
    var showImagePreview by remember(message.id) { mutableStateOf(false) }
    var editing by remember(message.id) { mutableStateOf(false) }
    var editText by remember(message.id) { mutableStateOf(message.content) }
    val timeLabel = remember(message.createdAt) {
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(message.createdAt))
    }
    // Text used when this message is quoted in a reply. Must be the *visible* answer — not the raw
    // content — otherwise quoting a reasoning model's reply drags its <think>…</think> block (and
    // any tool-call markup) into the quote, which then renders as literal tags in the composer.
    val quotableText = remember(message.content, isUser) {
        if (isUser) message.content
        else {
            val stripped = com.vervan.chat.tools.ToolCallParser.stripForDisplay(message.content)
            val answer = com.vervan.chat.llm.ThinkingParser.parse(stripped).answer
            com.vervan.chat.llm.ClarificationParser.parse(answer).answer.ifBlank { answer }.trim()
        }
    }
    // Actions (edit/speak/save/remember/regenerate/more) used to render permanently on every
    // bubble — busy and "congested" with a full conversation on screen. Tap the bubble to
    // reveal them instead, matching the tap-to-reveal pattern most chat apps use.
    var showActions by remember(message.id) { mutableStateOf(false) }
    // Long-press opens the modern context-menu sheet (reactions + actions) — the standard
    // pattern in WhatsApp/Telegram/iMessage. Kept alongside the legacy tap-to-reveal row so
    // users who learned the old gesture aren't broken; long-press is the discoverable one.
    var showActionsSheet by remember(message.id) { mutableStateOf(false) }
    // In-memory reaction for this bubble. Persisting reactions across sessions is out of scope
    // for this UI pass — the chat schema doesn't have a reactions table yet — but the visual
    // affordance is here so the UX is right and the data layer can be wired in later.
    val reactions = remember(message.reaction) {
        message.reaction?.let { listOf(MessageReaction(emoji = it, count = 1, mine = true)) }.orEmpty()
    }
    // Swipe right to reply instead of a "Quote in reply" menu item, same gesture on either
    // role's messages.
    val dragOffset = remember(message.id) { androidx.compose.animation.core.Animatable(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val quoteThresholdPx = remember(density) { with(density) { 56.dp.toPx() } }
    // The system's default touch slop (~8dp) is tuned for "did the finger move at all", which
    // is far too sensitive for a deliberate swipe-to-reply gesture — any accidental drift while
    // scrolling the list vertically would engage it. Require a real, clearly-horizontal swipe
    // before committing to drag mode at all.
    val swipeEngagePx = remember(density) { with(density) { 24.dp.toPx() } }
    Box(modifier.fillMaxWidth()) {
        Icon(
            Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .graphicsLayer { alpha = (dragOffset.value / quoteThresholdPx).coerceIn(0f, 1f) }
        )
        Column(
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxWidth(if (isUser) 0.82f else 0.96f),
            // Right-align the (wrap-width) user bubble within its right-anchored column, left-align
            // the assistant bubble. Without this a short user message renders start-aligned inside
            // the 82% column and appears floating near the middle of the screen.
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
        Card(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(dragOffset.value.roundToInt(), 0) }
                // Subtle entrance settle on first render — M3 Expressive "fast" spring keeps
                // bubbles from snapping in during streaming and branch switches.
                .graphicsLayer { alpha = 1f }
                .then(
                    if (isUser) Modifier.background(
                        com.vervan.chat.ui.theme.vervanBrandGradient(),
                        com.vervan.chat.ui.theme.VervanExtraShapes.userBubble
                    ) else Modifier
                )
                // A separate pointerInput(drag) + .clickable(tap) on the same node are two
                // independent gesture detectors racing over the same touch stream — on real
                // hardware a tap's inevitable sub-millimeter jitter would occasionally get
                // claimed by the drag detector first, so the tap silently never fired ("taps
                // sometimes expand, sometimes don't"). One manual gesture loop that decides
                // tap vs. drag vs. long-press itself is the reliable fix.
                // The manual gesture loop exposes no semantics of its own, so TalkBack users
                // would have no way to reveal the action row or reply — mirror both gestures
                // as accessibility actions here.
                .semantics {
                    onClick(label = if (showActions) "Hide message actions" else "Show message actions") {
                        showActions = !showActions
                        true
                    }
                    customActions = listOf(
                        androidx.compose.ui.semantics.CustomAccessibilityAction("Reply with quote") {
                            onQuote(quotableText)
                            true
                        },
                        androidx.compose.ui.semantics.CustomAccessibilityAction("Show message options") {
                            showActionsSheet = true
                            true
                        }
                    )
                }
                .pointerInput(message.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        // Once true, this gesture is a vertical scroll (or was never going to be
                        // a swipe) — stop evaluating it as a possible reply-swipe for the rest of
                        // this touch, but keep consuming nothing so the list's own scroll handles it.
                        var abandoned = false
                        var totalDx = 0f
                        var totalDy = 0f
                        // Long-press detection: timed separately from drag/tap. Matches the
                        // ViewConfiguration.getLongPressTimeout() (400ms default) but kept inline
                        // because we're already in a manual gesture loop and can't host a separate
                        // detectTapGestures.onLongPress without re-fighting the same race that
                        // motivated the manual loop in the first place.
                        val longPressTimeoutMs = 400L
                        val pressStart = System.currentTimeMillis()
                        var longPressFired = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (dragging) {
                                    val shouldQuote = dragOffset.value > quoteThresholdPx
                                    scope.launch {
                                        if (shouldQuote) onQuote(quotableText)
                                        dragOffset.animateTo(0f)
                                    }
                                } else if (!abandoned && !longPressFired) {
                                    showActions = !showActions
                                }
                                break
                            }
                            if (!dragging && !abandoned) {
                                totalDx += change.positionChange().x
                                totalDy += change.positionChange().y
                                val absDx = kotlin.math.abs(totalDx)
                                val absDy = kotlin.math.abs(totalDy)
                                when {
                                    // Direction lock: only a swipe that's clearly more horizontal
                                    // than vertical, and past a real commit distance, engages —
                                    // a diagonal or mostly-vertical drag never triggers it.
                                    absDx > swipeEngagePx && absDx > absDy * 1.5f -> dragging = true
                                    absDy > swipeEngagePx && absDy > absDx * 1.5f -> abandoned = true
                                }
                                // Long-press fires once, after the timeout, only if the finger
                                // has stayed roughly put (not engaged/abandoned). Haptic mirror
                                // of the long-press feedback the framework gives for free elsewhere.
                                if (!longPressFired && !dragging && !abandoned &&
                                    System.currentTimeMillis() - pressStart >= longPressTimeoutMs &&
                                    absDx < swipeEngagePx && absDy < swipeEngagePx
                                ) {
                                    longPressFired = true
                                    showActionsSheet = true
                                }
                            }
                            if (dragging) {
                                val dx = change.positionChange().x
                                change.consume()
                                scope.launch { dragOffset.snapTo((dragOffset.value + dx).coerceIn(0f, quoteThresholdPx * 1.5f)) }
                            }
                        }
                    }
                },
            shape = if (isUser) com.vervan.chat.ui.theme.VervanExtraShapes.userBubble else com.vervan.chat.ui.theme.VervanExtraShapes.assistantBubble,
            // Asymmetric modern chat canvas: the user's messages are brand-gradient bubbles
            // (instantly scannable as "mine"), while the assistant answers directly on the
            // conversation background — no box, no border — the open-canvas reading layout
            // ChatGPT/Gemini use. Rich inner content (sources, tool cards, attachments) brings
            // its own surfaces, so it still reads grouped without an enclosing card.
            // The gradient is painted behind a transparent Card via Modifier.background so the
            // Card still owns shape clipping and content color.
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp, hoveredElevation = 0.dp),
            border = null
        ) {
            Column(
                Modifier.padding(
                    horizontal = if (isUser) Space.lg else Space.xs,
                    vertical = if (isUser) Space.md else Space.sm
                )
            ) {
                // Assistant messages carry a compact identity header (gradient avatar + name +
                // live state). User messages drop the header entirely — no redundant "You" label
                // or avatar — and show a quiet timestamp under their text instead, so the
                // conversation reads asymmetrically the way modern chat apps do.
                if (!isUser) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = Space.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Brand-gradient avatar — the same mark the Home hero uses, one visual
                        // identity for "Vervan" across screens.
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(com.vervan.chat.ui.theme.vervanBrandGradient(), androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(
                            "Vervan",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = Space.sm)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            when (message.state) {
                                MessageState.STREAMING -> "Responding"
                                MessageState.INTERRUPTED -> "Interrupted"
                                MessageState.FAILED -> "Failed"
                                MessageState.CANCELLED -> "Stopped"
                                MessageState.AWAITING_CONFIRMATION -> "Needs approval"
                                MessageState.COMPLETE -> timeLabel
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (message.state) {
                                MessageState.FAILED, MessageState.INTERRUPTED -> MaterialTheme.colorScheme.error
                                MessageState.STREAMING -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                message.imagePath?.let { path ->
                    // Media-first bubble preview; tap opens the zoomable full-screen viewer.
                    val previewPx = with(LocalDensity.current) { 560.dp.roundToPx() }
                    val bitmap = remember(path, previewPx) {
                        com.vervan.chat.model.ImageUtils.decodeThumbnail(path, previewPx)?.asImageBitmap()
                    }
                    bitmap?.let {
                        Image(
                            it, contentDescription = "Attached image — tap to view",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 280.dp)
                                .padding(bottom = 8.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { showImagePreview = true },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                message.documentId?.let { documentId ->
                    val file = attachedDocument?.filePath?.let { java.io.File(it) }
                    val extension = attachedDocument?.displayName?.substringAfterLast('.', "")
                        ?.takeIf { it.isNotBlank() }?.uppercase() ?: "DOCUMENT"
                    Surface(
                        onClick = { onOpenDocument(documentId) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            ) {
                                Icon(
                                    Icons.Filled.Description,
                                    contentDescription = null,
                                    modifier = Modifier.padding(10.dp).size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(
                                    attachedDocument?.displayName ?: "Attached document",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    "$extension · ${readableFileSize(file?.takeIf { it.exists() }?.length())}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open document preview", modifier = Modifier.size(20.dp))
                        }
                    }
                }
                message.audioPath?.let { audioPath ->
                    if (isUser) {
                        // The slider's primary-colored track would be invisible against the
                        // solid-primary bubble — host the player on a normal surface island.
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = Space.xs)
                        ) { VoiceMessageRow(audioPath) }
                    } else {
                        VoiceMessageRow(audioPath)
                    }
                }
                if (editing) {
                    // Editing happens inside the solid-primary user bubble — the field gets a
                    // normal surface island and the buttons explicit onPrimary so neither
                    // disappears against the accent fill.
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    val editButtonColors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                    Row(Modifier.padding(top = 4.dp)) {
                        TextButton(colors = editButtonColors, onClick = {
                            editing = false
                            if (editText.isNotBlank() && editText != message.content) onEditAndResend(editText)
                        }) { Text("Send") }
                        TextButton(colors = editButtonColors, onClick = { editing = false; editText = message.content }) { Text("Cancel") }
                    }
                } else {
                    // Strip <tool_call> markup before Thinking/Clarification parsing, same as
                    // those two already do for their own tags — without this, the raw
                    // {"tool": ..., "params": ...} JSON types out visibly in the bubble while
                    // streaming and only disappears once the message reaches COMPLETE.
                    val toolCallHidden = remember(displayedContent) { com.vervan.chat.tools.ToolCallParser.stripForDisplay(displayedContent) }
                    val parsed = remember(toolCallHidden) { com.vervan.chat.llm.ThinkingParser.parse(toolCallHidden) }
                    val clarification = remember(parsed.answer) { com.vervan.chat.llm.ClarificationParser.parse(parsed.answer) }
                    if (parsed.reasoning != null) {
                        com.vervan.chat.ui.common.AssistantSubCard(
                            kind = com.vervan.chat.ui.common.SubCardKind.Reasoning,
                            title = "Reasoning",
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            MarkdownLiteText(parsed.reasoning)
                        }
                    }
                    // Markdown/code-block rendering — assistant output routinely
                    // contains fenced code and tables; user messages stay plain (they typed
                    // it, no need to reparse their own text as markdown).
                    if (isUser) {
                        Text(
                            clarification.answer.ifBlank { " " },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        if (clarification.answer.isNotBlank()) MarkdownLiteText(clarification.answer)
                        clarification.request?.takeIf { message.state == MessageState.COMPLETE }?.let { request ->
                            ClarificationCard(
                                request = request,
                                enabled = clarificationEnabled,
                                onReply = onClarificationReply,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
                // Header-less user bubbles carry their timestamp here, tucked under the text at a
                // low opacity so it's available but never competes with the message.
                if (isUser && message.state == MessageState.COMPLETE) {
                    Text(
                        timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                        modifier = Modifier.align(Alignment.End).padding(top = Space.xs)
                    )
                }
                if (message.state == MessageState.STREAMING) {
                    // "Thinking" indicator while the model is alive but hasn't emitted its first
                    // token yet — replaces the silent gap that previously made the app feel broken
                    // on slow models. Once the first token is in, the dots hand off to the
                    // streaming text and the indicator hides itself (visible = content is blank).
                    if (message.content.isBlank()) {
                        ThinkingIndicator(
                            label = "Thinking",
                            modifier = Modifier.padding(top = Space.sm)
                        )
                    } else {
                        Row(Modifier.padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(
                                "Generating on device",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = Space.sm)
                            )
                        }
                    }
                } else if (message.state == MessageState.INTERRUPTED) {
                    TextButton(onClick = onRegenerate, enabled = !isGenerating) { Text("Continue with a new response") }
                } else if (message.state == MessageState.FAILED) {
                    TextButton(onClick = onRegenerate, enabled = !isGenerating) { Text("Try again") }
                } else if (message.state == MessageState.CANCELLED) {
                    Text("Partial response kept", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                message.toolResultJson?.let { ToolResultCard(it, message.toolCallJson) }
                message.memoryActivityJson?.let { MemoryActivityCard(it) }
                if (message.state == MessageState.AWAITING_CONFIRMATION) {
                    ToolConfirmationCard(message.toolCallJson, onConfirmTool)
                }
                message.sourcesJson?.let {
                    SourceCards(
                        it,
                        onOpenPassage = { chunkId -> onOpenPassage(chunkId) },
                        onRetryWithQuality = onRetryWithQuality,
                        betterModelName = betterModelName
                    )
                }
                // Inline reaction badges — only render when this message has any reactions, so
                // bubbles stay visually quiet by default. Long-press → MessageActionsSheet is
                // where users add reactions.
                if (reactions.isNotEmpty()) {
                    ReactionBadges(
                        reactions = reactions,
                        onReact = { emoji ->
                            onReaction(if (message.reaction == emoji) null else emoji)
                        }
                    )
                }
                if (siblingPosition.second > 1) {
                    Row(Modifier.padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onSwitchBranch(-1) }, enabled = siblingPosition.first > 1) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous branch", modifier = Modifier.size(16.dp))
                        }
                        Text("${siblingPosition.first}/${siblingPosition.second}", style = MaterialTheme.typography.labelSmall)
                        IconButton(onClick = { onSwitchBranch(1) }, enabled = siblingPosition.first < siblingPosition.second) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next branch", modifier = Modifier.size(16.dp))
                        }
                        TextButton(onClick = onCompare) { Text("Compare", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
        // Quick reply suggestions under the *last* completed assistant message — mirrors
        // ChatGPT/Gemini's "Continue / Summarize / Make shorter / Regenerate" follow-up row.
        // Renders *outside* the bubble so it doesn't inherit its surface treatment and the
        // chips read as actions on the conversation, not as part of the answer.
        if (isLastAssistant && !editing && !isGenerating) {
            QuickReplyChips(
                suggestions = defaultQuickReplies(),
                onClick = onQuickReply,
                modifier = Modifier.fillMaxWidth().padding(top = Space.xs)
            )
        }
        if (!isUser && message.modelName != null && !editing) {
            val provenance = buildList {
                add(message.modelName)
                message.profile?.lowercase()?.replaceFirstChar { it.titlecase() }?.let(::add)
                message.thinkingMode?.takeIf { it != "OFF" }?.lowercase()
                    ?.replaceFirstChar { it.titlecase() }?.let { add("$it thinking") }
                if (showGenerationStats) message.backend?.let(::add)
            }.joinToString(" · ")
            Text(
                provenance,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Space.xs, top = 2.dp),
            )
        }
        // Stats + actions live below the bubble, outside its card — this is the response's
        // metadata/toolbar, not part of the response itself, and keeping it out of the Card
        // means the bubble's background/border doesn't stretch to fit five icon buttons.
        if (showActions && !editing) {
            // horizontalScroll here previously fought Arrangement.End — a scrollable
            // Row measures children with unbounded width, so "End" has no finite edge to align
            // against and buttons silently render left-aligned/off past the bubble instead of
            // at the visible right edge (looked like the last one, the 3-dot menu, vanished).
            // At most 5 buttons × 48dp comfortably fits any real phone width without scrolling,
            // so drop the scroll guard rather than fight the interaction — revisit only if a
            // narrower layout genuinely overflows.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showGenerationStats && !isUser && message.state == MessageState.COMPLETE && message.generationMs != null) {
                    val seconds = message.generationMs / 1000f
                    val tokens = message.tokenCount ?: 0
                    val tps = if (seconds > 0f) tokens / seconds else 0f
                    Text(
                        "%.1fs · ~%d tokens · %.1f tok/s".format(seconds, tokens, tps),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                }
                if (isUser && !isGenerating) {
                    IconButton(onClick = { editing = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit and resend", modifier = Modifier.size(18.dp))
                    }
                }
                if (!isUser && message.state == MessageState.COMPLETE && message.content.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onReadAloud(if (isUser) message.content else assistantSpokenText(message.content), message.id)
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (savedOutput == null) {
                                    app.container.db.savedOutputDao().upsert(
                                        SavedOutput(
                                            content = message.content,
                                            sourceChatId = message.chatId,
                                            label = message.id
                                        )
                                    )
                                } else {
                                    app.container.db.savedOutputDao().upsert(
                                        savedOutput.copy(deletedAt = System.currentTimeMillis())
                                    )
                                }
                                onBookmarkChanged(savedOutput == null)
                            }
                        }
                    ) {
                        Icon(
                            if (savedOutput == null) Icons.Outlined.BookmarkBorder else Icons.Filled.Bookmark,
                            contentDescription = if (savedOutput == null) "Bookmark response" else "Remove bookmark",
                            modifier = Modifier.size(18.dp),
                            tint = if (savedOutput == null) LocalContentColor.current else MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!isGenerating) {
                        IconButton(onClick = onRegenerate) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Regenerate", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                // Reply stays gesture-only here; copy is the visible gesture alternative.
                if (message.content.isNotBlank()) {
                    IconButton(
                        // Clipboard hygiene — auto-clears after 30s if
                        // nothing else has overwritten it since.
                        onClick = { clipboard.setSensitiveText(message.content, scope) }
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                    }
                    Box {
                        IconButton(onClick = { showMessageMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More message actions", modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showMessageMenu, onDismissRequest = { showMessageMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Fork chat from here") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null) },
                                onClick = { onFork(); showMessageMenu = false }
                            )
                            if (!isUser && message.state == MessageState.COMPLETE) {
                                DropdownMenuItem(
                                    text = { Text("Remember this") },
                                    leadingIcon = { Icon(Icons.Filled.Psychology, contentDescription = null) },
                                    onClick = { showRememberDialog = true; showMessageMenu = false }
                                )
                            }
                            DropdownMenuItem(text = { Text("Save as prompt template") }, onClick = {
                                showSaveAsPromptDialog = true
                                showMessageMenu = false
                            })
                            DropdownMenuItem(text = { Text("Add to note") }, onClick = {
                                scope.launch {
                                    app.container.db.noteDao().upsert(
                                        com.vervan.chat.data.db.entities.Note(
                                            title = message.content.take(60),
                                            content = message.content
                                        )
                                    )
                                }
                                showMessageMenu = false
                            })
                        }
                    }
                }
            }
        }
        }
    }

    if (showRememberDialog) {
        var text by remember { mutableStateOf(message.content) }
        AlertDialog(
            onDismissRequest = { showRememberDialog = false },
            title = { Text("Remember this?") },
            text = {
                Column {
                    Text(
                    "Saved to Memory for future chats.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemember(text)
                    showRememberDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRememberDialog = false }) { Text("Cancel") } }
        )
    }

    // Long-press / context-menu sheet. Uses the shared [MessageActionsSheet] component so the
    // reaction strip and primary actions are consistent with whatever other surfaces adopt it.
    // The destructives set is intentionally empty here (no Delete from the sheet — the existing
    // chat-level delete lives in the chat overflow and avoids accidental deletes mid-typing).
    if (showActionsSheet) {
        val (primaryActions, secondaryActions) = com.vervan.chat.ui.common.standardMessageActions(
            onCopy = { clipboard.setSensitiveText(message.content, scope) },
            onSpeak = { onReadAloud(if (isUser) message.content else assistantSpokenText(message.content), message.id) },
            onBookmark = {
                scope.launch {
                    if (savedOutput == null) {
                        app.container.db.savedOutputDao().upsert(SavedOutput(content = message.content, sourceChatId = message.chatId, label = message.id))
                    } else {
                        app.container.db.savedOutputDao().upsert(savedOutput.copy(deletedAt = System.currentTimeMillis()))
                    }
                    onBookmarkChanged(savedOutput == null)
                }
            },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message.content)
                }
                context.startActivity(Intent.createChooser(send, "Share message"))
            },
            onEdit = if (isUser) ({ editing = true }) else ({ /* no-op for assistant */ }),
            onRegenerate = if (!isUser && message.state == MessageState.COMPLETE) onRegenerate else ({ /* no-op */ }),
            onFork = onFork,
            onSaveAsPrompt = { showSaveAsPromptDialog = true },
            onAddToNote = {
                scope.launch {
                    app.container.db.noteDao().upsert(
                        com.vervan.chat.data.db.entities.Note(title = message.content.take(60), content = message.content)
                    )
                }
            }
        )
        // Filter out actions that don't apply to this message's role/state so the sheet doesn't
        // show inert buttons. Edits are user-only; regenerate is assistant-COMPLETE-only.
        val applicablePrimary = primaryActions.filter { action ->
            when (action.label) {
                "Edit & resend" -> isUser
                "Try again" -> !isUser && message.state in setOf(MessageState.COMPLETE, MessageState.INTERRUPTED, MessageState.FAILED)
                else -> true
            }
        }
        MessageActionsSheet(
            onDismiss = { showActionsSheet = false },
            selectedReaction = reactions.firstOrNull { it.mine }?.emoji,
            onReact = { emoji ->
                onReaction(if (message.reaction == emoji) null else emoji)
            },
            actions = applicablePrimary + secondaryActions
        )
    }

    if (showSaveAsPromptDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveAsPromptDialog = false },
            title = { Text("Save as prompt template") },
            text = {
                Column {
                    Text("Reusable via /name in any chat.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text("Command name (no spaces)") }, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val slug = name.trim().replace(Regex("\\s+"), "-")
                    if (slug.isNotBlank()) {
                        scope.launch {
                            app.container.db.promptTemplateDao().upsert(
                                com.vervan.chat.data.db.entities.PromptTemplate(name = slug, body = message.content)
                            )
                        }
                        showSaveAsPromptDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveAsPromptDialog = false }) { Text("Cancel") } }
        )
    }

    if (showImagePreview) {
        message.imagePath?.let { path ->
            FullScreenImagePreview(path = path, title = "Shared image", onDismiss = { showImagePreview = false })
        }
    }
}
