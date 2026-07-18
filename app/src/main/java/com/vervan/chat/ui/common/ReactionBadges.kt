package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space

/**
 * Inline reaction badges rendered under a message bubble. Matches WhatsApp/Telegram/iMessage
 * convention: each unique emoji + count appears as a small pill, the user's own reaction is
 * highlighted with a primary-tinted border.
 *
 * Reactions are display-only here; the call site owns the reaction data model and provides
 * [onReact] for tap-to-toggle (tapping a reaction you've made removes it).
 */
@Composable
fun ReactionBadges(
    reactions: List<MessageReaction>,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (reactions.isEmpty()) return
    Row(
        modifier = modifier.padding(top = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        reactions.forEach { reaction ->
            val isSelected = reaction.mine
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(100)
                    )
                    .clickable { onReact(reaction.emoji) }
                    .padding(horizontal = Space.sm, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                Text(reaction.emoji, style = MaterialTheme.typography.labelMedium)
                if (reaction.count > 1) {
                    Text(
                        reaction.count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

data class MessageReaction(
    val emoji: String,
    val count: Int,
    val mine: Boolean = false
)
