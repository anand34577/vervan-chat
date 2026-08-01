package com.vervan.chat.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanGridMinWidth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResponsiveActions(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        content = content
    )
}

/**
 * Adaptive non-lazy card grid for short collections. Large catalogs should continue to use
 * LazyVerticalGrid. Callers use FlowRowScope.weight(1f) on each item so every row shares space.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptiveCardFlow(
    modifier: Modifier = Modifier,
    minItemWidth: Dp = VervanGridMinWidth.standardCard,
    maxColumns: Int = 3,
    content: @Composable FlowRowScope.(columns: Int) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = Space.sm
        val availableColumns = (
            (maxWidth.value + gap.value) / (minItemWidth.value + gap.value)
        ).toInt().coerceAtLeast(1)
        val columns = availableColumns.coerceAtMost(maxColumns.coerceAtLeast(1))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            maxItemsInEachRow = columns
        ) {
            content(columns)
        }
    }
}
