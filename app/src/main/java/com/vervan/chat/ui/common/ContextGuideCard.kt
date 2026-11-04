package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.vervanAccentFor

/**
 * A compact, repeatable orientation card for feature screens. The same pattern is used across
 * Library, personas, and workspaces so people always know what an area is for before they act.
 */
@Composable
fun ContextGuideCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    accentIndex: Int = 0,
) {
    val accent = vervanAccentFor(accentIndex)
    // Orientation is for the first visits; once dismissed it stays gone (per card title), so
    // regular users get their screen space back.
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui_hints", android.content.Context.MODE_PRIVATE) }
    val key = "guide_dismissed_" + title
    var dismissed by remember { mutableStateOf(runCatching { prefs.getBoolean(key, false) }.getOrDefault(false)) }
    if (dismissed) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = SurfaceRole.Raised.cardColors(),
        border = SurfaceRole.Raised.border(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(accent.container, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent.onContainer, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
            androidx.compose.material3.IconButton(onClick = {
                dismissed = true
                runCatching { prefs.edit().putBoolean(key, true).apply() }
            }) {
                Icon(
                    androidx.compose.material.icons.Icons.Filled.Close,
                    contentDescription = androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.guide_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
