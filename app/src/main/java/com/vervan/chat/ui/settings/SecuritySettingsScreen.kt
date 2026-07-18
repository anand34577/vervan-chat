package com.vervan.chat.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.ScrollablePage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(onBack: () -> Unit = {}, onOpenPermissions: () -> Unit = {}, onOpenApiServer: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })

    val enabled by vm.appLockEnabled.collectAsState()
    val method by vm.appLockMethod.collectAsState()
    val timeoutSeconds by vm.autoLockTimeoutSeconds.collectAsState()
    val retentionDays by vm.autoDeleteAfterDays.collectAsState()
    var showPinSetup by remember { mutableStateOf(false) }
    var confirmWipeStep1 by remember { mutableStateOf(false) }
    var confirmWipeStep2 by remember { mutableStateOf(false) }
    var wiping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("App lock", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Require biometrics or a PIN when opening Vervan.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { turnOn ->
                                if (turnOn && method != "BIOMETRIC" && !vm.hasPin) showPinSetup = true
                                else vm.setAppLockEnabled(turnOn)
                            }
                        )
                    }
                    if (enabled || showPinSetup) {
                        Spacer(Modifier.height(12.dp))
                        Text("Unlock method", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.padding(top = 6.dp)) {
                            listOf("BIOMETRIC" to "Biometric", "PIN" to "PIN", "BOTH" to "Both").forEach { (value, label) ->
                                FilterChip(
                                    selected = method == value,
                                    onClick = {
                                        vm.setAppLockMethod(value)
                                        if (value != "BIOMETRIC" && !vm.hasPin) showPinSetup = true
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        }
                        if (method != "BIOMETRIC") {
                            OutlinedButton(onClick = { showPinSetup = true }, modifier = Modifier.padding(top = 8.dp)) {
                                Text(if (vm.hasPin) "Change PIN" else "Set PIN")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Auto-lock after ${timeoutSeconds}s in the background", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = timeoutSeconds.toFloat(),
                            onValueChange = { vm.setAutoLockTimeoutSeconds(it.toInt()) },
                            valueRange = 0f..600f,
                            steps = 11,
                            modifier = Modifier.semantics {
                                contentDescription = "Auto-lock timeout, $timeoutSeconds seconds"
                            }
                        )
                        Text(
                            if (timeoutSeconds == 0) "Locks immediately every time the app leaves the foreground."
                            else "Locks if the app was backgrounded for at least this long.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            val screenshotBlocking by vm.screenshotBlockingEnabled.collectAsState()
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Block screenshots & screen recording", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Protect every screen and hide content in the recent-apps preview.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = screenshotBlocking, onCheckedChange = { vm.setScreenshotBlockingEnabled(it) })
                    }
                }
            }

            OnDeviceDataSourcesCard(vm)

            QuickActionBubbleCard(vm)

            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Local API server", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Let trusted apps use the active model through a local API.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedButton(onClick = onOpenApiServer) { Text("Open") }
                }
            }

            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Privacy", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "No analytics or crash reports are sent. Diagnostics stay local unless you copy them.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    OutlinedButton(onClick = onOpenPermissions) { Text("See all permissions") }
                }
            }

            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Danger zone", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)

                    Text(
                        if (retentionDays == 0) "Auto-delete old chats: off" else "Auto-delete chats untouched for $retentionDays days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        "Moves old chats to the bin. Pinned and temporary chats are kept.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Slider(
                        value = retentionDays.toFloat(),
                        onValueChange = { vm.setAutoDeleteAfterDays(it.toInt()) },
                        valueRange = 0f..180f,
                        steps = 17,
                        modifier = Modifier.semantics {
                            contentDescription = if (retentionDays == 0) "Auto-delete old chats, off" else "Auto-delete old chats, $retentionDays days"
                        }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text("Panic wipe", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        "Erases all chats, notes, documents, and models, then closes the app. Back up first.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    OutlinedButton(
                        onClick = { confirmWipeStep1 = true },
                        enabled = !wiping,
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text(if (wiping) "Wiping…" else "Wipe everything") }
                }
            }
        }
    }

    if (confirmWipeStep1) {
        ConfirmDialog(
            title = "Wipe everything?",
            body = "Permanently erase all chats, notes, documents, and models?",
            confirmLabel = "Continue",
            destructive = true,
            onConfirm = { confirmWipeStep1 = false; confirmWipeStep2 = true },
            onDismiss = { confirmWipeStep1 = false }
        )
    }
    if (confirmWipeStep2) {
        ConfirmDialog(
            title = "Are you sure?",
            body = "This cannot be undone. The app will close when the wipe starts.",
            confirmLabel = "Wipe everything",
            destructive = true,
            onConfirm = {
                confirmWipeStep2 = false
                wiping = true
                scope.launch { vm.panicWipe() }
            },
            onDismiss = { confirmWipeStep2 = false }
        )
    }

    if (showPinSetup) {
        PinSetupDialog(
            onDismiss = { showPinSetup = false; if (!vm.hasPin) vm.setAppLockMethod("BIOMETRIC") },
            onConfirm = { pin ->
                vm.setPin(pin)
                showPinSetup = false
                vm.setAppLockEnabled(true)
            }
        )
    }
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && pin != confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 12) pin = it.filter(Char::isDigit) },
                    label = { Text("PIN (4+ digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { if (it.length <= 12) confirm = it.filter(Char::isDigit) },
                    label = { Text("Confirm PIN") },
                    isError = mismatch,
                    supportingText = if (mismatch) { { Text("PINs don't match") } } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }, enabled = pin.length >= 4 && pin == confirm) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Phase G — one row per on-device data source, each an app-level toggle independent of the OS
 * permission (defense in depth: granting the Android permission doesn't mean the model should
 * always be allowed to query it). Turning a permission-backed one on requests the runtime
 * permission first via the same [ActivityResultContracts.RequestPermission] pattern
 * ChatScreen.kt already uses for mic/camera — the setting only actually flips on if the
 * permission is granted.
 */
