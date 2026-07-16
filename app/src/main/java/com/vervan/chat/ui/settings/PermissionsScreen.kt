package com.vervan.chat.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vervan.chat.ui.common.rememberOnResumeTick

private data class PermissionInfo(val permission: String, val label: String, val why: String)

/** Phase H — plain-language "why we ask" per permission, with a live granted/not-granted
 * status instead of just a description. Pairs with DiagnosticsScreen (linked from there and
 * from Settings). */
private val PERMISSIONS = listOf(
    PermissionInfo(Manifest.permission.RECORD_AUDIO, "Microphone", "For voice messages sent as native audio to the model, and offline dictation-to-text."),
    PermissionInfo(Manifest.permission.CAMERA, "Camera", "For attaching photos to a chat and scanning documents with on-device OCR."),
    PermissionInfo(Manifest.permission.POST_NOTIFICATIONS, "Notifications", "For the ongoing 'generating response' notice and import/job completion notices."),
    PermissionInfo(Manifest.permission.READ_CONTACTS, "Contacts", "Only if you turn on the Contacts data source in Security — lets the model search contact names."),
    PermissionInfo(Manifest.permission.READ_CALENDAR, "Calendar", "Only if you turn on the Calendar data source in Security — lets the model search upcoming events."),
    PermissionInfo(Manifest.permission.READ_SMS, "SMS", "Only if you turn on the SMS data source in Security — lets the model search message content."),
    PermissionInfo(Manifest.permission.READ_CALL_LOG, "Call log", "Only if you turn on the Call log data source in Security — lets the model search recent calls."),
    PermissionInfo(Manifest.permission.ACCESS_COARSE_LOCATION, "Location", "Only if you turn on the Location data source in Security — approximate coordinates only, no address lookup."),
    PermissionInfo(Manifest.permission.READ_EXTERNAL_STORAGE, "Files", "Only if you turn on the Files data source in Security (Android 12 and below) — lets the model search Downloads by filename."),
    PermissionInfo("android.permission.SYSTEM_ALERT_WINDOW", "Draw over other apps", "Only if you turn on the quick-action bubble in Security — shows the floating capture button.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // A permission can be revoked from outside the app (system Settings, or Android's own
    // auto-reset of unused permissions) with no callback — re-check every time this screen
    // comes back into view instead of only trusting the status from when it first opened.
    val resumeTick = rememberOnResumeTick()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text(
                "Every permission this app can ask for, why, and whether it's currently granted. Nothing here is " +
                    "requested until the feature that needs it is actually used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            PERMISSIONS.forEach { info ->
                // SYSTEM_ALERT_WINDOW is a "special" permission granted via a system Settings
                // screen (Settings.canDrawOverlays), not the runtime flow — checkSelfPermission
                // reports it denied even when actually granted, which would show false status
                // on a screen whose entire point is being accurate.
                val granted = remember(resumeTick, info.permission) {
                    if (info.permission == "android.permission.SYSTEM_ALERT_WINDOW") {
                        android.provider.Settings.canDrawOverlays(context)
                    } else {
                        ContextCompat.checkSelfPermission(context, info.permission) == PackageManager.PERMISSION_GRANTED
                    }
                }
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(info.label, style = MaterialTheme.typography.bodyMedium)
                            Text(info.why, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            if (granted) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                            contentDescription = if (granted) "Granted" else "Not granted",
                            tint = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
