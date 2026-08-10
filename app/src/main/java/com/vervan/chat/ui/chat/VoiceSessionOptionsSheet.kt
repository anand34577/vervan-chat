package com.vervan.chat.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import com.vervan.chat.ui.theme.Space

@Composable
internal fun VoiceSessionOptionsSheet(
    speechOutputEnabled: Boolean,
    microphoneMuted: Boolean,
    immersiveEnabled: Boolean,
    pushToTalkEnabled: Boolean,
    onToggleSpeechOutput: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleImmersive: () -> Unit,
    onTogglePushToTalk: () -> Unit,
    onSwitchModel: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.xl, vertical = Space.md)
    ) {
        Text(stringResource(R.string.voice_session), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.voice_session_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs, bottom = Space.lg)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                SessionOptionToggle(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = stringResource(R.string.voice_replies),
                    description = stringResource(R.string.voice_replies_hint),
                    checked = speechOutputEnabled,
                    onCheckedChange = { onToggleSpeechOutput() }
                )
                SessionOptionToggle(
                    icon = Icons.Filled.MicOff,
                    title = stringResource(R.string.voice_microphone),
                    description = stringResource(R.string.voice_microphone_hint),
                    checked = microphoneMuted,
                    onCheckedChange = { onToggleMute() }
                )
                SessionOptionToggle(
                    icon = Icons.Filled.Fullscreen,
                    title = stringResource(R.string.voice_immersive),
                    description = stringResource(R.string.voice_immersive_hint),
                    checked = immersiveEnabled,
                    onCheckedChange = { onToggleImmersive() }
                )
                SessionOptionToggle(
                    icon = Icons.Filled.FrontHand,
                    title = stringResource(R.string.voice_push_to_talk),
                    description = stringResource(R.string.voice_push_to_talk_hint),
                    checked = pushToTalkEnabled,
                    onCheckedChange = { onTogglePushToTalk() }
                )
            }
        }

        Text(
            stringResource(R.string.voice_session_tools),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xl, bottom = Space.xs, start = Space.xs)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                SessionNavigationRow(Icons.Filled.Psychology, stringResource(R.string.voice_switch_model), onSwitchModel)
                SessionNavigationRow(Icons.Filled.Settings, stringResource(R.string.voice_all_settings), onOpenSettings)
            }
        }

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(top = Space.lg, bottom = Space.sm)
        ) {
            Text(stringResource(R.string.action_done))
        }
    }
}

@Composable
private fun SessionOptionToggle(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SessionNavigationRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.md, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
