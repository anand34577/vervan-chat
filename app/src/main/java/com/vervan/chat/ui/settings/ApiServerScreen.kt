package com.vervan.chat.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.setSensitiveText

/**
 * Phase J — on/off, LAN exposure (with an explicit warning), port, auth requirement + token,
 * and a live request counter reusing [com.vervan.chat.system.NetworkAuditLog] so the same
 * trust dashboard that proves outbound silence also covers this server's inbound traffic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiServerScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val enabled by vm.apiServerEnabled.collectAsState()
    val lan by vm.lanApiServerEnabled.collectAsState()
    val port by vm.apiServerPort.collectAsState()
    val requireAuth by vm.apiServerRequireAuth.collectAsState()
    val entries by app.container.networkAuditLog.entries.collectAsState()
    val requestCount = entries.count { it.reason.startsWith("Local API request") }

    var portText by remember(port) { mutableStateOf(port.toString()) }
    var token by remember { mutableStateOf(if (requireAuth) vm.apiServerToken else "") }
    var confirmRegenerate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local API server") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text(
                "Exposes an OpenAI-compatible /v1/chat/completions and /v1/models endpoint, backed by whichever " +
                    "model is active in this app. Off by default; nothing listens until you turn this on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Server", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { vm.setApiServerEnabled(it) })
                    }
                    Text(
                        if (enabled) "Listening on ${if (lan) "this device's LAN address" else "127.0.0.1 (this device only)"}, port $port."
                        else "Not running.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Allow other devices on this Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Off = only this phone can reach it (e.g. from Termux/Tasker). On = any device on the " +
                                    "same network can reach it too — make sure auth is required below if you turn this on.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = lan, onCheckedChange = { vm.setLanApiServerEnabled(it) })
                    }
                }
            }

            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Port", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { text ->
                            portText = text.filter(Char::isDigit).take(5)
                            portText.toIntOrNull()?.let { if (it in 1024..65535) vm.setApiServerPort(it) }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
            }

            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Require an API key", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Requests must send Authorization: Bearer <token>. Strongly recommended if the LAN toggle above is on.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = requireAuth,
                            onCheckedChange = {
                                vm.setApiServerRequireAuth(it)
                                if (it) token = vm.apiServerToken
                            }
                        )
                    }
                    if (requireAuth) {
                        Text(token, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        Row(Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { clipboard.setSensitiveText(token, scope) }) { Text("Copy") }
                            OutlinedButton(onClick = { confirmRegenerate = true }, modifier = Modifier.padding(start = 8.dp)) { Text("Regenerate") }
                        }
                    }
                }
            }

            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Requests this session: $requestCount", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Every request this server handles is logged in Diagnostics → Network activity, the same place that shows outbound calls.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (confirmRegenerate) {
        ConfirmDialog(
            title = "Regenerate API key?",
            body = "The current key stops working immediately — anything using it (scripts, other apps) will need the new one.",
            confirmLabel = "Regenerate",
            destructive = true,
            onConfirm = { token = vm.regenerateApiServerToken(); confirmRegenerate = false },
            onDismiss = { confirmRegenerate = false }
        )
    }
}
