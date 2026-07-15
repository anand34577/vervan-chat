package com.vervan.chat.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.backup.BackupManager
import com.vervan.chat.data.db.entities.JobRecord
import com.vervan.chat.data.db.entities.JobState
import com.vervan.chat.data.db.entities.JobType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val fileName = remember {
        "vervan-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            // B12: BACKUP is one of the job types the Job Queue screen promised would show up
            // but never did — this is the first real call site for it.
            val job = JobRecord(type = JobType.BACKUP, label = fileName, state = JobState.RUNNING)
            app.container.db.jobDao().upsert(job)
            resultMessage = try {
                app.contentResolver.openOutputStream(uri)?.use { BackupManager.export(app.container.db, it) }
                app.container.db.jobDao().upsert(job.copy(state = JobState.COMPLETED, updatedAt = System.currentTimeMillis()))
                "Backup saved."
            } catch (e: Exception) {
                app.container.db.jobDao().upsert(job.copy(state = JobState.FAILED, updatedAt = System.currentTimeMillis(), detail = e.message ?: ""))
                "Export failed: ${e.message}"
            }
            busy = false
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val job = JobRecord(type = JobType.BACKUP, label = "Restore backup", state = JobState.RUNNING)
            app.container.db.jobDao().upsert(job)
            resultMessage = try {
                val summary = app.contentResolver.openInputStream(uri)?.use { BackupManager.import(app.container.db, it) }
                app.container.db.jobDao().upsert(job.copy(state = JobState.COMPLETED, updatedAt = System.currentTimeMillis()))
                if (summary != null) {
                    "Restored ${summary.chats} chats, ${summary.notes} notes, ${summary.personas} personas, " +
                        "${summary.templates} templates, ${summary.workflows} workflows, ${summary.memories} memories, " +
                        "${summary.projects} projects, ${summary.workspaces} workspaces, ${summary.knowledgeBases} knowledge bases, " +
                        "${summary.savedOutputs} saved outputs, ${summary.studyCards} flashcards."
                } else "Could not open the selected file."
            } catch (e: Exception) {
                app.container.db.jobDao().upsert(job.copy(state = JobState.FAILED, updatedAt = System.currentTimeMillis(), detail = e.message ?: ""))
                "Import failed: ${e.message ?: "malformed backup file"}"
            }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import & export") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Export backup", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "A JSON file with your chats, notes, personas, templates, workflows, memories, projects, knowledge bases, saved outputs, and flashcards. " +
                            "Imported model files and knowledge-base documents are not included — re-import those from Models/Knowledge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Button(onClick = { exportLauncher.launch(fileName) }, enabled = !busy) { Text("Export to file") }
                }
            }
            Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Restore from backup", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Restoring merges into what's already here — matching IDs are overwritten, everything else is added.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, enabled = !busy) { Text("Choose backup file") }
                }
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp).size(24.dp), strokeWidth = 2.dp)
            }
            resultMessage?.let {
                Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
