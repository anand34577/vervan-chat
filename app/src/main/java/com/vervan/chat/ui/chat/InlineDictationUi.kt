package com.vervan.chat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import com.vervan.chat.ui.theme.Space
import java.util.Locale

@Composable
internal fun InlineDictationRecording(
    levels: List<Float>,
    elapsedMs: Long,
    onCancel: () -> Unit,
    onStop: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(Space.sm), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                Text(stringResource(R.string.dictation_recording_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.dictation_recording_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(formatDictationElapsed(elapsedMs), style = MaterialTheme.typography.labelLarge)
        }
        Row(
            Modifier.fillMaxWidth().height(38.dp).padding(top = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            levels.takeLast(32).ifEmpty { List(32) { 0.05f } }.forEach { level ->
                Box(
                    Modifier.weight(1f).height((4 + level.coerceIn(0f, 1f) * 30).dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
        Row(
            Modifier.padding(top = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Text(stringResource(R.string.action_cancel), modifier = Modifier.padding(start = Space.xs))
            }
            FilledIconButton(onClick = onStop, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.dictation_stop))
            }
        }
    }
}

@Composable
internal fun InlineDictationTranscribing(onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
            Text(stringResource(R.string.dictation_transcribing_title), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.dictation_transcribing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    }
}

@Composable
internal fun InlineDictationReview(
    transcript: String,
    onTranscriptChange: (String) -> Unit,
    onRecordMore: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onUseInComposer: () -> Unit,
    onSend: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(Space.sm)) {
        Text(stringResource(R.string.dictation_review_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.dictation_review_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs)
        )
        OutlinedTextField(
            value = transcript,
            onValueChange = { onTranscriptChange(it.take(12_000)) },
            modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
            minLines = 2,
            maxLines = 5,
            label = { Text(stringResource(R.string.dictation_transcript_label)) },
            supportingText = { Text(stringResource(R.string.dictation_transcript_hint)) }
        )
        Row(
            Modifier.fillMaxWidth().padding(top = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
            TextButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Replay, contentDescription = null)
                Text(stringResource(R.string.action_retry), modifier = Modifier.padding(start = Space.xs))
            }
            TextButton(onClick = onRecordMore, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Text(stringResource(R.string.dictation_add_more), modifier = Modifier.padding(start = Space.xs))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onUseInComposer,
                enabled = transcript.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.dictation_edit_draft)) }
            Button(
                onClick = onSend,
                enabled = transcript.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.dictation_send)) }
        }
    }
}

@Composable
internal fun InlineDictationError(message: String, onRetry: () -> Unit, onKeyboard: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(Space.sm),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(Modifier.padding(Space.md)) {
            Text(stringResource(R.string.dictation_error_title), style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Space.xs))
            Row(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                TextButton(onClick = onKeyboard) { Text(stringResource(R.string.dictation_use_keyboard)) }
                Button(onClick = onRetry) { Text(stringResource(R.string.dictation_record_again)) }
            }
        }
    }
}

private fun formatDictationElapsed(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}
