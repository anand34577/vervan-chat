package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.vervanSuccess

/**
 * §6/§7.6.2/§7.8 DiffViewer — AI text transforms (Writing, Developer, Notes actions) must show
 * a before/after review before replacing content, never overwrite silently. This is a
 * line-level before/after, not a character-level diff algorithm — good enough to review a
 * rewritten paragraph or fixed code block without pulling in a diff-match-patch dependency.
 */
@Composable
fun DiffViewer(
    original: String,
    transformed: String,
    modifier: Modifier = Modifier,
    onReplace: (() -> Unit)? = null,
    onInsertBelow: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        Text("Original", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth().padding(top = Space.xs, bottom = Space.md)) {
            Text(
                original,
                style = MaterialTheme.typography.bodySmall,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                    .verticalScroll(rememberScrollState())
                    .padding(Space.md)
            )
        }
        Text("Suggested", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth().padding(top = Space.xs)) {
            Text(
                transformed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.vervanSuccess.copy(alpha = 0.08f))
                    .verticalScroll(rememberScrollState())
                    .padding(Space.md)
            )
        }
        if (onReplace != null || onInsertBelow != null || onCopy != null || onCancel != null) {
            Row(Modifier.fillMaxWidth().padding(top = Space.md), horizontalArrangement = Arrangement.End) {
                onCancel?.let { TextButton(onClick = it) { Text("Cancel") } }
                onCopy?.let { TextButton(onClick = it) { Text("Copy") } }
                onInsertBelow?.let { TextButton(onClick = it) { Text("Insert below") } }
                onReplace?.let { TextButton(onClick = it) { Text("Replace") } }
            }
        }
    }
}
