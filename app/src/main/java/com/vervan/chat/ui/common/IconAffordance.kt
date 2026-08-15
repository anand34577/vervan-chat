package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The "icon in a tinted rounded box" affordance used for row leading icons across
 * Home, Knowledge, Chats, Recycle Bin and Workspaces. Previously each screen picked
 * its own box size (26/30/32/34/38/52dp) and corner shape independently — these three
 * named sizes are the only ones that should be reached for now.
 */
enum class IconAffordanceSize(val box: Dp, val icon: Dp) {
    /** Dense list rows — chat list, recycle bin, job queue. */
    Compact(28.dp, 16.dp),
    /** Card-style rows — Home recents, folders, knowledge documents. */
    Default(36.dp, 20.dp),
    /** Hero cards — Home's "Ask anything," project tiles, empty states. */
    Feature(48.dp, 26.dp)
}

@Composable
fun IconAffordance(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: IconAffordanceSize = IconAffordanceSize.Default,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentDescription: String? = null
) {
    // Leading icon badges share one corner token at every size. This is deliberately different
    // from avatars/status dots (which may be circular) and from continuous controls (which may
    // be stadium-shaped), so the navigation language stays predictable across routes.
    val shape = MaterialTheme.shapes.small
    // Feature-size badges are the ones a user actually looks at for a beat — hero cards, empty
    // states, hands-free entry points — so they get a soft two-tone gradient instead of the flat
    // fill dense list rows use. Compact/Default stay flat: a gradient recomputed on every list
    // row is wasted cost for a badge the eye skims past in a scrolling list.
    val boxModifier = if (size == IconAffordanceSize.Feature) {
        modifier.size(size.box).background(
            Brush.linearGradient(listOf(containerColor, lerp(containerColor, tint, 0.16f))),
            shape
        )
    } else {
        modifier.size(size.box).background(containerColor, shape)
    }
    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size.icon)
        )
    }
}
