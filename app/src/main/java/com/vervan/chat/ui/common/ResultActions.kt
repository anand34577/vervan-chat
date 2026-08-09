package com.vervan.chat.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space

/**
 * The canonical action row for generated work. Keeping these actions in one component makes
 * one-shot tools, guided sessions, and future document/canvas outputs behave the same: copy is
 * always available first, sharing is explicit, and persistence is an optional follow-up.
 */
@Composable
fun ResultActions(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    onRegenerate: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    saveLabel: String = "Save"
) {
    ResponsiveActions(modifier) {
        OutlinedButton(onClick = onCopy) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            androidx.compose.material3.Text("Copy", modifier = Modifier.padding(start = Space.sm))
        }
        onRegenerate?.let {
            OutlinedButton(onClick = it) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                androidx.compose.material3.Text("Regenerate", modifier = Modifier.padding(start = Space.sm))
            }
        }
        OutlinedButton(onClick = onShare) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            androidx.compose.material3.Text("Share", modifier = Modifier.padding(start = Space.sm))
        }
        onSave?.let {
            OutlinedButton(onClick = it) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                androidx.compose.material3.Text(saveLabel, modifier = Modifier.padding(start = Space.sm))
            }
        }
    }
}
