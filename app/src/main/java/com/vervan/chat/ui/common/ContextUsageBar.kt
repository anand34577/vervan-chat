package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.vervanWarning

/** One named slice of the context budget (context inspector: System/Persona/Project/
 * Folder/Memory/Sources/History/Current message/Reserved output). */
data class ContextSlice(val label: String, val tokens: Int, val color: androidx.compose.ui.graphics.Color)

/**
 * ContextUsageBar — a segmented bar showing how the context window is spent, plus
 * a plain-language summary line. Standard mode should pass a human translation like "Balanced
 * · about 18-30 typical messages"; Expert mode passes the exact token breakdown as [slices].
 */
@Composable
fun ContextUsageBar(
    usedTokens: Int,
    totalTokens: Int,
    summary: String,
    modifier: Modifier = Modifier,
    slices: List<ContextSlice> = emptyList()
) {
    val fraction = if (totalTokens <= 0) 0f else (usedTokens.toFloat() / totalTokens).coerceIn(0f, 1f)
    val nearLimit = fraction >= 0.9f
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(8.dp)) {
            if (slices.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .background(if (nearLimit) MaterialTheme.colorScheme.vervanWarning else MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    )
                }
            } else {
                val total = slices.sumOf { it.tokens }.coerceAtLeast(1)
                slices.forEach { slice ->
                    val weight = slice.tokens.toFloat().coerceAtLeast(total * 0.001f)
                    Box(
                        Modifier
                            .weight(weight)
                            .height(8.dp)
                            .background(slice.color, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
        Text(
            summary,
            style = MaterialTheme.typography.labelSmall,
            color = if (nearLimit) MaterialTheme.colorScheme.vervanWarning else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs)
        )
        if (slices.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = Space.xs),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                slices.filter { it.tokens > 0 }.forEach { slice ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(slice.color, CircleShape))
                        Text(
                            slice.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(start = Space.xs)
                        )
                        Text("~${slice.tokens}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
