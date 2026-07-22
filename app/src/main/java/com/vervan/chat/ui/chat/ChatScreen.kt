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

internal data class FollowSnapshot(
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
    // Chat Screen — restore-on-open: jump to the saved message-ID anchor (where the
    // user was last reading) instead of always landing on the latest message. Runs once per
    // chat; falls back to "latest message" when there's no anchor or it's no longer in the
    // rendered branch ("previous position unavailable" case).
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
    // Save the reading position when leaving the chat ("returning from another
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
    // : pause/stop TTS on audio-focus loss instead of talking over a call or other
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
            // Chat Screen — compact, horizontally-scrollable status chips; never
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
            // Model Loading Strategy — a distinct, non-alarming indicator during
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
            // Chat Screen — "jump to latest" once the user has scrolled away from
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

    // Chat Screen-5 — workspace indicator options: view/open, set as the app's active
    // workspace (distinct from just opening it — the chat's own workspace and the global
    // active workspace are independent), or move this chat to a different one. Moving shows a
    // preview before committing.
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

    // preview before the move actually happens.
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

    // Chat Screen — reset confirmation: what will be reset, what remains.
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

    // Chat Screen — chat statistics (message/branch/attachment counts, dates); no
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
