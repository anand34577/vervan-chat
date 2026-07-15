package com.vervan.chat.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space

/**
 * §6/§5.4 — the shared shape for any durable background job (model import, KB indexing,
 * backup, workflow run): stage label, progress, elapsed/remaining estimate, and up to two
 * actions. Previously every job surface (Job Queue, Knowledge import, Model import) built its
 * own ad hoc progress row.
 */
@Composable
fun JobProgressCard(
    title: String,
    stage: String,
    progress: Float?,
    modifier: Modifier = Modifier,
    elapsedLabel: String? = null,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(Space.lg)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                elapsedLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                stage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs, bottom = Space.sm)
            )
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (primaryActionLabel != null || secondaryActionLabel != null) {
                Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.End) {
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        TextButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
                    }
                    if (primaryActionLabel != null && onPrimaryAction != null) {
                        TextButton(onClick = onPrimaryAction) { Text(primaryActionLabel) }
                    }
                }
            }
        }
    }
}
