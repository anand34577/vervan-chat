package com.vervan.chat.overlay

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenSearchDesktop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import com.vervan.chat.ui.common.VervanFilledIconButton as FilledIconButton
import com.vervan.chat.ui.common.VervanFilledTonalButton as FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.vervan.chat.ui.common.VervanSmallFloatingActionButton as SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.R
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.MarkdownLiteText
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.VervanExtraShapes
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal fun isNearOverlayBottom(
    totalItemsCount: Int,
    lastVisibleIndex: Int,
    lastVisibleBottom: Int,
    viewportEnd: Int,
    tolerancePx: Int
): Boolean = totalItemsCount == 0 || (
    lastVisibleIndex == totalItemsCount - 1 &&
        lastVisibleBottom - viewportEnd <= tolerancePx
    )

/** Floating result card shown over other apps after a screen capture. */
@Composable
fun OverlayResultPanel(
    text: String,
    busy: Boolean,
    onCopy: () -> Unit,
    onOpen: (() -> Unit)?,
    onAsk: ((String) -> Unit)?,
    onShowNextScreen: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var question by rememberSaveable { mutableStateOf("") }
    var stickToBottom by remember { mutableStateOf(true) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    val bottomTolerancePx = with(LocalDensity.current) { 72.dp.toPx().roundToInt() }
    fun isNearBottom(): Boolean {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return true
        return isNearOverlayBottom(
            info.totalItemsCount,
            last.index,
            last.offset + last.size,
            info.viewportEndOffset - info.afterContentPadding,
            bottomTolerancePx
        )
    }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            stickToBottom = false
        } else {
            androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }.first { !it }
            stickToBottom = isNearBottom()
        }
    }
    LaunchedEffect(text, busy, stickToBottom, isDragged) {
        if (!stickToBottom || isDragged) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        if (listState.layoutInfo.totalItemsCount > 1) listState.scrollToItem(1)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(Space.sm),
        shape = VervanExtraShapes.hero,
        color = SurfaceRole.Overlay.containerColor().copy(alpha = 0.90f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = SurfaceRole.Overlay.border(),
        shadowElevation = SurfaceRole.Overlay.shadowElevation
    ) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAffordance(icon = Icons.Filled.ScreenSearchDesktop, size = IconAffordanceSize.Default)
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    Text(stringResource(R.string.screen_assist_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(if (busy) R.string.screen_assist_analyzing else R.string.screen_assist_ready),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = stringResource(R.string.action_close))
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                shape = MaterialTheme.shapes.medium,
                color = SurfaceRole.Sunken.containerColor().copy(alpha = 0.82f),
                border = SurfaceRole.Sunken.border()
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 260.dp).padding(Space.md)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                        item(key = "answer") {
                            MarkdownLiteText(text.ifBlank { if (busy) stringResource(R.string.screen_assist_reading) else stringResource(R.string.screen_assist_no_response) })
                        }
                        item(key = "answer-end") { Spacer(Modifier.size(1.dp)) }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !stickToBottom,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(Space.xs)
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(1)
                                    stickToBottom = true
                                }
                            },
                            containerColor = if (busy) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (busy) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                Icons.Filled.ExpandMore,
                                stringResource(if (busy) R.string.screen_assist_new_response else R.string.screen_assist_jump_latest)
                            )
                        }
                    }
                }
            }
            if (onAsk != null) {
                fun submitQuestion() {
                    val value = question.trim()
                    if (value.isEmpty() || busy) return
                    onAsk(value)
                    question = ""
                    keyboard?.hide()
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it.take(ValidationLimits.CHAT_COMPOSER) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.screen_assist_follow_up_label)) },
                        placeholder = { Text(stringResource(R.string.screen_assist_follow_up_hint)) },
                        minLines = 1,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitQuestion() })
                    )
                    FilledIconButton(
                        onClick = { submitQuestion() },
                        enabled = question.isNotBlank() && !busy,
                        modifier = Modifier.padding(start = Space.sm).size(52.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.screen_assist_send_follow_up))
                    }
                }
            }
            if (onShowNextScreen != null) {
                FilledTonalButton(
                    onClick = onShowNextScreen,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                ) {
                    Icon(Icons.Filled.ScreenSearchDesktop, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.screen_assist_show_current))
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xs, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCopy, enabled = text.isNotBlank()) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.action_copy))
                }
                if (onOpen != null) {
                    TextButton(onClick = onOpen) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.action_open_chat))
                    }
                }
            }
        }
    }
}

/** Compact, touch-friendly command surface opened by the draggable Quick bubble. */
@Composable
fun QuickBubbleMenu(
    captureAppAvailable: Boolean,
    onResume: (() -> Unit)?,
    onExplainScreen: () -> Unit,
    onCaptureArea: () -> Unit,
    onCaptureApp: () -> Unit,
    onHide: () -> Unit,
    onTurnOff: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(Space.xs),
        shape = VervanExtraShapes.hero,
        color = SurfaceRole.Floating.containerColor().copy(alpha = 0.98f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = SurfaceRole.Floating.border(),
        shadowElevation = SurfaceRole.Floating.shadowElevation
    ) {
        Column(Modifier.padding(vertical = Space.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconAffordance(icon = Icons.Filled.AutoAwesome, size = IconAffordanceSize.Default)
                Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                    Text(stringResource(R.string.screen_assist_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.screen_assist_quick_title), style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.screen_assist_close_quick))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = Space.sm))
            if (onResume != null) {
                QuickMenuAction(
                    icon = Icons.Filled.ScreenSearchDesktop,
                    title = stringResource(R.string.screen_assist_continue),
                    supporting = stringResource(R.string.screen_assist_return_chat),
                    onClick = onResume,
                )
                HorizontalDivider(Modifier.padding(vertical = Space.sm))
            }
            QuickMenuAction(
                icon = Icons.Filled.ScreenSearchDesktop,
                title = stringResource(R.string.screen_assist_explain),
                supporting = stringResource(R.string.screen_assist_capture_visible),
                onClick = onExplainScreen
            )
            QuickMenuAction(
                icon = Icons.Filled.CropFree,
                title = stringResource(R.string.screen_assist_select_area),
                supporting = stringResource(R.string.screen_assist_crop_before_asking),
                onClick = onCaptureArea
            )
            if (captureAppAvailable) {
                QuickMenuAction(
                    icon = Icons.Filled.Apps,
                    title = stringResource(R.string.screen_assist_choose_app),
                    supporting = stringResource(R.string.screen_assist_android_capture),
                    onClick = onCaptureApp
                )
            }
            HorizontalDivider(Modifier.padding(vertical = Space.sm))
            QuickMenuAction(
                icon = Icons.Filled.VisibilityOff,
                title = stringResource(R.string.screen_assist_hide),
                supporting = stringResource(R.string.screen_assist_show_next_time),
                onClick = onHide,
                emphasized = false
            )
            TextButton(
                onClick = onTurnOff,
                modifier = Modifier.align(Alignment.End).padding(horizontal = Space.sm),
                contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.sm)
            ) {
                Icon(Icons.Filled.PowerSettingsNew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.screen_assist_turn_off), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun QuickMenuAction(
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit,
    emphasized: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (emphasized) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = Space.md)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
