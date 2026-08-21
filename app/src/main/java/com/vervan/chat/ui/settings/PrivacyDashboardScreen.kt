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
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.vervan.chat.R
import com.vervan.chat.ui.common.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.traits
import com.vervan.chat.ui.common.ModernistMetricStrip
import com.vervan.chat.ui.common.ModernistScreenHeader
import com.vervan.chat.ui.common.ModernistTag
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

    val dashboardVm: PrivacyDashboardViewModel = viewModel(
        factory = viewModelFactory { initializer { PrivacyDashboardViewModel(app) } }
    )

    val models by dashboardVm.models.collectAsState()
    val activeModel by dashboardVm.activeModel.collectAsState()
    val documents by dashboardVm.documents.collectAsState()
    val chats by dashboardVm.chats.collectAsState()
    val memories by dashboardVm.memories.collectAsState()
    val knowledgeBases by dashboardVm.knowledgeBases.collectAsState()
    val totalChunks by dashboardVm.totalChunks.collectAsState()
    val embeddedChunks by dashboardVm.embeddedChunks.collectAsState()
    val networkEntries by dashboardVm.networkEntries.collectAsState()

    val calendarOn by vm.calendarToolEnabled.collectAsState()
    val deviceStatusOn by vm.deviceStatusToolEnabled.collectAsState()
    val apiServerOn by vm.apiServerEnabled.collectAsState()
    val apiServerLanOn by vm.apiServerAllowLan.collectAsState()
    val apiServerAuthOn by vm.apiServerRequireAuth.collectAsState()

    // Localhost-only mode is not a LAN exposure. LAN mode is explicit and authentication is
    // enforced for it by both settings persistence and ApiServerService.
    val lanRisk = apiServerOn && apiServerLanOn && !apiServerAuthOn
    val remoteModelActive = activeModel?.traits?.runsOnDevice == false
    val remoteHost = activeModel?.remoteBaseUrl?.let { runCatching { it.toUri().host }.getOrNull() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_dashboard_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.action_back)) } }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            ModernistScreenHeader(
                eyebrow = stringResource(R.string.ui_privacydashboardscreen_103_trust_privacy),
                title = stringResource(R.string.ui_privacydashboardscreen_104_privacy),
                body = stringResource(R.string.ui_privacydashboardscreen_105_see_exactly_what_can_leave_this_device),
                trailing = { ModernistTag("NO OUTBOUND TRAFFIC", active = !remoteModelActive && !apiServerOn) }
            )
            ModernistMetricStrip(
                metrics = listOf(
                    "MODEL" to (activeModel?.displayName ?: "NONE"),
                    "CHATS" to chats.size.toString(),
                    "DOCS" to documents.size.toString(),
                    "NETWORK" to networkEntries.size.toString()
                ),
                modifier = Modifier.padding(bottom = Space.md)
            )
            Card(
                Modifier.fillMaxWidth().padding(bottom = Space.sm),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(Space.lg)) {
                    Text(
                        when {
                            remoteModelActive -> stringResource(R.string.privacy_remote_dashboard_title)
                            activeModel == null -> stringResource(R.string.privacy_no_model_dashboard_title)
                            else -> stringResource(R.string.privacy_local_dashboard_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        when {
                            remoteModelActive -> stringResource(
                                R.string.privacy_remote_dashboard_body,
                                remoteHost ?: stringResource(R.string.privacy_configured_remote_endpoint)
                            )
                            activeModel == null -> stringResource(R.string.privacy_no_model_dashboard_body)
                            else -> stringResource(R.string.privacy_local_dashboard_body)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(top = Space.xs)
                    )
                }
            }

            PrivacySection(stringResource(R.string.privacy_stored_on_device)) {
                PrivacyRow(stringResource(R.string.privacy_active_model), activeModel?.displayName ?: stringResource(R.string.privacy_none))
                PrivacyRow(stringResource(R.string.privacy_installed_models), "${models.size} · ${formatBytes(models.sumOf { it.fileSizeBytes })}")
                PrivacyRow(
                    stringResource(R.string.privacy_documents),
                    "${documents.size} · ${formatBytes(documents.sumOf { java.io.File(it.filePath).takeIf(java.io.File::exists)?.length() ?: 0L })}"
                )
                PrivacyRow(stringResource(R.string.privacy_chats), chats.size.toString())
                PrivacyRow(stringResource(R.string.privacy_remembered_facts), memories.size.toString())
            }

            PrivacySection(stringResource(R.string.privacy_what_can_leave)) {
                PrivacyRow(stringResource(R.string.privacy_remote_requests), if (remoteModelActive) stringResource(R.string.privacy_enabled) else stringResource(R.string.privacy_not_active))
                PrivacyRow(stringResource(R.string.privacy_downloads), stringResource(R.string.privacy_only_when_requested))
                PrivacyRow(stringResource(R.string.privacy_external_tools), stringResource(R.string.privacy_controlled_in_security))
                Text(
                    stringResource(R.string.privacy_local_request_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }

            PrivacySection(stringResource(R.string.privacy_indexed_for_search)) {
                PrivacyRow(stringResource(R.string.privacy_knowledge_bases), knowledgeBases.size.toString())
                PrivacyRow(
                    stringResource(R.string.privacy_passages_indexed),
                    if (totalChunks == 0) stringResource(R.string.privacy_none_yet)
                    else stringResource(R.string.privacy_semantically_searchable, embeddedChunks, totalChunks)
                )
                if (totalChunks > 0 && embeddedChunks < totalChunks) {
                    Text(
                        stringResource(R.string.privacy_keyword_search_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs)
                    )
                }
            }

            PrivacySection(stringResource(R.string.privacy_model_access)) {
                PrivacyRow(stringResource(R.string.privacy_calendar), if (calendarOn) stringResource(R.string.privacy_on) else stringResource(R.string.privacy_off))
                PrivacyRow(stringResource(R.string.privacy_device_status), if (deviceStatusOn) stringResource(R.string.privacy_on) else stringResource(R.string.privacy_off))
                OutlinedButton(onClick = onOpenSecurity, modifier = Modifier.padding(top = Space.sm)) {
                    Text(stringResource(R.string.privacy_manage_security))
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
                            stringResource(R.string.privacy_local_api_server),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (lanRisk) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        when {
                            !apiServerOn -> stringResource(R.string.privacy_api_server_off)
                            lanRisk -> stringResource(R.string.privacy_api_server_no_key)
                            else -> stringResource(R.string.privacy_api_server_key_required)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lanRisk) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs)
                    )
                    OutlinedButton(onClick = onOpenApiServer, modifier = Modifier.padding(top = Space.sm)) {
                        Text(stringResource(R.string.privacy_open_api))
                    }
                }
            }

            PrivacySection(stringResource(R.string.privacy_recent_network_activity)) {
                if (networkEntries.isEmpty()) {
                    Text(
                        stringResource(R.string.privacy_no_network_activity),
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
                    Text(stringResource(R.string.privacy_full_diagnostics))
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = Space.sm), colors = com.vervan.chat.ui.theme.SurfaceRole.Card.cardColors(), border = com.vervan.chat.ui.theme.SurfaceRole.Card.border()) {
        Column(Modifier.padding(Space.lg)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Column(Modifier.padding(top = Space.sm)) { content() }
        }
    }
}

@Composable
private fun PrivacyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.58f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.42f)
        )
    }
}
