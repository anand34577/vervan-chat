package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole

/**
 * The standard page-content card shell: a full-width [SurfaceRole.Card] with the standard
 * border and a compact vertical gap to its neighbours. This is the shape every
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
    role: SurfaceRole = SurfaceRole.Card,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs)
            .clip(MaterialTheme.shapes.small)
            .background(role.containerColor(), MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), MaterialTheme.shapes.small),
        content = content
    )
}
