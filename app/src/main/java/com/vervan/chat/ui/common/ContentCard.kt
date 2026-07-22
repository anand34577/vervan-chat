package com.vervan.chat.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole

/**
 * The standard page-content card shell: a full-width [SurfaceRole.Card] with the standard
 * border and a `Space.xs` vertical gap to its neighbours. This is the shape every
 * settings/detail screen used to re-implement as a one-liner
 * `Card(Modifier.fillMaxWidth().padding(vertical = ...xs), colors = ...cardColors(), border = ...border())`
 * — often with `Space`/`SurfaceRole` written fully-qualified inline (the single biggest source
 * of FQ noise in the app). Centralising it here locks the container tint, border prominence,
 * and outer gap to the design tokens so they stop drifting per screen.
 *
 * This is intentionally a *shell* — it matches [Card]'s signature so callers keep their own
 * interior layout (the conventional `Column(Modifier.padding(Space.lg)) { … }`). That makes
 * migration a one-line replacement with no brace/indentation churn. For the divider-separated
 * list-of-rows card, see [SectionCard] instead.
 */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = Space.xs),
        colors = SurfaceRole.Card.cardColors(),
        border = SurfaceRole.Card.border(),
        content = content
    )
}
