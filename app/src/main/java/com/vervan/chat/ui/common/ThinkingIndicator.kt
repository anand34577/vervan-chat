package com.vervan.chat.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanMotion

/**
 * Animated "thinking…" indicator shown inside a fresh assistant bubble before the first token
 * streams in. Replaces the silent gap between send→firstToken that previously made the app feel
 * broken on slow models (ChatGPT/Claude/Gemini all have an equivalent).
 *
 * Three pulsing dots (staggered phase) + optional status text. Fades itself out via
 * [AnimatedVisibility] when [visible] flips false — the parent should keep this mounted through
 * the transition so the dots dissolve rather than vanishing mid-pulse.
 *
 * Reduced-motion users see the same dots statically dimmed (no pulsing); semantics announce
 * "Vervan is thinking" via a polite live region so screen-reader users get the same reassurance
 * the visual gives everyone else.
 */
@Composable
fun ThinkingIndicator(
    label: String = "Thinking",
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    AnimatedVisibility(visible = visible, enter = androidx.compose.animation.fadeIn(VervanMotion.emphasizedDecelerate(220)), exit = androidx.compose.animation.fadeOut(VervanMotion.emphasizedAccelerate(160))) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = Space.xs)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Vervan is thinking"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            ThinkingDots(reducedMotion = reducedMotion)
            if (label.isNotBlank()) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Three staggered pulsing dots. The M3 expressive variant uses a gentle scale spring rather
 * than the legacy alpha blink — feels softer, matches the rest of the app's motion.
 *
 * Exposed as its own composable because the same dot pattern is reused inside the composer
 * (when a generation is in-flight) and inside SystemStatusStrip's "Running" tone.
 */
@Composable
fun ThinkingDots(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion()
) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val scales = (0..2).map { index ->
        transition.animateFloat(
            initialValue = if (reducedMotion) 0.75f else 0.6f,
            targetValue = if (reducedMotion) 0.75f else 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (reducedMotion) 1 else 720, delayMillis = index * 160, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot$index"
        )
    }
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = Space.md, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        scales.forEach { animatable ->
            val scale by animatable
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
