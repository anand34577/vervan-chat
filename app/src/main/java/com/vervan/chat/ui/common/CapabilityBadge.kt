package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanSuccess

enum class Capability(val label: String, val icon: ImageVector) {
    Vision("Vision", Icons.Filled.Image),
    Audio("Audio", Icons.Filled.Mic),
    Tools("Tools", Icons.Filled.Build),
    Thinking("Thinking", Icons.Filled.Psychology)
}

/** Whether a capability is definitely supported/unsupported, or won't be known until the
 * model actually loads (§7.2.2 — "Will be checked when the model loads" is a distinct state
 * from "Unsupported", not the same disabled look). */
enum class CapabilityState { Supported, Unsupported, Unknown }

/**
 * §6 CapabilityBadge — one small chip per model capability (vision/audio/tools/thinking),
 * used on model cards and attachment pickers so support state reads the same everywhere
 * instead of each screen inventing its own icon-only or text-only convention.
 */
@Composable
fun CapabilityBadge(capability: Capability, state: CapabilityState, modifier: Modifier = Modifier) {
    val (color, icon) = when (state) {
        CapabilityState.Supported -> VervanSuccess to capability.icon
        CapabilityState.Unsupported -> MaterialTheme.colorScheme.onSurfaceVariant to capability.icon
        CapabilityState.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Filled.HelpOutline
    }
    val alpha = if (state == CapabilityState.Unsupported) 0.5f else 1f
    Row(
        modifier
            .background(color.copy(alpha = 0.14f * alpha), MaterialTheme.shapes.small)
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color.copy(alpha = alpha), modifier = Modifier.size(14.dp))
        Text(
            capability.label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = alpha),
            modifier = Modifier.padding(start = Space.xs)
        )
    }
}
