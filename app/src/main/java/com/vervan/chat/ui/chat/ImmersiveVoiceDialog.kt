package com.vervan.chat.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.voice.VoiceTurn

/** Full-screen presentation of the existing controller-backed chat voice session. */
@Composable
internal fun ImmersiveVoiceDialog(
    conversationTitle: String,
    modelName: String?,
    turns: List<VoiceTurn>,
    liveTranscript: String,
    onExitImmersive: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onExitImmersive,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(Space.lg)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            conversationTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            modelName ?: "Local model",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onExitImmersive) {
                        Icon(Icons.Filled.CloseFullscreen, contentDescription = "Exit full screen")
                    }
                }
                Text(
                    "LIVE VOICE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Space.xs, bottom = Space.md)
                )
                val listState = rememberLazyListState()
                LaunchedEffect(turns.size, turns.lastOrNull()?.text) {
                    if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.lg)
                ) {
                    if (turns.isEmpty()) {
                        item {
                            Text(
                                liveTranscript.ifBlank { "Ask anything when you’re ready." },
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (liveTranscript.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(vertical = Space.xl)
                            )
                        }
                    }
                    items(turns, key = { it.id }) { turn ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                if (turn.fromUser) "YOU" else "ASSISTANT",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (turn.fromUser) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                turn.text.ifBlank { if (turn.isStreaming) "Thinking…" else "" },
                                style = if (turn.fromUser) MaterialTheme.typography.headlineSmall
                                else MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = Space.xs)
                            )
                        }
                    }
                    if (liveTranscript.isNotBlank() && turns.lastOrNull()?.text != liveTranscript) {
                        item {
                            Column(Modifier.fillMaxWidth()) {
                                Text("YOU · LIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    liveTranscript,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Space.xs)
                                )
                            }
                        }
                    }
                }
                content()
            }
        }
    }
}
