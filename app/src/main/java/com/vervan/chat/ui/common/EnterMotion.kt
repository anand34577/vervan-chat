package com.vervan.chat.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * A one-shot fade + gentle rise on first composition, for the piece of a screen that's the first
 * thing the eye lands on (a hero, an empty state) — not for routine content that scrolls past.
 * Respects [rememberReducedMotion] the same way [LoadingSkeletonRow]'s pulse and NavGraph's
 * screen transitions do, rather than adding a fourth place in the app that has to be told
 * separately about the system "remove animations" setting.
 *
 * `remember` keys this to the call site's composition, so it fires once when that content first
 * appears and does not re-trigger on ordinary recomposition (a search query changing empty-state
 * copy, a StateFlow re-emitting the same hero data, etc).
 */
@Composable
fun EnterMotion(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reducedMotion = rememberReducedMotion()
    var visible by remember { mutableStateOf(reducedMotion) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reducedMotion) EnterTransition.None
        else fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 6 }
    ) {
        content()
    }
}
