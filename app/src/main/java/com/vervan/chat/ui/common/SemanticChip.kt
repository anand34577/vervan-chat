package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.VervanSuccess
import com.vervan.chat.ui.theme.VervanWarn

/**
 * Semantic tone for a status word — separate from the app's accent color, so a
 * "Ready"/"Indexing"/"Failed" style state reads the same everywhere regardless of
 * which accent color the user has picked in Settings.
 */
enum class ChipTone { Success, Warning, Error, Neutral }

/**
 * One shared "tinted background, colored label" status chip. Previously reimplemented
 * independently as StatusChip (Knowledge), SpecChip (Model Manager) and MiniBadge
 * (Chat list) — all three were the same `color.copy(alpha = 0.15f)` background pattern.
 */
@Composable
fun SemanticChip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val color = when (tone) {
        ChipTone.Success -> VervanSuccess
        ChipTone.Warning -> VervanWarn
        ChipTone.Error -> MaterialTheme.colorScheme.error
        ChipTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(100.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
