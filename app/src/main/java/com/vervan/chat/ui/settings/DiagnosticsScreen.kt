package com.vervan.chat.ui.settings

import android.app.ActivityManager
import android.os.Build
import android.os.StatFs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.runtime.Composable
import com.vervan.chat.ui.common.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
import com.vervan.chat.ui.common.setText
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, onOpenPermissions: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val vm: DiagnosticsViewModel = viewModel(factory = viewModelFactory {
        initializer { DiagnosticsViewModel(app) }
    })
    val state by vm.state.collectAsState()
    val models = state.models
    val active = state.activeModel
    val documents = state.documents
    val chats = state.chats
    val notes = state.notes
    val thermal = state.thermal
    val networkEntries = state.networkEntries
    val isLoading by vm.isLoading.collectAsState()
    val loadError by vm.error.collectAsState()
    val memory = ActivityManager.MemoryInfo().also(context.getSystemService(ActivityManager::class.java)::getMemoryInfo)
    val free = StatFs(context.filesDir.path).availableBytes
    val sections = listOf(
        "Runtime" to listOf(
            "Active model" to (active?.displayName ?: "None"),
            "Verified backend" to (active?.lastWorkingBackend?.name ?: "Not tested"),
            "Vision / audio" to "${app.container.llmEngine.visionEnabled} / ${app.container.llmEngine.audioEnabled}",
            "Thermal" to thermal.name
        ),
        "Device" to listOf(
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "ABI" to Build.SUPPORTED_ABIS.joinToString(),
            "RAM" to "${formatBytes(memory.availMem)} available / ${formatBytes(memory.totalMem)} total",
            "App storage free" to formatBytes(free)
        ),
        "Local data" to listOf(
            "Models" to "${models.size} · ${formatBytes(models.sumOf { it.fileSizeBytes })}",
            "Documents" to "${documents.size} · ${formatBytes(documents.sumOf { java.io.File(it.filePath).takeIf(java.io.File::exists)?.length() ?: 0L })}",
            "Chats / notes" to "${chats.size} / ${notes.size}",
            "Database" to formatBytes(context.getDatabasePath("vervan.db").takeIf { it.exists() }?.length() ?: 0L)
        ),
        // Network trust dashboard — every intentional network call this app makes is
        // meant to report to NetworkAuditLog first, so "no silent networking" is verifiable
        // instead of just claimed. Downloads, store access, and API-server events report here.
        "Network activity" to if (networkEntries.isEmpty()) {
            listOf("This session" to "No recorded network activity")
        } else {
            networkEntries.takeLast(10).map {
                java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it.timestamp)) to it.reason
            }
        }
    )
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var crashLogs by remember { mutableStateOf(app.crashLogManager.listLogs()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_diagnostics)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = {
                        val text = sections.joinToString("\n\n") { (title, rows) ->
                            (listOf(title) + rows.map { (label, value) -> "$label: $value" }).joinToString("\n")
                        }
                        clipboard.setText(text, scope)
                        scope.launch { snackbarHostState.showSnackbar("Copied diagnostics") }
                    }) { Icon(Icons.Filled.ContentCopy, "Copy all") }
                    IconButton(onClick = onOpenPermissions) { Icon(Icons.Filled.Shield, "Permissions") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        ScrollablePage(padding) {
            when {
                loadError != null -> OperationErrorCard(
                    title = stringResource(R.string.ui_diagnosticsscreen_131_diagnostics_unavailable),
                    message = loadError.orEmpty(),
                    recovery = stringResource(R.string.ui_diagnostics_snapshot_recovery),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::retry,
                    modifier = Modifier.padding(bottom = Space.md)
                )
                isLoading -> LoadingSkeletonList(rows = 7)
                else -> {
                    sections.forEach { (title, rows) -> DiagnosticCard(title, rows) }
                    CrashReportsCard(
                        logs = crashLogs,
                        onShare = { file ->
                            val text = runCatching { file.readText() }.getOrDefault("")
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Vervan crash report ${file.nameWithoutExtension}")
                                putExtra(android.content.Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(android.content.Intent.createChooser(send, "Share crash report"))
                        },
                        onClear = {
                            app.crashLogManager.clear()
                            crashLogs = emptyList()
                            scope.launch { snackbarHostState.showSnackbar("Crash reports cleared") }
                        }
                    )
                    Text(
                        "Compatibility is tested during import, not guessed from filenames.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.sm)
                    )
                }
            }
        }
    }
}

/** Crash/system-exit history (see [com.vervan.chat.system.CrashLogManager]) — the offline
 * substitute for a remote crash reporter. Expand a row to read it in place; Share hands the
 * plain text to any messaging/email app so the user can send it to the developer. */
@Composable
private fun CrashReportsCard(logs: List<java.io.File>, onShare: (java.io.File) -> Unit, onClear: () -> Unit) {
    var expanded by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth().padding(bottom = Space.sm)) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_diagnosticsscreen_179_crash_reports), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (logs.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
                }
            }
            if (logs.isEmpty()) {
                Text(
                    "No crashes recorded. New crash reports will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.sm)
                )
            }
            logs.forEach { file ->
                val headline = remember(file.name) {
                    runCatching { file.useLines { it.firstOrNull() } }.getOrNull() ?: file.name
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.sm)
                        .clickable { expanded = if (expanded == file.name) null else file.name }
                ) {
                    Text(headline, style = MaterialTheme.typography.bodyMedium)
                    Text(file.nameWithoutExtension, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (expanded == file.name) {
                        Text(
                            runCatching { file.readText() }.getOrDefault("(unreadable)"),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = Space.sm)
                        )
                        TextButton(onClick = { onShare(file) }) { Text(stringResource(R.string.action_share)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCard(title: String, rows: List<Pair<String, String>>) {
    Card(Modifier.fillMaxWidth().padding(bottom = Space.sm)) {
        Column(Modifier.padding(Space.lg)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { (label, value) -> Text(stringResource(R.string.ui_diagnostics_label_value, label, value), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Space.sm)) }
        }
    }
}
