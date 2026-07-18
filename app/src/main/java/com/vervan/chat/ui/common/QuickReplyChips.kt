package com.vervan.chat.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.ui.theme.vervanBorder

/**
 * Suggestion chips rendered under a completed assistant reply (or empty state). Mirrors the
 * ChatGPT/Gemini pattern of "Continue / Summarize / Make shorter / Try again" follow-ups, but
 * each chip carries an [icon] so non-English users (and skim-readers) get a visual anchor.
 *
 * The default suggested set ([defaultQuickReplies]) is opinionated and small — three to five
 * chips is the sweet spot before users stop scanning. Callers can pass an alternate set for
 * context-aware suggestions (e.g., after a code block: "Explain / Refactor / Add tests").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickReplyChips(
    suggestions: List<QuickReply>,
    onClick: (QuickReply) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true
) {
    AnimatedVisibility(visible = visible) {
        FlowRow(
            modifier = modifier.padding(top = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            maxItemsInEachRow = 3
        ) {
            suggestions.take(6).forEach { suggestion ->
                QuickReplyChip(suggestion, onClick = { onClick(suggestion) })
            }
        }
    }
}

/** One suggestion chip. Pill shape, surface-container fill, leading icon for affordance. */
@Composable
private fun QuickReplyChip(suggestion: QuickReply, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(VervanExtraShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = VervanExtraShapes.pill
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        suggestion.icon?.let {
            Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
        }
        Text(
            text = suggestion.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

data class QuickReply(
    val label: String,
    val prompt: String,
    val icon: ImageVector? = null
)

/** Default suggestions rendered after a completed assistant response. Curated to be useful
 *  across most contexts (writing, reasoning, translation) without being noisy. */
fun defaultQuickReplies(): List<QuickReply> = listOf(
    QuickReply("Continue", "Continue", Icons.AutoMirrored.Filled.ArrowForward),
    QuickReply("Summarize", "Summarize this in 3 bullets", Icons.Filled.AutoAwesome),
    QuickReply("Make shorter", "Make this shorter", Icons.Filled.Compress),
    QuickReply("Regenerate", "__regenerate__", Icons.Filled.Refresh),
    QuickReply("Translate", "Translate this to Hindi", Icons.Filled.Translate)
)
