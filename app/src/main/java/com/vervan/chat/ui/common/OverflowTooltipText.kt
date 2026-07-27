package com.vervan.chat.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
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

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = tooltipState,
        modifier = modifier,
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
