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
 * Depth is carried primarily by tonal surfaces, with borders used only where they clarify an
 * interaction boundary. Before this, screens picked containers and border alphas ad hoc, which
 * made a page feel like a stack of unrelated boxes. This maps each surface role to a predictable
 * tonal level, border prominence, and optional elevation.
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
        Card -> MaterialTheme.colorScheme.surfaceContainerLow
        Raised -> MaterialTheme.colorScheme.surfaceContainer
        Floating -> MaterialTheme.colorScheme.surfaceContainerHigh
        Overlay -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    /** Border prominence paired with this role, so edge weight tracks depth consistently. */
    @Composable
    fun border(): BorderStroke = vervanBorder(
        when (this) {
            Sunken -> VervanBorderProminence.Subtle
            Card -> VervanBorderProminence.Subtle
            Raised -> VervanBorderProminence.Standard
            Floating -> VervanBorderProminence.Emphasized
            Overlay -> VervanBorderProminence.Emphasized
        }
    )

    /** Shadow elevation for surfaces that truly float above content. */
    val shadowElevation: Dp
        get() = when (this) {
            Sunken -> 0.dp
            Card -> 1.dp
            Raised -> 2.dp
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