@Composable
private fun OnDeviceDataSourcesCard(vm: SettingsViewModel) {
    val context = LocalContext.current
    val contacts by vm.contactsToolEnabled.collectAsState()
    val calendar by vm.calendarToolEnabled.collectAsState()
    val sms by vm.smsToolEnabled.collectAsState()
    val deviceStatus by vm.deviceStatusToolEnabled.collectAsState()
    val files by vm.filesToolEnabled.collectAsState()
    val location by vm.locationToolEnabled.collectAsState()
    val callLog by vm.callLogToolEnabled.collectAsState()
    val screenTime by vm.screenTimeToolEnabled.collectAsState()

    val requestContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setContactsToolEnabled(granted) }
    val requestCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setCalendarToolEnabled(granted) }
    val requestSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setSmsToolEnabled(granted) }
    val requestFiles = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setFilesToolEnabled(granted) }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setLocationToolEnabled(granted) }
    val requestCallLog = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setCallLogToolEnabled(granted) }
    // PACKAGE_USAGE_STATS is a special access, not a runtime permission — granted via a Settings
    // redirect and checked through AppOpsManager, same shape as the overlay permission below.
    val usageAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (hasUsageAccess(context)) vm.setScreenTimeToolEnabled(true)
    }

    // Each toggle's own app-level "on" is independently checked at call time anyway (see
    // ToolRegistry.gatedResult), so a revoked permission never breaks a tool call — this just
    // keeps the switch itself honest instead of showing "on" for something that will actually
    // fail every time, which otherwise only gets noticed by turning it off and on again.
    val resumeTick = com.vervan.chat.ui.common.rememberOnResumeTick()
    fun hasPermission(permission: String) = androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    androidx.compose.runtime.LaunchedEffect(resumeTick) {
        if (contacts && !hasPermission(android.Manifest.permission.READ_CONTACTS)) vm.setContactsToolEnabled(false)
        if (calendar && !hasPermission(android.Manifest.permission.READ_CALENDAR)) vm.setCalendarToolEnabled(false)
        if (sms && !hasPermission(android.Manifest.permission.READ_SMS)) vm.setSmsToolEnabled(false)
        if (files && android.os.Build.VERSION.SDK_INT <= 32 && !hasPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)) vm.setFilesToolEnabled(false)
        if (location && !hasPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)) vm.setLocationToolEnabled(false)
        if (callLog && !hasPermission(android.Manifest.permission.READ_CALL_LOG)) vm.setCallLogToolEnabled(false)
        if (screenTime && !hasUsageAccess(context)) vm.setScreenTimeToolEnabled(false)
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("On-device data sources", style = MaterialTheme.typography.titleSmall)
            Text(
                "Allow selected local data sources. Off by default and processed on-device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            DataSourceRow("Contacts", contacts) { turnOn ->
                if (turnOn) requestContacts.launch(android.Manifest.permission.READ_CONTACTS) else vm.setContactsToolEnabled(false)
            }
            DataSourceRow("Calendar", calendar) { turnOn ->
                if (turnOn) requestCalendar.launch(android.Manifest.permission.READ_CALENDAR) else vm.setCalendarToolEnabled(false)
            }
            DataSourceRow(
                "SMS", sms,
                subtitle = "Only available outside the Play Store."
            ) { turnOn ->
                if (turnOn) requestSms.launch(android.Manifest.permission.READ_SMS) else vm.setSmsToolEnabled(false)
            }
            DataSourceRow("Device status (battery, storage, network, Wi-Fi)", deviceStatus) { vm.setDeviceStatusToolEnabled(it) }
            DataSourceRow("Files (Downloads)", files) { turnOn ->
                if (turnOn) requestFiles.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE) else vm.setFilesToolEnabled(false)
            }
            DataSourceRow("Location (coarse, no address lookup)", location) { turnOn ->
                if (turnOn) requestLocation.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) else vm.setLocationToolEnabled(false)
            }
            DataSourceRow("Call log", callLog) { turnOn ->
                if (turnOn) requestCallLog.launch(android.Manifest.permission.READ_CALL_LOG) else vm.setCallLogToolEnabled(false)
            }
            DataSourceRow("Screen time (per-app usage today)", screenTime) { turnOn ->
                if (!turnOn) {
                    vm.setScreenTimeToolEnabled(false)
                } else if (hasUsageAccess(context)) {
                    vm.setScreenTimeToolEnabled(true)
                } else {
                    usageAccessLauncher.launch(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        }
    }
}

