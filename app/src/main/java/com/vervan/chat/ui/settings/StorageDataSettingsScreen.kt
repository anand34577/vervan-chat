package com.vervan.chat.ui.settings

import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.ContentCard
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.OperationProgressCard
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.CancellationException
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StorageDataViewModel(private val app: VervanApp) : ViewModel() {
    private val _overview = MutableStateFlow(StorageOverview())
    val overview: StateFlow<StorageOverview> = _overview
    private val reload = MutableStateFlow(0)
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _clearing = MutableStateFlow(false)
    val clearing: StateFlow<Boolean> = _clearing

    init {
        viewModelScope.launch {
            reload
                .flatMapLatest {
                    combine(
                app.container.db.modelDao().observeModels(),
                app.container.db.documentDao().observeAll()
                    ) { models, documents -> models.sumOf { it.fileSizeBytes } to documents.map { it.filePath } }
                }
                // refresh() does a full recursive walk of filesDir (which holds multi-GB model
                // files). Document indexing and model rows re-emit on every status/progress write
                // with the size-relevant inputs (total model bytes, document paths) unchanged, so
                // collapse those here — otherwise we'd re-stat gigabytes per second exactly while
                // the disk is already busy writing.
                .distinctUntilChanged()
                .onStart { _isLoading.value = true }
                .onEach {
                    _isLoading.value = false
                    _error.value = null
                }
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _isLoading.value = false
                    _error.value = throwable.message ?: "Storage details could not be loaded."
                    emit(0L to emptyList())
                }
                .collect { (modelBytes, documentPaths) ->
                    try {
                        refresh(modelBytes, documentPaths)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        _error.value = t.message ?: "Storage details could not be loaded."
                    }
                }
        }
    }

    fun retry() {
        _error.value = null
        reload.value += 1
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _clearing.value = true
            _error.value = null
            try {
                app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                val current = _overview.value
                refresh(current.models, app.container.db.documentDao().observeAll().first().map { it.filePath })
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _error.value = t.message ?: "Cache could not be cleared."
            } finally {
                _clearing.value = false
            }
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
    val overview by vm.overview.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val loadError by vm.error.collectAsStateWithLifecycle()
    val clearing by vm.clearing.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage & backup") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            when {
                loadError != null -> OperationErrorCard(
                    title = "Storage details unavailable",
                    message = loadError.orEmpty(),
                    recovery = "Your files are safe. Retry reading storage details.",
                    actionLabel = "Retry",
                    onAction = vm::retry
                )
                isLoading -> OperationProgressCard(
                    title = "Reading storage",
                    body = "Calculating model, document, cache, and database usage."
                )
                else -> {
            StorageHero(overview)
            Text("App data", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.lg, bottom = Space.sm))
            StorageBreakdown(overview)
            ContentCard {
                Column(Modifier.fillMaxWidth().padding(Space.lg)) {
                    Text("Where your data lives", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Chats, settings, imported documents, and models are stored in Vervan's private app storage. " +
                            "Other apps cannot browse it. Uninstalling Vervan removes this local data because Android system backup is disabled. " +
                            "Files you export remain wherever you choose to save them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs)
                    )
                }
            }
            ContentCard {
                Row(Modifier.fillMaxWidth().padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Storage, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                        Text("Temporary files", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (overview.cache == 0L) "Cache is already clear" else "${formatBytes(overview.cache)} can be removed safely",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = vm::clearCache, enabled = overview.cache > 0L && !clearing) { Text(if (clearing) "Clearing…" else "Clear") }
                }
            }

            Text("Data tools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.lg, bottom = Space.sm))
            SettingsRow(Icons.Filled.ImportExport, "Backup & restore", "Export or restore your data", onOpenBackup)
            SettingsRow(Icons.Filled.DeleteOutline, "Recycle bin", "Restore items or delete them forever", onOpenRecycleBin)
            SettingsRow(Icons.AutoMirrored.Filled.ListAlt, "Background jobs", "View, stop, or clear work", onOpenJobs)
            SettingsRow(Icons.Filled.Build, "Search index", "Repair or rebuild document search", onOpenIndexMaintenance)
            SettingsRow(Icons.Filled.SmartToy, "Model calculator", "Find model sizes that fit this device", onOpenModelCalculator)
            SettingsRow(Icons.Filled.MonitorHeart, "Diagnostics", "Inspect runtime and device health", onOpenDiagnostics)
                }
            }
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
        Column(Modifier.padding(Space.lg)) {
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
        }
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
