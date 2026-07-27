package com.vervan.chat.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vervan.chat.ui.theme.Space

@Composable
internal fun VoiceSessionOptionsSheet(
    speechOutputEnabled: Boolean,
    microphoneMuted: Boolean,
    immersiveEnabled: Boolean,
    onToggleSpeechOutput: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleImmersive: () -> Unit,
    onSwitchModel: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.md)) {
        Text("Voice session options", style = MaterialTheme.typography.headlineSmall)
        SessionOptionToggle(
            title = "Voice replies",
            description = "Speak assistant responses during this session.",
            checked = speechOutputEnabled,
            onCheckedChange = { onToggleSpeechOutput() }
        )
        SessionOptionToggle(
            title = "Hard mute",
            description = "Keep the microphone closed until you unmute it.",
            checked = microphoneMuted,
            onCheckedChange = { onToggleMute() }
        )
        SessionOptionToggle(
            title = "Immersive presentation",
            description = "Open hands-free mode in this conversation.",
            checked = immersiveEnabled,
            onCheckedChange = { onToggleImmersive() }
        )
        TextButton(onClick = onSwitchModel, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Psychology, contentDescription = null)
            Text("Switch local model", modifier = Modifier.padding(start = Space.sm))
        }
        TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Settings, contentDescription = null)
            Text("All voice settings", modifier = Modifier.padding(start = Space.sm))
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = Space.sm)) {
            Text("Done")
        }
    }
}

@Composable
private fun SessionOptionToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = Space.md)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
