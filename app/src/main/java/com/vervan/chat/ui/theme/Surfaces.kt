package com.vervan.chat.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic surface-role system — the missing "elevation" layer.
 *
 * Vervan is a flat + bordered aesthetic (no drop shadows in the base palette): depth is carried
 * by *tinted surface containers* + border prominence, not by shadow. Before this, screens picked
 * `surfaceContainerLow`/`surfaceContainer`/`surfaceContainerHigh` ad hoc and paired each with an
 * arbitrary hardcoded border alpha, so two cards meant to read as "the same kind of thing" often
 * didn't. This maps the five things a surface can *be* to one container tint + one border
 * prominence + an optional shadow elevation, so a card's role is declared once and reads
 * consistently everywhere.
 *
 * Roles (low → high in the stack):
 *  - [Sunken]   wells the eye sits *into* — input backgrounds, read-only value fields.
 *  - [Card]     the default resting card on a page — most content lives here.
 *  - [Raised]   an interactive/nested card that should read as sitting above [Card].
 *  - [Floating] transient surfaces that hover over content — composer, menus, snackbars.
 *  - [Overlay]  modal surfaces on a scrim — dialogs, bottom sheets.
 *
 * Usage:
 *   Card(colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border())
 * or the one-liner convenience [vervanCard] pairing.
 */
enum class SurfaceRole {
    Sunken,
    Card,
    Raised,
    Floating,
    Overlay;

    /** Container tint for this role, resolved from the active color scheme. */
    @Composable
    @ReadOnlyComposable
    fun containerColor(): Color = when (this) {
        Sunken -> MaterialTheme.colorScheme.surfaceContainerLowest
        // One tint step brighter than before, because resting cards no longer carry an outline:
        // the fill alone has to lift them off the page.
        Card -> MaterialTheme.colorScheme.surfaceContainer
        Raised -> MaterialTheme.colorScheme.surfaceContainerHigh
        Floating -> MaterialTheme.colorScheme.surfaceContainerHigh
        Overlay -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    /**
     * Border paired with this role. Resting surfaces are borderless: a page of outlined boxes
     * was most of what made screens read as busy, and the tint already separates them. Surfaces
     * that float over content keep a quiet edge. High contrast brings every outline back, so
     * nothing relies on a faint tint alone for people who asked for stronger edges.
     */
    @Composable
    fun border(): BorderStroke {
        if (LocalVervanHighContrast.current) {
            return vervanBorder(if (this == Sunken) VervanBorderProminence.Subtle else VervanBorderProminence.Standard)
        }
        return when (this) {
            Sunken, Card, Raised -> BorderStroke(0.dp, Color.Transparent)
            Floating, Overlay -> vervanBorder(VervanBorderProminence.Standard)
        }
    }

    /** Shadow elevation for the few surfaces that truly float above content. Flat roles are 0dp:
     *  the border + tint already separate them, and shadows on a warm-dark palette muddy more than
     *  they help. Only [Floating]/[Overlay] get a whisper of shadow for physical lift. */
    val shadowElevation: Dp
        get() = when (this) {
            Sunken, Card, Raised -> 0.dp
            Floating -> 3.dp
            Overlay -> 6.dp
        }

    /** [CardColors] for this role — container tint applied, content color left to the scheme. */
    @Composable
    fun cardColors(): CardColors = CardDefaults.cardColors(containerColor = containerColor())
}

/** Divider tint that tracks the same high-contrast floor as [vervanBorder] instead of every
 *  call site hardcoding `outlineVariant.copy(alpha = 0.4f)`. Use for [androidx.compose.material3.HorizontalDivider]
 *  / VerticalDivider `color`, which take a [Color] rather than a [BorderStroke]. */
@Composable
@ReadOnlyComposable
fun vervanDividerColor(): Color = MaterialTheme.colorScheme.outlineVariant

/** Fainter divider for inside-a-card row separators, where the card border already frames the group. */
@Composable
@ReadOnlyComposable
fun vervanSubtleDividerColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
