package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanExtraShapes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Date separator pill rendered between chat messages from different days (Today / Yesterday /
 * weekday / absolute date). Closes a gap with every modern chat app — long conversations
 * previously rendered as a wall of bubbles with no temporal anchors.
 *
 * The pill is centered, capped at a sensible max width, and uses [VervanExtraShapes.datePill]
 * so the shape tracks the rest of the brand (instead of every screen hand-rolling a `CircleShape`
 * background). Marked as a heading for screen-reader scan navigation.
 */
@Composable
fun DatePill(
    timestamp: Long,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis()
) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = Space.md), contentAlignment = Alignment.Center) {
        Text(
            text = formatRelativeDay(timestamp, now),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 180.dp)
                .clip(VervanExtraShapes.datePill)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = Space.md, vertical = Space.xs)
                .semantics { heading() }
        )
    }
}

/** Render "Today" / "Yesterday" / "Last Monday" / "Mar 14" depending on how far back [ts] is.
 *  Public so ChatViewModel / SearchScreen can pre-compute the label and reuse the same wording. */
fun formatRelativeDay(ts: Long, now: Long = System.currentTimeMillis()): String {
    val nowCal = Calendar.getInstance().apply { timeInMillis = now }
    val tsCal = Calendar.getInstance().apply { timeInMillis = ts }
    val dayDiff = TimeUnit.MILLISECONDS.toDays(
        startOfDay(nowCal.timeInMillis) - startOfDay(tsCal.timeInMillis)
    )
    return when {
        sameDay(nowCal, tsCal) -> "Today"
        dayDiff == 1L -> "Yesterday"
        dayDiff in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ts))
        sameYear(nowCal, tsCal) -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ts))
    }
}

private fun startOfDay(ts: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun sameYear(a: Calendar, b: Calendar): Boolean = a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
