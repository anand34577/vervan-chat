package com.vervan.chat.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material3.Scaffold
import com.vervan.chat.ui.common.VervanToggle as Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.setSensitiveText
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole

/** Best-effort Wi-Fi IPv4 address for display only ("browse to this from another device") — not
 * used anywhere in the actual server bind logic (NanoHTTPD always binds all interfaces; see
 * ApiServerService). Returns null rather than throwing if no non-loopback IPv4 interface is up
 * (airplane mode, Ethernet-only, VPN-only), since this is purely informational. */
private fun localLanAddress(): String? = runCatching {
    java.net.NetworkInterface.getNetworkInterfaces().asSequence()
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<java.net.Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
}.getOrNull()

/**
 * on/off, port, auth requirement + token, and a live request counter reusing
 * [com.vervan.chat.system.NetworkAuditLog] so the same trust dashboard that proves outbound
 * silence also covers this server's inbound traffic.
 *
 * The server always binds every network interface while it's on (see ApiServerService) — there
 * is no separate "allow LAN" switch anymore, since one that looked off while the server was
 * still reachable over Wi-Fi would be actively misleading on a screen whose whole point is
 * showing the truth about network exposure. "Require an API key" is the one real gate left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiServerScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val enabled by vm.apiServerEnabled.collectAsState()
    val port by vm.apiServerPort.collectAsState()
    val requireAuth by vm.apiServerRequireAuth.collectAsState()
    val fullMode by vm.apiServerFullMode.collectAsState()
    val autoStart by vm.apiServerAutoStart.collectAsState()
    val entries by app.container.networkAuditLog.entries.collectAsState()
    val clients by app.container.apiClientRegistry.clients.collectAsState()
    val requestCount = entries.count { it.reason.startsWith("Local API request") }

    val modelTtl by vm.apiModelTtlSeconds.collectAsState()
    val appTools by vm.apiServerAppTools.collectAsState()
    val allowWriteTools by vm.apiServerAllowWriteTools.collectAsState()
    var portText by remember(port) { mutableStateOf(port.toString()) }
    var ttlText by remember(modelTtl) { mutableStateOf(modelTtl.toString()) }
    var portError by remember { mutableStateOf<String?>(null) }
    var ttlError by remember { mutableStateOf<String?>(null) }
    // Keyed on requireAuth so the token presentation follows the current security choice. An
    // unkeyed remember can capture the initial state forever, leaving the key field and Copy
    // action empty after authentication is enabled.
    var token by remember(requireAuth) { mutableStateOf(if (requireAuth) vm.apiServerToken else "") }
    var confirmRegenerate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local API server") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        // Reachable-with-no-key is the one state worth calling out in color everywhere else in
        // this app (see PrivacyDashboardScreen's identical lanRisk) — kept consistent here rather
        // than inventing a second color language for the same fact on its own settings screen.
        // Full web app mode exposes app data (chats, documents, attachments), not just inference,
        // so running it without a key is worth flagging harder than the inference-only case — but
        // the key stays the user's choice either way.
        val lanRisk = enabled && !requireAuth
        val fullModeWithoutKey = enabled && fullMode && !requireAuth
        val statusTone = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            lanRisk -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }

        ScrollablePage(padding) {
            Text(
                "Let local apps use the active model through OpenAI-compatible endpoints.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.md)
            )

            // Status hero — the one thing this whole screen exists to answer ("is it on, and is it
            // safe"), so it gets the same colored-icon-card emphasis as the Local API server card
            // on the Privacy Dashboard, not a plain row identical to every setting below it.
            Card(
                Modifier.fillMaxWidth().padding(bottom = Space.sm),
                colors = CardDefaults.cardColors(
                    containerColor = if (lanRisk) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(Modifier.padding(Space.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(active = enabled, tone = statusTone)
                        Column(Modifier.weight(1f).padding(start = Space.md)) {
                            Text(
                                "Server",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (lanRisk) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (enabled) "Listening on port $port" else "Not running",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (lanRisk) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = { vm.setApiServerEnabled(it) })
                    }
                    AnimatedVisibility(visible = enabled, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(Modifier.padding(top = Space.md)) {
                            if (lanRisk) {
                                StatusLine(
                                    Icons.Filled.Warning,
                                    "Reachable from this Wi-Fi network with no API key required.",
                                    MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            val lanAddress = remember { localLanAddress() }
                            StatusLine(
                                Icons.Filled.Wifi,
                                if (lanAddress != null) "From another device: http://$lanAddress:$port/"
                                else "From another device: http://<this device's LAN IP>:$port/",
                                if (lanRisk) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // A one-time ?token= query param saves the user from copy-pasting the
                            // key on this same device (see webui/index.html, which reads it once
                            // then scrubs it from the URL); a second device still needs the LAN
                            // URL + key shown above, pasted in by hand.
                            OutlinedButton(
                                onClick = {
                                    val tokenParam = if (requireAuth) "?token=${Uri.encode(vm.apiServerToken)}" else ""
                                    val url = "http://127.0.0.1:$port/$tokenParam"
                                    runCatching {
                                        app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                },
                                modifier = Modifier.padding(top = Space.sm)
                            ) {
                                Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Open web UI", modifier = Modifier.padding(start = Space.xs))
                            }
                        }
                    }
                }
            }

            ApiSection(
                title = "Start automatically",
                icon = Icons.Filled.RestartAlt,
                trailing = { Switch(checked = autoStart, onCheckedChange = { vm.setApiServerAutoStart(it) }) }
            ) {
                Text(
                    if (autoStart)
                        "The server starts on its own each time you open Vervan, and is restarted if " +
                            "Android has shut it down in the background. Turning the server off above " +
                            "still stops it until you open the app again."
                    else
                        "The server only runs when you switch it on above, and stays off after you close " +
                            "the app. Turn this on to have it come back by itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ApiSection(
                title = "Web app mode",
                icon = Icons.Filled.Language,
                trailing = { Switch(checked = fullMode, onCheckedChange = { vm.setApiServerFullMode(it) }) }
            ) {
                Text(
                    if (fullMode)
                        "The browser page is the full app: chat, RAG over your knowledge bases, document " +
                            "upload, and vision/audio when the selected model supports them. The server " +
                            "runs on this device, but prompts may still leave it if the selected model is remote."
                    else
                        "The browser page is a bare status/API page — just the OpenAI-compatible endpoints " +
                            "for other apps to call. Turn this on for the full chat experience in a browser " +
                            "instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedVisibility(visible = fullMode, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    StatusLine(
                        Icons.Filled.Warning,
                        if (fullModeWithoutKey)
                            "No API key is required right now, so anyone who can reach this device on the " +
                                "network can read and change your chats, knowledge bases and documents — not " +
                                "just run inference. Turn on \"Require an API key\" below unless you trust " +
                                "every device on this network."
                        else
                            "Bigger trust boundary than Basic: also lets whoever has the URL (and the API " +
                                "key, if required) read and add to your knowledge bases and documents, not " +
                                "just run inference.",
                        MaterialTheme.colorScheme.error,
                        topPadding = Space.sm
                    )
                }
            }

            ApiSection(title = "Server configuration", icon = Icons.Filled.Router) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { text ->
                        portText = text.filter(Char::isDigit).take(5)
                        portError = when {
                            portText.isBlank() -> null
                            portText.toIntOrNull() !in 1024..65535 -> "Port must be between 1024 and 65535"
                            else -> null
                        }
                        portText.toIntOrNull()?.let { if (it in 1024..65535) vm.setApiServerPort(it) }
                    },
                    label = { Text("Port") },
                    isError = portError != null,
                    supportingText = portError?.let { { Text(it) } },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Timer, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Model idle timeout (TTL)",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = Space.xs)
                    )
                }
                Text(
                    "A request with nothing loaded automatically loads your default model. This is how " +
                        "long it stays in memory after the last request before it's unloaded again. 0 keeps " +
                        "it loaded until you unload it yourself. Models you load in the app are never " +
                        "unloaded by this.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs, bottom = Space.sm)
                )
                OutlinedTextField(
                    value = ttlText,
                    onValueChange = { text ->
                        ttlText = text.filter(Char::isDigit).take(5)
                        ttlError = when {
                            ttlText.isBlank() -> null
                            ttlText.toIntOrNull() !in 0..86_400 -> "TTL must be between 0 and 86400 seconds"
                            else -> null
                        }
                        ttlText.toIntOrNull()?.let { if (it in 0..86_400) vm.setApiModelTtlSeconds(it) }
                    },
                    suffix = { Text("seconds") },
                    isError = ttlError != null,
                    supportingText = ttlError?.let { { Text(it) } },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Two gates, not one: whether an API caller may reach this phone's own tools at all,
            // and — separately — whether the ones that change data or open another app are
            // included. In the app a tool call is a card the user sees and can approve; an API
            // request has no such moment, which is why both default to off.
            ApiSection(
                title = "Let API clients use this device's tools",
                icon = Icons.Filled.Build,
                trailing = { Switch(checked = appTools, onCheckedChange = { vm.setApiServerAppTools(it) }) }
            ) {
                Text(
                    "Off: an API client can still declare its own tools and run them itself — that never " +
                        "needs permission here. On: the model can also call this phone's tools (search your " +
                        "notes, check the battery, and so on) while answering an API request.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedVisibility(visible = appTools, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(Modifier.padding(top = Space.md)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Include tools that write or open apps",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = allowWriteTools, onCheckedChange = { vm.setApiServerAllowWriteTools(it) })
                        }
                        Text(
                            "Without this, only read-only tools run for an API request. With it, a remote " +
                                "caller's model can create notes, log expenses and open other apps on this " +
                                "device with no confirmation prompt.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (allowWriteTools) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs)
                        )
                    }
                }
            }

            ApiSection(
                title = "Require an API key",
                icon = if (requireAuth) Icons.Filled.Lock else Icons.Filled.LockOpen,
                trailing = {
                    Switch(
                        checked = requireAuth,
                        onCheckedChange = {
                            vm.setApiServerRequireAuth(it)
                            if (it) token = vm.apiServerToken
                        }
                    )
                }
            ) {
                Text(
                    "The server is reachable from your local network whenever it's on — this is the only " +
                        "thing standing between that and anyone on the network being able to use it with " +
                        "no key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedVisibility(visible = requireAuth, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(Modifier.padding(top = Space.sm)) {
                        Text(
                            token,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
                                .padding(horizontal = Space.md, vertical = Space.sm)
                        )
                        Row(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            OutlinedButton(onClick = { clipboard.setSensitiveText(token, scope) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Copy", modifier = Modifier.padding(start = Space.xs))
                            }
                            OutlinedButton(onClick = { confirmRegenerate = true }) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Regenerate", modifier = Modifier.padding(start = Space.xs))
                            }
                        }
                    }
                }
            }

            Card(
                Modifier.fillMaxWidth().padding(vertical = Space.sm),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.padding(start = Space.md)) {
                        Text("Requests this session: $requestCount", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "View requests in Settings → Storage & backup → Diagnostics.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Who is actually talking to the server right now. A bare request counter says traffic
            // happened; this says where from — which is the part worth acting on when the server is
            // reachable over the network with no key.
            ApiSection(
                title = "Connected clients",
                icon = Icons.Filled.Devices,
                trailing = {
                    if (clients.isNotEmpty()) {
                        OutlinedButton(onClick = { app.container.apiClientRegistry.clear() }) { Text("Clear") }
                    }
                }
            ) {
                if (clients.isEmpty()) {
                    Text(
                        if (enabled) "Nothing has connected yet this session."
                        else "The server is off, so nothing can connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Seen since the app started. Cleared when the app restarts — this is a live view, " +
                            "not a stored record of who used your device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Space.sm)
                    )
                    clients.forEach { client -> ConnectedClientRow(client, requireAuth) }
                }
            }
        }
    }

    if (confirmRegenerate) {
        ConfirmDialog(
            title = "Regenerate API key?",
            body = "The current key stops working now. Update it in every connected app.",
            confirmLabel = "Regenerate",
            destructive = true,
            onConfirm = { token = vm.regenerateApiServerToken(); confirmRegenerate = false },
            onDismiss = { confirmRegenerate = false }
        )
    }
}

/**
 * One row of the Connected clients list. A remote address is the thing worth noticing, so it gets
 * the warning tone while loopback (this phone talking to itself — the bundled web UI, or another
 * app on the device) stays neutral: colouring every entry alarming would make the one that matters
 * invisible.
 */
