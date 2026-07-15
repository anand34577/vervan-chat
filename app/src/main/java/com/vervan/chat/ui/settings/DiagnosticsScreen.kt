package com.vervan.chat.ui.settings

import android.app.ActivityManager
import android.os.Build
import android.os.StatFs
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, onOpenPermissions: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val models by app.container.db.modelDao().observeModels().collectAsState(initial = emptyList())
    val active by app.container.db.modelDao().observeActiveModel(ModelRole.GENERATION).collectAsState(initial = null)
    val documents by app.container.db.documentDao().observeAll().collectAsState(initial = emptyList())
    val chats by app.container.db.chatDao().observeAllChats().collectAsState(initial = emptyList())
    val notes by app.container.db.noteDao().observeAll().collectAsState(initial = emptyList())
    val thermal by app.container.thermalMonitor.level.collectAsState()
    val networkEntries by app.container.networkAuditLog.entries.collectAsState()
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
        // Network trust dashboard (Phase D) — every intentional network call this app makes is
        // meant to report to NetworkAuditLog first, so "no silent networking" is verifiable
        // instead of just claimed. Empty today because there are no network call sites at all
        // yet (no downloader, no update checker, no analytics) — see NetworkAuditLog's own doc.
        "Network activity" to if (networkEntries.isEmpty()) {
            listOf("This session" to "0 outbound connections — nothing in this app has contacted the network")
        } else {
            networkEntries.takeLast(10).map {
                java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it.timestamp)) to it.reason
            }
        }
    )
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics & storage") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        val text = sections.joinToString("\n\n") { (title, rows) ->
                            (listOf(title) + rows.map { (label, value) -> "$label: $value" }).joinToString("\n")
                        }
                        clipboard.setText(AnnotatedString(text))
                        scope.launch { snackbarHostState.showSnackbar("Copied diagnostics") }
                    }) { Icon(Icons.Filled.ContentCopy, "Copy all") }
                    IconButton(onClick = onOpenPermissions) { Icon(Icons.Filled.Shield, "Permissions") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            sections.forEach { (title, rows) -> DiagnosticCard(title, rows) }
            Text(
                "Compatibility is determined by real initialization and sanity inference during import; filenames are not capability declarations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DiagnosticCard(title: String, rows: List<Pair<String, String>>) {
    Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { (label, value) -> Text("$label: $value", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}
