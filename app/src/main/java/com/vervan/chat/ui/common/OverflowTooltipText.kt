package com.vervan.chat.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * A label that exposes its complete value when layout has to ellipsize it.
 *
 * Material tooltips support pointer hover and touch-and-hold, while the explicit semantics value
 * ensures accessibility services receive the complete label rather than only its visible prefix.
 * Keeping the tooltip conditional avoids adding a redundant long-press target for short labels.
 *
 * [modifier] (typically `Modifier.weight(1f)` from a caller's `Row`, e.g. a chat-list row's
 * title next to its timestamp) is applied to a plain [Box], not to [TooltipBox] directly.
 * [TooltipBox] doesn't reliably honor a `weight` modifier applied to itself — a Row measuring a
 * weighted [TooltipBox] next to an unweighted trailing sibling (a timestamp, in every caller of
 * this composable) could end up granting it the *unconstrained* intrinsic width of the full,
 * untruncated [text] instead of its fair share, squeezing that trailing sibling out of the row
 * entirely rather than letting the title ellipsize — exactly the "long chat title hides the
 * timestamp" bug this fixes. A plain [Box] is a well-behaved `Row`/`Column` weight participant,
 * so resolving the modifier there and only then handing [TooltipBox] a hard `fillMaxWidth()`
 * (bounded by whatever width the Box was actually given) forces the inner [Text] to truncate
 * within that bound instead of expanding past it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverflowTooltipText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = 1
) {
    var hasVisualOverflow by remember(text, maxLines) { mutableStateOf(false) }
    val tooltipState = rememberTooltipState()

    Box(modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(text) } },
            state = tooltipState,
            modifier = Modifier.fillMaxWidth(),
            enableUserInput = hasVisualOverflow
        ) {
            Text(
                text = text,
                modifier = Modifier.clearAndSetSemantics { contentDescription = text },
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { hasVisualOverflow = it.hasVisualOverflow }
            )
        }
    }
}
