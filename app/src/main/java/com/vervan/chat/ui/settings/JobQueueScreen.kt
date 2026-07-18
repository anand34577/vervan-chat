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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.JobRecord
import com.vervan.chat.data.db.entities.JobState
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.theme.VervanMono
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class JobQueueViewModel(app: VervanApp) : ViewModel() {
    private val db = app.container.db
    val jobs: StateFlow<List<JobRecord>> = db.jobDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobQueueScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: JobQueueViewModel = viewModel(factory = viewModelFactory { initializer { JobQueueViewModel(app) } })
    val jobs by vm.jobs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job queue") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
          if (jobs.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                title = "No background jobs yet",
                body = "Track indexing, model checks, benchmarks, and backups.",
                modifier = Modifier
            )
          } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(jobs, key = { it.id }) { job ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(job.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(
                                    when (job.state) {
                                        JobState.WAITING -> "Waiting"
                                        JobState.PREPARING -> "Preparing"
                                        JobState.RUNNING -> "In progress"
                                        JobState.PAUSED -> "Paused"
                                        JobState.COMPLETED -> "Completed"
                                        JobState.FAILED -> "Needs attention"
                                        JobState.CANCELLED -> "Cancelled"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (job.state == JobState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(job.type.name, style = MaterialTheme.typography.labelSmall, fontFamily = VervanMono)
                            if (job.state == JobState.RUNNING || job.state == JobState.PREPARING) {
                                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                            }
                            if (job.detail.isNotBlank()) {
                                Text(
                                    if (job.state == JobState.FAILED) job.detail.toUserMessage() else job.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (job.state == JobState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
          }
        }
    }
}
