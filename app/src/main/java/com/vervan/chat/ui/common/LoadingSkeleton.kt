package com.vervan.chat.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space

/**
 * §6/§5.4 delay-aware list/card loading placeholder. A static tonal block under reduced
 * motion (§7.9.4/§11.5 — shimmer must not be the only way progress reads), a gentle alpha
 * pulse otherwise.
 */
@Composable
private fun skeletonColor(): Color {
    val reducedMotion = rememberReducedMotion()
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    if (reducedMotion) return base
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    return base.copy(alpha = alpha)
}

@Composable
private fun SkeletonBlock(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.clip(MaterialTheme.shapes.small).background(skeletonColor())
    )
}

/** One list-row shaped placeholder: leading icon block + two text lines. */
@Composable
fun LoadingSkeletonRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        SkeletonBlock(Modifier.size(36.dp))
        Column(Modifier.padding(start = Space.md).weight(1f)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(14.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(11.dp).padding(top = Space.xs))
        }
    }
}

/** One card-shaped placeholder: title line + two body lines, for grid/card lists. */
@Composable
fun LoadingSkeletonCard(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(Space.lg)) {
        SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(16.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(12.dp).padding(top = Space.md))
        SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(12.dp).padding(top = Space.xs))
    }
}

/** One assistant-message-shaped placeholder for the chat feed while a response streams in
 * before its first token arrives. */
@Composable
fun LoadingSkeletonMessage(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.sm)) {
        SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(14.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(14.dp).padding(top = Space.xs))
        SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(14.dp).padding(top = Space.xs))
    }
}

@Composable
fun LoadingSkeletonList(rows: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        repeat(rows) { LoadingSkeletonRow() }
    }
}
