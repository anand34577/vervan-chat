package com.vervan.chat.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Screen-to-screen motion, following the Material motion system's timings
 * (m3.material.io/styles/motion/transitions). Durations and easings are the published ones, so
 * the app moves like the platform and the Google apps people already know.
 */
internal object MotionSpec {
    /** Shared axis and fade through both run over 300ms. */
    private const val DURATION_MS = 300
    /** The outgoing screen is gone in the first 30% so the two never read as overlapping. */
    private const val OUTGOING_MS = 90
    /** The incoming screen fades in over the remaining time. */
    private const val INCOMING_MS = DURATION_MS - OUTGOING_MS
    const val REDUCED_MS = 150

    /** Shared axis X, incoming: slides from [offsetPx] while fading in after the outgoing fade. */
    fun sharedAxisIn(offsetPx: Int): EnterTransition =
        slideInHorizontally(tween(DURATION_MS, easing = FastOutSlowInEasing)) { offsetPx } +
            fadeIn(tween(INCOMING_MS, delayMillis = OUTGOING_MS, easing = LinearOutSlowInEasing))

    /** Shared axis X, outgoing: slides toward [offsetPx] while fading out quickly. */
    fun sharedAxisOut(offsetPx: Int): ExitTransition =
        slideOutHorizontally(tween(DURATION_MS, easing = FastOutSlowInEasing)) { offsetPx } +
            fadeOut(tween(OUTGOING_MS, easing = FastOutLinearInEasing))

    /** Fade through, incoming: waits for the outgoing fade, then fades in from 92% scale. */
    fun fadeThroughIn(): EnterTransition =
        fadeIn(tween(INCOMING_MS, delayMillis = OUTGOING_MS, easing = LinearOutSlowInEasing)) +
            scaleIn(tween(INCOMING_MS, delayMillis = OUTGOING_MS, easing = LinearOutSlowInEasing), initialScale = 0.92f)

    /**
     * Swipe back, outgoing: the page slides off whole, in the direction of the swipe. Linear
     * because the gesture drives its progress; easing would make the page lag the finger.
     */
    fun swipeBackOut(fromRightEdge: Boolean): ExitTransition =
        slideOutHorizontally(tween(DURATION_MS, easing = LinearEasing)) { width -> if (fromRightEdge) -width else width }

    /** Swipe back, incoming: the previous page moves in from a quarter-width behind it. */
    fun swipeBackIn(fromRightEdge: Boolean): EnterTransition =
        slideInHorizontally(tween(DURATION_MS, easing = LinearEasing)) { width -> if (fromRightEdge) width / 4 else -width / 4 } +
            fadeIn(tween(DURATION_MS, easing = LinearEasing), initialAlpha = 0.6f)

    /** Fade through, outgoing: a short fade with no movement. */
    fun fadeThroughOut(): ExitTransition = fadeOut(tween(OUTGOING_MS, easing = FastOutLinearInEasing))
}
