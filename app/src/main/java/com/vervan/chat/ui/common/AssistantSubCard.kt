package com.vervan.chat.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.vervanSuccess
import com.vervan.chat.ui.theme.vervanWarning

/** §7.2.2 — the color-coded family a chat turn's non-message content belongs to: reasoning
 * (neutral), sources (informational/success), a reversible tool call (amber warning), an
 * external/irreversible action (error), and a plain tool result. One enum instead of every
 * screen picking its own ad hoc accent per card type. */
enum class SubCardKind { Reasoning, Sources, Memory, ReversibleTool, ExternalAction, ToolResult, ContextOmission }

@Composable
private fun SubCardKind.color(): Color = when (this) {
    SubCardKind.Reasoning -> MaterialTheme.colorScheme.onSurfaceVariant
    SubCardKind.Sources -> MaterialTheme.colorScheme.vervanSuccess
    SubCardKind.Memory -> MaterialTheme.colorScheme.primary
    SubCardKind.ReversibleTool -> MaterialTheme.colorScheme.vervanWarning
    SubCardKind.ExternalAction -> MaterialTheme.colorScheme.error
    SubCardKind.ToolResult -> MaterialTheme.colorScheme.secondary
    SubCardKind.ContextOmission -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun SubCardKind.icon(): ImageVector = when (this) {
    SubCardKind.Reasoning -> Icons.Filled.Psychology
    SubCardKind.Sources -> Icons.Filled.Description
    SubCardKind.Memory -> Icons.Filled.Psychology
    SubCardKind.ReversibleTool -> Icons.Filled.Build
    SubCardKind.ExternalAction -> Icons.Filled.Warning
    SubCardKind.ToolResult -> Icons.Filled.Build
    SubCardKind.ContextOmission -> Icons.Filled.Warning
}

/**
 * §6/§7.2.2 AssistantSubCard — one shared, collapsible, color-coded card shape for everything
 * that isn't the message text itself: reasoning traces, source citations, tool calls/results,
 * and context-omission notices. Collapsed by default per spec (reasoning especially).
 */
@Composable
fun AssistantSubCard(
    kind: SubCardKind,
    title: String,
    modifier: Modifier = Modifier,
    collapsible: Boolean = true,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val color = kind.color()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = collapsible) { expanded = !expanded }
                .padding(Space.md)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(kind.icon(), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                    Text(title, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(start = Space.xs))
                }
                if (collapsible) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = color
                    )
                }
            }
            if (expanded || !collapsible) {
                Column(Modifier.padding(top = Space.sm)) { content() }
            }
        }
    }
}
