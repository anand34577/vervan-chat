package com.vervan.chat.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.ModernistTokens

/**
 * Compact rectangular binary toggle.
 *
 * The 48dp wrapper is transparent and exists only for touch accessibility. The track is the
 * only visible container, so toggles do not acquire the extra bordered/padded shell that made
 * them look like nested cards in settings rows.
 */
@Composable
fun VervanToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackShape = MaterialTheme.shapes.small
    val thumbShape = MaterialTheme.shapes.extraSmall
    val thumbOffset: Dp by animateDpAsState(
        targetValue = if (checked) ModernistTokens.Component.toggleThumbOffset else 0.dp,
        label = "vervan-toggle-thumb"
    )
    val trackColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val trackContentColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            // Keep a comfortable hit target without painting a second visible frame around the
            // 48x28 visual track.
            .sizeIn(
                minWidth = ModernistTokens.Component.minTouchTarget,
                minHeight = ModernistTokens.Component.minTouchTarget,
            )
            .toggleable(
                value = checked,
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .semantics {
                stateDescription = if (checked) "On" else "Off"
                role = Role.Switch
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(ModernistTokens.Component.toggleTrackWidth)
                .height(ModernistTokens.Component.toggleTrackHeight),
            shape = trackShape,
            color = trackColor,
            contentColor = trackContentColor,
            border = BorderStroke(ModernistTokens.Component.innerRule, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                Modifier.fillMaxSize().padding(ModernistTokens.Component.toggleTrackInset),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    modifier = Modifier
                        .offset(x = thumbOffset)
                        .size(ModernistTokens.Component.toggleThumbSize)
                        .shadow(ModernistTokens.Component.innerRule, thumbShape),
                    shape = thumbShape,
                    color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface,
                    contentColor = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (checked) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