@Composable
private fun ConnectedClientRow(client: com.vervan.chat.server.ApiClientInfo, requireAuth: Boolean) {
    val tone = when {
        client.isLocal -> MaterialTheme.colorScheme.onSurfaceVariant
        !requireAuth -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        Modifier.fillMaxWidth().padding(vertical = Space.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (client.isLocal) Icons.Filled.PhoneAndroid else Icons.Filled.Wifi,
                    contentDescription = null, tint = tone, modifier = Modifier.size(18.dp)
                )
                Text(
                    client.address,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tone,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = Space.sm)
                )
                Text(
                    if (client.isLocal) "This device" else "Network",
                    style = MaterialTheme.typography.labelSmall,
                    color = tone
                )
            }
            if (client.userAgent.isNotBlank()) {
                Text(
                    client.userAgent,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }
            Text(
                "${client.requestCount} request${if (client.requestCount == 1) "" else "s"} · " +
                    "last ${relativeSince(client.lastSeenAt)} · ${client.lastPath}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.xs)
            )
            // Only meaningful while a key is actually required — with the key off, nothing was ever
            // verified and saying "no key presented" about every client would be noise.
            if (requireAuth) {
                Text(
                    when {
                        client.unauthorizedCount > 0 && !client.authenticated ->
                            "Rejected ${client.unauthorizedCount} time${if (client.unauthorizedCount == 1) "" else "s"} — wrong or missing key"
                        client.unauthorizedCount > 0 -> "Signed in (${client.unauthorizedCount} earlier attempt rejected)"
                        else -> "Signed in with the API key"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (client.authenticated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }
        }
    }
}

/** Coarse "how long ago", matching the phrasing the web UI's own chat list uses. */
private fun relativeSince(timestamp: Long): String {
    val minutes = ((System.currentTimeMillis() - timestamp) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}

/** One setting group — icon + title header, optional trailing control (a Switch, typically), then
 * free-form body content. Same "titled card, not six identical anonymous ones" idiom as
 * PrivacyDashboardScreen's PrivacySection, adapted with a header icon since every section here
 * maps to one recognizable concept (network, timeout, key) that reads faster as a glyph. */
@Composable
private fun ApiSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(vertical = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
        Column(Modifier.padding(Space.lg)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    title, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(start = Space.sm).semantics { heading() }
                )
                trailing?.invoke()
            }
            Column(Modifier.padding(top = Space.sm)) { content() }
        }
    }
}

/** Filled/hollow status dot with a soft glow ring when active — the same "online" affordance
 * webui/full.html's own connection indicator uses, so the native Settings screen and the web app
 * agree on what "the server is on" looks like at a glance. */
@Composable
private fun StatusDot(active: Boolean, tone: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(if (active) tone else MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun StatusLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tone: Color,
    topPadding: androidx.compose.ui.unit.Dp = Space.xs
) {
    Row(Modifier.fillMaxWidth().padding(top = topPadding), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = tone, modifier = Modifier.padding(start = Space.xs))
    }
}
