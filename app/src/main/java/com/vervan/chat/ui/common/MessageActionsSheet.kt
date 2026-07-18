package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.SpeakerNotes
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.theme.vervanSubtleDividerColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space

/**
 * Long-press / context menu sheet for chat messages. Replaces the previous tap-to-expand action
 * row pattern — modern chat apps (WhatsApp/Telegram/iMessage) all use long-press as the primary
 * message interaction, so this matches user expectations and frees up bubble real estate.
 *
 * Layout, top to bottom:
 *  1. Reaction strip — small set of emoji reactions (priority: must be tappable in one motion)
 *  2. Primary actions — Copy / Speak / Bookmark / Share (chips, 4 most common)
 *  3. Secondary actions — Edit / Regenerate / Fork / Save as prompt / Add to note / Delete
 *
 * Each row is a 48dp-min Row with leading IconAffordance so the menu scans the same way the
 * rest of the app does. The reaction set is intentionally tiny (👍 👎 ❤️ 🎯 💡 😂) so the
 * strip stays single-row on phone widths.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MessageActionsSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    reactions: List<String> = defaultReactions,
    onReact: ((String) -> Unit)? = null,
    selectedReaction: String? = null,
    actions: List<MessageAction> = emptyList(),
    destructiveActions: List<MessageAction> = emptyList(),
    showReactionsHeader: Boolean = true
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(bottom = Space.xxl), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            if (onReact != null && reactions.isNotEmpty()) {
                ReactionStrip(
                    reactions = reactions,
                    onReact = { emoji -> onReact(emoji); onDismiss() },
                    selected = selectedReaction,
                    showHeader = showReactionsHeader
                )
            }
            if (actions.isNotEmpty()) {
                ActionGrid(actions) { it.onClick(); onDismiss() }
            }
            if (destructiveActions.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.lg)
                        .height(1.dp)
                        .background(vervanSubtleDividerColor())
                )
                destructiveActions.forEach { action ->
                    ActionRow(action, isDestructive = true) { action.onClick(); onDismiss() }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionStrip(
    reactions: List<String>,
    onReact: (String) -> Unit,
    selected: String?,
    showHeader: Boolean
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
        if (showHeader) {
            Text(
                "React",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.sm)
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            reactions.forEach { emoji ->
                val isSelected = emoji == selected
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 44.dp else 40.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onReact(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionGrid(actions: List<MessageAction>, onClick: (MessageAction) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        maxItemsInEachRow = 4
    ) {
        actions.take(4).forEach { action ->
            ActionChip(action, onClick = { onClick(action) })
        }
    }
    actions.drop(4).forEach { action ->
        ActionRow(action, isDestructive = false) { onClick(action) }
    }
}

@Composable
private fun ActionChip(action: MessageAction, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(Space.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            action.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Text(
            action.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.xs)
        )
    }
}

@Composable
private fun ActionRow(action: MessageAction, isDestructive: Boolean, onClick: () -> Unit) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconAffordance(
            icon = action.icon,
            size = IconAffordanceSize.Compact,
            tint = tint,
            containerColor = if (isDestructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        )
        Text(
            action.label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.padding(start = Space.md)
        )
    }
}

data class MessageAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/** Default emoji reaction set — kept small so the strip is one row on phone widths.
 *  Matches the most-common reactions across modern chat apps. */
val defaultReactions = listOf("👍", "👎", "❤️", "🎯", "💡", "😂")

/** Standard message actions used everywhere. Callers can filter / reorder. */
fun standardMessageActions(
    onCopy: () -> Unit = {},
    onSpeak: () -> Unit = {},
    onBookmark: () -> Unit = {},
    onShare: () -> Unit = {},
    onEdit: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    onFork: () -> Unit = {},
    onSaveAsPrompt: () -> Unit = {},
    onAddToNote: () -> Unit = {},
    onDelete: () -> Unit = {}
): Pair<List<MessageAction>, List<MessageAction>> {
    val primary = listOf(
        MessageAction("Copy", Icons.Filled.ContentCopy, onCopy),
        MessageAction("Listen", Icons.AutoMirrored.Filled.VolumeUp, onSpeak),
        MessageAction("Save", Icons.Filled.BookmarkBorder, onBookmark),
        MessageAction("Share", Icons.Filled.IosShare, onShare)
    )
    val secondary = listOf(
        MessageAction("Edit & resend", Icons.Filled.Edit, onEdit),
        MessageAction("Try again", Icons.Filled.Refresh, onRegenerate),
        MessageAction("Branch from here", Icons.Filled.AccountTree, onFork),
        MessageAction("Save as prompt", Icons.AutoMirrored.Filled.SpeakerNotes, onSaveAsPrompt),
        MessageAction("Add to note", Icons.Outlined.BookmarkBorder, onAddToNote),
        MessageAction("Delete", Icons.Filled.DeleteOutline, onDelete)
    )
    return primary to secondary
}
