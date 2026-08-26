package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.vervanSuccess

/**
 * DiffViewer — AI text transforms (Writing, Developer, Notes actions) must show
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
        Text(stringResource(R.string.diff_original), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(stringResource(R.string.diff_suggested), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            ResponsiveActions(Modifier.padding(top = Space.md)) {
                onCancel?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_cancel)) } }
                onCopy?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_copy)) } }
                onInsertBelow?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_insert_below)) } }
                onReplace?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_replace)) } }
            }
        }
    }
}
