package com.vervan.chat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/**
 * A categorical accent palette for icon tiles so a grid of tools/actions reads as a colorful,
 * scannable set instead of a wall of identical amber chips. Each pair is (container, on-container),
 * tuned to sit on the dark Nomad surface and to stay legible in light mode. Pick by stable index
 * ([vervanAccentFor]) so the same item always gets the same color.
 */
data class VervanAccent(val container: Color, val onContainer: Color)

private val AccentPalette = listOf(
    VervanAccent(Color(0xFF4A3A20), Color(0xFFF3C27B)), // amber
    VervanAccent(Color(0xFF1A2440), Color(0xFF9DBBFF)), // blue
    VervanAccent(Color(0xFF1C4A2C), Color(0xFF8CE6AC)), // green
    VervanAccent(Color(0xFF3A2A66), Color(0xFFC9B3FF)), // violet
    VervanAccent(Color(0xFF632233), Color(0xFFFFAABB)), // rose
    VervanAccent(Color(0xFF0E3F45), Color(0xFF87DCE6)), // teal
    VervanAccent(Color(0xFF553017), Color(0xFFFFB48A))  // orange
)

/**
 * Icon-tile colours for [index]. Tiles are now one calm neutral (like ChatGPT's and Claude's
 * monochrome icons): a rainbow of tiles on every list made screens feel loud, and the fixed
 * dark palette above also rendered as dark blocks in light mode. The index is kept so callers
 * and the palette stay available if a screen ever needs categorical colour again.
 */
@Composable
@ReadOnlyComposable
@Suppress("UNUSED_PARAMETER")
fun vervanAccentFor(index: Int): VervanAccent = VervanAccent(
    container = MaterialTheme.colorScheme.surfaceContainerHighest,
    onContainer = MaterialTheme.colorScheme.onSurface
)

/** The original categorical palette, for the rare place colour carries meaning. */
fun vervanCategoricalAccentFor(index: Int): VervanAccent = AccentPalette[((index % AccentPalette.size) + AccentPalette.size) % AccentPalette.size]

/** Number of distinct categorical accents available. */
val vervanAccentCount: Int get() = AccentPalette.size

/**
 * The one brand fill: the active accent as a flat colour. The app deliberately uses no gradients;
 * identity comes from the accent itself, applied to the few places that carry it (send button,
 * the user's bubbles, the assistant mark). Returned as a [Brush] so it drops into `background()`.
 */
@Composable
@ReadOnlyComposable
fun vervanBrandFill(): Brush = SolidColor(MaterialTheme.colorScheme.primary)
