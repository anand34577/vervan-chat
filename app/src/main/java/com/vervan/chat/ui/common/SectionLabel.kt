package com.vervan.chat.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.vervan.chat.ui.theme.Space

/**
 * The canonical page-level section header used across settings, library, model manager, and
 * recycle-bin screens. One typography (titleSmall), one color (primary), one rhythm
 * (top = Space.lg, bottom = Space.sm), and — importantly — one accessibility annotation
 * (`semantics { heading() }`) so screen readers announce section boundaries.
 *
 * Previously re-implemented four times with three different padding schemes, two typographies,
 * and only one of the four marking the heading semantics — which meant TalkBack skipped
 * section boundaries on three screens. Migrate every page-section header here; reserve a
 * separate inline-eyebrow composable for the small uppercase label *inside* a card.
 *
 * @param title  Section title.
 * @param modifier  Optional extra modifier (applied before the standard padding/semantics).
 * @param count  Optional trailing count badge (e.g. number of items in the section). When null,
 *  only the title is rendered.
 */
@Composable
fun SectionLabel(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null
) {
    val base = modifier
        .padding(top = Space.lg, bottom = Space.sm)
        .semantics { heading() }
    if (count == null) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = base
        )
    } else {
        Row(
            base.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
