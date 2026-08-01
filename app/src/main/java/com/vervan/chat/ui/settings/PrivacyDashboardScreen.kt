package com.vervan.chat.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vervan.chat.ui.common.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.theme.Space
import java.text.DateFormat
import java.util.Date

/**
 * Read-only privacy summary — distinct from [SecuritySettingsScreen] (which is the *controls*
 * screen: toggles, PIN, panic wipe) and [DiagnosticsScreen] (developer-facing technical dump).
 * This answers three specific questions at a glance: what's stored on this device, what's been
 * indexed for retrieval, and what — if anything — has ever left it. Every figure here reads
 * from the same repositories the rest of the app already uses; nothing is computed specially
 * for this screen, so it can't drift from what's actually true.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardScreen(
    onBack: () -> Unit,
    onOpenSecurity: () -> Unit = {},
    onOpenApiServer: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })

    val models by app.container.db.modelDao().observeModels().collectAsState(initial = emptyList())
    val documents by app.container.db.documentDao().observeAll().collectAsState(initial = emptyList())
    val chats by app.container.db.chatDao().observeAllChats().collectAsState(initial = emptyList())
    val memories by app.container.db.memoryDao().observeAll().collectAsState(initial = emptyList())
    val knowledgeBases by app.container.db.knowledgeBaseDao().observeAll().collectAsState(initial = emptyList())
    val totalChunks by app.container.db.chunkDao().observeTotalCount().collectAsState(initial = 0)
    val embeddedChunks by app.container.db.chunkDao().observeEmbeddedCount().collectAsState(initial = 0)
    val networkEntries by app.container.networkAuditLog.entries.collectAsState()

    val calendarOn by vm.calendarToolEnabled.collectAsState()
    val locationOn by vm.locationToolEnabled.collectAsState()
    val deviceStatusOn by vm.deviceStatusToolEnabled.collectAsState()
    val apiServerOn by vm.apiServerEnabled.collectAsState()
    val lanExposed by vm.lanApiServerEnabled.collectAsState()

    val lanRisk = apiServerOn && lanExposed

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy dashboard") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            Card(
                Modifier.fillMaxWidth().padding(bottom = Space.sm),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(Space.lg)) {
                    Text(
                        "Vervan runs entirely on this device.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "Conversations, documents, and memory never leave your phone. The only network " +
                            "requests this app ever makes are the ones below — model downloads, Model Store " +
                            "catalogue checks, and anything you explicitly enable, like the local API server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(top = Space.xs)
                    )
                }
            }

            PrivacySection("What's stored on this device") {
                PrivacyRow("Installed models", "${models.size} · ${formatBytes(models.sumOf { it.fileSizeBytes })}")
                PrivacyRow(
                    "Documents",
                    "${documents.size} · ${formatBytes(documents.sumOf { java.io.File(it.filePath).takeIf(java.io.File::exists)?.length() ?: 0L })}"
                )
                PrivacyRow("Chats", chats.size.toString())
                PrivacyRow("Remembered facts", memories.size.toString())
            }

            PrivacySection("What's indexed for search") {
                PrivacyRow("Knowledge bases", knowledgeBases.size.toString())
                PrivacyRow(
                    "Passages indexed",
                    if (totalChunks == 0) "None yet"
                    else "$embeddedChunks of $totalChunks semantically searchable"
                )
                if (totalChunks > 0 && embeddedChunks < totalChunks) {
                    Text(
                        "The rest stay keyword-searchable — load an embedding model to finish indexing them.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs)
                    )
                }
            }

            PrivacySection("What the model can access") {
                PrivacyRow("Calendar", if (calendarOn) "On" else "Off")
                PrivacyRow("Location", if (locationOn) "On" else "Off")
                PrivacyRow("Device status", if (deviceStatusOn) "On" else "Off")
                OutlinedButton(onClick = onOpenSecurity, modifier = Modifier.padding(top = Space.sm)) {
                    Text("Manage in Privacy & security")
                }
            }

            Card(
                Modifier.fillMaxWidth().padding(bottom = Space.sm),
                colors = if (lanRisk) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(Space.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (lanRisk) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (lanRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = Space.sm)
                        )
                        Text(
                            "Local API server",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (lanRisk) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        when {
                            !apiServerOn -> "Off. No other app or device can reach this phone's models."
                            lanRisk -> "On and reachable from other devices on this Wi-Fi network."
                            else -> "On, localhost only — only apps on this phone can reach it."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lanRisk) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs)
                    )
                    OutlinedButton(onClick = onOpenApiServer, modifier = Modifier.padding(top = Space.sm)) {
                        Text("Open API server settings")
                    }
                }
            }

            PrivacySection("Recent network activity") {
                if (networkEntries.isEmpty()) {
                    Text(
                        "No recorded network activity this session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    networkEntries.takeLast(8).asReversed().forEach { entry ->
                        PrivacyRow(
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.timestamp)),
                            entry.reason
                        )
                    }
                }
                OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.padding(top = Space.sm)) {
                    Text("Full diagnostics")
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = Space.sm), colors = com.vervan.chat.ui.theme.SurfaceRole.Card.cardColors(), border = com.vervan.chat.ui.theme.SurfaceRole.Card.border()) {
        Column(Modifier.padding(Space.lg)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Column(Modifier.padding(top = Space.sm)) { content() }
        }
    }
}

@Composable
private fun PrivacyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
