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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.ConfirmDialog
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
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("App lock", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Require biometrics or a PIN to open the app, matching your device's lock screen security.",
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
                            steps = 11
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
                                "Applies everywhere in the app, not just one chat — the recent-apps switcher also " +
                                    "shows a blank thumbnail instead of your chats. App lock (above) blocks these too, " +
                                    "for as long as it's on, independent of this switch.",
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
                        "Turns this app into an OpenAI-compatible endpoint other tools on your device (or LAN, opt-in) can call.",
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
                        "No crash reporting or analytics of any kind is built into this app. Diagnostics are generated " +
                            "locally and only ever leave the device when you manually copy them yourself.",
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
                        "Moves them to the recycle bin, same as deleting by hand — pinned and temporary chats are never touched.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Slider(
                        value = retentionDays.toFloat(),
                        onValueChange = { vm.setAutoDeleteAfterDays(it.toInt()) },
                        valueRange = 0f..180f,
                        steps = 17
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text("Panic wipe", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        "Immediately and permanently erases every chat, note, document, and model on this device, and " +
                            "closes the app. This cannot be undone — there is no backup step here; use Settings → " +
                            "Storage & data → Backup first if you want to keep anything.",
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
            body = "This permanently erases every chat, note, document, and model on this device. There is no undo.",
            confirmLabel = "Continue",
            destructive = true,
            onConfirm = { confirmWipeStep1 = false; confirmWipeStep2 = true },
            onDismiss = { confirmWipeStep1 = false }
        )
    }
    if (confirmWipeStep2) {
        ConfirmDialog(
            title = "Are you sure?",
            body = "Last chance — this cannot be undone, and the app will close immediately once it starts.",
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

    val requestContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setContactsToolEnabled(granted) }
    val requestCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setCalendarToolEnabled(granted) }
    val requestSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setSmsToolEnabled(granted) }

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
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("On-device data sources", style = MaterialTheme.typography.titleSmall)
            Text(
                "Lets the model read this data to answer your questions — entirely on-device, nothing leaves the phone. Off by default.",
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
                subtitle = "Outside Play Store distribution only — see note below."
            ) { turnOn ->
                if (turnOn) requestSms.launch(android.Manifest.permission.READ_SMS) else vm.setSmsToolEnabled(false)
            }
            DataSourceRow("Device status (battery, storage, network)", deviceStatus) { vm.setDeviceStatusToolEnabled(it) }
        }
    }
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
                        "A small floating button that captures a single screenshot and explains it using the " +
                            "active model's vision support. Needs the \"draw over other apps\" permission.",
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
