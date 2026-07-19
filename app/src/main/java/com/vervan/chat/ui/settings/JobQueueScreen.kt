package com.vervan.chat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.JobRecord
import com.vervan.chat.data.db.entities.JobState
import com.vervan.chat.data.db.entities.JobType
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val ACTIVE_STATES = setOf(JobState.WAITING, JobState.PREPARING, JobState.RUNNING, JobState.PAUSED)
private val STOPPABLE_TYPES = setOf(
    JobType.DOCUMENT_INDEXING, JobType.OCR, JobType.EMBEDDING, JobType.BATCH_SUMMARIZE,
    JobType.INDEX_REBUILD, JobType.MODEL_VERIFY, JobType.BENCHMARK, JobType.LONG_GENERATION,
    JobType.TTS_MODEL_DOWNLOAD
)
private enum class JobView(val label: String) { ACTIVE("Active"), HISTORY("History"), ALL("All") }

class JobQueueViewModel(app: VervanApp) : ViewModel() {
    private val dao = app.container.db.jobDao()
    val jobs: StateFlow<List<JobRecord>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun stop(job: JobRecord) {
        if (job.state !in ACTIVE_STATES || job.type !in STOPPABLE_TYPES) return
        viewModelScope.launch { dao.requestStop(job.id) }
    }

    fun clearHistory() = viewModelScope.launch { dao.clearFinished() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobQueueScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: JobQueueViewModel = viewModel(factory = viewModelFactory { initializer { JobQueueViewModel(app) } })
    val jobs by vm.jobs.collectAsState()
    var view by remember { mutableStateOf(JobView.ACTIVE) }
    var confirmClear by remember { mutableStateOf(false) }
    val active = jobs.filter { it.state in ACTIVE_STATES }
    val history = jobs.filter { it.state !in ACTIVE_STATES }
    val visible = when (view) {
        JobView.ACTIVE -> active
        JobView.HISTORY -> history
        JobView.ALL -> jobs
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job queue") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) {
                        Icon(Icons.Filled.DeleteSweep, "Clear job history")
                    }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
            Column(Modifier.fillMaxSize().padding(top = Space.sm)) {
                JobSummary(active = active.size, waiting = active.count { it.state == JobState.WAITING }, history = history.size)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    JobView.entries.forEach { option ->
                        VervanFilterChip(
                            selected = view == option,
                            onClick = { view = option },
                            label = { Text("${option.label} (${if (option == JobView.ACTIVE) active.size else if (option == JobView.HISTORY) history.size else jobs.size})") }
                        )
                    }
                }
                if (visible.isEmpty()) {
                    EmptyState(
                        icon = if (view == JobView.HISTORY) Icons.Filled.History else Icons.AutoMirrored.Filled.ListAlt,
                        title = if (view == JobView.HISTORY) "No job history" else "Nothing is running",
                        body = if (view == JobView.HISTORY) "Completed and stopped jobs appear here." else "Background work will appear here automatically.",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        items(visible, key = { it.id }) { job -> JobCard(job, onStop = { vm.stop(job) }) }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear job history?",
            body = "Remove ${history.size} finished job${if (history.size == 1) "" else "s"}? Active work will stay.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = { vm.clearHistory(); confirmClear = false },
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
private fun JobSummary(active: Int, waiting: Int, history: Int) {
    Card(
        Modifier.fillMaxWidth(),
        colors = SurfaceRole.Raised.cardColors(),
        border = SurfaceRole.Raised.border()
    ) {
        Row(Modifier.fillMaxWidth().padding(Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            SummaryStat(active.toString(), "In progress", Icons.Filled.PendingActions, Modifier.weight(1f))
            SummaryStat(waiting.toString(), "Waiting", Icons.AutoMirrored.Filled.ListAlt, Modifier.weight(1f))
            SummaryStat(history.toString(), "History", Icons.Filled.History, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun JobCard(job: JobRecord, onStop: () -> Unit) {
    val active = job.state in ACTIVE_STATES
    val failed = job.state == JobState.FAILED
    Card(Modifier.fillMaxWidth(), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(job.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(jobTypeLabel(job), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(jobStateLabel(job.state), style = MaterialTheme.typography.labelMedium, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            if (active) {
                if (job.progress in 1..99) {
                    LinearProgressIndicator(progress = { job.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = Space.xs))
                    Text("${job.progress}% complete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Space.xs))
                }
            }
            if (job.detail.isNotBlank()) {
                Text(
                    if (failed) job.detail.toUserMessage() else job.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(job.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (active && job.type in STOPPABLE_TYPES) {
                    TextButton(onClick = onStop) {
                        Icon(Icons.Filled.StopCircle, null, modifier = Modifier.padding(end = Space.xs))
                        Text("Stop")
                    }
                } else if (active) {
                    Text("Finishing current step", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun jobStateLabel(state: JobState): String = when (state) {
    JobState.WAITING -> "Waiting"
    JobState.PREPARING -> "Preparing"
    JobState.RUNNING -> "In progress"
    JobState.PAUSED -> "Paused"
    JobState.COMPLETED -> "Completed"
    JobState.FAILED -> "Needs attention"
    JobState.CANCELLED -> "Stopped"
}

private fun jobTypeLabel(job: JobRecord): String = job.type.name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
