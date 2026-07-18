package com.vervan.chat.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.vervanSubtleDividerColor

/**
 * A grouped card whose children are separated by inset dividers — the "list of rows inside one
 * bordered card" pattern that Home (Continue / Local workspace), Settings groups, project
 * dashboards, and several tool screens each re-implemented with their own `Card { forEachIndexed
 * { … HorizontalDivider … } }` boilerplate (and their own border alpha). Centralizing it means
 * the container tint, border prominence, and divider tint all come from [SurfaceRole] tokens, so
 * every grouped card reads the same.
 *
 * Rows are supplied as a list of composables; dividers are inserted *between* them automatically
 * (never above the first or below the last). Use [SectionRow] for the common
 * icon · title · subtitle · trailing layout, or pass any composable for bespoke rows.
 */
@Composable
fun SectionCard(
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    role: SurfaceRole = SurfaceRole.Card
) {
    if (items.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = role.cardColors(),
        border = role.border()
    ) {
        items.forEachIndexed { index, row ->
            row()
            if (index != items.lastIndex) {
                HorizontalDivider(
                    Modifier.padding(horizontal = Space.lg),
                    color = vervanSubtleDividerColor()
                )
            }
        }
    }
}

/**
 * The canonical row for a [SectionCard]: a leading icon affordance, an optional uppercase
 * eyebrow, a title, an optional subtitle, and optional trailing content (chevron, chip, switch).
 * Tap target is a comfortable 64dp minimum. Everything but [title] is optional so the same row
 * serves navigation rows, status rows, and recent-item rows.
 */
@Composable
fun SectionRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    eyebrow: String? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = 64.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(Space.lg)
    Row(base, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconAffordance(icon = icon, size = IconAffordanceSize.Compact)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = if (icon != null) Space.md else 0.dp)
        ) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Row(
                Modifier.padding(start = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) { trailing() }
        }
    }
}
