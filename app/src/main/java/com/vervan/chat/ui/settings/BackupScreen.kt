package com.vervan.chat.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import com.vervan.chat.validation.InputLimits
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.vervan.chat.VervanApp
import com.vervan.chat.R
import com.vervan.chat.data.backup.BackupManager
import com.vervan.chat.data.db.entities.JobRecord
import com.vervan.chat.data.db.entities.JobState
import com.vervan.chat.data.db.entities.JobType
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.SystemStatusStrip
import com.vervan.chat.ui.common.StatusTone
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
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
    var showExportPassword by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var exportPasswordConfirmation by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }

    val fileName = remember {
        "vervan-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) {
            exportPassword = ""
            exportPasswordConfirmation = ""
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busy = true
            // B12: BACKUP is one of the job types the Job Queue screen promised would show up
            // but never did — this is the first real call site for it.
            val job = JobRecord(type = JobType.BACKUP, label = fileName, state = JobState.RUNNING)
            app.container.db.jobDao().upsert(job)
            resultIsError = false
            resultMessage = try {
                val output = requireNotNull(app.contentResolver.openOutputStream(uri)) {
                    "The selected location could not be opened."
                }
                output.use { BackupManager.exportEncrypted(app.container.db, it, exportPassword) }
                app.container.db.jobDao().upsert(job.copy(state = JobState.COMPLETED, updatedAt = System.currentTimeMillis()))
                "Backup saved."
            } catch (e: Exception) {
                app.container.db.jobDao().upsert(job.copy(state = JobState.FAILED, updatedAt = System.currentTimeMillis(), detail = e.message ?: ""))
                resultIsError = true
                "Export failed. ${e.toUserMessage()}"
            }
            busy = false
            exportPassword = ""
            exportPasswordConfirmation = ""
        }
    }
    // File selection just stages the URI — the actual merge (which overwrites any item with a
    // matching ID) only runs once the user confirms via the password dialog below, matching every
    // other destructive/overwriting action in the app.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            importPassword = ""
        }
    }
    fun runImport(uri: Uri, password: String) {
        scope.launch {
            busy = true
            val job = JobRecord(type = JobType.BACKUP, label = app.getString(R.string.backup_restore_title), state = JobState.RUNNING)
            app.container.db.jobDao().upsert(job)
            resultIsError = false
            resultMessage = try {
                val summary = app.contentResolver.openInputStream(uri)?.use { BackupManager.import(app.container.db, it, password) }
                if (summary != null) {
                    app.container.db.jobDao().upsert(job.copy(state = JobState.COMPLETED, updatedAt = System.currentTimeMillis()))
                    "Restored ${summary.chats} chats, ${summary.notes} notes, ${summary.projects} projects, " +
                        "${summary.workspaces} workspaces, and other saved items."
                } else {
                    app.container.db.jobDao().upsert(job.copy(state = JobState.FAILED, updatedAt = System.currentTimeMillis(), detail = "The selected file could not be opened."))
                    resultIsError = true
                    "Could not open the selected file."
                }
            } catch (e: Exception) {
                app.container.db.jobDao().upsert(job.copy(state = JobState.FAILED, updatedAt = System.currentTimeMillis(), detail = e.message ?: ""))
                resultIsError = true
                "Restore failed. ${e.toUserMessage()}"
            }
            busy = false
            importPassword = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_screen_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            Card(Modifier.fillMaxWidth(), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
                    Text(stringResource(R.string.backup_export_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.backup_export_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs, bottom = Space.md)
                    )
                    Button(onClick = { showExportPassword = true }, enabled = !busy) { Text(stringResource(R.string.backup_encrypted_export)) }
                }
            }
            Card(Modifier.fillMaxWidth().padding(top = Space.sm), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
                Column(Modifier.padding(Space.lg)) {
                    Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.backup_restore_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs, bottom = Space.md)
                    )
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, enabled = !busy) {
                        Text(stringResource(R.string.backup_choose_backup_file))
                    }
                }
            }
            if (busy) {
                com.vervan.chat.ui.common.OperationProgressCard(
                    title = stringResource(R.string.backup_processing_title),
                    body = stringResource(R.string.backup_processing_body),
                    modifier = Modifier.padding(top = Space.sm)
                )
            }
            resultMessage?.let {
                if (resultIsError) {
                    com.vervan.chat.ui.common.OperationErrorCard(
                        title = stringResource(R.string.backup_operation_failed),
                        message = it,
                        recovery = stringResource(R.string.backup_operation_recovery),
                        modifier = Modifier.padding(top = Space.sm)
                    )
                } else {
                    SystemStatusStrip(
                        title = stringResource(R.string.backup_done),
                        body = it,
                        tone = StatusTone.Ready,
                        modifier = Modifier.padding(top = Space.sm)
                    )
                }
            }
        }
    }

    if (showExportPassword) {
        AlertDialog(
            onDismissRequest = {
                showExportPassword = false
                exportPassword = ""
                exportPasswordConfirmation = ""
            },
            title = { Text(stringResource(R.string.backup_protect_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.backup_password_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it.take(InputLimits.BACKUP_PASSWORD_CHARS) },
                        label = { Text(stringResource(R.string.backup_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = exportPassword.isNotEmpty() && exportPassword.length < 8,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.md)
                    )
                    OutlinedTextField(
                        value = exportPasswordConfirmation,
                        onValueChange = { exportPasswordConfirmation = it.take(InputLimits.BACKUP_PASSWORD_CHARS) },
                        label = { Text(stringResource(R.string.backup_password_confirm_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = exportPasswordConfirmation.isNotEmpty() && exportPasswordConfirmation != exportPassword,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showExportPassword = false; exportLauncher.launch(fileName) },
                    enabled = exportPassword.length >= 8 && exportPassword == exportPasswordConfirmation
                ) { Text(stringResource(R.string.backup_choose_file)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportPassword = false
                    exportPassword = ""
                    exportPasswordConfirmation = ""
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = {
                pendingImportUri = null
                importPassword = ""
            },
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.backup_restore_password_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it.take(InputLimits.BACKUP_PASSWORD_CHARS) },
                        label = { Text(stringResource(R.string.backup_restore_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.md)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    pendingImportUri = null
                    val password = importPassword
                    importPassword = ""
                    runImport(uri, password)
                }) {
                    Text(stringResource(R.string.backup_restore_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    importPassword = ""
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
