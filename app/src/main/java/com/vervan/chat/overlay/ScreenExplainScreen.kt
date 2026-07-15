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
import androidx.compose.material3.CircularProgressIndicator
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
import com.vervan.chat.ui.common.MarkdownLiteText
import kotlin.math.abs

@Composable
fun ScreenExplainScreen(state: CaptureState, onDismiss: () -> Unit, onConfirmSelection: (RectF?) -> Unit = {}) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp
    ) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("What's on screen", style = MaterialTheme.typography.titleMedium)
            when (state) {
                is CaptureState.Capturing, is CaptureState.Generating -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                if (state is CaptureState.Capturing) "Capturing…" else "Reading the screenshot…",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 12.dp)
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
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                is CaptureState.Failed -> {
                    ErrorCard(title = "Couldn't explain the screen", body = state.message, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
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
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
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
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
        if (selection != null) {
            TextButton(onClick = { selection = null }) { Text("Clear") }
        }
        TextButton(onClick = { onConfirmSelection(null) }) { Text("Whole screen") }
        TextButton(
            enabled = selection != null && canvasSize.width > 0,
            onClick = {
                val r = selection ?: return@TextButton
                val left = minOf(r.left, r.right); val top = minOf(r.top, r.bottom)
                val w = abs(r.width); val h = abs(r.height)
                if (w < 8 || h < 8) { onConfirmSelection(null); return@TextButton }
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
