package com.vervan.chat.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Central border helper. Multiple analysts flagged that the previous pattern
 * `outlineVariant.copy(alpha = 0.38f–0.58f)` produced sub-3:1 decorative borders that
 * disappeared on the warm-amber dark palette, and that the theme already has a high-contrast
 * override (`Theme.kt#withHighContrast`) that *no call site could amplify further* because each
 * one was hardcoding its own alpha on top of `outlineVariant`.
 *
 * This helper:
 *  - reads `outlineVariant` from the resolved color scheme so the high-contrast pass already
 *    applied at the theme level is the floor (not the surface it gets attenuated away from),
 *  - exposes one knob (`prominence`) that maps to consistent alpha bands so screens stop
 *    reaching for arbitrary `0.38f`/`0.42f`/`0.55f` values, and
 *  - lets a screen opt a specific border up to `outline` (the "important boundary" token,
 *    per M3 §shape/color) when it really is structural (text-field-like) rather than decorative.
 *
 * Usage:
 *   `border = vervanBorder(VervanBorderProminence.Subtle)`
 *   `border = vervanBorder(VervanBorderProminence.Standard, width = 1.5.dp)`
 */
enum class VervanBorderProminence {
    /** Barely-there divider tint — for nested cards where surface alone is doing the separation. */
    Subtle,
    /** Default decorative border for cards/tiles/sheets. Replaces the old 0.45–0.55 alphas. */
    Standard,
    /** Visible separator — section dividers, sheets, list rows. */
    Emphasized,
    /** M3 `outline` — structural boundaries (text-field-equivalents, focused surfaces). */
    Structural
}

@Composable
fun vervanBorder(
    prominence: VervanBorderProminence = VervanBorderProminence.Standard,
    width: Dp = 1.dp,
    color: Color? = null
): BorderStroke {
    val base = color ?: MaterialTheme.colorScheme.outlineVariant
    // The high-contrast pass in Theme.kt already pulled outlineVariant up to outline strength,
    // so even `Subtle` reads cleanly there. In default themes these alphas are picked to land
    // just above the WCAG 3:1 decorative minimum on the warm-amber dark palette.
    val alpha = when (prominence) {
        VervanBorderProminence.Subtle -> 0.65f
        VervanBorderProminence.Standard -> 1f
        VervanBorderProminence.Emphasized -> 1f
        VervanBorderProminence.Structural -> 1f
    }
    val resolved = when (prominence) {
        VervanBorderProminence.Subtle -> base.copy(alpha = alpha)
        VervanBorderProminence.Standard -> base
        // Emphasized/Structural escalate to the outline token rather than tinting outlineVariant
        // further — M3 explicitly reserves `outline` for important boundaries; this is the only
        // place we bridge from one to the other so call sites don't.
        VervanBorderProminence.Emphasized,
        VervanBorderProminence.Structural -> if (color == null) MaterialTheme.colorScheme.outline else base
    }
    return BorderStroke(width, resolved)
}