/** PACKAGE_USAGE_STATS special-access check — see [OnDeviceDataSourcesCard]'s screen-time row. */
private fun hasUsageAccess(context: android.content.Context): Boolean {
    val appOps = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    @Suppress("DEPRECATION")
    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    } else {
        appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    }
    return mode == android.app.AppOpsManager.MODE_ALLOWED
}

@Composable
private fun DataSourceRow(label: String, checked: Boolean, subtitle: String? = null, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

/**
 * Phase I — the one feature in this app that needs the SYSTEM_ALERT_WINDOW overlay permission,
 * granted via a system Settings redirect ([android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION]),
 * not a runtime permission dialog. Turning the toggle on checks [android.provider.Settings.canDrawOverlays]
 * first and only actually enables the setting (and starts [com.vervan.chat.overlay.BubbleService])
 * once that's confirmed granted.
 */
@Composable
private fun QuickActionBubbleCard(vm: SettingsViewModel) {
    val context = LocalContext.current
    val enabled by vm.quickActionBubbleEnabled.collectAsState()
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (android.provider.Settings.canDrawOverlays(context)) vm.setQuickActionBubbleEnabled(true)
    }
    // The overlay permission can be revoked (system Settings, or Android auto-resetting unused
    // permissions) without the app hearing about it — without this, the switch stays "on" while
    // the bubble silently fails to (re)draw, the exact stuck-toggle bug this whole app-lifecycle
    // rework (see VervanApp/BubbleService) was trying to avoid.
    val resumeTick = com.vervan.chat.ui.common.rememberOnResumeTick()
    androidx.compose.runtime.LaunchedEffect(resumeTick) {
        if (enabled && !android.provider.Settings.canDrawOverlays(context)) vm.setQuickActionBubbleEnabled(false)
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quick-action bubble", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Show a floating button to explain one screenshot locally. Requires overlay permission.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { turnOn ->
                        if (!turnOn) {
                            vm.setQuickActionBubbleEnabled(false)
                        } else if (android.provider.Settings.canDrawOverlays(context)) {
                            vm.setQuickActionBubbleEnabled(true)
                        } else {
                            overlayLauncher.launch(
                                Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}
