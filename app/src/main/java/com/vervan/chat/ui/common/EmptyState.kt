package com.vervan.chat.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanMotion

/**
 * The icon + title + explanatory body (+ optional CTAs) empty state already used by
 * Notes/Folders/Collections/JobQueue/RecycleBin/StudyReview — as a shared component so
 * screens that currently render nothing when empty (Projects, a workspace's chat list,
 * a brand-new chat) get the same reassurance for free instead of a blank screen.
 *
 * An empty state is usually the *first* thing a user sees on a fresh install, so it now settles
 * in with a gentle fade + rise (skipped under reduced motion) rather than snapping in cold, and
 * supports an optional [secondaryActionLabel] for the common "primary + learn-more / alternate"
 * pairing (e.g. "Import a model" + "See recommended models").
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
    onSecondaryAction: (() -> Unit)? = null
) {
    val reducedMotion = rememberReducedMotion()
    // Play the reveal exactly once per appearance. `remember { true }` after an initial false
    // flip drives AnimatedVisibility from hidden → shown on first composition.
    var shown by remember { mutableStateOf(reducedMotion) }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }

    AnimatedVisibility(
        visible = shown,
        enter = if (reducedMotion) fadeIn(tween0) else
            fadeIn(VervanMotion.emphasizedDecelerate(360)) +
                slideInVertically(VervanMotion.emphasizedDecelerate(360)) { it / 12 }
    ) {
        Column(
            modifier = modifier.fillMaxSize().padding(Space.xxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconAffordance(icon = icon, size = IconAffordanceSize.Feature)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.lg)
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.xs)
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth().padding(top = Space.lg)) {
                    Text(actionLabel)
                }
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                TextButton(onClick = onSecondaryAction, modifier = Modifier.padding(top = Space.xs)) {
                    Text(secondaryActionLabel)
                }
            }
        }
    }
}

private val tween0 = androidx.compose.animation.core.tween<Float>(0)
