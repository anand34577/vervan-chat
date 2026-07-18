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
import android.net.Uri
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
import com.vervan.chat.ui.common.ConfirmDialog
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.system.toUserMessage
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
    var resultIsError by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

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
            resultIsError = false
            resultMessage = try {
                app.contentResolver.openOutputStream(uri)?.use { BackupManager.export(app.container.db, it) }
                app.container.db.jobDao().upsert(job.copy(state = JobState.COMPLETED, updatedAt = System.currentTimeMillis()))
                "Backup saved."
            } catch (e: Exception) {
                app.container.db.jobDao().upsert(job.copy(state = JobState.FAILED, updatedAt = System.currentTimeMillis(), detail = e.message ?: ""))
                resultIsError = true
                "Export failed. ${e.toUserMessage()}"
            }
            busy = false
        }
    }
    // File selection just stages the URI — the actual merge (which overwrites any item with a
    // matching ID) only runs once the user confirms via the ConfirmDialog below, matching every
    // other destructive/overwriting action in the app.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImportUri = uri
    }
    fun runImport(uri: Uri) {
        scope.launch {
            busy = true
            val job = JobRecord(type = JobType.BACKUP, label = "Restore backup", state = JobState.RUNNING)
            app.container.db.jobDao().upsert(job)
            resultIsError = false
            resultMessage = try {
                val summary = app.contentResolver.openInputStream(uri)?.use { BackupManager.import(app.container.db, it) }
                app.container.db.jobDao().upsert(job.copy(state = JobState.COMPLETED, updatedAt = System.currentTimeMillis()))
                if (summary != null) {
                    "Restored ${summary.chats} chats, ${summary.notes} notes, ${summary.projects} projects, " +
                        "${summary.workspaces} workspaces, and other saved items."
                } else {
                    resultIsError = true
                    "Could not open the selected file."
                }
            } catch (e: Exception) {
                app.container.db.jobDao().upsert(job.copy(state = JobState.FAILED, updatedAt = System.currentTimeMillis(), detail = e.message ?: ""))
                resultIsError = true
                "Restore failed. ${e.toUserMessage()}"
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
        ScrollablePage(padding) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Export backup", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Includes app data, but not model files or imported documents.",
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
                        "Adds backup data and replaces matching items.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, enabled = !busy) { Text("Choose backup file") }
                }
            }
            if (busy) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = "Working with your backup",
                    body = "Reading or saving local data. Keep this screen open.",
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            resultMessage?.let {
                if (resultIsError) {
                    com.vervan.chat.ui.common.OperationErrorCard(
                        title = "Backup operation failed",
                        message = it,
                        recovery = "Your data is safe. Check the file and free storage, then try again.",
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    pendingImportUri?.let { uri ->
        ConfirmDialog(
            title = "Restore from backup?",
            body = "Matching items will be replaced. Other local data stays unchanged.",
            confirmLabel = "Restore",
            destructive = true,
            onConfirm = { pendingImportUri = null; runImport(uri) },
            onDismiss = { pendingImportUri = null }
        )
    }
}
