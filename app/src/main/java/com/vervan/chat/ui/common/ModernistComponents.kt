package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.ModernistTokens
import com.vervan.chat.ui.theme.Space

/** A screen-level title treatment shared by every route. */
@Composable
fun ModernistScreenHeader(
    eyebrow: String,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(bottom = Space.md)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke(this)
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = Space.xs),
        )
        body?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A segmented meter for runtime state: downloads, indexing, context, storage, and study. */
@Composable
fun ModernistMeter(
    value: Float,
    modifier: Modifier = Modifier,
    segments: Int = 12,
    label: String? = null,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    val clamped = value.coerceIn(0f, 1f)
    Column(
        modifier
            .fillMaxWidth()
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f) },
    ) {
        Row(
            Modifier.fillMaxWidth().height(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(segments.coerceAtLeast(1)) { index ->
                val threshold = (index + 1).toFloat() / segments
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .background(
                            if (clamped >= threshold - (0.5f / segments)) color
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.shapes.extraSmall,
                        ),
                )
            }
        }
        label?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

/** A ruled metric strip used for the prototype's at-a-glance runtime summaries. */
@Composable
fun ModernistMetricStrip(
    metrics: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small)
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        metrics.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                androidx.compose.material3.VerticalDivider(
                    modifier = Modifier.height(30.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = Space.md, vertical = Space.sm)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Compact tag treatment for model capabilities, source state, and filters. */
@Composable
fun ModernistTag(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.extraSmall)
            .border(ModernistTokens.Component.innerRule, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Space.sm, vertical = Space.xs),
    )
}
