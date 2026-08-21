package com.vervan.chat.ui.chat

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
import com.vervan.chat.audio.WavRecorder
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.MessageState
import com.vervan.chat.data.db.entities.traits
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.DatePill
import com.vervan.chat.ui.common.ActivityStatusPill
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.collectAsState
import com.vervan.chat.ui.common.rememberThumbnail
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.ModernistTokens
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.ui.theme.SurfaceRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar

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
): Boolean =
    totalItemsCount == 0 || (lastVisibleIndex == totalItemsCount - 1 && lastVisibleBottom - viewportEnd <= tolerancePx)

/** Day-boundary check for [DatePill] separators. Public so other date-aware surfaces (search
 *  results, exported transcripts) reuse the same notion of "same day" as the chat feed. */
internal fun sameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) && ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(
        java.util.Calendar.DAY_OF_YEAR
    )
}

/** Folds any scanned-attachment text (OCR, QR/barcode) ahead of [bodyBase] — shared by both send
 * paths (voice-respond and [ChatScreen]'s own sendPendingMessage) so the two prefixes stay in
 * sync instead of drifting as separate copies. Either or both may be present. */
private fun withScannedAttachmentsPrefix(bodyBase: String, ocrText: String?, qrText: String?): String {
    var body = bodyBase
    if (qrText != null) body = "Decoded from a QR/barcode photo:\n\"\"\"\n$qrText\n\"\"\"\n\n$body"
    if (ocrText != null) body = "Text extracted from a photo via OCR:\n\"\"\"\n$ocrText\n\"\"\"\n\n$body"
    return body
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
    onOpenPdfPage: (documentId: String, page: Int) -> Unit = { _, _ -> },
    onOpenFolders: () -> Unit = {},
    onOpenModels: () -> Unit = {},
    onOpenVoiceSettings: () -> Unit = {},
    onOpenWorkspace: (String) -> Unit = {},
    activityLabel: String? = null,
    onOpenActivity: () -> Unit = {},
    onForkChat: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val vm: ChatViewModel = viewModel(factory = viewModelFactory {
        initializer { ChatViewModel(app, chatId) }
    })
    // See ChatScreenComponents.kt's doc comment on ChatVmState/ChatVoiceSettingsState/
    // VoiceControllerUiState for why these are bundled instead of ~50 separate collectAsState
    // calls sitting directly in this method — that was the real driver of ChatScreen's compiled
    // method exceeding ART's JIT compile-size limit (dropped frames every time a chat opened).
    val (messages, allMessages, isGenerating, isRetrieving, isRecallingMemory, error, chat, workspace, folder, isWorkspaceArchived, titleGenerating, confirmationMessage, pendingDocument, attachEmbedProgress, documents, savedOutputs, persona, personas, activeModelName, selectedGenerationModel, generationModels, modelLoadState, visionAvailable, audioAvailable) = rememberChatVmState(
        vm,
        app
    )
    val chatSavedOutputs =
        remember(savedOutputs, chatId) { savedOutputs.filter { it.sourceChatId == chatId } }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(confirmationMessage) {
        confirmationMessage?.let { snackbarHostState.showSnackbar(it); vm.clearConfirmation() }
    }
    // Defaults to true when no model is resolved yet: this screen's baseline claim is on-device,
    // and only a model that actually runs elsewhere may weaken it.
    val modelRunsOnDevice = selectedGenerationModel?.traits?.runsOnDevice != false
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val (hapticsEnabled, speechInputEnabled, modelAudioSttEnabled, whisperSttEnabled, androidSttEnabled, sttEnginePreference, sttFallbackEnabled, installedVoiceModels, voiceReplyMode, transcriptReviewEnabled, voicePushToTalkEnabled, autoReadAloud) = rememberChatVoiceSettingsState(
        app
    )
    val whisperSttAvailable =
        com.vervan.chat.BuildConfig.WHISPER_CPP_AVAILABLE && com.vervan.chat.voice.WhisperCppSttEngine.findInstalledModelFile(
            context, installedVoiceModels.firstOrNull {
                it.engine.equals(
                    com.vervan.chat.voice.WhisperCppSttEngine.ENGINE,
                    true
                ) && it.language.equals(
                    com.vervan.chat.voice.WhisperCppSttEngine.MODEL_LANGUAGE_KEY, true
                ) && it.isReady
            }?.filePath
        ) != null
    val androidSttAvailable = remember {
        com.vervan.chat.voice.AndroidSystemSttRecognizer.isAvailable(context)
    }
    val sttResolution = com.vervan.chat.voice.SttEnginePolicy.resolve(
        com.vervan.chat.voice.SttAvailability(
            speechInputEnabled = speechInputEnabled,
            preference = sttEnginePreference,
            fallbackEnabled = sttFallbackEnabled,
            modelEnabled = modelAudioSttEnabled,
            // null means this selected model has not been loaded/tested yet. Let the action load
            // it; the voice controller re-checks the runtime-confirmed capability before capture.
            modelAvailable = selectedGenerationModel != null && audioAvailable != false,
            whisperEnabled = whisperSttEnabled,
            whisperAvailable = whisperSttAvailable,
            androidEnabled = androidSttEnabled,
            androidAvailable = androidSttAvailable
        )
    )
    val speechInputAvailable = sttResolution.isAvailable

    var draft by remember { mutableStateOf("") }
    var draftInputModality by rememberSaveable(chatId) { mutableStateOf("TEXT") }
    var draftOriginalTranscript by rememberSaveable(chatId) { mutableStateOf<String?>(null) }
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
        val anchorIndex =
            chat?.scrollAnchorMessageId?.let { id -> messages.indexOfFirst { it.id == id } } ?: -1
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
    var handsFreeActive by rememberSaveable(chatId) { mutableStateOf(false) }
    var voiceSessionKey by rememberSaveable(chatId) { mutableStateOf(0) }
    var showVoiceOptions by remember { mutableStateOf(false) }
    var showComposerVoiceMenu by remember { mutableStateOf(false) }
    var immersiveVoiceActive by rememberSaveable(chatId) { mutableStateOf(false) }
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
    var dictationRecording by remember { mutableStateOf(false) }
    var dictationTranscribing by remember { mutableStateOf(false) }
    var dictationTranscript by rememberSaveable(chatId) { mutableStateOf<String?>(null) }
    var dictationOriginalTranscript by rememberSaveable(chatId) { mutableStateOf<String?>(null) }
    var draftVoiceRecordingPath by rememberSaveable(chatId) { mutableStateOf<String?>(null) }
    var draftSttLabel by rememberSaveable(chatId) { mutableStateOf<String?>(null) }
    var dictationError by remember { mutableStateOf<String?>(null) }
    var dictationLevels by remember { mutableStateOf<List<Float>>(emptyList()) }
    var dictationStartedAt by remember { mutableStateOf(0L) }
    var dictationElapsedMs by remember { mutableStateOf(0L) }
    var dictationRecorder by remember { mutableStateOf<WavRecorder?>(null) }
    var dictationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var dictationBaseText by remember { mutableStateOf("") }

    fun discardDraftVoiceAttachment() {
        draftVoiceRecordingPath = null
        draftSttLabel = null
        draftOriginalTranscript = null
        draftInputModality = "TEXT"
        vm.setPendingAudio(null)
    }

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
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isRunningOcr by remember { mutableStateOf(false) }
    var isRunningQr by remember { mutableStateOf(false) }
    var sendDocumentWhenReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latestDraft = rememberUpdatedState(draft)
    val latestPendingQuote = rememberUpdatedState(pendingQuote)
    val latestVoiceReplyMode = rememberUpdatedState(voiceReplyMode)
    val latestVoicePushToTalk = rememberUpdatedState(voicePushToTalkEnabled)
    // Connects the realtime audio session to the ordinary chat pipeline: capture, VAD, STT and
    // TTS remain owned by RealtimeVoiceController, while this (the only real caller) owns
    // message persistence, context assembly, retrieval, attachments, tools, branching and LLM
    // selection — what lets voice remain a modality of an existing conversation instead of
    // becoming a second chat system.
    val voiceRespond: suspend (com.vervan.chat.voice.VoiceInputTurn, (String) -> Unit) -> String =
        remember(vm) {
            val callback: suspend (com.vervan.chat.voice.VoiceInputTurn, (String) -> Unit) -> String =
                { input, onAssistantUpdate ->
                    val typedPrefix =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                            val value = latestDraft.value.trim()
                            draft = ""
                            vm.saveDraft("")
                            value
                        }
                    val quotePrefix = latestPendingQuote.value?.let { quoted ->
                        quoted.lineSequence().joinToString("\n") { "> $it" } + "\n\n"
                    }.orEmpty()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        pendingQuote = null
                    }
                    val attached = vm.consumeAttachments()
                    val document =
                        vm.pendingDocument.value as? ChatViewModel.DocumentAttachState.Ready
                    vm.clearPendingDocument()
                    val ocrText = attached.ocrText?.takeIf { it.isNotBlank() }
                    val qrText = attached.qrText?.takeIf { it.isNotBlank() }
                    val mergedSpeech =
                        listOf(typedPrefix, input.text.trim()).filter { it.isNotBlank() }
                            .joinToString(" ")
                    val bodyBase = mergedSpeech.ifBlank {
                        when {
                            document != null -> "Describe this document."
                            attached.imagePath != null && attached.audioPath != null -> "Analyze the attached image and audio together."

                            attached.imagePath != null -> "Describe this image."
                            attached.audioPath != null -> "Transcribe and respond to the attached audio."
                            else -> ""
                        }
                    }
                    val body = withScannedAttachmentsPrefix(bodyBase, ocrText, qrText)
                    vm.sendVoiceAndAwait(
                        text = quotePrefix + body,
                        imagePath = attached.imagePath,
                        audioPath = attached.audioPath,
                        documentId = document?.documentId,
                        inputModality = when {
                            typedPrefix.isNotBlank() -> "MIXED"
                            latestVoicePushToTalk.value -> "PUSH_TO_TALK"
                            else -> "HANDS_FREE"
                        },
                        outputModalities = if (latestVoiceReplyMode.value == "NEVER") "TEXT" else "TEXT,SPEECH",
                        voiceRecordingPath = input.recordingPath,
                        sttLabel = input.sttLabel,
                        durationMs = input.durationMs,
                        onAssistantUpdate = onAssistantUpdate
                    )
                }
            callback
        }
    val voiceCancel = remember(vm) { { vm.cancelGeneration() } }
    val voiceController =
        remember(chatId, voiceSessionKey, voiceRespond, selectedGenerationModel?.id) {
            com.vervan.chat.voice.RealtimeVoiceController(
                app, voiceRespond, voiceCancel, selectedGenerationModel?.id
            )
        }
    val (voiceState, voiceTurns, voiceWaveform, voiceElapsedMs, voiceLiveTranscript, voiceSttLabel, voiceTtsLabel, voiceHasEchoCancellation, voicePlaybackPaused, voiceMicrophoneMuted, voiceSpeechOutputEnabled, voiceModelLoadError, voiceSttUnavailable, voiceLoadingModelName, voicePushToTalkHeld) = rememberVoiceControllerUiState(
        voiceController
    )
    val onTogglePushToTalkMode: () -> Unit = {
        val enabled = !voicePushToTalkEnabled
        voiceController.setPushToTalkEnabled(enabled)
        scope.launch { app.container.settingsRepository.setVoicePushToTalkEnabled(enabled) }
    }
    val (pickImage, requestCameraPermission) = rememberImageAttachLaunchers(
        vm = vm,
        scope = scope,
        onPreviewReady = { showPendingImagePreview = it },
        onError = { attachmentError = it })
    // "Document" attach — any standard document type, run through extract/chunk/embed and
    // attached as a per-chat knowledge source (see ChatViewModel.attachDocument).
    var selectedDocument by remember { mutableStateOf<PendingDocumentSelection?>(null) }
    val pickDocument = rememberDocumentAttachLauncher(context) { selectedDocument = it }
    val pickAudio =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                isImportingAudio = true
                var importedPath: String? = null
                vm.importAudio(uri).mapCatching { path ->
                    importedPath = path
                    val file = java.io.File(path)
                    val transcription =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.vervan.chat.voice.OfflineDictationTranscriber.transcribe(
                                app, file, selectedGenerationModel?.id
                            ).getOrThrow()
                        }
                    path to transcription
                }.onSuccess { (path, transcription) ->
                    draftVoiceRecordingPath?.let { old ->
                        if (old != path) java.io.File(old).delete()
                    }
                    draftVoiceRecordingPath = path
                    vm.setPendingAudio(path)
                    draftSttLabel = transcription.engineLabel
                    val hadTypedText = draft.isNotBlank()
                    val combined =
                        listOf(draft.trim(), transcription.text).filter { it.isNotBlank() }
                            .joinToString(" ")
                    draftInputModality = if (hadTypedText) "MIXED" else "AUDIO_FILE"
                    draftOriginalTranscript = transcription.text
                    if (transcriptReviewEnabled) {
                        dictationTranscript = combined
                        dictationOriginalTranscript = transcription.text
                    } else {
                        draft = combined
                        vm.saveDraft(draft)
                    }
                }.onFailure {
                    importedPath?.let { path -> java.io.File(path).delete() }
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
    val ocrNoTextToast = stringResource(R.string.chat_ocr_no_text_toast)
    var showOcrPreview by remember { mutableStateOf(false) }
    fun applyOcrResult(result: Result<ChatViewModel.OcrResult>) {
        isRunningOcr = false
        result.onSuccess { r ->
            vm.setPendingOcr(r.imagePath, r.text)
            showOcrPreview = true
            if (r.text.isBlank()) {
                android.widget.Toast.makeText(
                    context, ocrNoTextToast, android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }.onFailure {
            attachmentError = it.toUserMessage()
        }
    }
    val (pickOcrImage, requestOcrCameraPermission) = rememberOcrAttachLaunchers(
        vm = vm,
        scope = scope,
        onRunningChange = { isRunningOcr = it },
        onOcrResult = ::applyOcrResult,
        onError = { attachmentError = it })

    // QR/barcode attach — same shape as OCR attach above, decoding via ML Kit's barcode
    // scanner instead of its text recognizer.
    val pendingQrImagePath = pendingAttachments.qrImagePath
    val pendingQrText = pendingAttachments.qrText
    val qrNoCodeToast = stringResource(R.string.chat_qr_no_code_toast)
    var showQrPreview by remember { mutableStateOf(false) }
    fun applyQrResult(result: Result<ChatViewModel.QrResult>) {
        isRunningQr = false
        result.onSuccess { r ->
            vm.setPendingQr(r.imagePath, r.text)
            showQrPreview = true
            if (r.text.isBlank()) {
                android.widget.Toast.makeText(
                    context, qrNoCodeToast, android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }.onFailure {
            attachmentError = it.toUserMessage()
        }
    }
    val (pickQrImage, requestQrCameraPermission) = rememberQrAttachLaunchers(
        vm = vm,
        scope = scope,
        onRunningChange = { isRunningQr = it },
        onQrResult = ::applyQrResult,
        onError = { attachmentError = it })

    fun startVoiceMessageRecording() {
        val file = vm.newAudioFile()
        val recorder = WavRecorder(file)
        runCatching { recorder.start() }.onSuccess {
            activeRecorder = recorder
            isRecording = true
        }.onFailure {
            recorder.cancel()
            attachmentError = it.toUserMessage()
        }
    }

    fun startInlineDictation() {
        dictationJob?.cancel()
        dictationError = null
        dictationBaseText = dictationTranscript.orEmpty()
        dictationTranscript = null
        dictationLevels = emptyList()
        if (!sttResolution.isAvailable) {
            dictationError = sttResolution.unavailableReason ?: "Speech input is unavailable"
            return
        }
        if (sttResolution.candidates.first() == com.vervan.chat.voice.SttEngineChoice.ANDROID) {
            dictationStartedAt = android.os.SystemClock.elapsedRealtime()
            dictationElapsedMs = 0L
            dictationRecording = true
            dictationJob = scope.launch {
                val language = app.container.settingsRepository.voiceInputLanguage.first()
                val maxSeconds = app.container.settingsRepository.maxUtteranceSeconds.first()
                val result = com.vervan.chat.voice.AndroidSystemSttRecognizer.recognizeOnce(
                    app, language, maxSeconds
                )
                dictationRecording = false
                result.onSuccess { text ->
                    draftSttLabel = com.vervan.chat.voice.SttEngineChoice.ANDROID.label
                    draftVoiceRecordingPath = null
                    vm.setPendingAudio(null)
                    val combined =
                        listOf(dictationBaseText, text).filter { it.isNotBlank() }.joinToString(" ")
                    val original = listOf(
                        dictationOriginalTranscript.orEmpty(),
                        text
                    ).filter { it.isNotBlank() }.joinToString(" ")
                    if (transcriptReviewEnabled) {
                        dictationTranscript = combined
                        dictationOriginalTranscript = original
                    } else {
                        val hadTypedText = draft.isNotBlank()
                        draft = listOf(draft.trim(), combined).filter { it.isNotBlank() }
                            .joinToString(" ")
                        draftInputModality = if (hadTypedText) "MIXED" else "VOICE_DICTATION"
                        draftOriginalTranscript = original
                        vm.saveDraft(draft)
                    }
                    dictationBaseText = ""
                }.onFailure {
                    dictationError = it.toUserMessage()
                    dictationTranscript = dictationBaseText.takeIf { it.isNotBlank() }
                    dictationBaseText = ""
                }
                dictationJob = null
            }
            return
        }
        val recorder = WavRecorder(vm.newAudioFile()) { level ->
            scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                dictationLevels = (dictationLevels + level).takeLast(32)
            }
        }
        runCatching { recorder.start() }.onSuccess {
            dictationRecorder = recorder
            dictationStartedAt = android.os.SystemClock.elapsedRealtime()
            dictationElapsedMs = 0L
            dictationRecording = true
        }.onFailure {
            recorder.cancel()
            dictationError = it.toUserMessage()
        }
    }

    fun cancelInlineDictation() {
        dictationJob?.cancel()
        dictationJob = null
        dictationRecorder?.cancel()
        dictationRecorder = null
        dictationRecording = false
        dictationTranscribing = false
        dictationLevels = emptyList()
    }

    fun finishInlineDictation() {
        val recorder = dictationRecorder ?: return
        dictationRecorder = null
        dictationRecording = false
        dictationTranscribing = true
        dictationJob = scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                recorder.stop()
                val failure = recorder.failureReason
                if (failure != null) Result.failure(IllegalStateException(failure))
                else com.vervan.chat.voice.OfflineDictationTranscriber.transcribe(
                    app, recorder.outputFile, selectedGenerationModel?.id
                )
            }
            dictationTranscribing = false
            result.onSuccess { transcription ->
                val text = transcription.text
                val keepRecording = app.container.settingsRepository.storeVoiceRecordings.first()
                if (keepRecording) {
                    draftVoiceRecordingPath?.let { old ->
                        if (old != recorder.outputFile.absolutePath) java.io.File(old).delete()
                    }
                    draftVoiceRecordingPath = recorder.outputFile.absolutePath
                    vm.setPendingAudio(draftVoiceRecordingPath)
                } else {
                    recorder.outputFile.delete()
                    draftVoiceRecordingPath = null
                    vm.setPendingAudio(null)
                }
                draftSttLabel = transcription.engineLabel
                val combined =
                    listOf(dictationBaseText, text).filter { it.isNotBlank() }.joinToString(" ")
                val original =
                    listOf(dictationOriginalTranscript.orEmpty(), text).filter { it.isNotBlank() }
                        .joinToString(" ")
                if (transcriptReviewEnabled) {
                    dictationTranscript = combined
                    dictationOriginalTranscript = original
                } else {
                    val hadTypedText = draft.isNotBlank()
                    draft =
                        listOf(draft.trim(), combined).filter { it.isNotBlank() }.joinToString(" ")
                    draftInputModality = if (hadTypedText) "MIXED" else "VOICE_DICTATION"
                    draftOriginalTranscript = original
                    vm.saveDraft(draft)
                    dictationTranscript = null
                    dictationOriginalTranscript = null
                }
                dictationBaseText = ""
            }.onFailure {
                recorder.outputFile.delete()
                dictationError = it.toUserMessage()
                dictationTranscript = dictationBaseText.takeIf { it.isNotBlank() }
                dictationBaseText = ""
            }
            dictationJob = null
        }
    }
    LaunchedEffect(dictationRecording) {
        while (dictationRecording) {
            dictationElapsedMs = android.os.SystemClock.elapsedRealtime() - dictationStartedAt
            kotlinx.coroutines.delay(200)
        }
    }
    val requestMicPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!speechInputAvailable) {
                dictationError = sttResolution.unavailableReason ?: "Speech input is unavailable."
            } else if (granted) {
                startInlineDictation()
            } else {
                dictationError =
                    "Microphone access is off. Allow it in Android Settings → Apps → Vervan → Permissions."
            }
        }
    val requestHandsFreePermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                handsFreeActive = true
            } else {
                attachmentError =
                    "Microphone access is off. Allow it in Android Settings → Apps → Vervan → Permissions."
            }
        }
    val requestRecordPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!speechInputAvailable) {
                attachmentError = sttResolution.unavailableReason ?: "Speech input is unavailable."
            } else if (granted) startVoiceMessageRecording()
            else attachmentError =
                "Microphone access is off. Allow it in Android Settings → Apps → Vervan → Permissions."
        }
    var initialActionHandled by rememberSaveable(chatId, initialAction) { mutableStateOf(false) }
    LaunchedEffect(handsFreeActive, voiceController, voiceReplyMode) {
        if (handsFreeActive) {
            voiceController.setSpeechOutputEnabled(voiceReplyMode != "NEVER")
            voiceController.start(scope)
        }
    }
    LaunchedEffect(speechInputAvailable) {
        if (!speechInputAvailable && handsFreeActive) {
            immersiveVoiceActive = false
            voiceController.stop()
            handsFreeActive = false
        }
    }
    DisposableEffect(voiceController) {
        onDispose { voiceController.stop() }
    }
    LaunchedEffect(
        chatId, initialAction, initialActionHandled, modelLoadState, selectedGenerationModel?.id
    ) {
        if (!initialActionHandled) {
            if (initialAction == "voice" && (modelLoadState is ChatViewModel.ModelLoadState.NotLoaded || modelLoadState is ChatViewModel.ModelLoadState.Loading)) return@LaunchedEffect
            if (initialAction == "handsfree") {
                val selected = selectedGenerationModel ?: return@LaunchedEffect
                val startupResolution = com.vervan.chat.voice.SttEnginePolicy.resolve(
                    app, modelSupportsAudio = selected.supportsAudio != false
                )
                if (!startupResolution.isAvailable) {
                    initialActionHandled = true
                    attachmentError =
                        startupResolution.unavailableReason ?: "Speech input is unavailable."
                    return@LaunchedEffect
                }
            }
            initialActionHandled = true
            when (initialAction) {
                "image" -> pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                "voice" -> requestRecordPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                "handsfree" -> requestHandsFreePermission.launch(android.Manifest.permission.RECORD_AUDIO)
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
                if (copied == null) attachmentError =
                    "Couldn’t prepare that shared image. Try attaching it from the gallery."
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
    val latestDictationRecorder by rememberUpdatedState(dictationRecorder)
    val latestDictationJob by rememberUpdatedState(dictationJob)
    // Only the in-progress recorder is screen-scoped. Generation cancellation, empty/incognito
    // chat purge, and attachment-file cleanup all live in ChatViewModel.onCleared(), which fires
    // when the chat's back-stack entry is actually popped — composition dispose also happens on
    // *forward* navigation (Chat Info, branch tree, a document), where cancelling the stream or
    // deleting unsent attachments was wrong.
    DisposableEffect(chatId) {
        onDispose {
            latestRecorder?.cancel()
            latestDictationRecorder?.cancel()
            latestDictationJob?.cancel()
        }
    }
    // Auto-read-aloud speaks via the same Piper/Kokoro engine chain as realtime voice chat —
    // Android's system TTS is deliberately never used anywhere in this app. If no offline voice
    // is downloaded yet, TtsEngineSelector.resolve() returns null and TtsPlaybackQueue silently
    // skips synthesis rather than falling back to a device voice.
    val ttsEngineSelector = remember {
        com.vervan.chat.voice.TtsEngineSelector(
            app.container.settingsRepository,
            com.vervan.chat.voice.PiperTtsEngine(app.container.db.ttsVoiceModelDao()),
            com.vervan.chat.voice.KokoroTtsEngine(app.container.db.ttsVoiceModelDao()),
            com.vervan.chat.voice.SupertonicTtsEngine(
                app.container.db.ttsVoiceModelDao(), app.container.settingsRepository
            )
        )
    }
    val autoReadQueue =
        remember { com.vervan.chat.voice.TtsPlaybackQueue(app, ttsEngineSelector, scope) }
    DisposableEffect(Unit) {
        onDispose {
            // release() is suspend (it joins the playback coroutine before releasing the native
            // AudioTrack — see its doc comment) and onDispose isn't a suspend context; `scope`
            // (rememberCoroutineScope, tied to this same composable) would itself be cancelling
            // right now, so use the app-lifetime scope instead to let this actually finish.
            app.applicationScope.launch(Dispatchers.Default) { autoReadQueue.release() }
        }
    }
    fun speakAloud(text: String) {
        autoReadQueue.startTurn()
        val chunker = com.vervan.chat.voice.SentenceChunker { sentence ->
            autoReadQueue.enqueue(com.vervan.chat.voice.markdownToSpeechText(sentence))
        }
        chunker.append(text)
        chunker.flush()
        autoReadQueue.endTurn()
    }

    var autoReadBaselineReady by remember(chatId) { mutableStateOf(false) }
    var lastAutoReadId by remember(chatId) { mutableStateOf<String?>(null) }
    // Tracks the assistant message currently being streamed to TTS, plus how much of its
    // (thinking/clarification-stripped) spoken text has already been fed to the chunker — lets
    // each LaunchedEffect firing (one per streamed token batch, since it's keyed on `messages`)
    // enqueue only the new delta, so TTS starts speaking sentence 1 while the LLM is still
    // generating the rest instead of waiting for MessageState.COMPLETE.
    var speakingMessageId by remember(chatId) { mutableStateOf<String?>(null) }
    var spokenSoFar by remember(chatId) { mutableStateOf("") }
    var activeChunker by remember(chatId) {
        mutableStateOf<com.vervan.chat.voice.SentenceChunker?>(
            null
        )
    }
    LaunchedEffect(chatId) {
        val chatRow = app.container.db.chatDao().getChat(chatId)
        val stored = app.container.db.messageDao().getMessages(chatId)
        lastAutoReadId =
            com.vervan.chat.data.branch.BranchUtil.pathTo(stored, chatRow?.activeLeafId)
                .lastOrNull { it.role == MessageRole.ASSISTANT && it.state == MessageState.COMPLETE }?.id
        autoReadBaselineReady = true
    }
    LaunchedEffect(autoReadAloud, messages, autoReadBaselineReady) {
        val last = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
        if (!autoReadBaselineReady || last == null) return@LaunchedEffect
        if (!autoReadAloud) {
            if (speakingMessageId != null) {
                autoReadQueue.stop()
                activeChunker = null
                speakingMessageId = null
                spokenSoFar = ""
            }
            lastAutoReadId = last.id
            return@LaunchedEffect
        }
        if (last.id == lastAutoReadId) return@LaunchedEffect
        if (last.id != speakingMessageId) {
            autoReadQueue.startTurn()
            activeChunker = com.vervan.chat.voice.SentenceChunker { sentence ->
                autoReadQueue.enqueue(com.vervan.chat.voice.markdownToSpeechText(sentence))
            }
            speakingMessageId = last.id
            spokenSoFar = ""
        }
        val fullSpoken = assistantSpokenText(last.content)
        val delta =
            if (fullSpoken.startsWith(spokenSoFar)) fullSpoken.removePrefix(spokenSoFar) else fullSpoken
        spokenSoFar = fullSpoken
        if (delta.isNotEmpty()) activeChunker?.append(delta)
        if (last.state == MessageState.COMPLETE) {
            activeChunker?.flush()
            autoReadQueue.endTurn()
            activeChunker = null
            speakingMessageId = null
            lastAutoReadId = last.id
        }
    }

    fun sendPendingMessage(): Boolean {
        val documentReady = pendingDocument is ChatViewModel.DocumentAttachState.Ready
        val canSend =
            (draft.isNotBlank() || pendingImagePath != null || pendingOcrImagePath != null || pendingQrImagePath != null || pendingAudioPath != null || draftVoiceRecordingPath != null || documentReady) && modelLoadState is ChatViewModel.ModelLoadState.Ready && !isWorkspaceArchived && draft.length <= 12_000
        if (!canSend) return false

        val quotePrefix = pendingQuote?.let { quoted ->
            quoted.lineSequence().joinToString("\n") { "> $it" } + "\n\n"
        }.orEmpty()
        val attached = vm.consumeAttachments()
        val audioForSend = attached.audioPath ?: draftVoiceRecordingPath
        val ocrText = attached.ocrText?.takeIf { it.isNotBlank() }
        val qrText = attached.qrText?.takeIf { it.isNotBlank() }
        val bodyBase = draft.ifBlank {
            when {
                documentReady -> "Describe this document."
                attached.imagePath != null && audioForSend != null -> "Analyze the attached image and audio together."

                attached.imagePath != null -> "Describe this image."
                audioForSend != null -> "Transcribe and respond to the attached audio."
                else -> draft
            }
        }
        val body = withScannedAttachmentsPrefix(bodyBase, ocrText, qrText)
        val documentId = (pendingDocument as? ChatViewModel.DocumentAttachState.Ready)?.documentId
        val sendModality = when {
            attached.imagePath != null && audioForSend != null -> "IMAGE_AUDIO"
            draftInputModality != "TEXT" -> draftInputModality
            audioForSend != null -> "AUDIO_FILE"
            else -> "TEXT"
        }
        val voicePathForDisplay = draftVoiceRecordingPath ?: audioForSend

        draft = ""
        pendingQuote = null
        vm.clearPendingDocument()
        stickToBottom = true
        val transcriptMetadata = draftOriginalTranscript?.let { original ->
            org.json.JSONObject().put("originalTranscript", original).put("submittedText", body)
                .put("edited", original.trim() != body.trim()).put("sttModel", draftSttLabel)
                .put("audioReference", draftVoiceRecordingPath).toString()
        }
        vm.send(
            quotePrefix + body,
            attached.imagePath,
            audioForSend,
            documentId,
            inputModality = sendModality,
            transcriptMetadataJson = transcriptMetadata,
            voiceRecordingPath = voicePathForDisplay
        )
        draftInputModality = "TEXT"
        draftOriginalTranscript = null
        draftVoiceRecordingPath = null
        draftSttLabel = null
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

    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val chatDetailsContentDescription = stringResource(R.string.chat_details)
    val chatGeneratingContentDescription = stringResource(R.string.chat_generating_title)
    val chatMessageContentDescription = stringResource(R.string.chat_message)

    Scaffold(
        // The chat content owns the IME inset below. Keeping Scaffold's default system-bar
        // insets here as well double-counts the bottom inset when the keyboard is visible and
        // leaves a large, empty band between the composer and the keyboard.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = {
                Column(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onOpenChatInfo)
                        .semantics {
                            contentDescription = chatDetailsContentDescription
                        }, verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "CONVERSATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OverflowTooltipText(
                        text = chat?.title ?: "New conversation",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // A subtitle-colored line of text alone was too easy to miss as
                        // "something is happening" — a knowledge-base search can take a
                        // moment, and without a spinner the only signal was small text that
                        // looked identical in weight to the normal "Private · on device" idle
                        // subtitle it replaces.
                        if (isRetrieving || isRecallingMemory) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(end = Space.xs),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            when {
                                isRetrieving -> "Searching knowledge base…"
                                isRecallingMemory -> "Recalling memories…"
                                chat?.isTemporary == true -> "Incognito · deletes when you leave"
                                selectedGenerationModel == null -> "No generation model selected"
                                // Saying "on device" for an engine that runs off it would claim the
                                // opposite of what's happening — this is a privacy statement, not
                                // decoration.
                                modelLoadState is ChatViewModel.ModelLoadState.Ready -> {
                                    val ready = modelLoadState as ChatViewModel.ModelLoadState.Ready
                                    if (modelRunsOnDevice) "Ready · ${ready.backend} · on device" else "Ready · via API"
                                }

                                modelRunsOnDevice -> "Private · on device"
                                else -> "Remote model · via API"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isGenerating || isRetrieving || isRecallingMemory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }, navigationIcon = {
                IconButton(onClick = { vm.closeEmptyDraft(onBack) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }, actions = {
                val activeModel =
                    chat?.modelId?.let { id -> generationModels.firstOrNull { it.id == id } }
                        ?: generationModels.firstOrNull { it.isActive }
                // The model name/switcher lives in the context strip below the bar (and in
                // "Mode & model"), not here — the top bar was too cramped with the pill taking
                // roughly a third of its width on a phone.
                // Keep the frequently changed chat controls directly accessible.
                val grounded = chat?.sourceGrounded == true
                IconButton(onClick = { showSourcePicker = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = stringResource(R.string.chat_sources),
                        tint = if (grounded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (titleGenerating) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = chatGeneratingContentDescription
                                liveRegion = LiveRegionMode.Polite
                            }, contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                var showOverflow by remember { mutableStateOf(false) }
                var showMoreSheet by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    // WhatsApp-style: the dropdown holds only the handful of everyday actions,
                    // so it never runs off the screen. Everything else lives in an organized,
                    // sectioned "More options" bottom sheet one tap away.
                    DropdownMenu(
                        expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                        // Search first, then incognito right below it (moved out of the "More
                        // options" sheet — a per-session privacy switch is everyday enough to
                        // live at the top level, not a tap further in), then the rest, then
                        // "More options" last.
                        DropdownMenuItem(text = { Text(stringResource(R.string.chat_find_conversation)) }, leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }, onClick = { showOverflow = false; showSearch = true })
                        DropdownMenuItem(
                            text = { Text(stringResource(if (chat?.isTemporary == true) R.string.chat_turn_incognito_off else R.string.chat_turn_incognito_on)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = { vm.toggleTemporary(); showOverflow = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.chat_details)) }, leadingIcon = {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }, onClick = { showOverflow = false; showChatStats = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.chat_mode_model)) }, leadingIcon = {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }, onClick = { showOverflow = false; showModeSettings = true })
                        DropdownMenuItem(
                            text = { Text(stringResource(if (chat?.pinned == true) R.string.chat_unpin else R.string.chat_pin)) },
                            leadingIcon = {
                                Icon(
                                    if (chat?.pinned == true) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = { vm.togglePin(); showOverflow = false })
                        DropdownMenuItem(
                            text = { Text(stringResource(if (chat?.archived == true) R.string.chat_unarchive else R.string.chat_archive)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = { vm.toggleArchive(); showOverflow = false })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_more)) }, leadingIcon = {
                            Icon(
                                Icons.Filled.MoreHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }, onClick = { showOverflow = false; showMoreSheet = true })
                    }
                }
                if (showMoreSheet) {
                    ChatMoreOptionsSheet(
                        hasAssistantReply = allMessages.any { it.role == MessageRole.ASSISTANT },
                        canGenerateTitle = generationModels.isNotEmpty() && !titleGenerating,
                        hasPreviousTitle = chat?.previousTitle != null,
                        savedResponsesCount = chatSavedOutputs.size,
                        onDismiss = { showMoreSheet = false },
                        onRename = { showMoreSheet = false; showRenameDialog = true },
                        onGenerateTitle = { showMoreSheet = false; vm.generateTitle() },
                        onRestoreTitle = { showMoreSheet = false; vm.restorePreviousTitle() },
                        onSavedResponses = { showMoreSheet = false; showSavedResponses = true },
                        onBranchTree = { showMoreSheet = false; onOpenBranchTree() },
                        onContextInspector = {
                            showMoreSheet = false; scope.launch {
                            contextBreakdown = vm.inspectContext(draft)
                        }
                        },
                        toolsAvailable = activeModel?.supportsTools != false,
                        onChatTools = { showMoreSheet = false; showChatTools = true },
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
                                context.startActivity(
                                    Intent.createChooser(
                                        send, "Export chat as Markdown"
                                    )
                                )
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
                                context.startActivity(
                                    Intent.createChooser(
                                        send, "Export chat as PDF"
                                    )
                                )
                            }
                        },
                        onResetSettings = { showMoreSheet = false; showResetConfirm = true },
                        onDelete = { showMoreSheet = false; showDeleteConfirm = true },
                    )
                }
                if (showModeSettings) {
                    val defaultTemperature by app.container.settingsRepository.temperature.collectAsState(
                        initial = 0.8f
                    )
                    val defaultTopP by app.container.settingsRepository.topP.collectAsState(
                        initial = 0.95f
                    )
                    val defaultTopK by app.container.settingsRepository.topK.collectAsState(
                        initial = 40
                    )
                    ModeSettingsDialog(
                        thinkingMode = chat?.thinkingMode,
                        modelDefaultThinkingMode = activeModel?.defaultThinkingMode,
                        thinkingAvailable = activeModel?.supportsThinking != false,
                        currentProfile = chat?.profile ?: "BALANCED",
                        onThinkingChange = { vm.setThinkingMode(it) },
                        onProfileChange = { vm.setProfile(it) },
                        onOpenModelPicker = {
                            showModeSettings = false; showModelPicker = true
                        },
                        onOpenPersonaPicker = {
                            showModeSettings = false; showPersonaPicker = true
                        },
                        temperature = chat?.temperature,
                        topP = chat?.topP,
                        topK = chat?.topK,
                        defaultTemperature = defaultTemperature,
                        defaultTopP = defaultTopP,
                        defaultTopK = defaultTopK,
                        onTemperatureChange = { vm.setTemperatureOverride(it) },
                        onTopPChange = { vm.setTopPOverride(it) },
                        onTopKChange = { vm.setTopKOverride(it) },
                        onDismiss = { showModeSettings = false })
                }
            })
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(padding)
                .imePadding()
        ) {
            // Keep the context meter visually attached to the app bar. It is intentionally
            // text-free; the accessible description and the Context chip below expose the exact
            // accounting without competing with the conversation.
            run {
                val defaultContextLimit by app.container.settingsRepository.contextTokenLimit.collectAsState(
                    initial = 4096
                )
                val includePastThinking by app.container.settingsRepository.includePastThinkingInContext.collectAsState(
                    initial = false
                )
                // Prefer the resolved model's actual context window over the app-wide default —
                // the old version measured the whole branch against the global setting, so a chat
                // that would fit fine after history trimming could still read a scary "400%".
                // `selectedGenerationModel` (not a local chat?.modelId lookup) is what actually
                // resolves the same chat→folder-default→loaded-model chain generation itself uses
                // (see ChatViewModel.resolveGenerationModel) — the old local lookup skipped the
                // folder-default and currently-loaded rungs, so a per-model context override only
                // showed up here when the model also happened to be the global "Default" one.
                val contextLimit = selectedGenerationModel?.contextTokens ?: defaultContextLimit
                val estimatedTokens =
                    messages
                        .filterNot {
                            it.state == MessageState.STREAMING ||
                                (it.role == MessageRole.ASSISTANT &&
                                    (it.state == MessageState.CANCELLED || it.state == MessageState.FAILED))
                        }
                        .sumOf {
                            com.vervan.chat.llm.estimateTokens(
                                ChatFormatting.contextMessageContent(it, includePastThinking)
                            )
                        }
                val contextPercent =
                    if (contextLimit > 0) (estimatedTokens * 100 / contextLimit).coerceIn(
                        0, 100
                    ) else 0
                ChatContextProgressBar(
                    contextTokens = estimatedTokens,
                    contextLimit = contextLimit,
                    contextPercent = contextPercent,
                )
                ChatContextStrip(
                    workspaceName = workspace?.name,
                    folderName = folder?.name,
                    personaName = persona?.name,
                    modelName = activeModelName?.substringBefore(" · "),
                    thinkingMode = com.vervan.chat.llm.ThinkingPolicy.effectiveThinkingMode(
                        chat?.thinkingMode,
                        selectedGenerationModel?.defaultThinkingMode,
                        selectedGenerationModel?.supportsThinking
                    ).takeIf { it != "OFF" },
                    sourceCount = chat?.kbIdList()?.size?.takeIf { chat?.sourceGrounded == true && it > 0 },
                    contextTokens = estimatedTokens,
                    contextLimit = contextLimit,
                    contextPercent = contextPercent,
                    onWorkspaceClick = { showWorkspaceOptions = true },
                    onFolderClick = onOpenFolders,
                    onPersonaClick = { showPersonaPicker = true },
                    // Model chip switches the model directly (the everyday action) instead of
                    // routing through the Mode & model dialog first — that dialog is still one tap
                    // away in the overflow menu for thinking-mode/profile/sampler changes.
                    onModelClick = { showModelPicker = true },
                    onSourcesClick = { showSourcePicker = true },
                    onContextClick = {
                        scope.launch {
                            contextBreakdown = vm.inspectContext(draft)
                        }
                    })
            }
            ModelReadinessPanel(
                state = modelLoadState,
                modelRunsOnDevice = modelRunsOnDevice,
                onLoad = vm::retryModelLoad,
                onOpenModels = onOpenModels
            )
            // Model Loading Strategy — a distinct, non-alarming indicator during
            // active generation when the device is thermally throttling. Only SEVERE is shown:
            // ELEVATED (THERMAL_STATUS_MODERATE) is common during normal sustained generation and
            // doesn't mean the device is hot to the touch — see HomeAlert, which applies the same
            // SEVERE-only threshold.
            if (isGenerating) {
                val thermalLevel by app.container.thermalMonitor.level.collectAsState()
                if (thermalLevel == com.vervan.chat.system.ThermalLevel.SEVERE) {
                    ThermalNotice()
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
                    })
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)
                        .align(Alignment.Center),
                    // The composer owns its own vertical padding; keep only a compact list tail so
                    // the final response does not float unnecessarily far above the input field.
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Space.lg, top = Space.xs, end = Space.lg, bottom = Space.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    if (messages.isEmpty()) {
                        item(key = "empty-chat") {
                            ChatEmptyState(
                                personaName = persona?.name,
                                modelName = activeModelName?.substringBefore(" · "),
                                modelRunsOnDevice = modelRunsOnDevice,
                                // A bounded empty-state rhythm keeps the composer visible and
                                // avoids the unavailable LazyItemScope fillParentMaxHeight API.
                                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
                                onSuggestion = { suggestion ->
                                    draft = suggestion
                                    if (draftLoaded) vm.saveDraft(suggestion)
                                })
                        }
                    }
                    // Hoisted out of the per-item lambdas: each of these was an O(n) list scan (or,
                    // for siblingPosition, two O(n) scans) per visible item per recomposition — and
                    // streaming recomposes this list every ~80ms, so long/branchy chats janked
                    // exactly when the app was busiest. Same fix as lastCompleteAssistantId/
                    // lastMessageId below, extended to the other per-item lookups that had the same
                    // shape: build the lookup structure once per list recomposition, not once per
                    // rendered row.
                    val lastCompleteAssistantId =
                        messages.lastOrNull { it.role == MessageRole.ASSISTANT && it.state == MessageState.COMPLETE }?.id
                    val lastMessageId = messages.lastOrNull()?.id
                    val documentsById = documents.associateBy { it.id }
                    val savedOutputsByLabel =
                        chatSavedOutputs.filter { it.label.isNotBlank() }.associateBy { it.label }
                    val blankLabelSavedOutputsByContent =
                        chatSavedOutputs.filter { it.label.isBlank() }.associateBy { it.content }
                    val siblingPositions =
                        com.vervan.chat.data.branch.BranchUtil.siblingPositions(allMessages)
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
                            var betterModelName by remember(message.id) {
                                mutableStateOf<String?>(
                                    null
                                )
                            }
                            LaunchedEffect(message.id, message.reaction) {
                                betterModelName =
                                    if (message.reaction == "👎") vm.suggestBetterModel(message)?.displayName else null
                            }
                            MessageBubble(
                                // Placement animation smooths branch switches and regenerations,
                                // which otherwise snap the whole list into its new shape — but NOT
                                // while this exact message is the one streaming: its height changes on
                                // every persisted chunk (STREAM_PERSIST_INTERVAL_MS, 80ms), and
                                // animateItem() is built for reorder/insert/remove transitions, not a
                                // continuously growing item. Animating that growth is what read as
                                // flickering on a fast model — the item visibly overshoots and snaps
                                // back on every single update instead of just getting taller.
                                //
                                // animateItem() itself must stay on this item's modifier chain every
                                // frame regardless — disable the animation via placementSpec = null
                                // instead of adding/removing the modifier between recompositions.
                                // Toggling the modifier off then back on right as this exact item's
                                // content changes the most (streaming finishes, citations attach, the
                                // item's height jumps) corrupted LazyColumn's internal per-item
                                // placement bookkeeping — its deferred placement callback for the
                                // stale pre-toggle node fired on a later frame against an
                                // already-detached LayoutNode, crashing with "LayoutNode should be
                                // attached to an owner" (most visible in a document chat: source
                                // citations attaching right as the message finishes is exactly this
                                // "content jumps at the streaming→complete boundary" case).
                                modifier = if (message.state == MessageState.STREAMING) {
                                    Modifier.animateItem(placementSpec = null)
                                } else {
                                    Modifier.animateItem()
                                },
                                message = message,
                                attachedDocument = message.documentId?.let { id -> documentsById[id] },
                                savedOutput = savedOutputsByLabel[message.id]
                                    ?: blankLabelSavedOutputsByContent[message.content],
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
                                onReadAloud = { text, _ -> speakAloud(text) },
                                isGenerating = isGenerating,
                                showStreamingStatus = !handsFreeActive,
                                siblingPosition = siblingPositions[message.id] ?: (1 to 1),
                                onConfirmTool = { approve ->
                                    vm.confirmToolCall(
                                        message.id, approve
                                    )
                                },
                                onEditAndResend = { newText ->
                                    vm.editAndResend(
                                        message.id, newText
                                    )
                                },
                                onRegenerate = { vm.regenerate(message.id) },
                                onSwitchBranch = { direction ->
                                    val siblings =
                                        com.vervan.chat.data.branch.BranchUtil.siblingsOf(
                                            allMessages, message.id
                                        )
                                    val index = siblings.indexOfFirst { it.id == message.id }
                                    val targetIndex = index + direction
                                    if (targetIndex in siblings.indices) vm.switchBranch(siblings[targetIndex].id)
                                },
                                onCompare = { compareMessageId = message.id },
                                onFork = { scope.launch { onForkChat(vm.forkChat(message.id)) } },
                                onOpenPassage = { chunkId -> onOpenPassage(chunkId) },
                                onOpenPdfPage = { documentId, page ->
                                    onOpenPdfPage(
                                        documentId, page
                                    )
                                },
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
                                betterModelName = betterModelName,
                                modelRunsOnDevice = modelRunsOnDevice
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
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(
                        initialScale = 0.8f
                    ),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(
                        targetScale = 0.8f
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Space.md)
                ) {
                    com.vervan.chat.ui.common.VervanSmallFloatingActionButton(
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
            title = stringResource(R.string.chat_generation_error),
                    body = it,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.md, vertical = Space.xs)
                        .semantics { liveRegion = LiveRegionMode.Polite })
            }
            attachmentError?.let {
                com.vervan.chat.ui.common.OperationErrorCard(
            title = stringResource(R.string.chat_attachment_error),
                    message = it,
                    recovery = stringResource(R.string.ui_chat_message_recovery),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.md, vertical = Space.xs),
            actionLabel = stringResource(R.string.action_close),
                    onAction = { attachmentError = null })
            }

            val templates by app.container.db.promptTemplateDao().observeAll()
                .collectAsState(initial = emptyList())
            val matchingCommands = if (draft.startsWith("/") && !draft.contains(" ")) {
                templates.filter { it.name.startsWith(draft.removePrefix("/"), ignoreCase = true) }
            } else emptyList()
            if (matchingCommands.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 840.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = Space.lg)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    matchingCommands.forEach { template ->
                        AssistChip(
                            onClick = { draft = "/${template.name} "; vm.saveDraft(draft) },
                            shape = MaterialTheme.shapes.small,
                            label = { Text("/${template.name}") })
                    }
                }
            }
            // Quote reply and a pending attachment are both "context attached to the next
            // message" — grouped into one composing tray instead of two separately-floating
            // rows so they read as one unit sitting above the composer, not a growing stack.
            if (pendingQuote != null || pendingImagePath != null || pendingOcrImagePath != null || pendingQrImagePath != null || pendingAudioPath != null || pendingDocument != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 840.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = Space.lg, vertical = Space.xs)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                ) {
                    pendingDocument?.let { docState ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = docState is ChatViewModel.DocumentAttachState.Ready,
                                    role = Role.Button,
                                    onClick = {
                                        (docState as? ChatViewModel.DocumentAttachState.Ready)?.let {
                                            onOpenDocument(
                                                it.documentId
                                            )
                                        }
                                    })
                                .padding(Space.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (docState) {
                                is ChatViewModel.DocumentAttachState.Importing -> {
                                    // Embedding is the one stage slow enough to want a real number
                                    // (see JobProgressCard's use on the Knowledge Base screen for
                                    // the same signal) — copy/extract/chunk stay an indeterminate
                                    // spinner since they're normally near-instant.
                                    val embedding = attachEmbedProgress
                                    if (embedding != null) {
                                        CircularProgressIndicator(
                                            progress = {
                                                embedding.done.toFloat() / embedding.total.coerceAtLeast(
                                                    1
                                                )
                                            }, modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            Modifier.size(18.dp), strokeWidth = 2.dp
                                        )
                                    }
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(start = Space.sm)
                                    ) {
                                        Text(
                                            "Preparing \"${docState.name}\"",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            if (embedding != null) "Embedding ${embedding.done} of ${embedding.total} chunks…"
                                            else "Copying and indexing locally. Large files may take a few minutes.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                is ChatViewModel.DocumentAttachState.Ready -> {
                                    Icon(
                                        Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(start = Space.sm)
                                    ) {
                                        Text(
                                            docState.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            if (docState.grounded) "Indexed — semantic search" else "Indexed — keyword search only",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { vm.clearPendingDocument() }) {
                                        Icon(
                                            Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                is ChatViewModel.DocumentAttachState.Failed -> {
                                    Icon(
                                        Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(start = Space.sm)
                                    ) {
                                        Text(
                                            "Could not attach \"${docState.name}\"",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            docState.reason,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            "Choose another file or try again.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { vm.clearPendingDocument() }) {
                                        Icon(
                                            Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (pendingQuote != null || pendingImagePath != null || pendingOcrImagePath != null || pendingQrImagePath != null || pendingAudioPath != null) HorizontalDivider()
                    }
                    pendingQuote?.let { quoted ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(Space.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = Space.sm)
                            ) {
                                Text(
                                    "Replying to",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    quoted,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { pendingQuote = null }) {
                                Icon(
                                    Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chat_cancel_reply),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    if (pendingQuote != null && (pendingImagePath != null || pendingOcrImagePath != null || pendingQrImagePath != null || pendingAudioPath != null)) {
                        HorizontalDivider()
                    }
                    pendingOcrImagePath?.let { path ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(Space.sm)
                                .clickable { showOcrPreview = true }) {
                            val thumbnailPx = with(LocalDensity.current) { 720.dp.roundToPx() }
                            val bitmap = rememberThumbnail(path, thumbnailPx)
                            bitmap?.let {
                                Image(
                                    it,
                        contentDescription = stringResource(R.string.chat_ocr_preview),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 140.dp, max = 220.dp)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(Space.sm),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                                contentColor = MaterialTheme.colorScheme.inverseOnSurface
                            ) {
                                Text(
                                    stringResource(
                                        if (pendingOcrText.isNullOrBlank()) R.string.chat_ocr_no_text
                                        else R.string.chat_ocr_view_text
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(
                                        horizontal = Space.sm, vertical = Space.xs
                                    )
                                )
                            }
                            IconButton(
                                onClick = { vm.setPendingOcr(null, null) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(Space.xs)
                                    .background(
                                        MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f),
                                        MaterialTheme.shapes.small
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chat_remove_ocr),
                                    tint = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                        }
                    }
                    pendingQrImagePath?.let { path ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(Space.sm)
                                .clickable { showQrPreview = true }) {
                            val thumbnailPx = with(LocalDensity.current) { 720.dp.roundToPx() }
                            val bitmap = rememberThumbnail(path, thumbnailPx)
                            bitmap?.let {
                                Image(
                                    it,
                        contentDescription = stringResource(R.string.chat_qr_preview),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 140.dp, max = 220.dp)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(Space.sm),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                                contentColor = MaterialTheme.colorScheme.inverseOnSurface
                            ) {
                                Text(
                                    stringResource(
                                        if (pendingQrText.isNullOrBlank()) R.string.chat_qr_no_code
                                        else R.string.chat_qr_view_text
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(
                                        horizontal = Space.sm, vertical = Space.xs
                                    )
                                )
                            }
                            IconButton(
                                onClick = { vm.setPendingQr(null, null) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(Space.xs)
                                    .background(
                                        MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f),
                                        MaterialTheme.shapes.small
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chat_remove_qr),
                                    tint = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                        }
                    }
                    pendingImagePath?.let { path ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 92.dp)
                                .padding(Space.sm)
                                .clickable { showPendingImagePreview = true }) {
                            val thumbnailPx = with(LocalDensity.current) { 720.dp.roundToPx() }
                            val bitmap = rememberThumbnail(path, thumbnailPx)
                            bitmap?.let {
                                Image(
                                    it,
                        contentDescription = stringResource(R.string.chat_attached_image_preview),
                                    modifier = Modifier
                                        .size(84.dp)
                                        .align(Alignment.CenterStart)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 96.dp, end = 44.dp),
                                shape = MaterialTheme.shapes.small,
                                color = androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Column {
                            Text(stringResource(R.string.chat_photo), style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        "Ready to send · Tap to preview",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { vm.setPendingImage(null) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(Space.xs)
                                    .background(
                                        MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f),
                                        MaterialTheme.shapes.small
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chat_remove_image),
                                    tint = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                        }
                    }
                    pendingAudioPath?.let { path ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(Space.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Audio attachment", style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    "Ready to send with your message",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                VoiceMessageRow(path)
                            }
                TextButton(onClick = { discardDraftVoiceAttachment() }) { Text(stringResource(R.string.action_remove)) }
                        }
                    }
                }
            }
            val composerEnabled =
                modelLoadState is ChatViewModel.ModelLoadState.Ready && !isWorkspaceArchived
            activityLabel?.let { label ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.lg, vertical = Space.xs),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    ActivityStatusPill(label = label, onClick = onOpenActivity)
                }
            }
            if (handsFreeActive) {
                IntegratedVoicePanel(
                    state = voiceState,
                    waveform = voiceWaveform,
                    elapsedMs = voiceElapsedMs,
                    liveTranscript = voiceLiveTranscript,
                    modelName = activeModelName?.substringBefore(" · ") ?: voiceLoadingModelName,
                    sttLabel = voiceSttLabel,
                    ttsLabel = voiceTtsLabel,
                    microphoneMuted = voiceMicrophoneMuted,
                    speechOutputEnabled = voiceSpeechOutputEnabled,
                    playbackPaused = voicePlaybackPaused,
                    hasEchoCancellation = voiceHasEchoCancellation,
                    attachmentLabel = when {
                        pendingDocument is ChatViewModel.DocumentAttachState.Importing -> (pendingDocument as ChatViewModel.DocumentAttachState.Importing).name + " · preparing"

                        pendingDocument is ChatViewModel.DocumentAttachState.Ready -> (pendingDocument as ChatViewModel.DocumentAttachState.Ready).name

                        pendingDocument is ChatViewModel.DocumentAttachState.Failed -> (pendingDocument as ChatViewModel.DocumentAttachState.Failed).name + " · failed"

                        pendingImagePath != null -> "Photo ready"
                        pendingAudioPath != null -> "Audio file ready"
                        pendingOcrImagePath != null || pendingQrImagePath != null -> "Scanned text ready"
                        else -> null
                    },
                    errorMessage = voiceModelLoadError ?: if (voiceSttUnavailable) {
                        "Offline speech input isn’t ready. Download a voice model or use the keyboard."
                    } else null,
                    onStart = { requestHandsFreePermission.launch(android.Manifest.permission.RECORD_AUDIO) },
                    onFinishUtterance = voiceController::finishListening,
                    onCancelUtterance = voiceController::cancelCurrentUtterance,
                    onInterrupt = voiceController::manualInterrupt,
                    onTogglePlayback = voiceController::togglePlaybackPause,
                    onToggleMute = voiceController::toggleMicrophoneMute,
                    onToggleSpeechOutput = voiceController::toggleSpeechOutput,
                    onAttach = { showAttachmentSheet = true },
                    onKeyboard = {
                        voiceController.stop()
                        handsFreeActive = false
                        immersiveVoiceActive = false
                        voiceSessionKey += 1
                    },
                    onMore = { showVoiceOptions = true },
                    onRetry = {
                        voiceController.stop()
                        voiceSessionKey += 1
                    },
                    onEnd = {
                        voiceController.stop()
                        handsFreeActive = false
                        immersiveVoiceActive = false
                        voiceSessionKey += 1
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = Space.lg, vertical = Space.sm),
                    pushToTalkEnabled = voicePushToTalkEnabled,
                    onTogglePushToTalkMode = onTogglePushToTalkMode,
                    pushToTalkHeld = voicePushToTalkHeld,
                    onPushToTalkPress = voiceController::pushToTalkPress,
                    onPushToTalkRelease = voiceController::pushToTalkRelease
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 840.dp)
                        .align(Alignment.CenterHorizontally)
                        // Keep a clear bottom margin when the IME is hidden while preserving the
                        // tighter keyboard-open spacing. The text editor itself is unchanged.
                        .padding(
                            start = Space.lg,
                            top = Space.sm,
                            end = Space.lg,
                            bottom = if (imeVisible) Space.xs else Space.lg
                        )
                        .alpha(if (composerEnabled || isGenerating) 1f else 0.62f)
                        .animateContentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, VervanExtraShapes.composer)
                        .border(
                            androidx.compose.foundation.BorderStroke(
                                ModernistTokens.Component.rule,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                            ),
                            VervanExtraShapes.composer
                        ),
                    // The dedicated composer shape keeps this input distinct from content cards.
                    // One border system instead of (border + lifted container + 22dp shape) — the
                    // previous composer had three competing emphases. Now: the floating surface role
                    // owns its tint, lift, and emphasized edge.
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(Space.sm)
                    ) {
                        when {
                            dictationRecording -> {
                                InlineDictationRecording(
                                    levels = dictationLevels,
                                    elapsedMs = dictationElapsedMs,
                                    onCancel = {
                                        cancelInlineDictation()
                                        dictationTranscript =
                                            dictationBaseText.takeIf { it.isNotBlank() }
                                        dictationBaseText = ""
                                    },
                                    onStop = ::finishInlineDictation
                                )
                            }

                            dictationTranscribing -> {
                                InlineDictationTranscribing(onCancel = {
                                    cancelInlineDictation()
                                    dictationTranscript =
                                        dictationBaseText.takeIf { it.isNotBlank() }
                                    dictationBaseText = ""
                                })
                            }

                            dictationError != null -> {
                                InlineDictationError(message = dictationError.orEmpty(), onRetry = {
                                    dictationError = null
                                    requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                }, onKeyboard = { dictationError = null })
                            }

                            dictationTranscript != null -> {
                                InlineDictationReview(
                                    transcript = dictationTranscript.orEmpty(),
                                    onTranscriptChange = { dictationTranscript = it },
                                    onRecordMore = { requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO) },
                                    onRetry = {
                                        discardDraftVoiceAttachment()
                                        dictationTranscript = null
                                        dictationOriginalTranscript = null
                                        requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                    },
                                    onCancel = {
                                        discardDraftVoiceAttachment()
                                        dictationTranscript = null
                                        dictationOriginalTranscript = null
                                    },
                                    onUseInComposer = {
                                        val transcript = dictationTranscript.orEmpty().trim()
                                        val hadTypedText = draft.isNotBlank()
                                        draft = listOf(
                                            draft.trim(), transcript
                                        ).filter { it.isNotBlank() }.joinToString(" ")
                                        draftInputModality =
                                            if (hadTypedText) "MIXED" else "VOICE_DICTATION"
                                        draftOriginalTranscript =
                                            dictationOriginalTranscript ?: transcript
                                        vm.saveDraft(draft)
                                        dictationTranscript = null
                                        dictationOriginalTranscript = null
                                    },
                                    onSend = {
                                        val transcript = dictationTranscript.orEmpty().trim()
                                        val hadTypedText = draft.isNotBlank()
                                        draft = listOf(
                                            draft.trim(), transcript
                                        ).filter { it.isNotBlank() }.joinToString(" ")
                                        draftInputModality =
                                            if (hadTypedText) "MIXED" else "VOICE_DICTATION"
                                        draftOriginalTranscript =
                                            dictationOriginalTranscript ?: transcript
                                        vm.saveDraft(draft)
                                        dictationTranscript = null
                                        dictationOriginalTranscript = null
                                        sendPendingMessage()
                                    })
                            }

                            isRecording -> {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Filled.GraphicEq,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(start = Space.md)
                                    ) {
                                        Text(
                                            "Recording for transcription",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            "Your selected STT engine will convert this to text",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(onClick = {
                                        activeRecorder?.cancel(); activeRecorder =
                                        null; isRecording = false
                    }) { Text(stringResource(R.string.action_cancel)) }
                                    TextButton(onClick = {
                                        val recorder = activeRecorder
                                        activeRecorder = null
                                        isRecording = false
                                        scope.launch {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { recorder?.stop() }
                                            recorder?.outputFile?.let { file ->
                                                isImportingAudio = true
                                                com.vervan.chat.voice.OfflineDictationTranscriber.transcribe(
                                                    app, file, selectedGenerationModel?.id
                                                ).onSuccess { transcription ->
                                                    draftVoiceRecordingPath?.let { old ->
                                                        if (old != file.absolutePath) java.io.File(
                                                            old
                                                        ).delete()
                                                    }
                                                    draftVoiceRecordingPath = file.absolutePath
                                                    vm.setPendingAudio(file.absolutePath)
                                                    draftSttLabel = transcription.engineLabel
                                                    val hadTypedText = draft.isNotBlank()
                                                    val combined = listOf(
                                                        draft.trim(),
                                                        transcription.text
                                                    ).filter { it.isNotBlank() }.joinToString(" ")
                                                    draftInputModality =
                                                        if (hadTypedText) "MIXED" else "VOICE_FILE"
                                                    draftOriginalTranscript = transcription.text
                                                    if (transcriptReviewEnabled) {
                                                        dictationTranscript = combined
                                                        dictationOriginalTranscript =
                                                            transcription.text
                                                    } else {
                                                        draft = combined
                                                        vm.saveDraft(draft)
                                                    }
                                                }.onFailure {
                                                    file.delete()
                                                    attachmentError = it.toUserMessage()
                                                }
                                                isImportingAudio = false
                                            }
                                        }
                    }) { Text(stringResource(R.string.action_use)) }
                                }
                            }

                            else -> {
                                // Modern single-row composer: [attach] [field] [/ commands] [mic] [send],
                                // icons anchored to the bottom as the field grows — the WhatsApp/Telegram
                                // layout, replacing the previous two-row field-above-toolbar design that
                                // made the composer read as a form. The commands shortcut yields its slot
                                // once typing starts (typing "/" directly still opens suggestions), so a
                                // long draft gets the width back.
                                if (draft.length >= 9_600) {
                                    Text(
                                        if (draft.length > 12_000) stringResource(R.string.chat_character_limit_exceeded)
                                        else stringResource(R.string.chat_character_limit, draft.length),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (draft.length > 12_000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .padding(horizontal = Space.md, vertical = Space.xs)
                                    )
                                }
                                if (isImportingAudio) {
                                    Row(
                                        Modifier.padding(start = Space.md, bottom = Space.xs),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            Modifier.size(12.dp), strokeWidth = 2.dp
                                        )
                                        Text(
                                            stringResource(R.string.chat_converting_audio),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = Space.sm)
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
                                        Icon(
                                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.chat_open_attachment_options)
                                        )
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = draft,
                                        onValueChange = {
                                            draft = it.take(12_000)
                                            if (draftLoaded) vm.saveDraft(draft)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp, max = 148.dp)
                                            .semantics {
                        contentDescription = chatMessageContentDescription
                                            },
                                        enabled = composerEnabled,
                                        maxLines = 5,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = if (draft.length > 12_000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(
                                            MaterialTheme.colorScheme.primary
                                        ),
                                        decorationBox = { inner ->
                                            Box(
                                                Modifier.padding(
                                                    horizontal = Space.xs, vertical = Space.md
                                                ), contentAlignment = Alignment.CenterStart
                                            ) {
                                                if (draft.isEmpty()) {
                                                    Text(
                                                        if (composerEnabled) stringResource(R.string.chat_message_placeholder)
                                                        else stringResource(R.string.chat_waiting_for_model),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                }
                                                inner()
                                            }
                                        })
                                    if (speechInputAvailable) {
                                        Box {
                                            IconButton(
                                                onClick = { showComposerVoiceMenu = true },
                                                enabled = composerEnabled && !isGenerating
                                            ) {
                                                Icon(
                                                    Icons.Filled.KeyboardVoice,
                        contentDescription = stringResource(R.string.chat_voice_options),
                                                    tint = if (voiceReplyMode == "AUTOMATIC") {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showComposerVoiceMenu,
                                                onDismissRequest = {
                                                    showComposerVoiceMenu = false
                                                }) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.chat_dictate)) },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Filled.KeyboardVoice,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    onClick = {
                                                        showComposerVoiceMenu = false
                                                        requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                                    })
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.chat_start_hands_free)) },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Filled.RecordVoiceOver,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    onClick = {
                                                        showComposerVoiceMenu = false
                                                        requestHandsFreePermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                                    })
                                                DropdownMenuItem(text = {
                                                    Text(
                                                        stringResource(
                                                            R.string.chat_voice_replies_label,
                                                            stringResource(
                                                                when (voiceReplyMode) {
                                                                    "AUTOMATIC" -> R.string.chat_voice_replies_automatic
                                                                    "NEVER" -> R.string.chat_voice_replies_off
                                                                    else -> R.string.chat_voice_replies_manual
                                                                }
                                                            )
                                                        )
                                                    )
                                                }, leadingIcon = {
                                                    Icon(
                                                        if (voiceReplyMode == "NEVER") Icons.AutoMirrored.Filled.VolumeOff
                                                        else Icons.AutoMirrored.Filled.VolumeUp,
                                                        contentDescription = null
                                                    )
                                                }, onClick = {
                                                    val next = when (voiceReplyMode) {
                                                        "NEVER" -> "MANUAL"
                                                        "MANUAL" -> "AUTOMATIC"
                                                        else -> "NEVER"
                                                    }
                                                    scope.launch {
                                                        app.container.settingsRepository.setVoiceReplyMode(
                                                            next
                                                        )
                                                        app.container.settingsRepository.setAutoReadAloud(
                                                            next == "AUTOMATIC"
                                                        )
                                                    }
                                                })
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.chat_voice_settings)) },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Filled.Tune,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    onClick = {
                                                        showComposerVoiceMenu = false
                                                        onOpenVoiceSettings()
                                                    })
                                            }
                                        }
                                    }
                                    val documentReady =
                                        pendingDocument is ChatViewModel.DocumentAttachState.Ready
                                    val canSend =
                                        (draft.isNotBlank() || pendingImagePath != null || pendingOcrImagePath != null || pendingQrImagePath != null || pendingAudioPath != null || documentReady) && composerEnabled && draft.length <= 12_000
                                    val sendActive = canSend || isGenerating
                                    Box(
                                        Modifier
                                            .padding(start = Space.xs)
                                            .size(48.dp)
                                            .clip(MaterialTheme.shapes.small)
                                            .background(
                                                if (sendActive) com.vervan.chat.ui.theme.vervanBrandGradient()
                                                else androidx.compose.ui.graphics.SolidColor(
                                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                                )
                                            ), contentAlignment = Alignment.Center
                                    ) {
                                        if (isGenerating) {
                                            IconButton(onClick = {
                                                if (hapticsEnabled) haptics.performHapticFeedback(
                                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                                )
                                                vm.cancelGeneration()
                                            }) {
                                                Icon(
                                                    Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.chat_stop_generating),
                                                    tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        } else {
                                            IconButton(enabled = canSend, onClick = {
                                                if (hapticsEnabled) haptics.performHapticFeedback(
                                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                                )
                                                sendPendingMessage()
                                            }) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send_message),
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
        }
    }

    if (showAttachmentSheet) {
        ModernChatAttachmentSheet(
            visionAvailable = visionAvailable,
            audioAvailable = speechInputAvailable,
            modelRunsOnDevice = modelRunsOnDevice,
            isImportingAudio = isImportingAudio,
            isRunningOcr = isRunningOcr,
            isRunningQr = isRunningQr,
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
            onQrPhoto = {
                showAttachmentSheet = false
                pickQrImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onQrCamera = {
                showAttachmentSheet = false
                requestQrCameraPermission.launch(android.Manifest.permission.CAMERA)
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
                    // Same fix as KnowledgeBaseDetailScreen's picker — a trailing "*/*" made this
                    // whole allow-list a no-op, letting the user pick anything (only to fail at
                    // extraction). Listing only what the import pipeline actually supports keeps
                    // the system picker itself from offering something doomed to fail.
                    arrayOf(
                        "text/*",
                        "application/pdf",
                        "application/epub+zip",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-powerpoint",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    )
                )
            })
    }

    if (handsFreeActive && immersiveVoiceActive) {
        ImmersiveVoiceDialog(conversationTitle = chat?.title?.takeIf { it.isNotBlank() }
            ?: "Voice conversation",
            modelName = activeModelName?.substringBefore(" · ") ?: voiceLoadingModelName,
            turns = voiceTurns,
            liveTranscript = voiceLiveTranscript,
            onExitImmersive = { immersiveVoiceActive = false }) {
            IntegratedVoicePanel(
                state = voiceState,
                waveform = voiceWaveform,
                elapsedMs = voiceElapsedMs,
                liveTranscript = voiceLiveTranscript,
                modelName = activeModelName?.substringBefore(" · ") ?: voiceLoadingModelName,
                sttLabel = voiceSttLabel,
                ttsLabel = voiceTtsLabel,
                microphoneMuted = voiceMicrophoneMuted,
                speechOutputEnabled = voiceSpeechOutputEnabled,
                playbackPaused = voicePlaybackPaused,
                hasEchoCancellation = voiceHasEchoCancellation,
                attachmentLabel = when {
                    pendingDocument is ChatViewModel.DocumentAttachState.Importing -> (pendingDocument as ChatViewModel.DocumentAttachState.Importing).name + " · preparing"

                    pendingDocument is ChatViewModel.DocumentAttachState.Ready -> (pendingDocument as ChatViewModel.DocumentAttachState.Ready).name

                    pendingImagePath != null -> "Photo ready"
                    pendingAudioPath != null -> "Audio file ready"
                    pendingOcrImagePath != null || pendingQrImagePath != null -> "Scanned text ready"
                    else -> null
                },
                errorMessage = voiceModelLoadError ?: if (voiceSttUnavailable) {
                    "Offline speech input isn’t ready. Download a voice model or use the keyboard."
                } else null,
                onStart = { requestHandsFreePermission.launch(android.Manifest.permission.RECORD_AUDIO) },
                onFinishUtterance = voiceController::finishListening,
                onCancelUtterance = voiceController::cancelCurrentUtterance,
                onInterrupt = voiceController::manualInterrupt,
                onTogglePlayback = voiceController::togglePlaybackPause,
                onToggleMute = voiceController::toggleMicrophoneMute,
                onToggleSpeechOutput = voiceController::toggleSpeechOutput,
                onAttach = { showAttachmentSheet = true },
                onKeyboard = {
                    immersiveVoiceActive = false
                    voiceController.stop()
                    handsFreeActive = false
                    voiceSessionKey += 1
                },
                onMore = { showVoiceOptions = true },
                onRetry = {
                    voiceController.stop()
                    voiceSessionKey += 1
                },
                onEnd = {
                    immersiveVoiceActive = false
                    voiceController.stop()
                    handsFreeActive = false
                    voiceSessionKey += 1
                },
                pushToTalkEnabled = voicePushToTalkEnabled,
                onTogglePushToTalkMode = onTogglePushToTalkMode,
                pushToTalkHeld = voicePushToTalkHeld,
                onPushToTalkPress = voiceController::pushToTalkPress,
                onPushToTalkRelease = voiceController::pushToTalkRelease
            )
        }
    }

    if (showVoiceOptions) {
        ChatVoiceOptionsBottomSheet(
            speechOutputEnabled = voiceSpeechOutputEnabled,
            microphoneMuted = voiceMicrophoneMuted,
            immersiveEnabled = immersiveVoiceActive,
            pushToTalkEnabled = voicePushToTalkEnabled,
            onToggleSpeechOutput = voiceController::toggleSpeechOutput,
            onToggleMute = voiceController::toggleMicrophoneMute,
            onToggleImmersive = { immersiveVoiceActive = !immersiveVoiceActive },
            onTogglePushToTalk = onTogglePushToTalkMode,
            onSwitchModel = { showVoiceOptions = false; showModelPicker = true },
            onOpenSettings = { showVoiceOptions = false; onOpenVoiceSettings() },
            onDismiss = { showVoiceOptions = false })
    }

    selectedDocument?.let { selection ->
        DocumentComposerPreviewDialog(selection = selection, caption = draft, onCaptionChange = {
            draft = it
            if (draftLoaded) vm.saveDraft(it)
        }, onDismiss = { selectedDocument = null }, onSend = {
            selectedDocument = null
            // Clear any older ready attachment before arming auto-send; otherwise replacing
            // a document could briefly satisfy the Ready effect with the previous file.
            vm.clearPendingDocument()
            sendDocumentWhenReady = true
            vm.attachDocument(selection.uri)
        })
    }

    pendingImagePath?.takeIf { showPendingImagePreview }?.let { path ->
        FullScreenImagePreview(
            path = path,
            title = stringResource(R.string.chat_photo_preview),
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
            confirmLabel = stringResource(R.string.chat_send),
            confirmEnabled = modelLoadState is ChatViewModel.ModelLoadState.Ready && !isWorkspaceArchived && !isGenerating && draft.length <= 12_000,
            onConfirm = {
                if (sendPendingMessage()) showPendingImagePreview = false
            })
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
                discardDraftVoiceAttachment()
                showPendingAudioPreview = false
            },
            onSend = {
                if (sendPendingMessage()) showPendingAudioPreview = false
            })
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
            })
    }

    pendingQrImagePath?.takeIf { showQrPreview }?.let { path ->
        OcrPreviewDialog(
            imagePath = path,
            text = pendingQrText.orEmpty(),
            onTextChange = { vm.updateQrText(it) },
            caption = draft,
            onCaptionChange = {
                draft = it
                if (draftLoaded) vm.saveDraft(it)
            },
            confirmEnabled = modelLoadState is ChatViewModel.ModelLoadState.Ready && !isWorkspaceArchived && !isGenerating && draft.length <= 12_000,
            onRemove = {
                vm.setPendingQr(null, null)
                showQrPreview = false
            },
            onDismiss = { showQrPreview = false },
            onSend = {
                if (sendPendingMessage()) showQrPreview = false
            },
            title = stringResource(R.string.media_qr_preview),
            subtitle = stringResource(R.string.media_qr_on_device),
            removeDescription = stringResource(R.string.media_remove_qr))
    }

    if (showRenameDialog) {
        RenameChatDialog(
            initialTitle = chat?.title.orEmpty(),
            onDismiss = { showRenameDialog = false },
            onRename = { vm.rename(it); showRenameDialog = false })
    }

    // Chat Screen-5 — workspace indicator options: view/open, set as the app's active
    // workspace (distinct from just opening it — the chat's own workspace and the global
    // active workspace are independent), or move this chat to a different one. Moving shows a
    // preview before committing.
    var pendingMoveTarget by remember {
        mutableStateOf<com.vervan.chat.data.db.entities.Workspace?>(
            null
        )
    }
    workspace?.let { ws ->
        if (showWorkspaceOptions) {
            WorkspaceOptionsDialog(
                workspace = ws,
                onDismiss = { showWorkspaceOptions = false },
                onOpen = { showWorkspaceOptions = false; onOpenWorkspace(ws.id) },
                onSetActive = { vm.setChatWorkspaceActive(); showWorkspaceOptions = false },
                onMoveTo = { target -> pendingMoveTarget = target; showWorkspaceOptions = false })
        }
    }

    // preview before the move actually happens.
    pendingMoveTarget?.let { target ->
        MoveToWorkspaceConfirmDialog(
            targetName = target.name,
            fromWorkspaceName = workspace?.name.orEmpty(),
            folderName = folder?.name,
            onDismiss = { pendingMoveTarget = null },
            onConfirm = { vm.moveToWorkspace(target.id, target.name); pendingMoveTarget = null })
    }

    // Chat Screen — reset confirmation: what will be reset, what remains.
    if (showResetConfirm) {
        ResetChatSettingsDialog(
            onDismiss = { showResetConfirm = false },
            onConfirm = { vm.resetChatSettings(); showResetConfirm = false })
    }

    if (showDeleteConfirm) {
        com.vervan.chat.ui.common.ConfirmDialog(
            title = stringResource(R.string.action_recycle),
            body = stringResource(R.string.ui_chatscreen_2853_this_chat_will_be_moved_to_the_recycle_bin_y),
            confirmLabel = stringResource(R.string.chat_recycle_confirm),
            destructive = true,
            onConfirm = { showDeleteConfirm = false; vm.moveToTrash(onDone = onBack) },
            onDismiss = { showDeleteConfirm = false })
    }

    // Chat Screen — chat statistics (message/branch/attachment counts, dates); no
    // token counts since this app doesn't record per-message usage anywhere yet.
    if (showChatStats) {
        val stats = remember(messages, allMessages) { vm.chatStats() }
        ChatStatsDialog(stats = stats, onDismiss = { showChatStats = false })
    }

    if (showSourcePicker) {
        SourcePickerDialog(
            initiallyEnabled = chat?.sourceGrounded == true,
            initiallySelected = chat?.kbIdList()?.toSet() ?: emptySet(),
            onDismiss = { showSourcePicker = false },
            onConfirm = { enabled, selected ->
                vm.setSourceGrounding(
                    enabled, selected.toList()
                ); showSourcePicker = false
            })
    }

    if (showChatTools) {
        val globallyDisabled by app.container.settingsRepository.disabledToolIds.collectAsState(
            initial = emptySet()
        )
        ChatToolsDialog(
            toolsEnabled = chat?.toolsEnabled == true,
            onSetToolsEnabled = { vm.setToolsEnabled(it) },
            overrides = chat?.toolOverrideMap() ?: emptyMap(),
            globallyDisabled = globallyDisabled,
            onSetOverride = { toolId, state -> vm.setToolOverride(toolId, state) },
            onDismiss = { showChatTools = false })
    }

    if (showKbPicker) {
        val knowledgeBases by vm.knowledgeBases.collectAsState()
        AddToKnowledgeBaseDialog(
            knowledgeBases = knowledgeBases,
            onDismiss = { showKbPicker = false },
            onSelect = { id -> vm.addToKnowledgeBase(id); showKbPicker = false })
    }

    if (showPersonaPicker) {
        PersonaPickerDialog(
            personas = personas,
            selectedPersonaId = chat?.personaId,
            onDismiss = { showPersonaPicker = false },
            onSelect = { id -> vm.setPersona(id); showPersonaPicker = false })
    }

    if (showModelPicker) {
        ChatModelPickerDialog(
            models = generationModels,
            selectedModelId = chat?.modelId,
            onDismiss = { showModelPicker = false },
            onSelect = { id -> vm.setModel(id); showModelPicker = false })
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
                    app.container.db.savedOutputDao()
                        .upsert(output.copy(deletedAt = System.currentTimeMillis()))
                    snackbarHostState.showSnackbar("Bookmark removed")
                }
            })
    }

    contextBreakdown?.let { breakdown ->
        ContextBreakdownDialog(breakdown = breakdown, onDismiss = { contextBreakdown = null })
    }

    compareMessageId?.let { targetId ->
        val siblings = com.vervan.chat.data.branch.BranchUtil.siblingsOf(allMessages, targetId)
        CompareDialog(
            siblings = siblings,
            onDismiss = { compareMessageId = null },
            onUse = { id -> vm.switchBranch(id); compareMessageId = null })
    }

    feedbackReasonPromptFor?.let { target ->
        FeedbackReasonDialog(onDismiss = { feedbackReasonPromptFor = null }, onSelect = { reason ->
            vm.setFeedbackReason(
                target.id, reason
            ); feedbackReasonPromptFor = null
        })
    }
}
