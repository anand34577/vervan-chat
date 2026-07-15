package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The "icon in a tinted rounded box" affordance used for row leading icons across
 * Home, Knowledge, Chats, Recycle Bin and Workspaces. Previously each screen picked
 * its own box size (26/30/32/34/38/52dp) and corner shape independently — these three
 * named sizes are the only ones that should be reached for now.
 */
enum class IconAffordanceSize(val box: Dp, val corner: Dp, val icon: Dp) {
    /** Dense list rows — chat list, recycle bin, job queue. */
    Compact(28.dp, 8.dp, 16.dp),
    /** Card-style rows — Home recents, folders, knowledge documents. */
    Default(36.dp, 12.dp, 20.dp),
    /** Hero cards — Home's "Ask anything," project tiles, empty states. */
    Feature(48.dp, 16.dp, 26.dp)
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
    Box(
        modifier = modifier
            .size(size.box)
            .background(containerColor, RoundedCornerShape(size.corner)),
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
