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


/** Composer attachment, document, audio, and image-preview UI extracted from ChatScreen. */

internal data class PendingDocumentSelection(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
)

internal fun inspectDocument(context: Context, uri: Uri): PendingDocumentSelection {
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

internal fun readableFileSize(bytes: Long?): String = when {
    bytes == null || bytes < 0 -> "Size unavailable"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
internal fun DocumentComposerPreviewDialog(
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
internal fun ModernChatAttachmentSheet(
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
internal fun CompactAttachmentAction(
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

internal fun formatMs(ms: Int): String {
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
internal fun AttachmentCaptionBar(
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
internal fun AudioComposerPreviewDialog(
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
internal fun OcrPreviewDialog(
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
