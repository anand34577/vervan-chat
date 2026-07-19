package com.vervan.chat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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

/** Stable categorical accent for [index] (wraps around the palette). */
fun vervanAccentFor(index: Int): VervanAccent = AccentPalette[((index % AccentPalette.size) + AccentPalette.size) % AccentPalette.size]

/** Number of distinct categorical accents available. */
val vervanAccentCount: Int get() = AccentPalette.size

/**
 * The Aurora brand gradient — the one gradient in the app. Runs from the active accent's primary
 * into a hue-shifted companion (primary blended toward secondary), so it always harmonizes with
 * whatever accent theme the user picked instead of being a fixed two-color stamp. Used sparingly:
 * the Create button in the nav dock, the user's chat bubbles, and the assistant avatar — the three
 * places that carry the product's identity.
 */
@Composable
@ReadOnlyComposable
fun vervanBrandGradient(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.linearGradient(
        listOf(scheme.primary, lerp(scheme.primary, scheme.secondary, 0.55f))
    )
}
