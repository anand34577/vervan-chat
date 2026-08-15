package com.vervan.chat.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanContentWidth

/**
 * The shared full-screen empty state used across lists, queues, reviews, and projects.
 * It renders immediately, keeps copy concise, and supports one primary and one secondary action.
 * Purpose-built inline empty states remain appropriate inside larger content surfaces.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    centered: Boolean = false,
) {
    // An empty state is exactly the moment there's nothing else on screen competing for
    // attention, so it's a cheap place for the shared one-shot entrance (see EnterMotion) instead
    // of just popping in fully formed.
    EnterMotion {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconAffordance(icon = icon, size = IconAffordanceSize.Feature)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = Space.md).semantics { heading() }
            )
        }
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .widthIn(max = VervanContentWidth.reading)
                .padding(top = Space.sm),
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .widthIn(max = VervanContentWidth.action)
                    .padding(top = Space.lg),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(actionLabel)
            }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            TextButton(
                onClick = onSecondaryAction,
                modifier = Modifier.padding(top = Space.xs)
            ) {
                Text(secondaryActionLabel)
            }
        }
    }
    }
}
