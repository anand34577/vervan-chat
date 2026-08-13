package com.vervan.chat.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space

/**
 * A compact, repeatable orientation card for feature screens. The same pattern is used across
 * Library, personas, and workspaces so people always know what an area is for before they act.
 */
@Composable
fun ContextGuideCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    accentIndex: Int = 0,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAffordance(
                icon = icon,
                size = IconAffordanceSize.Default,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
    }
}
