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

private data class FollowSnapshot(
    val enabled: Boolean,
    val messageCount: Int,
    val totalItemsCount: Int,
    val viewportStart: Int,
    val viewportEnd: Int,
    val lastVisibleIndex: Int,
    val lastVisibleOffset: Int,
    val lastVisibleSize: Int
)

internal fun isNearConversationBottom(
    totalItemsCount: Int,
    lastVisibleIndex: Int,
    lastVisibleBottom: Int,
    viewportEnd: Int,
    tolerancePx: Int
): Boolean = totalItemsCount == 0 || (
    lastVisibleIndex == totalItemsCount - 1 &&
        lastVisibleBottom - viewportEnd <= tolerancePx
    )

/** Day-boundary check for [DatePill] separators. Public so other date-aware surfaces (search
 *  results, exported transcripts) reuse the same notion of "same day" as the chat feed. */
internal fun sameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    initialAction: String? = null,
    pendingAttachUri: android.net.Uri? = null,
    pendingAttachAsImage: Boolean = false,
    pendingAttachShowPreview: Boolean = false,
    onAttachConsumed: () -> Unit = {},
    initialMessageId: String? = null,
    onInitialMessageConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onOpenChatInfo: () -> Unit = {},
    onOpenDocument: (String) -> Unit = {},
    onOpenBranchTree: () -> Unit = {},
    onOpenPassage: (String) -> Unit = {},
    onOpenFolders: () -> Unit = {},
    onOpenModels: () -> Unit = {},
    onOpenWorkspace: (String) -> Unit = {},
    onForkChat: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val vm: ChatViewModel = viewModel(factory = viewModelFactory {
        initializer { ChatViewModel(app, chatId) }
    })
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
    val documents by app.container.db.documentDao().observeAll().collectAsState(initial = emptyList())
    val savedOutputs by app.container.db.savedOutputDao().observeAll().collectAsState(initial = emptyList())
    val chatSavedOutputs = remember(savedOutputs, chatId) { savedOutputs.filter { it.sourceChatId == chatId } }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(confirmationMessage) {
        confirmationMessage?.let { snackbarHostState.showSnackbar(it); vm.clearConfirmation() }
    }
    val persona by vm.persona.collectAsState()
    val personas by vm.personas.collectAsState()
    val activeModelName by vm.activeModelName.collectAsState()
    val generationModels by vm.generationModels.collectAsState()
    val modelLoadState by vm.modelLoadState.collectAsState()
    val visionAvailable by vm.visionAvailable.collectAsState()
    val audioAvailable by vm.audioAvailable.collectAsState()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hapticsEnabled by app.container.settingsRepository.hapticsEnabled.collectAsState(initial = true)

    var draft by remember { mutableStateOf("") }
    var draftLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(chatId) {
        draft = app.container.db.chatDao().getChat(chatId)?.draft.orEmpty()
        draftLoaded = true
    }

    val listState = rememberLazyListState()
    // Following mode vs reading mode (see class doc). Starts false so the anchor-restore
    // effect below gets to decide the opening position before anything else reacts — see
    // scrollRestored.
    var stickToBottom by rememberSaveable(chatId) { mutableStateOf(false) }
    // "Near bottom" is a tolerance range, not exact pixel equality — the last item just has to
    // be within this many px of the viewport's bottom edge, so trivial layout jitter (a table
    // finishing its expansion, a late-loading image, a small overscroll bounce/settle) can't
    // spuriously read as "not at the bottom" when the user never actually meant to leave it.
    // 48dp was too tight — normal settle wobble at the very bottom kept tripping it, showing
    // the "Jump to latest" FAB even while already at the bottom.
    val bottomTolerancePx = with(LocalDensity.current) { 120.dp.toPx() }
    fun isNearBottom(): Boolean {
        val info = listState.layoutInfo
        if (info.totalItemsCount == 0) return true
        val last = info.visibleItemsInfo.lastOrNull() ?: return false
        return isNearConversationBottom(
            totalItemsCount = info.totalItemsCount,
            lastVisibleIndex = last.index,
            lastVisibleBottom = last.offset + last.size,
            viewportEnd = info.viewportEndOffset - info.afterContentPadding,
            tolerancePx = bottomTolerancePx.roundToInt()
        )
    }
    // Real user touch-drag on the list, as distinct from a programmatic scrollToItem/
    // animateScrollToItem call — Compose's own drag recognizer already filters out
    // sub-threshold "accidental" movement before this flips true, so no extra tolerance is
    // needed here (spec: "minor layout movement... should not disable following").
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) {
            // Immediately interpret a real upward/downward drag as user intent — don't wait
            // for it to finish before leaving following mode, so mid-drag content growth
            // can't yank the viewport out from under the gesture.
            stickToBottom = false
        } else {
            // Drag released — let any resulting fling settle, then decide the mode from
            // where the user actually landed (re-enables following only if they scrolled
            // themselves back to the bottom; content changes elsewhere never do this).
            androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }.first { !it }
            stickToBottom = isNearBottom()
        }
    }
    // Chat Screen spec §17 — restore-on-open: jump to the saved message-ID anchor (where the
    // user was last reading) instead of always landing on the latest message. Runs once per
    // chat; falls back to "latest message" when there's no anchor or it's no longer in the
    // rendered branch (spec §17.1's "previous position unavailable" case).
    var scrollRestored by remember(chatId) { mutableStateOf(false) }
    LaunchedEffect(initialMessageId, messages) {
        val messageId = initialMessageId ?: return@LaunchedEffect
        val index = messages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            listState.scrollToItem(index)
            stickToBottom = false
            scrollRestored = true
            onInitialMessageConsumed()
        }
    }
    LaunchedEffect(chatId, messages, chat) {
        if (scrollRestored || messages.isEmpty() || chat == null) return@LaunchedEffect
        val anchorIndex = chat?.scrollAnchorMessageId?.let { id -> messages.indexOfFirst { it.id == id } } ?: -1
        if (anchorIndex >= 0) {
            listState.scrollToItem(anchorIndex, chat?.scrollAnchorOffsetPx ?: 0)
            stickToBottom = isNearBottom()
        } else {
            // The extra list item after the messages is the actual conversation end. Scrolling
            // to the final message would align its top, which fails for responses taller than
            // the viewport.
            listState.scrollToItem(messages.size)
            stickToBottom = true
        }
        scrollRestored = true
    }
    val latestMessageCount by rememberUpdatedState(messages.size)
    val latestShouldFollow by rememberUpdatedState(
        scrollRestored && stickToBottom && !isDragged && messages.isNotEmpty()
    )
    // Observe both streamed content and layout changes. snapshotFlow coalesces rapid updates,
    // while the frame boundary waits for Markdown/composer/IME remeasurement before moving.
    // Reading mode takes no action, leaving LazyColumn's stable-key anchoring in control.
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            FollowSnapshot(
                enabled = latestShouldFollow,
                messageCount = latestMessageCount,
                totalItemsCount = info.totalItemsCount,
                viewportStart = info.viewportStartOffset,
                viewportEnd = info.viewportEndOffset,
                lastVisibleIndex = lastVisible?.index ?: -1,
                lastVisibleOffset = lastVisible?.offset ?: 0,
                lastVisibleSize = lastVisible?.size ?: 0
            )
        }.collect {
            if (!it.enabled) return@collect
            androidx.compose.runtime.withFrameNanos { }
            val endIndex = it.messageCount
            if (latestShouldFollow && listState.layoutInfo.totalItemsCount > endIndex) {
                // withFrameNanos above is a suspension point, but LaunchedEffect cancellation on
                // navigate-away isn't synchronous with this LazyColumn's node detachment — there's
                // a race window where scrollToItem fires just as the tree is being torn down,
                // throwing "LayoutNode should be attached to an owner" (streaming's frequent
                // scroll calls make this the effect most likely to land in that window).
                try {
                    listState.scrollToItem(endIndex)
                } catch (_: IllegalStateException) {
                }
            }
        }
    }
    // Save the reading position when leaving the chat (spec §17.10 "returning from another
    // screen" and normal navigation-away) so the effect above has something to restore.
    val latestMessages by rememberUpdatedState(messages)
    DisposableEffect(chatId, listState) {
        onDispose {
            val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            val msg = firstVisible?.let { latestMessages.getOrNull(it.index) }
            if (msg != null) vm.saveScrollAnchor(msg.id, (-firstVisible.offset).coerceAtLeast(0))
        }
    }

    var showModeSettings by remember { mutableStateOf(false) }
    var showChatTools by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showKbPicker by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showPersonaPicker by remember { mutableStateOf(false) }
    var pendingQuote by remember { mutableStateOf<String?>(null) }
    var contextBreakdown by remember { mutableStateOf<ContextBreakdown?>(null) }
    // Attachment state lives in the ViewModel (see ComposerAttachments) so forward navigation
    // — which disposes this composition but not the ViewModel — can't drop an unsent attachment.
    val pendingAttachments by vm.attachments.collectAsState()
    val pendingImagePath = pendingAttachments.imagePath
    val pendingAudioPath = pendingAttachments.audioPath
    var showPendingImagePreview by remember { mutableStateOf(false) }
    var showPendingAudioPreview by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var isImportingAudio by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var activeRecorder by remember { mutableStateOf<WavRecorder?>(null) }
    var compareMessageId by remember { mutableStateOf<String?>(null) }
    // A fresh 👎 reaction prompts for why, so a weak model/preset leaves a trail (see
    // ChatViewModel.setFeedbackReason) — holds the message just reacted to, not a running dialog
    // stack, so at most one prompt is ever pending.
    var feedbackReasonPromptFor by remember { mutableStateOf<Message?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showWorkspaceOptions by remember { mutableStateOf(false) }
    var showChatStats by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showSavedResponses by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var isRunningOcr by remember { mutableStateOf(false) }
    var sendDocumentWhenReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val copied = vm.copyImage(uri)
            vm.setPendingImage(copied)
            showPendingImagePreview = copied != null
            if (copied == null) attachmentError = "Couldn’t prepare that image. Choose another photo and try again."
        }
    }
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingCameraFile?.let { com.vervan.chat.model.ImageUtils.fixOrientation(it) }
            pendingCameraFile?.absolutePath?.let { vm.setPendingImage(it) }
            showPendingImagePreview = pendingCameraFile != null
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
                attachmentError = "Camera access is off. Choose a photo or allow it in Settings."
        }
    }
    // "Document" attach — any standard document type, run through extract/chunk/embed and
    // attached as a per-chat knowledge source (see ChatViewModel.attachDocument).
    var selectedDocument by remember { mutableStateOf<PendingDocumentSelection?>(null) }
    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) selectedDocument = inspectDocument(context, uri)
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            isImportingAudio = true
            vm.importAudio(uri)
                .onSuccess { path ->
                    vm.setPendingAudio(path)
                    showPendingAudioPreview = true
                }
                .onFailure {
                    attachmentError = it.toUserMessage()
                }
            isImportingAudio = false
        }
    }

    // OCR attach — same picker/camera UX as the vision "Photo"/"Camera" tiles, but the LLM
    // never sees the image: on-device ML Kit recognizes the text, the user can review/edit it
    // in a preview sheet, and only that text is folded into the outgoing message. Works with
    // any loaded model, vision-capable or not.
    val pendingOcrImagePath = pendingAttachments.ocrImagePath
    val pendingOcrText = pendingAttachments.ocrText
    var showOcrPreview by remember { mutableStateOf(false) }
    fun applyOcrResult(result: Result<ChatViewModel.OcrResult>) {
        isRunningOcr = false
        result.onSuccess { r ->
            vm.setPendingOcr(r.imagePath, r.text)
            showOcrPreview = true
            if (r.text.isBlank()) {
                android.widget.Toast.makeText(context, "No text found in that image", android.widget.Toast.LENGTH_LONG).show()
            }
        }.onFailure {
            attachmentError = it.toUserMessage()
        }
    }
    val pickOcrImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isRunningOcr = true
            scope.launch { applyOcrResult(vm.extractOcr(uri)) }
        }
    }
    var pendingOcrCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    val takeOcrPicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingOcrCameraFile
        pendingOcrCameraFile = null
        if (success && file != null) {
            isRunningOcr = true
            scope.launch { applyOcrResult(vm.extractOcrFromFile(file)) }
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
                attachmentError = "Camera access is off. Choose an image or allow it in Settings."
        }
    }

    fun startVoiceMessageRecording() {
        val file = vm.newAudioFile()
        val recorder = WavRecorder(file)
        runCatching { recorder.start() }
            .onSuccess {
                activeRecorder = recorder
                isRecording = true
            }
            .onFailure {
                recorder.cancel()
                attachmentError = it.toUserMessage()
            }
    }
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                draft = if (draft.isBlank()) text else "$draft $text"
                vm.saveDraft(draft)
            }
        }
    }
    val requestMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (audioAvailable == true) {
                startVoiceMessageRecording()
            } else {
                dictate.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                })
            }
        } else {
                attachmentError = "Microphone access is off. Allow it in Settings to record or dictate."
        }
    }
    val requestRecordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceMessageRecording()
                else attachmentError = "Microphone access is off. Allow it in Settings to record audio."
    }
    var initialActionHandled by rememberSaveable(chatId, initialAction) { mutableStateOf(false) }
    LaunchedEffect(chatId, initialAction, initialActionHandled, modelLoadState) {
        if (!initialActionHandled) {
            if (initialAction == "voice" && (
                    modelLoadState is ChatViewModel.ModelLoadState.NotLoaded ||
                        modelLoadState is ChatViewModel.ModelLoadState.Loading
                )
            ) return@LaunchedEffect
            initialActionHandled = true
            when (initialAction) {
                "image" -> pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                "voice" -> requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    // External shares and "Chat with a file" enter through the same copy/preview/import path as
    // the composer's own pickers, then the nav graph clears the one-shot URI.
    LaunchedEffect(chatId, pendingAttachUri, pendingAttachAsImage, pendingAttachShowPreview) {
        val uri = pendingAttachUri ?: return@LaunchedEffect
        try {
            if (pendingAttachAsImage) {
                val copied = vm.copyImage(uri)
                vm.setPendingImage(copied)
                showPendingImagePreview = copied != null
                if (copied == null) attachmentError = "Couldn’t prepare that shared image. Try attaching it from the gallery."
            } else if (pendingAttachShowPreview) {
                selectedDocument = inspectDocument(context, uri)
            } else {
                vm.attachDocument(uri)
            }
        } finally {
            onAttachConsumed()
        }
    }
    val latestRecorder by rememberUpdatedState(activeRecorder)
    // Only the in-progress recorder is screen-scoped. Generation cancellation, empty/incognito
    // chat purge, and attachment-file cleanup all live in ChatViewModel.onCleared(), which fires
    // when the chat's back-stack entry is actually popped — composition dispose also happens on
    // *forward* navigation (Chat Info, branch tree, a document), where cancelling the stream or
    // deleting unsent attachments was wrong.
    DisposableEffect(chatId) {
        onDispose { latestRecorder?.cancel() }
    }
    val ttsRateSetting by app.container.settingsRepository.ttsRate.collectAsState(initial = 1.0f)
    val autoReadAloud by app.container.settingsRepository.autoReadAloud.collectAsState(initial = false)
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val instance = TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
        tts = instance
        onDispose { instance.shutdown() }
    }
    // Spec §14.4: pause/stop TTS on audio-focus loss instead of talking over a call or other
    // app's playback. Recording's own interruption handling (call/headset) is out of scope
    // here — AudioRecord has no focus API; that would need TelephonyManager call-state
    // observation, which needs its own permission story.
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val focusListener = remember {
        AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                tts?.stop()
            }
        }
    }
    val focusRequest = remember {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
    }
    fun speakWithFocus(text: String, utteranceId: String) {
        if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }
    DisposableEffect(Unit) { onDispose { audioManager.abandonAudioFocusRequest(focusRequest) } }
    LaunchedEffect(ttsRateSetting, tts) { tts?.setSpeechRate(ttsRateSetting) }
    var autoReadBaselineReady by remember(chatId) { mutableStateOf(false) }
    var lastAutoReadId by remember(chatId) { mutableStateOf<String?>(null) }
    LaunchedEffect(chatId) {
        val chatRow = app.container.db.chatDao().getChat(chatId)
        val stored = app.container.db.messageDao().getMessages(chatId)
        lastAutoReadId = com.vervan.chat.data.branch.BranchUtil.pathTo(stored, chatRow?.activeLeafId)
            .lastOrNull { it.role == MessageRole.ASSISTANT && it.state == MessageState.COMPLETE }
            ?.id
        autoReadBaselineReady = true
    }
    LaunchedEffect(autoReadAloud, messages, ttsReady, autoReadBaselineReady) {
        val last = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        if (!autoReadBaselineReady || last == null || last.state != MessageState.COMPLETE) return@LaunchedEffect
        if (!autoReadAloud) {
            lastAutoReadId = last.id
        } else if (ttsReady && last.id != lastAutoReadId) {
            speakWithFocus(assistantSpokenText(last.content), last.id)
            lastAutoReadId = last.id
        }
    }

    fun sendPendingMessage(): Boolean {
        val documentReady = pendingDocument is ChatViewModel.DocumentAttachState.Ready
        val canSend = (draft.isNotBlank() || pendingImagePath != null || pendingOcrImagePath != null || pendingAudioPath != null || documentReady) &&
            modelLoadState is ChatViewModel.ModelLoadState.Ready && !isWorkspaceArchived && draft.length <= 12_000
        if (!canSend) return false

        val quotePrefix = pendingQuote?.let { quoted ->
            quoted.lineSequence().joinToString("\n") { "> $it" } + "\n\n"
        }.orEmpty()
        val attached = vm.consumeAttachments()
        val ocrText = attached.ocrText?.takeIf { it.isNotBlank() }
        val bodyBase = draft.ifBlank {
            when {
                documentReady -> "Describe this document."
                attached.imagePath != null -> "Describe this image."
                else -> draft
            }
        }
        val body = if (ocrText != null) {
            "Text extracted from a photo via OCR:\n\"\"\"\n$ocrText\n\"\"\"\n\n$bodyBase"
        } else bodyBase
        val documentId = (pendingDocument as? ChatViewModel.DocumentAttachState.Ready)?.documentId

        draft = ""
        pendingQuote = null
        vm.clearPendingDocument()
        stickToBottom = true
        vm.send(quotePrefix + body, attached.imagePath, attached.audioPath, documentId)
        return true
    }

    // A document needs to be copied and indexed before it has a stable local ID. Pressing Send
    // in its preview starts that work and this effect completes the same send as soon as Ready is
    // reached. Failure stays visible in the composer instead of silently losing the caption.
    LaunchedEffect(pendingDocument, sendDocumentWhenReady, modelLoadState, isWorkspaceArchived) {
        when (pendingDocument) {
            is ChatViewModel.DocumentAttachState.Ready -> {
                if (sendDocumentWhenReady && sendPendingMessage()) sendDocumentWhenReady = false
            }
            is ChatViewModel.DocumentAttachState.Failed -> sendDocumentWhenReady = false
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        Modifier.heightIn(min = 48.dp).clickable(onClick = onOpenChatInfo).semantics {
                            contentDescription = "Chat details"
                        },
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            chat?.title ?: "New conversation",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            when {
                                isRetrieving -> "Searching knowledge base…"
                                isRecallingMemory -> "Recalling memories…"
                                chat?.isTemporary == true -> "Incognito · deletes when you leave"
                                modelLoadState is ChatViewModel.ModelLoadState.Ready -> {
                                    val ready = modelLoadState as ChatViewModel.ModelLoadState.Ready
                                    "Ready · ${ready.backend} · on device"
                                }
                                else -> "Private · on device"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isGenerating || isRetrieving || isRecallingMemory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.closeEmptyDraft(onBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val activeModel = chat?.modelId?.let { id -> generationModels.firstOrNull { it.id == id } }
                        ?: generationModels.firstOrNull { it.isActive }
                    // The model name/switcher lives in the context strip below the bar (and in
                    // "Mode & model"), not here — the top bar was too cramped with the pill taking
                    // roughly a third of its width on a phone.
                    // Keep the frequently changed chat controls directly accessible.
                    val grounded = chat?.sourceGrounded == true
                    IconButton(onClick = { showSourcePicker = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Sources",
                            tint = if (grounded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (titleGenerating) {
                        Box(
                            Modifier.size(48.dp).semantics {
                                contentDescription = "Generating a chat title"
                                liveRegion = LiveRegionMode.Polite
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    var showOverflow by remember { mutableStateOf(false) }
                    var showMoreSheet by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        // WhatsApp-style: the dropdown holds only the handful of everyday actions,
                        // so it never runs off the screen. Everything else lives in an organized,
                        // sectioned "More options" bottom sheet one tap away.
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(text = { Text("Chat details") }, leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp)) }, onClick = { showOverflow = false; showChatStats = true })
                            DropdownMenuItem(text = { Text("Find in conversation") }, leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }, onClick = { showOverflow = false; showSearch = true })
                            DropdownMenuItem(text = { Text("Mode & model") }, leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }, onClick = { showOverflow = false; showModeSettings = true })
                            DropdownMenuItem(text = { Text(if (chat?.pinned == true) "Unpin" else "Pin") }, leadingIcon = { Icon(if (chat?.pinned == true) Icons.Filled.PushPin else Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(18.dp)) }, onClick = { vm.togglePin(); showOverflow = false })
                            DropdownMenuItem(text = { Text(if (chat?.archived == true) "Unarchive" else "Archive") }, leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(18.dp)) }, onClick = { vm.toggleArchive(); showOverflow = false })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("More options") }, leadingIcon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) }, onClick = { showOverflow = false; showMoreSheet = true })
                        }
                    }
                    if (showMoreSheet) {
                        ChatMoreOptionsSheet(
                            hasAssistantReply = allMessages.any { it.role == MessageRole.ASSISTANT },
                            canGenerateTitle = generationModels.isNotEmpty() && !titleGenerating,
                            hasPreviousTitle = chat?.previousTitle != null,
                            savedResponsesCount = chatSavedOutputs.size,
                            isIncognito = chat?.isTemporary == true,
                            onDismiss = { showMoreSheet = false },
                            onRename = { showMoreSheet = false; showRenameDialog = true },
                            onGenerateTitle = { showMoreSheet = false; vm.generateTitle() },
                            onRestoreTitle = { showMoreSheet = false; vm.restorePreviousTitle() },
                            onSavedResponses = { showMoreSheet = false; showSavedResponses = true },
                            onBranchTree = { showMoreSheet = false; onOpenBranchTree() },
                            onContextInspector = { showMoreSheet = false; scope.launch { contextBreakdown = vm.inspectContext(draft) } },
                            toolsAvailable = activeModel?.supportsTools != false,
                            onChatTools = { showMoreSheet = false; showChatTools = true },
                            onToggleIncognito = { showMoreSheet = false; vm.toggleTemporary() },
                            onAddToKnowledgeBase = { showMoreSheet = false; showKbPicker = true },
                            onManageFolders = { showMoreSheet = false; onOpenFolders() },
                            onDuplicate = { showMoreSheet = false; vm.duplicate(onDone = onBack) },
                            onExportShare = {
                                showMoreSheet = false
                                scope.launch {
                                    val text = vm.exportText()
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, chat?.title ?: "Chat")
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Export chat"))
                                }
                            },
                            onExportMarkdown = {
                                showMoreSheet = false
                                scope.launch {
                                    // A content URI + stream share (not EXTRA_TEXT) so long
                                    // transcripts aren't truncated by receiving apps' text limits.
                                    val file = vm.exportMarkdownFile()
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", file
                                    )
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/markdown"
                                        putExtra(Intent.EXTRA_SUBJECT, chat?.title ?: "Chat")
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Export chat as Markdown"))
                                }
                            },
                            onExportPdf = {
                                showMoreSheet = false
                                scope.launch {
                                    val file = vm.exportPdfFile()
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", file
                                    )
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_SUBJECT, chat?.title ?: "Chat")
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Export chat as PDF"))
                                }
                            },
                            onResetSettings = { showMoreSheet = false; showResetConfirm = true },
                            onDelete = { showMoreSheet = false; vm.moveToTrash(onDone = onBack) },
                        )
                    }
                    if (showModeSettings) {
                        val defaultTemperature by app.container.settingsRepository.temperature.collectAsState(initial = 0.8f)
                        val defaultTopP by app.container.settingsRepository.topP.collectAsState(initial = 0.95f)
                        val defaultTopK by app.container.settingsRepository.topK.collectAsState(initial = 40)
                        ModeSettingsDialog(
                            thinkingMode = chat?.thinkingMode ?: "OFF",
                            thinkingAvailable = activeModel?.supportsThinking != false,
                            currentProfile = chat?.profile ?: "BALANCED",
                            onThinkingChange = { vm.setThinkingMode(it) },
                            onProfileChange = { vm.setProfile(it) },
                            onOpenModelPicker = { showModeSettings = false; showModelPicker = true },
                            onOpenPersonaPicker = { showModeSettings = false; showPersonaPicker = true },
                            temperature = chat?.temperature,
                            topP = chat?.topP,
                            topK = chat?.topK,
                            defaultTemperature = defaultTemperature,
                            defaultTopP = defaultTopP,
                            defaultTopK = defaultTopK,
                            onTemperatureChange = { vm.setTemperatureOverride(it) },
                            onTopPChange = { vm.setTopPOverride(it) },
                            onTopKChange = { vm.setTopKOverride(it) },
                            onDismiss = { showModeSettings = false }
                        )
                    }
                }
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            // Chat Screen spec §13 — compact, horizontally-scrollable status chips; never
            // wraps to a second row. Context % is a cheap chars/4 estimate (same tradeoff as
            // the context inspector), not an exact token count.
            run {
                val defaultContextLimit by app.container.settingsRepository.contextTokenLimit.collectAsState(initial = 4096)
                // Prefer the resolved model's actual context window over the app-wide default —
                // the old version measured the whole branch against the global setting, so a chat
                // that would fit fine after history trimming could still read a scary "400%".
                val chatModel = chat?.modelId?.let { id -> generationModels.firstOrNull { it.id == id } }
                    ?: generationModels.firstOrNull { it.isActive }
                val contextLimit = chatModel?.contextTokens ?: defaultContextLimit
                val estimatedTokens = messages.sumOf { com.vervan.chat.llm.estimateTokens(it.content) }
                val contextPercent = if (contextLimit > 0) (estimatedTokens * 100 / contextLimit).coerceIn(0, 100) else 0
                ChatContextStrip(
                    workspaceName = workspace?.name,
                    folderName = folder?.name,
                    personaName = persona?.name,
                    modelName = activeModelName?.substringBefore(" · "),
                    thinkingMode = chat?.thinkingMode?.takeIf { it != "OFF" },
                    sourceCount = chat?.kbIdList()?.size?.takeIf { chat?.sourceGrounded == true && it > 0 },
                    contextPercent = contextPercent,
                    onWorkspaceClick = { showWorkspaceOptions = true },
                    onFolderClick = onOpenFolders,
                    onPersonaClick = { showPersonaPicker = true },
                    // Model chip switches the model directly (the everyday action) instead of
                    // routing through the Mode & model dialog first — that dialog is still one tap
                    // away in the overflow menu for thinking-mode/profile/sampler changes.
                    onModelClick = { showModelPicker = true },
                    onSourcesClick = { showSourcePicker = true },
                    onContextClick = { scope.launch { contextBreakdown = vm.inspectContext(draft) } }
                )
            }
            ModelReadinessPanel(
                state = modelLoadState,
                onLoad = vm::retryModelLoad,
                onOpenModels = onOpenModels
            )
            // Model Loading Strategy §7 / §14.1 — a distinct, non-alarming indicator during
            // active generation when the device is thermally throttling. ThermalMonitor already
            // tracked this correctly; it just had no UI consumer anywhere until now, so a
            // throttled response looked like an unexplained slowdown instead of an informational
            // state clearly separate from ModelReadinessPanel's loading/error states above.
            if (isGenerating) {
                val thermalLevel by app.container.thermalMonitor.level.collectAsState()
                if (thermalLevel != com.vervan.chat.system.ThermalLevel.NORMAL) {
                    ThermalNotice(severe = thermalLevel == com.vervan.chat.system.ThermalLevel.SEVERE)
                }
                val liveStats by vm.liveGenStats.collectAsState()
                liveStats?.let { LiveGenStatsChip(it) }
            }
            if (isWorkspaceArchived) {
                ArchivedWorkspaceBanner(onRestore = { vm.restoreChatWorkspace() })
            }
            if (showSearch) {
                ConversationSearchBar(
                    messages = messages,
                    onClose = { showSearch = false },
                    onJumpTo = { index ->
                        stickToBottom = false
                        scope.launch { listState.animateScrollToItem(index) }
                    }
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().widthIn(max = 840.dp).align(Alignment.Center),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Space.lg, top = Space.md, end = Space.lg, bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                if (messages.isEmpty()) {
                    item(key = "empty-chat") {
                        ChatEmptyState(
                            personaName = persona?.name,
                            modelName = activeModelName?.substringBefore(" · "),
                            modifier = Modifier.fillParentMaxHeight(0.92f),
                            onSuggestion = { suggestion ->
                                draft = suggestion
                                if (draftLoaded) vm.saveDraft(suggestion)
                            }
                        )
                    }
                }
                // Hoisted out of the per-item lambdas: each of these was an O(n) list scan per
                // visible item per recomposition — and streaming recomposes this list every
                // ~80ms, so long chats janked exactly when the app was busiest.
                val lastCompleteAssistantId = messages.lastOrNull { it.role == MessageRole.ASSISTANT && it.state == MessageState.COMPLETE }?.id
                val lastMessageId = messages.lastOrNull()?.id
                itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                    // Date separator — rendered *before* the first message of each new day so
                    // long conversations get the same "Today / Yesterday / Mar 14" anchors every
                    // modern chat app provides. Only emitted for messages that render a bubble,
                    // so a hidden SYSTEM row can't leave an orphaned date pill.
                    if (message.role != MessageRole.SYSTEM) {
                    val prev = messages.getOrNull(index - 1)
                    if (prev == null || !sameDay(prev.createdAt, message.createdAt)) {
                        DatePill(timestamp = message.createdAt)
                    }
                    // Small-model recovery (P1): only worth a DB lookup right after the user has
                    // actually flagged this answer — avoids querying installed models for every
                    // message rendered in a long chat.
                    var betterModelName by remember(message.id) { mutableStateOf<String?>(null) }
                    LaunchedEffect(message.id, message.reaction) {
                        betterModelName = if (message.reaction == "👎") vm.suggestBetterModel(message)?.displayName else null
                    }
                    MessageBubble(
                            // Placement animation smooths branch switches and regenerations,
                            // which otherwise snap the whole list into its new shape.
                            modifier = Modifier.animateItem(),
                            message = message,
                            attachedDocument = message.documentId?.let { id -> documents.firstOrNull { it.id == id } },
                            savedOutput = chatSavedOutputs.firstOrNull {
                                it.label == message.id || (it.label.isBlank() && it.content == message.content)
                            },
                            onBookmarkChanged = { saved ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(if (saved) "Response bookmarked" else "Bookmark removed")
                                }
                            },
                            onRemember = { text -> vm.rememberMessage(message.id, text) },
                            onReaction = { emoji ->
                                vm.setReaction(message.id, emoji)
                                if (emoji == "👎") {
                                    feedbackReasonPromptFor = message
                                } else if (message.reaction == "👎") {
                                    // Reaction removed or changed away from 👎 — the earlier
                                    // reason no longer describes the current state.
                                    vm.setFeedbackReason(message.id, null)
                                }
                            },
                            onReadAloud = ::speakWithFocus,
                            isGenerating = isGenerating,
                            siblingPosition = com.vervan.chat.data.branch.BranchUtil.siblingPosition(allMessages, message.id),
                            onConfirmTool = { approve -> vm.confirmToolCall(message.id, approve) },
                            onEditAndResend = { newText -> vm.editAndResend(message.id, newText) },
                            onRegenerate = { vm.regenerate(message.id) },
                            onSwitchBranch = { direction ->
                                val siblings = com.vervan.chat.data.branch.BranchUtil.siblingsOf(allMessages, message.id)
                                val index = siblings.indexOfFirst { it.id == message.id }
                                val targetIndex = index + direction
                                if (targetIndex in siblings.indices) vm.switchBranch(siblings[targetIndex].id)
                            },
                            onCompare = { compareMessageId = message.id },
                            onFork = { scope.launch { onForkChat(vm.forkChat(message.id)) } },
                            onOpenPassage = { chunkId -> onOpenPassage(chunkId) },
                            onOpenDocument = onOpenDocument,
                            isLastAssistant = message.id == lastCompleteAssistantId,
                            clarificationEnabled = message.id == lastMessageId && !isGenerating,
                            onClarificationReply = { vm.send(it) },
                            onQuickReply = { reply ->
                                if (reply.prompt == "__regenerate__") {
                                    vm.regenerate(message.id)
                                } else {
                                    vm.send(reply.prompt)
                                }
                            },
                            // Swipe-to-reply used to prepend a "> quoted" blockquote directly into
                            // the draft text, which grew the input box with every reply. A compact
                            // preview bar above the composer (WhatsApp-style) keeps the box the same
                            // size — the quote is only merged into the actual sent text on Send.
                            onQuote = { quoted -> pendingQuote = quoted },
                            onRetryWithQuality = { vm.retryWithQuality(message.id) },
                            betterModelName = betterModelName
                        )
                    }
                }
                // Stable end target for long streaming responses. A message item itself cannot
                // be used as the target because scrollToItem aligns that message's top.
                item(key = "conversation-end") {
                    Spacer(Modifier.size(width = 1.dp, height = 1.dp))
                }
            }
            // Chat Screen spec §11 — "jump to latest" once the user has scrolled away from
            // the bottom (auto-follow only re-engages once they're back near it, see the
            // stickToBottom LaunchedEffect above). Primary color while a response is
            // streaming so "new content below" is signaled by more than the icon label.
            androidx.compose.animation.AnimatedVisibility(
                visible = scrollRestored && !stickToBottom && messages.isNotEmpty(),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
            ) {
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(messages.size)
                            stickToBottom = true
                        }
                    },
                    containerColor = if (isGenerating) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isGenerating) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = if (isGenerating) "New response" else "Jump to latest"
                    )
                }
            }
            }

            error?.let {
                ErrorCard(
                    title = "Generation could not continue",
                    body = it,
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            attachmentError?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
                    title = "Attachment could not be added",
                    message = it,
                    recovery = "Your message is safe. Check the file or permission, then try again.",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    actionLabel = "Dismiss",
                    onAction = { attachmentError = null }
                )
            }

            val templates by app.container.db.promptTemplateDao().observeAll().collectAsState(initial = emptyList())
            val matchingCommands = if (draft.startsWith("/") && !draft.contains(" ")) {
                templates.filter { it.name.startsWith(draft.removePrefix("/"), ignoreCase = true) }
            } else emptyList()
            if (matchingCommands.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().widthIn(max = 840.dp).align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    matchingCommands.forEach { template ->
                        AssistChip(
                            onClick = { draft = "/${template.name} "; vm.saveDraft(draft) },
                            label = { Text("/${template.name}") }
                        )
                    }
                }
            }
            // Quote reply and a pending attachment are both "context attached to the next
            // message" — grouped into one composing tray instead of two separately-floating
            // rows so they read as one unit sitting above the composer, not a growing stack.
            if (pendingQuote != null || pendingImagePath != null || pendingOcrImagePath != null || pendingAudioPath != null || pendingDocument != null) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 840.dp).align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    pendingDocument?.let { docState ->
                        Row(
                            Modifier.fillMaxWidth().clickable(
                                enabled = docState is ChatViewModel.DocumentAttachState.Ready,
                                onClick = { (docState as? ChatViewModel.DocumentAttachState.Ready)?.let { onOpenDocument(it.documentId) } }
                            ).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (docState) {
                                is ChatViewModel.DocumentAttachState.Importing -> {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text("Preparing \"${docState.name}\"", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                                "Copying and indexing locally. Large files may take a few minutes.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                is ChatViewModel.DocumentAttachState.Ready -> {
                                    Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(docState.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text(
                                            if (docState.grounded) "Indexed — semantic search" else "Indexed — keyword search only",
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { vm.clearPendingDocument() }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                    }
                                }
                                is ChatViewModel.DocumentAttachState.Failed -> {
                                    Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text("Could not attach \"${docState.name}\"", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                        Text(docState.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        Text("Choose another file or try again.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { vm.clearPendingDocument() }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        if (pendingQuote != null || pendingImagePath != null || pendingOcrImagePath != null || pendingAudioPath != null) HorizontalDivider()
                    }
                    pendingQuote?.let { quoted ->
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text("Replying to", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    quoted, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { pendingQuote = null }) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (pendingQuote != null && (pendingImagePath != null || pendingOcrImagePath != null || pendingAudioPath != null)) {
                        HorizontalDivider()
                    }
                    pendingOcrImagePath?.let { path ->
                        Box(Modifier.fillMaxWidth().padding(8.dp).clickable { showOcrPreview = true }) {
                            val thumbnailPx = with(LocalDensity.current) { 720.dp.roundToPx() }
                            val bitmap = remember(path, thumbnailPx) {
                                com.vervan.chat.model.ImageUtils.decodeThumbnail(path, thumbnailPx)?.asImageBitmap()
                            }
                            bitmap?.let {
                                Image(
                                    it,
                                    contentDescription = "OCR photo preview",
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 220.dp).clip(MaterialTheme.shapes.large),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Surface(
                                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                                contentColor = MaterialTheme.colorScheme.inverseOnSurface
                            ) {
                                Text(
                                    if (pendingOcrText.isNullOrBlank()) "OCR · no text found · tap to view" else "OCR · tap to view extracted text",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = { vm.setPendingOcr(null, null) },
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f), androidx.compose.foundation.shape.CircleShape)
                            ) { Icon(Icons.Filled.Close, contentDescription = "Remove OCR attachment", tint = MaterialTheme.colorScheme.inverseOnSurface) }
                        }
                    }
                    pendingImagePath?.let { path ->
                        Box(Modifier.fillMaxWidth().heightIn(min = 92.dp).padding(8.dp).clickable { showPendingImagePreview = true }) {
                            val thumbnailPx = with(LocalDensity.current) { 720.dp.roundToPx() }
                            val bitmap = remember(path, thumbnailPx) {
                                com.vervan.chat.model.ImageUtils.decodeThumbnail(path, thumbnailPx)?.asImageBitmap()
                            }
                            bitmap?.let {
                                Image(
                                    it,
                                    contentDescription = "Attached image preview",
                                    modifier = Modifier.size(84.dp).align(Alignment.CenterStart).clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Surface(
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 96.dp, end = 44.dp),
                                shape = MaterialTheme.shapes.small,
                                color = androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Column {
                                    Text("Photo", style = MaterialTheme.typography.labelLarge)
                                    Text("Ready to send · Tap to preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(
                                onClick = { vm.setPendingImage(null) },
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f), androidx.compose.foundation.shape.CircleShape)
                            ) { Icon(Icons.Filled.Close, contentDescription = "Remove image", tint = MaterialTheme.colorScheme.inverseOnSurface) }
                        }
                    }
                    pendingAudioPath?.let { path ->
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { VoiceMessageRow(path) }
                            TextButton(onClick = { vm.setPendingAudio(null) }) { Text("Remove") }
                        }
                    }
                }
            }
            val composerEnabled = modelLoadState is ChatViewModel.ModelLoadState.Ready && !isWorkspaceArchived
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = Space.lg, vertical = Space.sm)
                    .alpha(if (composerEnabled || isGenerating) 1f else 0.62f)
                    .animateContentSize(),
                // VervanExtraShapes.composer = 28dp, a deliberate "this is the composer" size
                // distinct from cards (16dp) and the previous extraLarge (now 32dp, dialogs).
                shape = com.vervan.chat.ui.theme.VervanExtraShapes.composer,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                // One border system instead of (border + lifted container + 22dp shape) — the
                // previous composer had three competing emphases. Now: surface tint + standard border.
                border = vervanBorder(com.vervan.chat.ui.theme.VervanBorderProminence.Emphasized)
            ) {
                Column(Modifier.fillMaxWidth().padding(Space.sm)) {
                    if (isRecording) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("Recording voice message", style = MaterialTheme.typography.labelLarge)
                                Text("Kept locally until sent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { activeRecorder?.cancel(); activeRecorder = null; isRecording = false }) { Text("Cancel") }
                            TextButton(onClick = {
                                val recorder = activeRecorder
                                activeRecorder = null
                                isRecording = false
                                scope.launch {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { recorder?.stop() }
                                    recorder?.outputFile?.absolutePath?.let {
                                        vm.setPendingAudio(it)
                                        showPendingAudioPreview = true
                                    }
                                }
                            }) { Text("Use") }
                        }
                    } else {
                        // Modern single-row composer: [attach] [field] [/ commands] [mic] [send],
                        // icons anchored to the bottom as the field grows — the WhatsApp/Telegram
                        // layout, replacing the previous two-row field-above-toolbar design that
                        // made the composer read as a form. The commands shortcut yields its slot
                        // once typing starts (typing "/" directly still opens suggestions), so a
                        // long draft gets the width back.
                        if (draft.length >= 9_600) {
                            Text(
                                if (draft.length > 12_000) "Message is over the 12,000 character limit" else "${draft.length} / 12,000 characters",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (draft.length > 12_000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End).padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                        if (isImportingAudio) {
                            Row(
                                Modifier.padding(start = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                                Text(
                                    "Converting audio…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                        // Every control in this row shares one 48dp slot height and sits on the
                        // same bottom baseline. The field is a BasicTextField with an exact 48dp
                        // single-line height (Material's OutlinedTextField enforces its own 56dp
                        // minimum, which left the 48dp icons and 46dp send circle visibly sunken
                        // beside it no matter how they were nudged). Single-line: everything is
                        // flush-centered. Multi-line: the field grows upward, icons stay anchored.
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            IconButton(
                                onClick = { showAttachmentSheet = true },
                                enabled = composerEnabled
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Open attachment options")
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = draft,
                                onValueChange = {
                                    draft = it
                                    if (draftLoaded) vm.saveDraft(it)
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 148.dp).semantics {
                                    contentDescription = "Message"
                                },
                                enabled = composerEnabled,
                                maxLines = 5,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = if (draft.length > 12_000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    Box(
                                        Modifier.padding(horizontal = Space.xs, vertical = Space.md),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (draft.isEmpty()) {
                                            Text(
                                                if (composerEnabled) "Message Vervan…" else "Waiting for a local model",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                            if (draft.isBlank()) {
                                IconButton(
                                    onClick = {
                                        if (!draft.startsWith("/")) {
                                            draft = "/$draft"
                                            if (draftLoaded) vm.saveDraft(draft)
                                        }
                                    },
                                    enabled = composerEnabled
                                ) {
                                    Icon(Icons.Filled.Bolt, contentDescription = "Open commands")
                                }
                            }
                            IconButton(
                                onClick = { requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO) },
                                enabled = composerEnabled
                            ) {
                                Icon(
                                    if (audioAvailable == true) Icons.Filled.GraphicEq else Icons.Filled.KeyboardVoice,
                                    contentDescription = if (audioAvailable == true) "Record audio for model" else "Dictate with Android speech recognition"
                                )
                            }
                            val documentReady = pendingDocument is ChatViewModel.DocumentAttachState.Ready
                            val canSend = (draft.isNotBlank() || pendingImagePath != null || pendingOcrImagePath != null || pendingAudioPath != null || documentReady) &&
                                composerEnabled && draft.length <= 12_000
                            val sendActive = canSend || isGenerating
                            Box(
                                Modifier.padding(start = Space.xs).size(48.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(
                                        if (sendActive) com.vervan.chat.ui.theme.vervanBrandGradient()
                                        else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isGenerating) {
                                    IconButton(onClick = {
                                        if (hapticsEnabled) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        vm.cancelGeneration()
                                    }) {
                                        Icon(Icons.Filled.Stop, contentDescription = "Stop generating", tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                } else {
                                    IconButton(enabled = canSend, onClick = {
                                        if (hapticsEnabled) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        sendPendingMessage()
                                    }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send message",
                                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAttachmentSheet) {
        ModernChatAttachmentSheet(
            visionAvailable = visionAvailable,
            audioAvailable = audioAvailable,
            isImportingAudio = isImportingAudio,
            isRunningOcr = isRunningOcr,
            onDismiss = { showAttachmentSheet = false },
            onPhoto = {
                showAttachmentSheet = false
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCamera = {
                showAttachmentSheet = false
                requestCameraPermission.launch(android.Manifest.permission.CAMERA)
            },
            onOcrPhoto = {
                showAttachmentSheet = false
                pickOcrImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onOcrCamera = {
                showAttachmentSheet = false
                requestOcrCameraPermission.launch(android.Manifest.permission.CAMERA)
            },
            onRecordAudio = {
                showAttachmentSheet = false
                requestRecordPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            },
            onAudioFile = {
                showAttachmentSheet = false
                pickAudio.launch(arrayOf("audio/*", "application/ogg"))
            },
            onDocument = {
                showAttachmentSheet = false
                pickDocument.launch(
                    arrayOf(
                        "text/*", "application/pdf", "application/epub+zip",
                        "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "*/*"
                    )
                )
            }
        )
    }

    selectedDocument?.let { selection ->
        DocumentComposerPreviewDialog(
            selection = selection,
            caption = draft,
            onCaptionChange = {
                draft = it
                if (draftLoaded) vm.saveDraft(it)
            },
            onDismiss = { selectedDocument = null },
            onSend = {
                selectedDocument = null
                // Clear any older ready attachment before arming auto-send; otherwise replacing
                // a document could briefly satisfy the Ready effect with the previous file.
                vm.clearPendingDocument()
                sendDocumentWhenReady = true
                vm.attachDocument(selection.uri)
            }
        )
    }

    pendingImagePath?.takeIf { showPendingImagePreview }?.let { path ->
        FullScreenImagePreview(
            path = path,
            title = "Photo preview",
            onDismiss = { showPendingImagePreview = false },
            onRemove = {
                vm.setPendingImage(null)
                showPendingImagePreview = false
            },
            caption = draft,
            onCaptionChange = {
                draft = it
                if (draftLoaded) vm.saveDraft(it)
            },
            confirmLabel = "Send",
            confirmEnabled = modelLoadState is ChatViewModel.ModelLoadState.Ready && !isWorkspaceArchived && !isGenerating && draft.length <= 12_000,
            onConfirm = {
                if (sendPendingMessage()) showPendingImagePreview = false
            }
        )
    }

    pendingAudioPath?.takeIf { showPendingAudioPreview }?.let { path ->
        AudioComposerPreviewDialog(
            path = path,
            caption = draft,
            onCaptionChange = {
                draft = it
                if (draftLoaded) vm.saveDraft(it)
            },
            confirmEnabled = modelLoadState is ChatViewModel.ModelLoadState.Ready && !isWorkspaceArchived && !isGenerating && draft.length <= 12_000,
            onDismiss = { showPendingAudioPreview = false },
            onRemove = {
                vm.setPendingAudio(null)
                showPendingAudioPreview = false
            },
            onSend = {
                if (sendPendingMessage()) showPendingAudioPreview = false
            }
        )
    }

    pendingOcrImagePath?.takeIf { showOcrPreview }?.let { path ->
        OcrPreviewDialog(
            imagePath = path,
            text = pendingOcrText.orEmpty(),
            onTextChange = { vm.updateOcrText(it) },
            caption = draft,
            onCaptionChange = {
                draft = it
                if (draftLoaded) vm.saveDraft(it)
            },
            confirmEnabled = modelLoadState is ChatViewModel.ModelLoadState.Ready && !isWorkspaceArchived && !isGenerating && draft.length <= 12_000,
            onRemove = {
                vm.setPendingOcr(null, null)
                showOcrPreview = false
            },
            onDismiss = { showOcrPreview = false },
            onSend = {
                if (sendPendingMessage()) showOcrPreview = false
            }
        )
    }

    if (showRenameDialog) {
        var title by remember { mutableStateOf(chat?.title.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename chat") },
            text = {
                BoundedTextField(value = title, onValueChange = { title = it }, maxLength = 120, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { vm.rename(title); showRenameDialog = false }, enabled = title.trim().isNotBlank() && title.length <= 120) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }

    // Chat Screen spec §3-5 — workspace indicator options: view/open, set as the app's active
    // workspace (distinct from just opening it — the chat's own workspace and the global
    // active workspace are independent), or move this chat to a different one. Moving shows a
    // preview (§5) before committing.
    var pendingMoveTarget by remember { mutableStateOf<com.vervan.chat.data.db.entities.Workspace?>(null) }
    if (showWorkspaceOptions && workspace != null) {
        val otherWorkspaces by app.container.db.workspaceDao().observeActive().collectAsState(initial = emptyList())
        val activeWorkspaceId by app.container.settingsRepository.activeWorkspaceId.collectAsState(initial = "")
        val isChatWorkspaceActive = workspace?.id == activeWorkspaceId
        AlertDialog(
            onDismissRequest = { showWorkspaceOptions = false },
            title = { Text(workspace?.name.orEmpty()) },
            text = {
                Column {
                    Text("Open workspace", modifier = Modifier.fillMaxWidth().clickable {
                        showWorkspaceOptions = false
                        workspace?.let { onOpenWorkspace(it.id) }
                    }.padding(vertical = 12.dp))
                    if (!isChatWorkspaceActive) {
                        Text(
                            "Set as active workspace",
                            modifier = Modifier.fillMaxWidth()
                                .clickable { vm.setChatWorkspaceActive(); showWorkspaceOptions = false }
                                .padding(vertical = 12.dp)
                        )
                    }
                    HorizontalDivider()
                    Text("Move to another workspace", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    otherWorkspaces.filter { it.id != workspace?.id }.forEach { ws ->
                        Text(
                            ws.name,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { pendingMoveTarget = ws; showWorkspaceOptions = false }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showWorkspaceOptions = false }) { Text("Close") } }
        )
    }

    // §5 — preview before the move actually happens.
    pendingMoveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingMoveTarget = null },
            title = { Text("Move to \"${target.name}\"?") },
            text = {
                Column {
                    Text("From: ${workspace?.name.orEmpty()}")
                    Text("To: ${target.name}")
                    if (folder != null) Text("This chat will leave \"${folder?.name}\" and become unfiled.")
                    Text("Messages, branches, attachments, and history are kept.")
                    Text("Chat-specific model and persona choices are also kept.")
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.moveToWorkspace(target.id, target.name); pendingMoveTarget = null }) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = { pendingMoveTarget = null }) { Text("Cancel") } }
        )
    }

    // Chat Screen spec §10 — reset confirmation: what will be reset, what remains.
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset chat settings?") },
            text = {
                Column {
                    Text("Resets AI, source, tool, and knowledge settings to workspace defaults.")
                    Text("Messages, attachments, workspace, and folder stay unchanged.", modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { vm.resetChatSettings(); showResetConfirm = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }

    // Chat Screen spec §26 — chat statistics (message/branch/attachment counts, dates); no
    // token counts since this app doesn't record per-message usage anywhere yet.
    if (showChatStats) {
        val stats = remember(messages, allMessages) { vm.chatStats() }
        val dateFormat = remember { java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { showChatStats = false },
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
            confirmButton = { TextButton(onClick = { showChatStats = false }) { Text("Close") } }
        )
    }

    if (showSourcePicker) {
        SourcePickerDialog(
            initiallyEnabled = chat?.sourceGrounded == true,
            initiallySelected = chat?.kbIdList()?.toSet() ?: emptySet(),
            onDismiss = { showSourcePicker = false },
            onConfirm = { enabled, selected -> vm.setSourceGrounding(enabled, selected.toList()); showSourcePicker = false }
        )
    }

    if (showChatTools) {
        val globallyDisabled by app.container.settingsRepository.disabledToolIds.collectAsState(initial = emptySet())
        ChatToolsDialog(
            overrides = chat?.toolOverrideMap() ?: emptyMap(),
            globallyDisabled = globallyDisabled,
            onSetOverride = { toolId, state -> vm.setToolOverride(toolId, state) },
            onDismiss = { showChatTools = false }
        )
    }

    if (showKbPicker) {
        val knowledgeBases by vm.knowledgeBases.collectAsState()
        AlertDialog(
            onDismissRequest = { showKbPicker = false },
            title = { Text("Add to knowledge base") },
            text = {
                Column {
                    if (knowledgeBases.isEmpty()) {
                        Text("No knowledge bases yet. Create one in Knowledge.")
                    }
                    knowledgeBases.forEach { kb ->
                        Text(
                            kb.name,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { vm.addToKnowledgeBase(kb.id); showKbPicker = false }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showKbPicker = false }) { Text("Cancel") } }
        )
    }

    if (showPersonaPicker) {
        AlertDialog(
            onDismissRequest = { showPersonaPicker = false },
            title = { Text("Persona") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    // Single-select list — radio buttons, not checkboxes (M3 selection semantics).
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                        vm.setPersona(null)
                        showPersonaPicker = false
                    }) {
                        androidx.compose.material3.RadioButton(selected = chat?.personaId == null, onClick = null)
                        Text("No persona", modifier = Modifier.padding(start = 8.dp))
                    }
                    personas.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                            vm.setPersona(option.id)
                            showPersonaPicker = false
                        }) {
                            androidx.compose.material3.RadioButton(selected = chat?.personaId == option.id, onClick = null)
                            Text(option.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPersonaPicker = false }) { Text("Close") } }
        )
    }

    if (showModelPicker) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("Chat model") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                        vm.setModel(null)
                        showModelPicker = false
                    }) {
                        androidx.compose.material3.RadioButton(selected = chat?.modelId == null, onClick = null)
                        Text("Use active default", modifier = Modifier.padding(start = 8.dp))
                    }
                    generationModels.forEach { model ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                            vm.setModel(model.id)
                            showModelPicker = false
                        }) {
                            androidx.compose.material3.RadioButton(selected = chat?.modelId == model.id, onClick = null)
                            Text(model.displayName, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelPicker = false }) { Text("Close") } }
        )
    }

    if (showSavedResponses) {
        SavedResponsesDialog(
            outputs = chatSavedOutputs,
            onDismiss = { showSavedResponses = false },
            onOpen = { output ->
                val index = messages.indexOfFirst { message ->
                    message.id == output.label || (output.label.isBlank() && message.content == output.content)
                }
                showSavedResponses = false
                if (index >= 0) {
                    stickToBottom = false
                    scope.launch { listState.animateScrollToItem(index) }
                }
            },
            onRemove = { output ->
                scope.launch {
                    app.container.db.savedOutputDao().upsert(output.copy(deletedAt = System.currentTimeMillis()))
                    snackbarHostState.showSnackbar("Bookmark removed")
                }
            }
        )
    }

    contextBreakdown?.let { breakdown ->
        AlertDialog(
            onDismissRequest = { contextBreakdown = null },
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
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { contextBreakdown = null }) { Text("Close") } }
        )
    }

    compareMessageId?.let { targetId ->
        val siblings = com.vervan.chat.data.branch.BranchUtil.siblingsOf(allMessages, targetId)
        CompareDialog(
            siblings = siblings,
            onDismiss = { compareMessageId = null },
            onUse = { id -> vm.switchBranch(id); compareMessageId = null }
        )
    }

    feedbackReasonPromptFor?.let { target ->
        FeedbackReasonDialog(
            onDismiss = { feedbackReasonPromptFor = null },
            onSelect = { reason -> vm.setFeedbackReason(target.id, reason); feedbackReasonPromptFor = null }
        )
    }
}

private data class PendingDocumentSelection(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
)

private fun inspectDocument(context: Context, uri: Uri): PendingDocumentSelection {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
    var size: Long? = null
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }
                    ?.let { name = cursor.getString(it) ?: name }
                cursor.getColumnIndex(android.provider.OpenableColumns.SIZE).takeIf { it >= 0 }
                    ?.let { index -> if (!cursor.isNull(index)) size = cursor.getLong(index) }
            }
        }
    }
    return PendingDocumentSelection(
        uri = uri,
        name = name,
        mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "Document" },
        sizeBytes = size
    )
}

private fun readableFileSize(bytes: Long?): String = when {
    bytes == null || bytes < 0 -> "Size unavailable"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun DocumentComposerPreviewDialog(
    selection: PendingDocumentSelection,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
) {
    val type = selection.name.substringAfterLast('.', "FILE").uppercase().take(8)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White)
                    }
                    Text("Document preview", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
                }
                Box(Modifier.fillMaxWidth().weight(1f).padding(Space.xl), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp
                    ) {
                        Column(Modifier.fillMaxWidth().padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                                Column(Modifier.padding(horizontal = 30.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(52.dp))
                                    Text(type, style = MaterialTheme.typography.labelLarge, fontFamily = com.vervan.chat.ui.theme.VervanMono, modifier = Modifier.padding(top = Space.xs))
                                }
                            }
                            Text(
                                selection.name,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(top = Space.lg)
                            )
                            Text(
                                "${selection.mimeType.substringAfterLast('/').uppercase()} · ${readableFileSize(selection.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.xs)
                            )
                            Row(Modifier.padding(top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                                Text("Indexed privately after sending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Space.xs))
                            }
                        }
                    }
                }
                AttachmentCaptionBar(
                    caption = caption,
                    onCaptionChange = onCaptionChange,
                    confirmEnabled = caption.length <= 12_000,
                    onSend = onSend
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernChatAttachmentSheet(
    visionAvailable: Boolean?,
    audioAvailable: Boolean?,
    isImportingAudio: Boolean,
    isRunningOcr: Boolean,
    onDismiss: () -> Unit,
    onPhoto: () -> Unit,
    onCamera: () -> Unit,
    onOcrPhoto: () -> Unit,
    onOcrCamera: () -> Unit,
    onRecordAudio: () -> Unit,
    onAudioFile: () -> Unit,
    onDocument: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 720.dp).align(Alignment.CenterHorizontally)
                .padding(horizontal = Space.lg).padding(bottom = Space.xxl)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Share", style = MaterialTheme.typography.headlineSmall)
                    Text("Prepared privately on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.Lock, contentDescription = "Private and offline", tint = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CompactAttachmentAction(Icons.Filled.Image, "Gallery", if (visionAvailable == false) "Unavailable" else "Photos", visionAvailable != false, onPhoto, vervanAccentFor(1), Modifier.weight(1f))
                CompactAttachmentAction(Icons.Filled.PhotoCamera, "Camera", if (visionAvailable == false) "Unavailable" else "Take photo", visionAvailable != false, onCamera, vervanAccentFor(3), Modifier.weight(1f))
                CompactAttachmentAction(Icons.Filled.Description, "Document", "PDF or file", true, onDocument, vervanAccentFor(2), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CompactAttachmentAction(Icons.Filled.DocumentScanner, "Scan text", if (isRunningOcr) "Reading…" else "From photo", !isRunningOcr, onOcrPhoto, vervanAccentFor(5), Modifier.weight(1f))
                CompactAttachmentAction(Icons.Filled.Mic, "Record", if (audioAvailable == true) "Voice note" else "Unavailable", audioAvailable == true && !isImportingAudio, onRecordAudio, vervanAccentFor(4), Modifier.weight(1f))
                CompactAttachmentAction(Icons.Filled.AudioFile, if (isImportingAudio) "Preparing…" else "Audio", if (audioAvailable == true) "Choose file" else "Unavailable", audioAvailable == true && !isImportingAudio, onAudioFile, vervanAccentFor(4), Modifier.weight(1f))
            }
            TextButton(
                onClick = onOcrCamera,
                enabled = !isRunningOcr,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Space.sm)
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = Space.xs))
                Text("Scan text with camera")
            }
        }
    }
}

@Composable
private fun CompactAttachmentAction(
    icon: ImageVector,
    title: String,
    helper: String,
    enabled: Boolean,
    onClick: () -> Unit,
    accent: VervanAccent,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.alpha(if (enabled) 1f else 0.45f).clickable(enabled = enabled, onClick = onClick).padding(vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = accent.container, contentColor = accent.onContainer) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(14.dp).size(24.dp))
        }
        Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.sm), maxLines = 1)
        Text(helper, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

private fun formatMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/** WhatsApp-style voice message row: play/pause, a seekable progress bar, and elapsed/total
 *  time. One MediaPlayer per bubble, released whenever the bubble leaves composition or the
 *  audio path changes. Used for the composer's pending attachment preview, sent messages, and
 *  (via the same composable) chat history — there's only one implementation of this. */
@Composable
internal fun VoiceMessageRow(path: String) {
    var isPlaying by remember(path) { mutableStateOf(false) }
    var mediaPlayer by remember(path) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var durationMs by remember(path) { mutableStateOf(0) }
    var positionMs by remember(path) { mutableStateOf(0) }
    // Surface a real error UI instead of swallowing prepare()/setDataSource() failures —
    // previously a corrupt or missing voice file silently rendered as an unplayable row with
    // a 0:00 duration, looking like a stuck player. Now the user sees the failure and can
    // retry in case it was a transient read error.
    var loadFailed by remember(path) { mutableStateOf(false) }
    DisposableEffect(path) {
        onDispose { mediaPlayer?.release(); mediaPlayer = null }
    }
    // Polls playback position while playing instead of a callback — MediaPlayer has no
    // position-changed listener, and 200ms is smooth enough for a voice-note seek bar.
    LaunchedEffect(isPlaying, mediaPlayer) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        while (isPlaying) {
            positionMs = runCatching { mp.currentPosition }.getOrDefault(positionMs)
            kotlinx.coroutines.delay(200)
        }
    }
    fun ensurePlayer(onReady: (android.media.MediaPlayer) -> Unit) {
        val mp = mediaPlayer
        if (mp != null) {
            onReady(mp)
            return
        }
        runCatching {
            android.media.MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { isPlaying = false; positionMs = 0 }
                prepare()
            }
        }.onSuccess {
            mediaPlayer = it
            durationMs = runCatching { it.duration }.getOrDefault(0)
            loadFailed = false
            onReady(it)
        }.onFailure {
            // Don't keep the half-constructed player around — release and clear so the next
            // tap retries from scratch.
            mediaPlayer?.release()
            mediaPlayer = null
            loadFailed = true
        }
    }
    Row(Modifier.padding(bottom = 4.dp).widthIn(min = 180.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            // No explicit size override — the default 48dp keeps this within Material's minimum
            // touch target; the 20dp Icon inside already gives the compact visual footprint.
            onClick = {
                if (loadFailed) {
                    // Tap on the warning icon retries — a transient SAF/IO hiccup often succeeds
                    // on a second attempt without making the user feel stuck.
                    loadFailed = false
                    ensurePlayer { mp ->
                        mp.start()
                        isPlaying = true
                    }
                    return@IconButton
                }
                ensurePlayer { mp ->
                    if (isPlaying) {
                        mp.pause()
                        isPlaying = false
                    } else {
                        if (mp.currentPosition >= mp.duration && mp.duration > 0) mp.seekTo(0)
                        mp.start()
                        isPlaying = true
                    }
                }
            }
        ) {
            Icon(
                when {
                    loadFailed -> Icons.Filled.Warning
                    isPlaying -> Icons.Filled.Pause
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = when {
                    loadFailed -> "Voice message failed to load — tap to retry"
                    isPlaying -> "Pause voice message"
                    else -> "Play voice message"
                },
                tint = if (loadFailed) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
        }
        if (loadFailed) {
            Text(
                "Couldn't load audio",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        } else {
            Slider(
                value = if (durationMs > 0) positionMs.toFloat().coerceIn(0f, durationMs.toFloat()) else 0f,
                valueRange = 0f..(durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { value ->
                    positionMs = value.toInt()
                    ensurePlayer { mp -> mp.seekTo(positionMs) }
                },
                modifier = Modifier.weight(1f).height(24.dp)
            )
            Text(
                "${formatMs(positionMs)} / ${formatMs(durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun AttachmentCaptionBar(
    caption: String,
    onCaptionChange: (String) -> Unit,
    confirmEnabled: Boolean,
    onSend: () -> Unit,
    placeholder: String = "Add a message"
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            if (caption.length >= 9_600) {
                Text(
                    if (caption.length > 12_000) "Message is over the 12,000 character limit" else "${caption.length} / 12,000",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (caption.length > 12_000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End).padding(horizontal = Space.lg, vertical = Space.xs)
                )
            }
            Row(Modifier.fillMaxWidth().padding(Space.sm), verticalAlignment = Alignment.Bottom) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = com.vervan.chat.ui.theme.VervanExtraShapes.composer,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = caption,
                        onValueChange = onCaptionChange,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 148.dp),
                        decorationBox = { inner ->
                            Box(Modifier.padding(horizontal = Space.lg, vertical = Space.md), contentAlignment = Alignment.CenterStart) {
                                if (caption.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                inner()
                            }
                        }
                    )
                }
                Box(
                    Modifier.padding(start = Space.sm).size(52.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            if (confirmEnabled) com.vervan.chat.ui.theme.vervanBrandGradient()
                            else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onSend, enabled = confirmEnabled) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send attachment",
                            tint = if (confirmEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioComposerPreviewDialog(
    path: String,
    caption: String,
    onCaptionChange: (String) -> Unit,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onSend: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White) }
                    Text("Audio preview", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remove audio", tint = androidx.compose.ui.graphics.Color.White) }
                }
                Box(Modifier.fillMaxWidth().weight(1f).padding(Space.xl), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp
                    ) {
                        Column(Modifier.fillMaxWidth().padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                                Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.padding(Space.lg).size(44.dp))
                            }
                            Text("Voice message", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.lg))
                            Text("Preview before sending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(Modifier.fillMaxWidth().padding(top = Space.md)) { VoiceMessageRow(path) }
                        }
                    }
                }
                AttachmentCaptionBar(caption, onCaptionChange, confirmEnabled, onSend)
            }
        }
    }
}

@Composable
internal fun FullScreenImagePreview(
    path: String,
    title: String,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
    caption: String? = null,
    onCaptionChange: ((String) -> Unit)? = null,
    confirmLabel: String? = null,
    confirmEnabled: Boolean = true,
    onConfirm: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val context = LocalContext.current
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White) }
                    Text(title, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    onRemove?.let {
                        IconButton(onClick = it) { Icon(Icons.Filled.Close, "Remove attachment", tint = androidx.compose.ui.graphics.Color.White) }
                    }
                    IconButton(onClick = {
                        com.vervan.chat.ui.common.openWithExternalApp(context, java.io.File(path), "image/*")
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open with…", tint = androidx.compose.ui.graphics.Color.White)
                    }
                    if (caption == null && confirmLabel != null && onConfirm != null) {
                        TextButton(onClick = onConfirm) { Text(confirmLabel) }
                    }
                }
                val previewPx = with(LocalDensity.current) { 1600.dp.roundToPx() }
                val bitmap = remember(path, previewPx) {
                    com.vervan.chat.model.ImageUtils.decodeThumbnail(path, previewPx)?.asImageBitmap()
                }
                // Pinch-to-zoom + pan, plus double-tap to toggle 1x/2.5x — the baseline
                // "view an image" gesture set users expect from any photo viewer. Pan only
                // engages while zoomed in; at 1x the image stays centered so it can't drift.
                var scale by remember(path) { mutableStateOf(1f) }
                var pan by remember(path) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 5f)
                    pan = if (scale > 1f) pan + panChange else androidx.compose.ui.geometry.Offset.Zero
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    bitmap?.let {
                        Image(
                            it, "Image preview",
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale; scaleY = scale
                                    translationX = pan.x; translationY = pan.y
                                }
                                .transformable(transformState)
                                .pointerInput(path) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (scale > 1f) {
                                                scale = 1f; pan = androidx.compose.ui.geometry.Offset.Zero
                                            } else scale = 2.5f
                                        }
                                    )
                                },
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                if (caption != null && onCaptionChange != null && confirmLabel != null && onConfirm != null) {
                    AttachmentCaptionBar(
                        caption = caption,
                        onCaptionChange = onCaptionChange,
                        confirmEnabled = confirmEnabled,
                        onSend = onConfirm
                    )
                }
            }
        }
    }
}

/** Preview + review sheet for an OCR attach — shows the scanned photo alongside the text ML
 * Kit recognized from it, editable in case OCR misread something, before it gets folded into
 * the outgoing message (see ChatScreen's send handler). This is the "same experience as
 * attaching an image" the user sees pre-send; the photo itself is discarded on send/remove and
 * never reaches the model. */
@Composable
private fun OcrPreviewDialog(
    imagePath: String,
    text: String,
    onTextChange: (String) -> Unit,
    caption: String,
    onCaptionChange: (String) -> Unit,
    confirmEnabled: Boolean,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    var showExtractedText by remember(imagePath) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White) }
                    Column {
                        Text("OCR preview", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
                        Text("Image stays on this device", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remove OCR image", tint = androidx.compose.ui.graphics.Color.White) }
                }

                val thumbnailPx = with(LocalDensity.current) { 1600.dp.roundToPx() }
                val bitmap = remember(imagePath, thumbnailPx) {
                    com.vervan.chat.model.ImageUtils.decodeThumbnail(imagePath, thumbnailPx)?.asImageBitmap()
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    bitmap?.let {
                        Image(
                            it,
                            contentDescription = "Scanned photo",
                            modifier = Modifier.fillMaxSize().padding(horizontal = Space.sm),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Surface(
                        onClick = { showExtractedText = !showExtractedText },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(Space.md),
                        shape = com.vervan.chat.ui.theme.VervanExtraShapes.pill,
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    ) {
                        Row(Modifier.padding(horizontal = Space.lg, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                if (showExtractedText) "Hide extracted text" else if (text.isBlank()) "No text found · add manually" else "View extracted text",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = Space.sm)
                            )
                        }
                    }
                }

                if (showExtractedText) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(start = Space.lg, end = Space.lg, top = Space.md)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Extracted text", style = MaterialTheme.typography.titleSmall)
                                    Text("Review or correct it before sending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { showExtractedText = false }) { Icon(Icons.Filled.Close, "Hide extracted text") }
                            }
                            OutlinedTextField(
                                value = text,
                                onValueChange = onTextChange,
                                modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = Space.sm),
                                placeholder = { Text("Type the text visible in the image") }
                            )
                        }
                    }
                }

                AttachmentCaptionBar(
                    caption = caption,
                    onCaptionChange = onCaptionChange,
                    confirmEnabled = confirmEnabled,
                    onSend = onSend,
                    placeholder = "Add a message about this text"
                )
            }
        }
    }
}

@Composable
private fun SavedResponsesDialog(
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
private fun ModelReadinessPanel(
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

/** §14.1 — informational, not an error: the model is working correctly, just constrained by
 * device temperature. Uses tertiary (not error) container so it reads distinctly from
 * [ModelReadinessPanel]'s failed/unavailable states even at a glance. */
@Composable
private fun ThermalNotice(severe: Boolean) {
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
private fun LiveGenStatsChip(stats: ChatViewModel.LiveGenStats) {
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
private fun ChatMoreOptionsSheet(
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
private fun MoreSheetSection(label: String) {
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
private fun MoreOptionRow(
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
private fun ChatEmptyState(
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

private data class ChatStarter(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val prompt: String
)

/**
 * Chat Screen spec §13 — context strip. Previously up to six separate chips (workspace, folder,
 * persona, model+thinking, sources, context%) in a horizontally-scrolling row with no wrap, which
 * meant the model chip — arguably the most important one — could scroll off-screen entirely with
 * no indication anything was hidden. Now a single compact summary chip ("Default · Gemma · 2
 * sources") that opens the full breakdown in [ChatContextDetailsSheet] on tap; only genuinely
 * exceptional state (no model selected, context nearly full) stays inline next to it, since that's
 * the state a user needs to notice without tapping anything. Hidden entirely when there's nothing
 * useful to show (a brand new chat with no persona/sources/thinking mode set).
 */
@Composable
private fun ChatContextStrip(
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
private fun ChatContextDetailsSheet(
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
 * Chat Screen spec §9/§23 — an archived workspace remains viewable (history, branches,
 * sources all intact) but blocks new messages until the workspace is restored.
 */
@Composable
private fun ArchivedWorkspaceBanner(onRestore: () -> Unit) {
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
 * Chat Screen spec §27 — find-in-conversation, scoped to the currently rendered branch path
 * (not the app-wide SearchScreen, which spans every chat). no inline highlighting of
 * the matched substring, just prev/next navigation and a match count — jumping to the message
 * is the useful part, highlighting inside MarkdownLiteText would need its own span-aware path.
 */
@Composable
private fun ConversationSearchBar(messages: List<Message>, onClose: () -> Unit, onJumpTo: (Int) -> Unit) {
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
private fun CompareDialog(siblings: List<Message>, onDismiss: () -> Unit, onUse: (String) -> Unit) {
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
private fun CompareBranchCard(index: Int, sibling: Message, onUse: (String) -> Unit, modifier: Modifier) {
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
private fun ModeSettingsDialog(
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
                // Per-chat sampler overrides (spec §6/§7) — the slider always shows the
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
private fun SamplerOverrideRow(
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
private fun SourcePickerDialog(
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
private fun ChatToolsDialog(
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

@Composable
private fun MessageBubble(
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
                    // Markdown/code-block rendering (spec §7.1) — assistant output routinely
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
                        // Clipboard hygiene (Phase H) — auto-clears after 30s if
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
