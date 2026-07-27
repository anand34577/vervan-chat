package com.vervan.chat.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
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
import androidx.core.content.ContextCompat
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
                title = { Text("Privacy & security") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            Card(Modifier.fillMaxWidth().padding(vertical = Space.xs), colors = com.vervan.chat.ui.theme.SurfaceRole.Card.cardColors(), border = com.vervan.chat.ui.theme.SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
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
                        Spacer(Modifier.height(Space.md))
                        Text("Unlock method", style = MaterialTheme.typography.labelMedium)
                        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = Space.sm)) {
                            listOf("BIOMETRIC" to "Biometric", "PIN" to "PIN", "BOTH" to "Both").forEach { (value, label) ->
                                VervanFilterChip(
                                    selected = method == value,
                                    onClick = {
                                        vm.setAppLockMethod(value)
                                        if (value != "BIOMETRIC" && !vm.hasPin) showPinSetup = true
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.padding(end = Space.sm)
                                )
                            }
                        }
                        if (method != "BIOMETRIC") {
                            OutlinedButton(onClick = { showPinSetup = true }, modifier = Modifier.padding(top = Space.sm)) {
                                Text(if (vm.hasPin) "Change PIN" else "Set PIN")
                            }
                        }
                        Spacer(Modifier.height(Space.md))
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
            Card(Modifier.fillMaxWidth().padding(vertical = Space.xs), colors = com.vervan.chat.ui.theme.SurfaceRole.Card.cardColors(), border = com.vervan.chat.ui.theme.SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Block screenshots & screen recording", style = MaterialTheme.typography.bodyMedium)
                            Text(
                            "Hide app content in screenshots, recordings, and recent apps.",
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

            Card(Modifier.fillMaxWidth().padding(vertical = Space.xs), colors = com.vervan.chat.ui.theme.SurfaceRole.Card.cardColors(), border = com.vervan.chat.ui.theme.SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
                    Text("Local API server", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Let trusted apps use the active model through a local API.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Space.sm)
                    )
                    OutlinedButton(onClick = onOpenApiServer) { Text("Open") }
                }
            }

            Card(Modifier.fillMaxWidth().padding(vertical = Space.xs), colors = com.vervan.chat.ui.theme.SurfaceRole.Card.cardColors(), border = com.vervan.chat.ui.theme.SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
                    Text("Data privacy", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Vervan sends no analytics or crash reports. Diagnostics stay on this device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs, bottom = Space.sm)
                    )
                    OutlinedButton(onClick = onOpenPermissions) { Text("See all permissions") }
                }
            }

            Card(
                Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xs),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(Space.lg)) {
                    Text("Danger zone", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)

                    Text(
                        if (retentionDays == 0) "Auto-delete old chats: off" else "Auto-delete chats untouched for $retentionDays days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(top = Space.md)
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

                    HorizontalDivider(Modifier.padding(vertical = Space.sm))

                    Text("Panic wipe", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        "Erases all local content and models, then closes Vervan. Export a backup first.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    OutlinedButton(
                        onClick = { confirmWipeStep1 = true },
                        enabled = !wiping,
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(top = Space.sm)
                    ) { Text(if (wiping) "Wiping…" else "Wipe everything") }
                }
            }
        }
    }

    if (confirmWipeStep1) {
        ConfirmDialog(
            title = "Wipe everything?",
            body = "Permanently erase all local content and models?",
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
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
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
 * one row per on-device data source, each an app-level toggle independent of the OS
 * permission (defense in depth: granting the Android permission doesn't mean the model should
 * always be allowed to query it). Turning a permission-backed one on requests the runtime
 * permission first via the same [ActivityResultContracts.RequestPermission] pattern
 * ChatScreen.kt already uses for mic/camera — the setting only actually flips on if the
 * permission is granted.
 */
@Composable
private fun OnDeviceDataSourcesCard(vm: SettingsViewModel) {
    val context = LocalContext.current
    val calendar by vm.calendarToolEnabled.collectAsState()
    val deviceStatus by vm.deviceStatusToolEnabled.collectAsState()
    val location by vm.locationToolEnabled.collectAsState()

    val requestCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setCalendarToolEnabled(granted) }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setLocationToolEnabled(granted) }

    // Each toggle's own app-level "on" is independently checked at call time anyway (see
    // ToolRegistry.gatedResult), so a revoked permission never breaks a tool call — this just
    // keeps the switch itself honest instead of showing "on" for something that will actually
    // fail every time, which otherwise only gets noticed by turning it off and on again.
    val resumeTick = com.vervan.chat.ui.common.rememberOnResumeTick()
    fun hasPermission(permission: String) = androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    androidx.compose.runtime.LaunchedEffect(resumeTick) {
        if (calendar && !hasPermission(android.Manifest.permission.READ_CALENDAR)) vm.setCalendarToolEnabled(false)
        if (location && !hasPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)) vm.setLocationToolEnabled(false)
    }

    Card(Modifier.fillMaxWidth().padding(vertical = Space.xs), colors = com.vervan.chat.ui.theme.SurfaceRole.Card.cardColors(), border = com.vervan.chat.ui.theme.SurfaceRole.Card.border()) {
        Column(Modifier.padding(Space.lg)) {
            Text("On-device data sources", style = MaterialTheme.typography.titleSmall)
            Text(
            "Choose which local data Vervan can use. Processing stays on-device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.sm)
            )
            DataSourceRow("Calendar", calendar) { turnOn ->
                if (turnOn) requestCalendar.launch(android.Manifest.permission.READ_CALENDAR) else vm.setCalendarToolEnabled(false)
            }
            DataSourceRow("Device status (battery, storage, network, Wi-Fi)", deviceStatus) { vm.setDeviceStatusToolEnabled(it) }
            DataSourceRow("Location (coarse, no address lookup)", location) { turnOn ->
                if (turnOn) requestLocation.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) else vm.setLocationToolEnabled(false)
            }
        }
    }
}

