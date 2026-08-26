package com.vervan.chat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * A theme-derived categorical palette for icon tiles. The prototype uses multiple accent roles to
 * make task families scannable; Android derives those roles from the active Material color scheme
 * instead of shipping a second hardcoded palette that could ignore light/dark/dynamic themes.
 */
data class VervanAccent(val container: Color, val onContainer: Color)

/** Stable categorical accent for [index] (wraps around semantic roles in the active theme). */
@Composable
@ReadOnlyComposable
fun vervanAccentFor(index: Int): VervanAccent {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        VervanAccent(scheme.primaryContainer, scheme.onPrimaryContainer),
        VervanAccent(scheme.secondaryContainer, scheme.onSecondaryContainer),
        VervanAccent(scheme.tertiaryContainer, scheme.onTertiaryContainer),
        VervanAccent(scheme.surfaceContainerHigh, scheme.onSurface),
        VervanAccent(scheme.surfaceContainerHighest, scheme.onSurface),
        VervanAccent(scheme.surfaceVariant, scheme.onSurfaceVariant)
    )
    return palette[((index % palette.size) + palette.size) % palette.size]
}

/** Number of distinct categorical accents available. */
const val vervanAccentCount: Int = 6

/**
 * The brand fill — the one gradient retained for identity-bearing surfaces. In the Modernist
 * system it follows the active primary/secondary theme roles, so it changes with the selected theme
 * without making layout, contrast, or interaction state depend on a copied prototype color.
 */
@Composable
@ReadOnlyComposable
fun vervanBrandGradient(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.linearGradient(
        listOf(scheme.primary, lerp(scheme.primary, scheme.secondary, 0.55f))
    )
}
