package com.vervan.chat.overlay

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenSearchDesktop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.MarkdownLiteText
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.VervanExtraShapes
import kotlin.math.abs

@Composable
fun ScreenExplainScreen(state: CaptureState, onDismiss: () -> Unit, onConfirmSelection: (RectF?) -> Unit = {}) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(Space.lg),
        shape = VervanExtraShapes.hero,
        color = SurfaceRole.Overlay.containerColor(),
        border = SurfaceRole.Overlay.border(),
        shadowElevation = SurfaceRole.Overlay.shadowElevation
    ) {
        Column(Modifier.padding(Space.xl).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAffordance(icon = Icons.Filled.ScreenSearchDesktop, size = IconAffordanceSize.Default)
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    Text("SCREEN ASSIST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("What's on screen", style = MaterialTheme.typography.titleMedium)
                }
            }
            when (state) {
                is CaptureState.Capturing, is CaptureState.Generating -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = Space.xxl), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                if (state is CaptureState.Capturing) "Capturing…" else "Reading the screenshot…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.md)
                            )
                        }
                    }
                }
                is CaptureState.Selecting -> {
                    SelectionStep(bitmap = state.bitmap, onConfirmSelection = onConfirmSelection)
                }
                is CaptureState.Done -> {
                    MarkdownLiteText(
                        state.explanation.ifBlank { "(no response)" },
                        modifier = Modifier.padding(top = Space.md)
                    )
                }
                is CaptureState.Failed -> {
                    ErrorCard(title = "Couldn't explain the screen", body = state.message, modifier = Modifier.padding(top = Space.md))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.lg), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

/** Snipping-tool-style crop: after the one unavoidable MediaProjection consent already grabbed
 * the full frame, this is a purely client-side selection over that bitmap — no second capture,
 * no second system dialog, just picking what to actually send to the model. */
@Composable
private fun SelectionStep(bitmap: android.graphics.Bitmap, onConfirmSelection: (RectF?) -> Unit) {
    var selection by remember { mutableStateOf<Rect?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Text(
        "Draw a box around just the part you want explained, or explain the whole screen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.sm, bottom = Space.sm)
    )
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
            .onSizeChanged { canvasSize = it }
            .pointerInput(bitmap) {
                detectDragGestures(
                    onDragStart = { offset -> selection = Rect(offset, offset) },
                    onDrag = { change, _ -> selection = selection?.let { Rect(it.topLeft, change.position) } }
                )
            }
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())) {
            drawImage(bitmap.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            selection?.let { r ->
                drawRect(
                    color = Color.White,
                    topLeft = Offset(minOf(r.left, r.right), minOf(r.top, r.bottom)),
                    size = Size(abs(r.width), abs(r.height)),
                    style = Stroke(width = 3f)
                )
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selection != null) {
            TextButton(onClick = { selection = null }) { Text("Clear") }
        }
        TextButton(onClick = { onConfirmSelection(null) }) { Text("Whole screen") }
        FilledTonalButton(
            enabled = selection != null && canvasSize.width > 0,
            onClick = {
                val r = selection ?: return@FilledTonalButton
                val left = minOf(r.left, r.right); val top = minOf(r.top, r.bottom)
                val w = abs(r.width); val h = abs(r.height)
                if (w < 8 || h < 8) { onConfirmSelection(null); return@FilledTonalButton }
                onConfirmSelection(
                    RectF(
                        (left / canvasSize.width).coerceIn(0f, 1f),
                        (top / canvasSize.height).coerceIn(0f, 1f),
                        ((left + w) / canvasSize.width).coerceIn(0f, 1f),
                        ((top + h) / canvasSize.height).coerceIn(0f, 1f)
                    )
                )
            }
        ) { Text("Explain selection") }
    }
}
