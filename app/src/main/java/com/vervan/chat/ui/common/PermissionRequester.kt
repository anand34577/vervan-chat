package com.vervan.chat.ui.common

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import com.vervan.chat.R

/** Launches a runtime-permission request. Same `launch(permission)` shape as the activity-result
 * launcher it wraps, so call sites don't change. */
class PermissionRequester internal constructor(private val onLaunch: (String) -> Unit) {
    fun launch(permission: String) = onLaunch(permission)
}

/**
 * A permission request that never dead-ends. When Android stops showing its own prompt (the user
 * chose "Don't allow" twice, or "Don't ask again"), a plain request returns "denied" instantly
 * and the button looks broken. This detects that case and offers a dialog with a direct
 * "Open settings" button instead of making people find the setting on their own.
 */
@Composable
fun rememberPermissionRequester(onResult: (granted: Boolean) -> Unit): PermissionRequester {
    val context = LocalContext.current
    val latestOnResult by rememberUpdatedState(onResult)
    var lastRequested by rememberSaveable { mutableStateOf<String?>(null) }
    var blockedPermission by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val permission = lastRequested
        if (!granted && permission != null) {
            val activity = context.findActivity()
            // After a denial, "no rationale" means the system will no longer ask.
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                blockedPermission = permission
            }
        }
        latestOnResult(granted)
    }
    blockedPermission?.let { permission ->
        AlertDialog(
            onDismissRequest = { blockedPermission = null },
            title = { Text(stringResource(R.string.permission_blocked_title)) },
            text = { Text(stringResource(permissionBlockedBody(permission))) },
            confirmButton = {
                TextButton(onClick = {
                    blockedPermission = null
                    context.openAppSettings()
                }) { Text(stringResource(R.string.permission_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { blockedPermission = null }) { Text(stringResource(R.string.permission_not_now)) }
            }
        )
    }
    return remember(launcher) {
        PermissionRequester { permission ->
            lastRequested = permission
            launcher.launch(permission)
        }
    }
}

private fun permissionBlockedBody(permission: String): Int = when (permission) {
    Manifest.permission.RECORD_AUDIO -> R.string.permission_blocked_microphone
    Manifest.permission.CAMERA -> R.string.permission_blocked_camera
    Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR -> R.string.permission_blocked_calendar
    else -> R.string.permission_blocked_generic
}

/** Opens this app's page in Android Settings, where permissions can be switched back on. */
fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