@Composable
private fun DataSourceRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

/**
 * the one feature in this app that needs the SYSTEM_ALERT_WINDOW overlay permission,
 * granted via a system Settings redirect ([android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION]),
 * not a runtime permission dialog. Turning the toggle on checks [android.provider.Settings.canDrawOverlays]
 * first and only actually enables the setting (and starts [com.vervan.chat.overlay.BubbleService])
 * once that's confirmed granted.
 */
@Composable
private fun QuickActionBubbleCard(vm: SettingsViewModel) {
    val context = LocalContext.current
    val enabled by vm.quickActionBubbleEnabled.collectAsState()
    var showPermissionExplanation by remember { mutableStateOf(false) }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (android.provider.Settings.canDrawOverlays(context)) vm.setQuickActionBubbleEnabled(true)
    }
    fun requestOverlayOrEnable() {
        if (android.provider.Settings.canDrawOverlays(context)) {
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
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Notifications improve awareness, but Android still permits the foreground service
        // when this optional runtime permission is declined.
        requestOverlayOrEnable()
    }
    // The overlay permission can be revoked (system Settings, or Android auto-resetting unused
    // permissions) without the app hearing about it — without this, the switch stays "on" while
    // the bubble silently fails to (re)draw, the exact stuck-toggle bug this whole app-lifecycle
    // rework (see VervanApp/BubbleService) was trying to avoid.
    val resumeTick = com.vervan.chat.ui.common.rememberOnResumeTick()
    androidx.compose.runtime.LaunchedEffect(resumeTick) {
        if (enabled && !android.provider.Settings.canDrawOverlays(context)) vm.setQuickActionBubbleEnabled(false)
    }

    Card(Modifier.fillMaxWidth().padding(vertical = Space.xs), colors = com.vervan.chat.ui.theme.SurfaceRole.Card.cardColors(), border = com.vervan.chat.ui.theme.SurfaceRole.Card.border()) {
        Column(Modifier.padding(Space.lg)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quick-action bubble", style = MaterialTheme.typography.bodyMedium)
                    Text(
                            "Show a floating button for screenshot questions. Requires overlay permission; each capture still needs approval.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { turnOn ->
                        if (!turnOn) {
                            vm.setQuickActionBubbleEnabled(false)
                        } else {
                            showPermissionExplanation = true
                        }
                    }
                )
            }
        }
    }

    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = { Text("Allow the quick-action bubble?") },
            text = {
                Text(
                    "The bubble appears above other apps with a quiet notification. Vervan captures " +
                        "only after you choose Capture and approve Android's prompt each time. Images " +
                        "stay on this device. A vision model is required, and protected apps may block capture."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionExplanation = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        requestOverlayOrEnable()
                    }
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionExplanation = false }) { Text("Not now") }
            }
        )
    }
}
