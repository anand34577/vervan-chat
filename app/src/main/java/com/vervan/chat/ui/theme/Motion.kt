package com.vervan.chat.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Material 3 Expressive motion tokens for Vervan.
 *
 * Spring physics are the M3 Expressive default for *in-screen* component motion (a button
 * growing when pressed, a sheet settling, a bubble entering). Fixed easing/duration specs
 * remain valid for *transitions* (enter/exit, shared axis) where the spec still references them.
 *
 * Reduced-motion respect lives in `ui/common/Accessibility.kt`; this file intentionally does
 * NOT short-circuit to no-op animations because:
 *   (a) M3 Expressive springs already settle quickly,
 *   (b) component callers can check `rememberReducedMotion()` themselves and skip the animation
 *       entirely, which is the appropriate response for "I need this to not move at all."
 */
object VervanMotion {
    /** Default expressive spring — fast, slight overshoot, snappy settle. Use for taps, presses. */
    fun <T> defaultSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Settling spring for bottom sheets, dialogs, FABs — perceptually slower, more spatial. */
    fun <T> settlingSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** Snappy spring for state layer transitions, message bubble entrance. No overshoot. */
    fun <T> fastSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Very slow spring for hero entrances (page transitions, empty-state reveals). */
    fun <T> slowSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessVeryLow
    )

    /** M3 emphasized easing for transitions that begin and end on screen. */
    val emphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val standardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> emphasized(durationMs: Int = 500): DurationBasedAnimationSpec<T> =
        tween(durationMs, easing = emphasizedEasing)

    fun <T> emphasizedDecelerate(durationMs: Int = 400): DurationBasedAnimationSpec<T> =
        tween(durationMs, easing = emphasizedDecelerate)

    fun <T> emphasizedAccelerate(durationMs: Int = 200): DurationBasedAnimationSpec<T> =
        tween(durationMs, easing = emphasizedAccelerate)
}
