package com.vervan.chat.ui.common

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * A [Switch] that carries its own accessible name.
 *
 * Prefer a whole-row `Modifier.toggleable(role = Role.Switch)` with `Switch(onCheckedChange = null)`
 * when the switch sits in a simple row. Use this where the row also holds other controls (a Delete
 * button, a slot-based section header) and can't become one toggle: a bare Switch there is
 * announced by TalkBack as just "Switch, off", with nothing saying what it controls.
 */
@Composable
fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = label },
    )
}
