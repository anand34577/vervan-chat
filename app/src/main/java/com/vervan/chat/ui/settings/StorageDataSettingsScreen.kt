package com.vervan.chat.ui.settings

import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StorageOverview(
    val deviceTotal: Long = 0,
    val deviceFree: Long = 0,
    val appTotal: Long = 0,
    val models: Long = 0,
    val documents: Long = 0,
    val database: Long = 0,
    val cache: Long = 0,
    val other: Long = 0
)

class StorageDataViewModel(private val app: VervanApp) : ViewModel() {
    private val _overview = MutableStateFlow(StorageOverview())
    val overview: StateFlow<StorageOverview> = _overview

    init {
        viewModelScope.launch {
            combine(
                app.container.db.modelDao().observeModels(),
                app.container.db.documentDao().observeAll()
            ) { models, documents -> models.sumOf { it.fileSizeBytes } to documents.map { it.filePath } }
                // refresh() does a full recursive walk of filesDir (which holds multi-GB model
                // files). Document indexing and model rows re-emit on every status/progress write
                // with the size-relevant inputs (total model bytes, document paths) unchanged, so
                // collapse those here — otherwise we'd re-stat gigabytes per second exactly while
                // the disk is already busy writing.
                .distinctUntilChanged()
                .collect { (modelBytes, documentPaths) ->
                    refresh(modelBytes, documentPaths)
                }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            val current = _overview.value
            refresh(current.models, app.container.db.documentDao().observeAll().first().map { it.filePath })
        }
    }

    private suspend fun refresh(modelBytes: Long, documentPaths: List<String>) = withContext(Dispatchers.IO) {
        val stats = StatFs(app.filesDir.path)
        val documentBytes = documentPaths.sumOf { path -> File(path).takeIf(File::isFile)?.length() ?: 0L }
        val dbFile = app.getDatabasePath("vervan.db")
        val databaseBytes = listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).sumOf { if (it.exists()) it.length() else 0L }
        val cacheBytes = directorySize(app.cacheDir)
        val filesBytes = directorySize(app.filesDir)
        val appTotal = filesBytes + databaseBytes + cacheBytes
        val known = modelBytes + documentBytes + databaseBytes + cacheBytes
        _overview.value = StorageOverview(
            deviceTotal = stats.totalBytes,
            deviceFree = stats.availableBytes,
            appTotal = appTotal,
            models = modelBytes,
            documents = documentBytes,
            database = databaseBytes,
            cache = cacheBytes,
            other = (appTotal - known).coerceAtLeast(0L)
        )
    }

    private fun directorySize(file: File): Long = when {
        !file.exists() -> 0L
        file.isFile -> file.length()
        else -> file.listFiles()?.sumOf(::directorySize) ?: 0L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDataSettingsScreen(
    onBack: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenRecycleBin: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenJobs: () -> Unit = {},
    onOpenIndexMaintenance: () -> Unit = {},
    onOpenModelCalculator: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: StorageDataViewModel = viewModel(factory = viewModelFactory { initializer { StorageDataViewModel(app) } })
    val overview by vm.overview.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage & data") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            StorageHero(overview)
            Text("App data", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.lg, bottom = Space.sm))
            StorageBreakdown(overview)
            Card(
                Modifier.fillMaxWidth().padding(top = Space.sm),
                colors = com.vervan.chat.ui.theme.SurfaceRole.Card.cardColors(),
                border = com.vervan.chat.ui.theme.SurfaceRole.Card.border()
            ) {
                Row(Modifier.fillMaxWidth().padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Storage, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                        Text("Temporary files", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (overview.cache == 0L) "Cache is already clear" else "${formatBytes(overview.cache)} can be removed safely",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = vm::clearCache, enabled = overview.cache > 0L) { Text("Clear") }
                }
            }

            Text("Manage", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.xl, bottom = Space.xs))
            SettingsRow(Icons.Filled.ImportExport, "Import & export", "Back up your saved app data", onOpenBackup)
            SettingsRow(Icons.Filled.DeleteOutline, "Recycle bin", "Restore or permanently remove deleted items", onOpenRecycleBin)
            SettingsRow(Icons.AutoMirrored.Filled.ListAlt, "Job queue", "View, stop, and clear background work", onOpenJobs)
            SettingsRow(Icons.Filled.Build, "Index maintenance", "Repair or rebuild document search", onOpenIndexMaintenance)
            SettingsRow(Icons.Filled.SmartToy, "Model calculator", "Find model sizes that fit this device", onOpenModelCalculator)
            SettingsRow(Icons.Filled.MonitorHeart, "Diagnostics", "Inspect runtime and device health", onOpenDiagnostics)
        }
    }
}

