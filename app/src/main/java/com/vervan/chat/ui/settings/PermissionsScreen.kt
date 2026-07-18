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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vervan.chat.ui.common.rememberOnResumeTick
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.theme.vervanSuccess

private data class PermissionInfo(val permission: String, val label: String, val why: String)

/** Phase H — plain-language "why we ask" per permission, with a live granted/not-granted
 * status instead of just a description. Pairs with DiagnosticsScreen (linked from there and
 * from Settings). */
private val PERMISSIONS = listOf(
    PermissionInfo(Manifest.permission.RECORD_AUDIO, "Microphone", "Record voice messages and use offline dictation."),
    PermissionInfo(Manifest.permission.CAMERA, "Camera", "Take photos and scan documents locally."),
    PermissionInfo(Manifest.permission.POST_NOTIFICATIONS, "Notifications", "Show generation, import, and job progress."),
    PermissionInfo(Manifest.permission.READ_CONTACTS, "Contacts", "Let enabled tools search contact names locally."),
    PermissionInfo(Manifest.permission.READ_CALENDAR, "Calendar", "Let enabled tools search upcoming events locally."),
    PermissionInfo(Manifest.permission.READ_SMS, "SMS", "Let enabled tools search message text locally."),
    PermissionInfo(Manifest.permission.READ_CALL_LOG, "Call log", "Let enabled tools search recent calls locally."),
    PermissionInfo(Manifest.permission.ACCESS_COARSE_LOCATION, "Location", "Share approximate coordinates with enabled local tools."),
    PermissionInfo(Manifest.permission.READ_EXTERNAL_STORAGE, "Files", "Search Downloads by filename on Android 12 or older."),
    PermissionInfo("android.permission.SYSTEM_ALERT_WINDOW", "Draw over other apps", "Show the optional quick-action bubble.")
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
        ScrollablePage(padding) {
            Text(
                "See why each permission is used. Vervan asks only when a feature needs it.",
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
                            tint = if (granted) MaterialTheme.colorScheme.vervanSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
