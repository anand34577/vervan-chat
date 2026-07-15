package com.vervan.chat.ui.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.window.Dialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.audio.WavRecorder
import com.vervan.chat.ui.common.BoundedTextField
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.DeleteMenuItem
import com.vervan.chat.ui.common.setSensitiveText
import com.vervan.chat.ui.common.MarkdownLiteText
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.data.db.entities.KnowledgeBase
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.MessageState
import com.vervan.chat.data.db.entities.SavedOutput
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    initialAction: String? = null,
    onBack: () -> Unit,
    onOpenBranchTree: () -> Unit = {},
    onOpenPassage: (String) -> Unit = {},
    onOpenFolders: () -> Unit = {},
    onOpenModels: () -> Unit = {},
    onOpenWorkspace: (String) -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: ChatViewModel = viewModel(factory = viewModelFactory {
        initializer { ChatViewModel(app, chatId) }
    })
    val messages by vm.messages.collectAsState()
    val allMessages by vm.allMessages.collectAsState()
    val isGenerating by vm.isGenerating.collectAsState()
    val error by vm.error.collectAsState()
    val chat by vm.chat.collectAsState()
    val workspace by vm.workspace.collectAsState()
    val folder by vm.folder.collectAsState()
    val isWorkspaceArchived by vm.isWorkspaceArchived.collectAsState()
    val titleGenerating by vm.titleGenerating.collectAsState()
    val confirmationMessage by vm.confirmationMessage.collectAsState()
    val pendingDocument by vm.pendingDocument.collectAsState()
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
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hapticsEnabled by app.container.settingsRepository.hapticsEnabled.collectAsState(initial = true)

    var draft by remember { mutableStateOf("") }
    var draftLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(chatId) {
        draft = app.container.db.chatDao().getChat(chatId)?.draft.orEmpty()
        draftLoaded = true
    }

    val listState = rememberLazyListState()
    val reducedMotion = com.vervan.chat.ui.common.rememberReducedMotion()
    // Only auto-scroll while the user is already at (or near) the bottom â€” otherwise every
    // streamed token would yank them back down while reading history (B1, spec Â§7.2).
    // Starts false (not true) so the initial anchor-restore effect below gets to decide the
    // opening position before this logic starts reacting to layout â€” see scrollRestored.
    var stickToBottom by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.layoutInfo }.collect { info ->
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            stickToBottom = info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - 1
        }
    }
    // Chat Screen spec Â§17 â€” restore-on-open: jump to the saved message-ID anchor (where the
    // user was last reading) instead of always landing on the latest message. Runs once per
    // chat; falls back to "latest message" when there's no anchor or it's no longer in the
    // rendered branch (spec Â§17.1's "previous position unavailable" case).
    var scrollRestored by remember(chatId) { mutableStateOf(false) }
    LaunchedEffect(chatId, messages, chat) {
        if (scrollRestored || messages.isEmpty() || chat == null) return@LaunchedEffect
        val anchorIndex = chat?.scrollAnchorMessageId?.let { id -> messages.indexOfFirst { it.id == id } } ?: -1
        if (anchorIndex >= 0) {
            listState.scrollToItem(anchorIndex, chat?.scrollAnchorOffsetPx ?: 0)
            stickToBottom = anchorIndex >= messages.size - 1
        } else {
            listState.scrollToItem(messages.size - 1)
            stickToBottom = true
        }
        scrollRestored = true
    }
    val lastMessage = messages.lastOrNull()
    LaunchedEffect(messages.size, lastMessage?.content?.length, lastMessage?.state) {
        if (messages.isNotEmpty() && scrollRestored && stickToBottom) {
            // Streaming text grows without changing messages.size. Follow it with an immediate
            // scroll; reserve animation for discrete message insertions/completion.
            if (reducedMotion || lastMessage?.state == MessageState.STREAMING) {
                listState.scrollToItem(messages.size - 1)
            } else {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    // Save the reading position when leaving the chat (spec Â§17.10 "returning from another
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
    var pendingImagePath by remember { mutableStateOf<String?>(null) }
    var pendingAudioPath by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var activeRecorder by remember { mutableStateOf<WavRecorder?>(null) }
    var compareMessageId by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showWorkspaceOptions by remember { mutableStateOf(false) }
    var showChatStats by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showSavedResponses by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { pendingImagePath = vm.copyImage(uri) }
    }
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingCameraFile?.let { com.vervan.chat.model.ImageUtils.fixOrientation(it) }
            pendingImagePath = pendingCameraFile?.absolutePath
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
        }
    }
    // "Document" attach â€” any standard document type, run through extract/chunk/embed and
    // attached as a per-chat knowledge source (see ChatViewModel.attachDocument).
    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attachDocument(uri)
    }
    // "Scan document (OCR)" â€” reuses the same camera capture flow as a photo attachment, but
    // the captured frame is routed through on-device ML Kit text recognition instead of being
    // sent to the model as an image.
    var pendingScanFile by remember { mutableStateOf<java.io.File?>(null) }
    val takeScan = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingScanFile?.let { com.vervan.chat.model.ImageUtils.fixOrientation(it); vm.attachScannedDocument(it) }
        } else {
            pendingScanFile?.delete()
        }
        pendingScanFile = null
    }
    val requestScanCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (file, uri) = vm.newCameraImageFile()
            pendingScanFile = file
            takeScan.launch(uri)
        }
    }

    val context = LocalContext.current
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
            dictate.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
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
                android.widget.Toast.makeText(context, "Could not start recording: ${it.message ?: "microphone unavailable"}", android.widget.Toast.LENGTH_LONG).show()
            }
    }
    val requestRecordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceMessageRecording()
    }
    var initialActionHandled by rememberSaveable(chatId, initialAction) { mutableStateOf(false) }
    LaunchedEffect(chatId, initialAction, initialActionHandled) {
        if (!initialActionHandled) {
            initialActionHandled = true
            when (initialAction) {
                "image" -> pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                "voice" -> requestRecordPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    val latestRecorder by rememberUpdatedState(activeRecorder)
    val latestPendingImagePath by rememberUpdatedState(pendingImagePath)
    val latestPendingAudioPath by rememberUpdatedState(pendingAudioPath)
    DisposableEffect(chatId) {
        onDispose {
            latestRecorder?.cancel()
            latestPendingImagePath?.let { java.io.File(it).delete() }
            latestPendingAudioPath?.let { java.io.File(it).delete() }
        }
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
            speakWithFocus(last.content, last.id)
            lastAutoReadId = last.id
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            chat?.title ?: "New conversation",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            when {
                                isGenerating -> "Generating on this device"
                                modelLoadState is ChatViewModel.ModelLoadState.Loading -> "Preparing local model"
                                modelLoadState is ChatViewModel.ModelLoadState.Ready -> {
                                    val ready = modelLoadState as ChatViewModel.ModelLoadState.Ready
                                    "Ready · ${ready.backend} · on device"
                                }
                                else -> "Private · on device"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isGenerating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    // Keep only two quick-toggle icons + overflow on phone screens â€”
                    // seven icons starved the title to near-zero width.
                    val grounded = chat?.sourceGrounded == true
                    IconButton(onClick = { showSourcePicker = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Sources",
                            tint = if (grounded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val toolsOn = chat?.toolsEnabled == true
                    val toolsAvailable = activeModel?.supportsTools != false
                    IconButton(onClick = { vm.setToolsEnabled(!toolsOn) }, enabled = toolsAvailable) {
                        Icon(
                            Icons.Filled.Build, contentDescription = "Tools",
                            tint = when {
                                !toolsAvailable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                toolsOn -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    var showOverflow by remember { mutableStateOf(false) }
                    var showOrganizeMenu by remember { mutableStateOf(false) }
                    var showChatActionsMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            // Rename and title generation used to sit two levels deep inside
                            // "Chat actions" â€” promoted to the top level since they're the most
                            // frequently reached-for items in this menu.
                            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }, onClick = { showOverflow = false; showRenameDialog = true })
                            DropdownMenuItem(
                                text = { Text(if (allMessages.any { it.role == MessageRole.ASSISTANT }) "Regenerate title" else "Generate title with AI") },
                                enabled = generationModels.isNotEmpty() && !titleGenerating,
                                onClick = { showOverflow = false; vm.generateTitle() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Find in conversation") }, leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }, onClick = { showOverflow = false; showSearch = true })
                            DropdownMenuItem(
                                text = { Text("Saved responses (${chatSavedOutputs.size})") },
                                leadingIcon = {
                                    Icon(
                                        if (chatSavedOutputs.isEmpty()) Icons.Outlined.BookmarkBorder else Icons.Filled.Bookmark,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = { showOverflow = false; showSavedResponses = true }
                            )
                            DropdownMenuItem(text = { Text("Mode & model") }, onClick = { showOverflow = false; showModeSettings = true })
                            DropdownMenuItem(text = { Text("Chat tools") }, leadingIcon = { Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp)) }, onClick = { showOverflow = false; showChatTools = true })
                            DropdownMenuItem(text = { Text("Organize") }, onClick = { showOverflow = false; showOrganizeMenu = true })
                            DropdownMenuItem(text = { Text("Context inspector") }, leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp)) }, onClick = {
                                showOverflow = false
                                scope.launch { contextBreakdown = vm.inspectContext(draft) }
                            })
                            DropdownMenuItem(text = { Text("Branch tree") }, leadingIcon = { Icon(Icons.Filled.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp)) }, onClick = { showOverflow = false; onOpenBranchTree() })
                            DropdownMenuItem(text = { Text("Chat actions") }, onClick = { showOverflow = false; showChatActionsMenu = true })
                        }
                        DropdownMenu(expanded = showOrganizeMenu, onDismissRequest = { showOrganizeMenu = false }) {
                            DropdownMenuItem(text = { Text(if (chat?.pinned == true) "Unpin" else "Pin") }, onClick = { vm.togglePin(); showOrganizeMenu = false })
                            DropdownMenuItem(text = { Text(if (chat?.archived == true) "Unarchive" else "Archive") }, onClick = { vm.toggleArchive(); showOrganizeMenu = false })
                            DropdownMenuItem(
                                text = { Text(if (chat?.isTemporary == true) "Make permanent" else "Make temporary") },
                                onClick = { vm.toggleTemporary(); showOrganizeMenu = false }
                            )
                            DropdownMenuItem(text = { Text("Add to knowledge base") }, onClick = { showOrganizeMenu = false; showKbPicker = true })
                            DropdownMenuItem(text = { Text("Manage folders") }, leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) }, onClick = { showOrganizeMenu = false; onOpenFolders() })
                        }
                        DropdownMenu(expanded = showChatActionsMenu, onDismissRequest = { showChatActionsMenu = false }) {
                            if (chat?.previousTitle != null) {
                                DropdownMenuItem(text = { Text("Restore previous title") }, onClick = { showChatActionsMenu = false; vm.restorePreviousTitle() })
                            }
                            DropdownMenuItem(text = { Text("Chat details") }, onClick = { showChatActionsMenu = false; showChatStats = true })
                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { vm.duplicate(onDone = onBack); showChatActionsMenu = false })
                            DropdownMenuItem(text = { Text("Export") }, onClick = {
                                showChatActionsMenu = false
                                scope.launch {
                                    val text = vm.exportText()
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, chat?.title ?: "Chat")
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Export chat"))
                                }
                            })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Reset chat settings") }, onClick = { showChatActionsMenu = false; showResetConfirm = true })
                            DeleteMenuItem(onClick = { vm.moveToTrash(onDone = onBack); showChatActionsMenu = false })
                        }
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
            // Chat Screen spec Â§13 â€” compact, horizontally-scrollable status chips; never
            // wraps to a second row. Context % is a cheap chars/4 estimate (same tradeoff as
            // the context inspector), not an exact token count.
            run {
                val contextLimit by app.container.settingsRepository.contextTokenLimit.collectAsState(initial = 4096)
                val estimatedTokens = messages.sumOf { it.content.length } / 4
                val contextPercent = if (contextLimit > 0) (estimatedTokens * 100 / contextLimit).coerceIn(0, 999) else 0
                ChatContextStrip(
                    workspaceName = workspace?.name,
                    folderName = folder?.name,
                    personaName = persona?.name,
                    modelName = activeModelName?.substringBefore(" Â· "),
                    thinkingMode = chat?.thinkingMode?.takeIf { it != "OFF" },
                    sourceCount = chat?.kbIdList()?.size?.takeIf { chat?.sourceGrounded == true && it > 0 },
                    contextPercent = contextPercent,
                    onWorkspaceClick = { showWorkspaceOptions = true },
                    onFolderClick = onOpenFolders,
                    onPersonaClick = { showPersonaPicker = true },
                    onModelClick = { showModeSettings = true },
                    onSourcesClick = { showSourcePicker = true },
                    onContextClick = { scope.launch { contextBreakdown = vm.inspectContext(draft) } }
                )
            }
            ModelReadinessPanel(
                state = modelLoadState,
                onLoad = vm::retryModelLoad,
                onOpenModels = onOpenModels
            )
            if (isWorkspaceArchived) {
                ArchivedWorkspaceBanner(onRestore = { vm.restoreChatWorkspace() })
            }
            if (showSearch) {
                ConversationSearchBar(
                    messages = messages,
                    onClose = { showSearch = false },
                    onJumpTo = { index -> scope.launch { listState.animateScrollToItem(index) } }
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().widthIn(max = 840.dp).align(Alignment.Center),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty()) {
                    item(key = "empty-chat") {
                        ChatEmptyState(
                            personaName = persona?.name,
                            modelName = activeModelName?.substringBefore(" Â· "),
                            modifier = Modifier.fillParentMaxHeight(0.92f),
                            onSuggestion = { suggestion ->
                                draft = suggestion
                                if (draftLoaded) vm.saveDraft(suggestion)
                            }
                        )
                    }
                }
                items(messages, key = { it.id }) { message ->
                    if (message.role != MessageRole.SYSTEM) {
                        MessageBubble(
                            message = message,
                            savedOutput = chatSavedOutputs.firstOrNull {
                                it.label == message.id || (it.label.isBlank() && it.content == message.content)
                            },
                            onBookmarkChanged = { saved ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(if (saved) "Response bookmarked" else "Bookmark removed")
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
                            onOpenPassage = { chunkId -> onOpenPassage(chunkId) },
                            // Swipe-to-reply used to prepend a "> quoted" blockquote directly into
                            // the draft text, which grew the input box with every reply. A compact
                            // preview bar above the composer (WhatsApp-style) keeps the box the same
                            // size â€” the quote is only merged into the actual sent text on Send.
                            onQuote = { quoted -> pendingQuote = quoted }
                        )
                    }
                }
            }
            // Chat Screen spec Â§11 â€” "jump to latest" once the user has scrolled away from
            // the bottom (auto-follow only re-engages once they're back near it, see the
            // stickToBottom LaunchedEffect above).
            if (!stickToBottom && messages.isNotEmpty()) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(messages.size - 1); stickToBottom = true } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    icon = { Icon(Icons.Filled.ExpandMore, contentDescription = null) },
                    text = { Text(if (isGenerating) "New response" else "Jump to latest") }
                )
            }
            }

            if (isGenerating) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            error?.let {
                ErrorCard(
                    title = "Generation could not continue",
                    body = it,
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
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
            // message" â€” grouped into one composing tray instead of two separately-floating
            // rows so they read as one unit sitting above the composer, not a growing stack.
            if (pendingQuote != null || pendingImagePath != null || pendingAudioPath != null || pendingDocument != null) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 840.dp).align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    pendingDocument?.let { docState ->
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            when (docState) {
                                is ChatViewModel.DocumentAttachState.Importing -> {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text(
                                        "Importing \"${docState.name}\"â€¦", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 10.dp)
                                    )
                                }
                                is ChatViewModel.DocumentAttachState.Ready -> {
                                    Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(docState.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text(
                                            if (docState.grounded) "Indexed â€” semantic search" else "Indexed â€” keyword search only",
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { vm.clearPendingDocument() }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                    }
                                }
                                is ChatViewModel.DocumentAttachState.Failed -> {
                                    Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    Text(
                                        "\"${docState.name}\" â€” ${docState.reason}", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f).padding(start = 8.dp)
                                    )
                                    IconButton(onClick = { vm.clearPendingDocument() }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        if (pendingQuote != null || pendingImagePath != null || pendingAudioPath != null) HorizontalDivider()
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
                            IconButton(onClick = { pendingQuote = null }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (pendingQuote != null && (pendingImagePath != null || pendingAudioPath != null)) {
                        HorizontalDivider()
                    }
                    pendingImagePath?.let { path ->
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            val thumbnailPx = with(LocalDensity.current) { 48.dp.roundToPx() }
                            val bitmap = remember(path, thumbnailPx) {
                                com.vervan.chat.model.ImageUtils.decodeThumbnail(path, thumbnailPx)?.asImageBitmap()
                            }
                            bitmap?.let {
                                Image(it, contentDescription = "Attached image", modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.extraSmall), contentScale = ContentScale.Crop)
                            }
                            Text(
                                "Image attached", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            )
                            TextButton(onClick = { java.io.File(path).delete(); pendingImagePath = null }) { Text("Remove") }
                        }
                    }
                    pendingAudioPath?.let { path ->
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "Voice message attached",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            )
                            TextButton(onClick = { java.io.File(path).delete(); pendingAudioPath = null }) { Text("Remove") }
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .alpha(if (composerEnabled || isGenerating) 1f else 0.62f)
                    .animateContentSize(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
            ) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    val visionAvailable by vm.visionAvailable.collectAsState()
                    val audioAvailable by vm.audioAvailable.collectAsState()
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
                                Text("Stored locally until you send it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { activeRecorder?.cancel(); activeRecorder = null; isRecording = false }) { Text("Cancel") }
                            TextButton(onClick = {
                                val recorder = activeRecorder
                                activeRecorder = null
                                isRecording = false
                                scope.launch {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { recorder?.stop() }
                                    pendingAudioPath = recorder?.outputFile?.absolutePath
                                }
                            }) { Text("Use") }
                        }
                    } else {
                        Text(
                            "Message",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                        )
                        OutlinedTextField(
                            value = draft,
                            onValueChange = {
                                draft = it
                                if (draftLoaded) vm.saveDraft(it)
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 156.dp),
                            placeholder = {
                                Text(if (composerEnabled) "Ask anything, add context, or use /commands" else "Waiting for a local model")
                            },
                            minLines = 1,
                            maxLines = 6,
                            enabled = composerEnabled,
                            isError = draft.length > 12_000,
                            shape = MaterialTheme.shapes.large,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                errorContainerColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                        if (draft.length >= 9_600) {
                            Text(
                                if (draft.length > 12_000) "Message is over the 12,000 character limit" else "${draft.length} / 12,000 characters",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (draft.length > 12_000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End).padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            var showAttachMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showAttachMenu = true }, enabled = composerEnabled) {
                                    Icon(Icons.Filled.Add, contentDescription = "Attach photo, document, scan, or audio")
                                }
                                DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(when (visionAvailable) {
                                                false -> "Photo (unsupported by this model)"
                                                null -> "Photo (checked when model loads)"
                                                true -> "Photo"
                                            })
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                                        enabled = visionAvailable != false,
                                        onClick = { showAttachMenu = false; pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(when (visionAvailable) {
                                                false -> "Camera (unsupported by this model)"
                                                null -> "Camera (checked when model loads)"
                                                true -> "Camera"
                                            })
                                        },
                                        leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                                        enabled = visionAvailable != false,
                                        onClick = { showAttachMenu = false; requestCameraPermission.launch(android.Manifest.permission.CAMERA) }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(when (audioAvailable) {
                                                false -> "Voice message (unsupported by this model)"
                                                null -> "Voice message (checked when model loads)"
                                                true -> "Voice message"
                                            })
                                        },
                                        leadingIcon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
                                        enabled = audioAvailable != false,
                                        onClick = { showAttachMenu = false; requestRecordPermission.launch(android.Manifest.permission.RECORD_AUDIO) }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Document") },
                                        leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                                        onClick = {
                                            showAttachMenu = false
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
                                    DropdownMenuItem(
                                        text = { Text("Scan document (OCR)") },
                                        leadingIcon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                                        onClick = { showAttachMenu = false; requestScanCameraPermission.launch(android.Manifest.permission.CAMERA) }
                                    )
                                }
                            }
                            IconButton(onClick = {
                                if (!draft.startsWith("/")) {
                                    draft = "/$draft"
                                    if (draftLoaded) vm.saveDraft(draft)
                                }
                            }, enabled = composerEnabled) {
                                Icon(Icons.Filled.Bolt, contentDescription = "Open commands")
                            }
                            IconButton(onClick = { requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO) }, enabled = composerEnabled) {
                                Icon(Icons.Filled.KeyboardVoice, contentDescription = "Dictate to text")
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Saved locally",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            val documentReady = pendingDocument is ChatViewModel.DocumentAttachState.Ready
                            val canSend = (draft.isNotBlank() || pendingImagePath != null || pendingAudioPath != null || documentReady) &&
                                composerEnabled && draft.length <= 12_000
                            Box(
                                Modifier.padding(start = 8.dp).size(48.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (canSend || isGenerating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
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
                                        val quotePrefix = pendingQuote?.let { quoted ->
                                            quoted.lineSequence().joinToString("\n") { "> $it" } + "\n\n"
                                        }.orEmpty()
                                        val body = draft.ifBlank { if (documentReady) "Describe this document." else draft }
                                        val image = pendingImagePath
                                        val audio = pendingAudioPath
                                        draft = ""
                                        pendingImagePath = null
                                        pendingAudioPath = null
                                        pendingQuote = null
                                        vm.clearPendingDocument()
                                        vm.send(quotePrefix + body, image, audio)
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

    // Chat Screen spec Â§3-5 â€” workspace indicator options: view/open, set as the app's active
    // workspace (distinct from just opening it â€” the chat's own workspace and the global
    // active workspace are independent), or move this chat to a different one. Moving shows a
    // preview (Â§5) before committing.
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
                    }.padding(vertical = 10.dp))
                    if (!isChatWorkspaceActive) {
                        Text(
                            "Set as active workspace",
                            modifier = Modifier.fillMaxWidth()
                                .clickable { vm.setChatWorkspaceActive(); showWorkspaceOptions = false }
                                .padding(vertical = 10.dp)
                        )
                    }
                    HorizontalDivider()
                    Text("Move to another workspace", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    otherWorkspaces.filter { it.id != workspace?.id }.forEach { ws ->
                        Text(
                            ws.name,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { pendingMoveTarget = ws; showWorkspaceOptions = false }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showWorkspaceOptions = false }) { Text("Close") } }
        )
    }

    // Â§5 â€” preview before the move actually happens.
    pendingMoveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingMoveTarget = null },
            title = { Text("Move to \"${target.name}\"?") },
            text = {
                Column {
                    Text("From: ${workspace?.name.orEmpty()}")
                    Text("To: ${target.name}")
                    if (folder != null) Text("Current folder \"${folder?.name}\" will be cleared â€” the chat lands under Unfoldered Chats.")
                    Text("Messages, branches, attachments, and response history are preserved.")
                    Text("Any chat-specific persona/model override is kept; only settings with no override will follow the new workspace.")
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.moveToWorkspace(target.id, target.name); pendingMoveTarget = null }) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = { pendingMoveTarget = null }) { Text("Cancel") } }
        )
    }

    // Chat Screen spec Â§10 â€” reset confirmation: what will be reset, what remains.
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset chat settings?") },
            text = {
                Column {
                    Text("Resets: persona, model, profile, thinking mode, sources, tools, and knowledge base selection to workspace defaults.")
                    Text("Kept: messages, branches, attachments, workspace, and folder.", modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { vm.resetChatSettings(); showResetConfirm = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }

    // Chat Screen spec Â§26 â€” chat statistics (message/branch/attachment counts, dates); no
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
                        Text("No knowledge bases yet â€” create one from the Knowledge tab first.")
                    }
                    knowledgeBases.forEach { kb ->
                        Text(
                            kb.name,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { vm.addToKnowledgeBase(kb.id); showKbPicker = false }
                                .padding(vertical = 10.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        vm.setPersona(null)
                        showPersonaPicker = false
                    }) {
                        Checkbox(checked = chat?.personaId == null, onCheckedChange = null)
                        Text("No persona", modifier = Modifier.padding(start = 8.dp))
                    }
                    personas.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            vm.setPersona(option.id)
                            showPersonaPicker = false
                        }) {
                            Checkbox(checked = chat?.personaId == option.id, onCheckedChange = null)
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        vm.setModel(null)
                        showModelPicker = false
                    }) {
                        Checkbox(checked = chat?.modelId == null, onCheckedChange = null)
                        Text("Use active default", modifier = Modifier.padding(start = 8.dp))
                    }
                    generationModels.forEach { model ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            vm.setModel(model.id)
                            showModelPicker = false
                        }) {
                            Checkbox(checked = chat?.modelId == model.id, onCheckedChange = null)
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
                if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
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
                        MaterialTheme.colorScheme.primary, com.vervan.chat.ui.theme.VervanSuccess,
                        com.vervan.chat.ui.theme.VervanWarn, MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.error,
                        MaterialTheme.colorScheme.outline
                    )
                    com.vervan.chat.ui.common.ContextUsageBar(
                        usedTokens = breakdown.estimatedTotalTokens,
                        totalTokens = breakdown.recommendedLimit,
                        summary = "~${breakdown.estimatedTotalTokens} tokens estimated, of ~${breakdown.recommendedLimit} recommended for this device profile.",
                        slices = breakdown.items.mapIndexed { i, item ->
                            com.vervan.chat.ui.common.ContextSlice(item.label, item.estimatedTokens, palette[i % palette.size])
                        }
                    )
                    if (breakdown.estimatedTotalTokens > breakdown.recommendedLimit) {
                        Text(
                            "Over the recommended limit for this device profile — history and retrieved sources will be trimmed before sending.",
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
                Text("Bookmark a response and it will appear here for this chat.")
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
                    if (state is ChatViewModel.ModelLoadState.Loading) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = if (state is ChatViewModel.ModelLoadState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
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

@Composable
private fun ChatEmptyState(
    personaName: String?,
    modelName: String?,
    modifier: Modifier = Modifier,
    onSuggestion: (String) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
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
            "What can we work on?",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            "Your conversation stays on this device. Add a file, speak, or start with a prompt.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp).padding(top = 8.dp)
        )
        val activeContext = listOfNotNull(personaName, modelName).joinToString(" · ")
        if (activeContext.isNotBlank()) {
            Text(
                activeContext,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "Help me think through an idea",
                "Summarize an attached document",
                "Draft something clear and concise"
            ).forEach { suggestion ->
                AssistChip(onClick = { onSuggestion(suggestion) }, label = { Text(suggestion) })
            }
        }
    }
}

/**
 * Chat Screen spec Â§13 â€” context strip: compact, horizontally-scrollable status chips below
 * the top bar. Hidden entirely when there's nothing useful to show (a brand new chat with no
 * persona/sources/thinking mode set). Never wraps â€” Row + horizontalScroll, same pattern as
 * the slash-command suggestion chips elsewhere in this file.
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
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        workspaceName?.let {
            AssistChip(
                onClick = onWorkspaceClick,
                label = { Text(it, maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        folderName?.let {
            AssistChip(
                onClick = onFolderClick,
                label = { Text(it, maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        personaName?.let {
            AssistChip(
                onClick = onPersonaClick,
                label = { Text(it, maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        modelName?.let {
            val label = if (thinkingMode != null) "$it · ${thinkingMode.lowercase().replaceFirstChar { c -> c.uppercase() }}" else it
            AssistChip(
                onClick = onModelClick,
                label = { Text(label, maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        sourceCount?.let {
            AssistChip(
                onClick = onSourcesClick,
                label = { Text("$it source${if (it == 1) "" else "s"}") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        if (contextPercent > 0) {
            AssistChip(
                onClick = onContextClick,
                label = { Text(if (contextPercent > 80) "Context high · ~$contextPercent%" else "Context · ~$contextPercent%") },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
    }
}

/**
 * Chat Screen spec Â§9/Â§23 â€” an archived workspace remains viewable (history, branches,
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
                    "This chat's workspace is archived â€” restore it to send new messages.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRestore) { Text("Restore and continue") }
        }
    }
}

/**
 * Chat Screen spec Â§27 â€” find-in-conversation, scoped to the currently rendered branch path
 * (not the app-wide SearchScreen, which spans every chat). ponytail: no inline highlighting of
 * the matched substring, just prev/next navigation and a match count â€” jumping to the message
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

/** Juxtaposes every sibling's full text side by side â€” ponytail: no token-level diff
 * highlighting, just the raw outputs next to each other, "compare" not "diff". */
@Composable
private fun CompareDialog(siblings: List<Message>, onDismiss: () -> Unit, onUse: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        // Adaptive layout (B5): stacked cards on a compact window, side-by-side on an
        // expanded one â€” measured locally via BoxWithConstraints rather than threading
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
 * picker â€” the same choices, far less scanning to find the current selection. */
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
                        FilterChip(
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
                        FilterChip(
                            selected = currentProfile == p.id,
                            onClick = { onProfileChange(p.id) },
                            label = { Text(p.label) }
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(bottom = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpenModelPicker() }.padding(vertical = 10.dp)) {
                    Text("Chat model", modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpenPersonaPicker() }.padding(vertical = 10.dp)) {
                    Text("Persona", modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                // Per-chat sampler overrides (spec Â§6/Â§7) â€” the slider always shows the
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
                    Text("No knowledge bases yet â€” import documents from the Knowledge tab first.", style = MaterialTheme.typography.bodySmall)
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
                    "Override which tools this chat can use, on top of Settings → Tools.",
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
                            FilterChip(
                                selected = override == null,
                                onClick = { onSetOverride(tool.name, null) },
                                label = { Text(if (tool.name in globallyDisabled) "Inherit (off)" else "Inherit (on)") }
                            )
                            FilterChip(
                                selected = override == true,
                                onClick = { onSetOverride(tool.name, true) },
                                label = { Text("On") },
                                modifier = Modifier.padding(start = 6.dp)
                            )
                            FilterChip(
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
    savedOutput: SavedOutput?,
    onBookmarkChanged: (Boolean) -> Unit,
    onReadAloud: (text: String, utteranceId: String) -> Unit,
    isGenerating: Boolean,
    siblingPosition: Pair<Int, Int>,
    onConfirmTool: (Boolean) -> Unit,
    onEditAndResend: (String) -> Unit,
    onRegenerate: () -> Unit,
    onSwitchBranch: (Int) -> Unit,
    onCompare: () -> Unit,
    onOpenPassage: (String) -> Unit = {},
    onQuote: (String) -> Unit = {}
) {
    val isUser = message.role == MessageRole.USER
    val app = LocalContext.current.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var showRememberDialog by remember { mutableStateOf(false) }
    var showSaveAsPromptDialog by remember { mutableStateOf(false) }
    var showMessageMenu by remember { mutableStateOf(false) }
    var editing by remember(message.id) { mutableStateOf(false) }
    var editText by remember(message.id) { mutableStateOf(message.content) }
    val timeLabel = remember(message.createdAt) {
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(message.createdAt))
    }
    // Actions (edit/speak/save/remember/regenerate/more) used to render permanently on every
    // bubble â€” busy and "congested" with a full conversation on screen. Tap the bubble to
    // reveal them instead, matching the tap-to-reveal pattern most chat apps use.
    var showActions by remember(message.id) { mutableStateOf(false) }
    // Swipe-left-to-reply (WhatsApp pattern) instead of a "Quote in reply" menu item â€” drag the
    // bubble left past the threshold to quote it, same gesture on either role's messages.
    val dragOffset = remember(message.id) { androidx.compose.animation.core.Animatable(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val quoteThresholdPx = remember(density) { with(density) { 56.dp.toPx() } }
    Box(Modifier.fillMaxWidth()) {
        Icon(
            Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .graphicsLayer { alpha = (-dragOffset.value / quoteThresholdPx).coerceIn(0f, 1f) }
        )
        Card(
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxWidth(if (isUser) 0.82f else 0.96f)
                .offset { androidx.compose.ui.unit.IntOffset(dragOffset.value.roundToInt(), 0) }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (dragOffset.value < -quoteThresholdPx) onQuote(message.content)
                                dragOffset.animateTo(0f)
                            }
                        },
                        onDragCancel = { scope.launch { dragOffset.animateTo(0f) } },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceIn(-quoteThresholdPx * 1.5f, 0f)) }
                        }
                    )
                }
                .clickable { showActions = !showActions },
            shape = if (isUser) com.vervan.chat.ui.theme.VervanExtraShapes.userBubble else com.vervan.chat.ui.theme.VervanExtraShapes.assistantBubble,
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            ),
            border = if (isUser) null else androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isUser) {
                        Surface(
                            modifier = Modifier.size(30.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                    Text(
                        if (isUser) "You" else "Vervan",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = if (isUser) 0.dp else 8.dp)
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
                message.imagePath?.let { path ->
                    val previewPx = with(LocalDensity.current) { 640.dp.roundToPx() }
                    val bitmap = remember(path, previewPx) {
                        com.vervan.chat.model.ImageUtils.decodeThumbnail(path, previewPx)?.asImageBitmap()
                    }
                    bitmap?.let {
                        Image(
                            it, contentDescription = "Attached image",
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
                if (message.audioPath != null) {
                    Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            "Voice message", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                if (editing) {
                    OutlinedTextField(value = editText, onValueChange = { editText = it }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.padding(top = 4.dp)) {
                        TextButton(onClick = {
                            editing = false
                            if (editText.isNotBlank() && editText != message.content) onEditAndResend(editText)
                        }) { Text("Send") }
                        TextButton(onClick = { editing = false; editText = message.content }) { Text("Cancel") }
                    }
                } else {
                    val parsed = remember(message.content) { com.vervan.chat.llm.ThinkingParser.parse(message.content) }
                    if (parsed.reasoning != null) {
                        com.vervan.chat.ui.common.AssistantSubCard(
                            kind = com.vervan.chat.ui.common.SubCardKind.Reasoning,
                            title = "Reasoning",
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                parsed.reasoning,
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            )
                        }
                    }
                    // Markdown/code-block rendering (spec Â§7.1) â€” assistant output routinely
                    // contains fenced code and tables; user messages stay plain (they typed
                    // it, no need to reparse their own text as markdown).
                    if (isUser) {
                        Text(
                            parsed.answer.ifBlank { " " },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        MarkdownLiteText(parsed.answer.ifBlank { " " })
                    }
                    if (showActions || (!isUser && message.state == MessageState.COMPLETE)) {
                        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isUser && !isGenerating) {
                                IconButton(onClick = { editing = true }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit and resend", modifier = Modifier.size(18.dp))
                                }
                            }
                            if (!isUser && message.state == MessageState.COMPLETE && message.content.isNotBlank()) {
                                IconButton(
                                    onClick = { onReadAloud(message.content, message.id) },
                                    modifier = Modifier.size(48.dp)
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
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        if (savedOutput == null) Icons.Outlined.BookmarkBorder else Icons.Filled.Bookmark,
                                        contentDescription = if (savedOutput == null) "Bookmark response" else "Remove bookmark",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (savedOutput == null) LocalContentColor.current else MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (!isGenerating) {
                                    IconButton(onClick = onRegenerate, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Regenerate", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            // Reply stays gesture-only here; copy is the visible gesture alternative.
                            if (!editing && message.content.isNotBlank()) {
                                IconButton(
                                    // Clipboard hygiene (Phase H) — auto-clears after 30s if
                                    // nothing else has overwritten it since.
                                    onClick = { clipboard.setSensitiveText(message.content, scope) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                                }
                                Box {
                                    IconButton(onClick = { showMessageMenu = true }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "More message actions", modifier = Modifier.size(18.dp))
                                    }
                                    DropdownMenu(expanded = showMessageMenu, onDismissRequest = { showMessageMenu = false }) {
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
                if (message.state == MessageState.STREAMING) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            "Generating on device…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                } else if (message.state == MessageState.INTERRUPTED) {
                    TextButton(onClick = onRegenerate, enabled = !isGenerating) { Text("Continue with a new response") }
                } else if (message.state == MessageState.FAILED) {
                    TextButton(onClick = onRegenerate, enabled = !isGenerating) { Text("Try again") }
                } else if (message.state == MessageState.CANCELLED) {
                    Text("Partial response kept", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                message.toolResultJson?.let { ToolResultCard(it) }
                if (message.state == MessageState.AWAITING_CONFIRMATION) {
                    ToolConfirmationCard(message.toolCallJson, onConfirmTool)
                }
                message.sourcesJson?.let { SourceCards(it, onOpenPassage = { chunkId -> onOpenPassage(chunkId) }) }
                if (siblingPosition.second > 1) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onSwitchBranch(-1) }, modifier = Modifier.size(48.dp), enabled = siblingPosition.first > 1) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous branch", modifier = Modifier.size(16.dp))
                        }
                        Text("${siblingPosition.first}/${siblingPosition.second}", style = MaterialTheme.typography.labelSmall)
                        IconButton(onClick = { onSwitchBranch(1) }, modifier = Modifier.size(48.dp), enabled = siblingPosition.first < siblingPosition.second) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next branch", modifier = Modifier.size(16.dp))
                        }
                        TextButton(onClick = onCompare) { Text("Compare", style = MaterialTheme.typography.labelSmall) }
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
                        "Saved as a global memory â€” future chats will see it as background context.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.container.db.memoryDao().upsert(com.vervan.chat.data.db.entities.Memory(text = text)) }
                    showRememberDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRememberDialog = false }) { Text("Cancel") } }
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
}

/** §7.3.4 — Standard mode translates the raw retrieval score into a plain-language match
 * strength instead of a bare number; Expert mode shows the exact score (see call site). */
private fun matchStrength(score: Double): String = when {
    score >= 0.75 -> "Strong"
    score >= 0.5 -> "Moderate"
    else -> "Weak"
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SourceCards(sourcesJson: String, onOpenPassage: (String) -> Unit = {}) {
    val array = remember(sourcesJson) { runCatching { JSONArray(sourcesJson) }.getOrNull() } ?: return
    var selected by remember(sourcesJson) { mutableStateOf<org.json.JSONObject?>(null) }
    // Mark-irrelevant is a client-side hide, not persisted or fed back into retrieval â€”
    // ponytail: a real "don't retrieve this chunk again" would need a per-chat exclusion set
    // threaded through RetrievalEngine; this covers the common "get this off my screen" need.
    val hiddenIndices = remember(sourcesJson) { mutableStateListOf<Int>() }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val app = LocalContext.current.applicationContext as VervanApp
    val expertMode by app.container.settingsRepository.expertMode.collectAsState(initial = false)
    if (array.length() == 0) {
        Text(
            "No matching sources found in the selected knowledge bases", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)
        )
        return
    }
    com.vervan.chat.ui.common.AssistantSubCard(
        kind = com.vervan.chat.ui.common.SubCardKind.Sources,
        title = "Sources (${array.length()})",
        collapsible = false,
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 0 until array.length()) {
                if (i in hiddenIndices) continue
                val obj = array.getJSONObject(i)
                AssistChip(
                    onClick = { selected = obj },
                    label = {
                        Text(
                            "[${i + 1}] ${obj.optString("documentName")}${obj.optString("sectionPath").let { if (it.isNotBlank()) " â€” $it" else "" }}",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
    selected?.let { source ->
        val index = (0 until array.length()).first { array.getJSONObject(it) === source }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(source.optString("documentName")) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    source.optString("sectionPath").takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(source.optString("excerpt"), modifier = Modifier.padding(top = 8.dp))
                    Text(
                        if (expertMode) {
                            "Retrieval score ${String.format("%.2f", source.optDouble("score"))} · rank ${index + 1} · ranking signal, not confidence"
                        } else {
                            "${matchStrength(source.optDouble("score"))} match"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(source.optString("excerpt"))) }) {
                            Text("Copy excerpt", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            val citation = "[${index + 1}] ${source.optString("documentName")}" +
                                source.optString("sectionPath").let { if (it.isNotBlank()) " â€” $it" else "" }
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(citation))
                        }) { Text("Copy citation", style = MaterialTheme.typography.labelSmall) }
                        TextButton(onClick = { hiddenIndices.add(index); selected = null }) {
                            Text("Mark irrelevant", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                val chunkId = source.optString("chunkId")
                if (chunkId.isNotBlank()) {
                    TextButton(onClick = { selected = null; onOpenPassage(chunkId) }) { Text("Open in context") }
                } else {
                    TextButton(onClick = { selected = null }) { Text("Close") }
                }
            },
            dismissButton = {
                if (source.optString("chunkId").isNotBlank()) {
                    TextButton(onClick = { selected = null }) { Text("Close") }
                }
            }
        )
    }
}

@Composable
private fun ToolResultCard(toolResultJson: String) {
    val obj = remember(toolResultJson) { runCatching { org.json.JSONObject(toolResultJson) }.getOrNull() } ?: return
    val success = obj.optBoolean("success", true)
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (success) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (success) com.vervan.chat.ui.theme.VervanSuccess else MaterialTheme.colorScheme.error
                )
                Text(
                    "${obj.optString("tool")}${if (!success) " failed" else " done"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = com.vervan.chat.ui.theme.VervanMono,
                    color = if (success) com.vervan.chat.ui.theme.VervanSuccess else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Text(obj.optString("summary"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ToolConfirmationCard(toolCallJson: String?, onConfirm: (Boolean) -> Unit) {
    val obj = remember(toolCallJson) { toolCallJson?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } } ?: return
    val params = obj.optJSONObject("params")
    // EXTERNAL_ACTION (leaves the app / can't be undone from in-app history, e.g. sending a
    // message) gets a required acknowledgment checkbox before Allow is enabled â€” REVERSIBLE_WRITE
    // (undoable in-app, e.g. via recycle bin) keeps the single-tap flow (B4).
    val isExternal = obj.optString("risk") == "EXTERNAL_ACTION"
    var acknowledged by remember(toolCallJson) { mutableStateOf(!isExternal) }
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.vervan.chat.ui.theme.VervanWarn.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = com.vervan.chat.ui.theme.VervanWarn)
                Text(
                    (if (isExternal) " Proposed external action Â· " else " Proposed action Â· ") + obj.optString("tool"),
                    style = MaterialTheme.typography.labelMedium,
                    color = com.vervan.chat.ui.theme.VervanWarn
                )
            }
            if (params != null) {
                Text(
                    params.toString(), style = MaterialTheme.typography.bodySmall,
                    fontFamily = com.vervan.chat.ui.theme.VervanMono, modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (isExternal) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                    Text(
                        "This leaves the app and can't be undone from here", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            Row(Modifier.padding(top = 8.dp)) {
                TextButton(onClick = { onConfirm(true) }, enabled = acknowledged) { Text("Allow") }
                TextButton(onClick = { onConfirm(false) }) { Text("Deny") }
            }
        }
    }
}