@Composable
private fun StorageHero(stats: StorageOverview) {
    val used = (stats.deviceTotal - stats.deviceFree).coerceAtLeast(0L)
    val usedFraction = if (stats.deviceTotal > 0) used.toFloat() / stats.deviceTotal else 0f
    Card(
        Modifier.fillMaxWidth(),
        colors = com.vervan.chat.ui.theme.SurfaceRole.Raised.cardColors(),
        border = com.vervan.chat.ui.theme.SurfaceRole.Raised.border()
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    val track = MaterialTheme.colorScheme.surfaceContainerHighest
                    val progress = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.size(92.dp)) {
                        val stroke = 10.dp.toPx()
                        drawArc(track, -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(progress, -90f, 360f * usedFraction.coerceIn(0f, 1f), false, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(usedFraction * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("device used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f).padding(start = Space.lg)) {
                    Text("${formatBytes(stats.deviceFree)} free", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("of ${formatBytes(stats.deviceTotal)} device storage", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Vervan uses ${formatBytes(stats.appTotal)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Space.sm))
                }
            }
            DeviceStorageBar(usedFraction)
        }
    }
}

@Composable
private fun DeviceStorageBar(usedFraction: Float) {
    val usedColor = MaterialTheme.colorScheme.primary
    val freeColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        drawRoundRect(freeColor, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        drawRoundRect(usedColor, size = Size(size.width * usedFraction.coerceIn(0f, 1f), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
    }
}

private data class StoragePart(val label: String, val bytes: Long, val color: Color)

@Composable
private fun StorageBreakdown(stats: StorageOverview) {
    val parts = listOf(
        StoragePart("Models", stats.models, MaterialTheme.colorScheme.primary),
        StoragePart("Documents", stats.documents, MaterialTheme.colorScheme.tertiary),
        StoragePart("Database", stats.database, MaterialTheme.colorScheme.secondary),
        StoragePart("Cache", stats.cache, MaterialTheme.colorScheme.error),
        StoragePart("Other", stats.other, MaterialTheme.colorScheme.outline)
    )
    val total = parts.sumOf { it.bytes }.coerceAtLeast(1L)
    Card(
        Modifier.fillMaxWidth(),
        colors = com.vervan.chat.ui.theme.SurfaceRole.Card.cardColors(),
        border = com.vervan.chat.ui.theme.SurfaceRole.Card.border()
    ) {
        Row(Modifier.fillMaxWidth().padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(124.dp)) {
                    var start = -90f
                    val stroke = 18.dp.toPx()
                    parts.filter { it.bytes > 0 }.forEach { part ->
                        val sweep = 360f * part.bytes.toFloat() / total
                        drawArc(part.color, start, (sweep - 2f).coerceAtLeast(0.5f), false, style = Stroke(stroke, cap = StrokeCap.Butt))
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatBytes(stats.appTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("app data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).padding(start = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                parts.forEach { part -> StorageLegendRow(part) }
            }
        }
    }
}

@Composable
private fun StorageLegendRow(part: StoragePart) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(part.color, CircleShape))
        Text(part.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(start = Space.sm))
        Text(formatBytes(part.bytes), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}
