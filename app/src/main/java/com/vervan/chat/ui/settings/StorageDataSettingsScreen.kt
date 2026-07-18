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
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.ScrollablePage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDataSettingsScreen(
    onBack: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenRecycleBin: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenJobs: () -> Unit = {},
    onOpenIndexMaintenance: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })

    val cacheSizeBytes by vm.cacheSizeBytes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage & data") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cache", style = MaterialTheme.typography.bodyMedium)
                        Text(formatBytes(cacheSizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { vm.clearCache() }) { Text("Clear") }
                }
            }
            SettingsRow(Icons.Filled.ImportExport, "Import & export", "Back up chats, notes, personas, and more to a file", onOpenBackup)
            SettingsRow(Icons.Filled.DeleteOutline, "Recycle bin", "Restore or permanently delete recently removed items", onOpenRecycleBin)
            SettingsRow(Icons.Filled.MonitorHeart, "Diagnostics & storage", "Runtime, backend, memory, thermal, and local data usage", onOpenDiagnostics)
            SettingsRow(Icons.AutoMirrored.Filled.ListAlt, "Job queue", "Background indexing, exports, and backups", onOpenJobs)
            SettingsRow(Icons.Filled.Build, "Index maintenance", "Re-index documents and repair the search index", onOpenIndexMaintenance)
        }
    }
}
