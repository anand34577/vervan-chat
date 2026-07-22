package com.vervan.chat.ui.tools

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.data.db.entities.ToolRun
import com.vervan.chat.data.db.entities.ToolRunState
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.common.VervanTopAppBar
import com.vervan.chat.ui.common.setText
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolRunHistoryScreen(
    onBack: () -> Unit,
    onContinueInChat: (String) -> Unit,
    onRerun: (String) -> Unit,
    highlightId: String? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val runs by app.container.db.toolRunDao().observeAll().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var expandedId by remember(highlightId) { mutableStateOf(highlightId) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val filtered = remember(runs, query) {
        if (query.isBlank()) runs else runs.filter {
            it.toolName.contains(query, true) || it.input.contains(query, true) || it.output.contains(query, true)
        }
    }

    Scaffold(
        topBar = {
            VervanTopAppBar(
                title = { Text("Run history") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
            Column(Modifier.fillMaxSize()) {
                VervanSearchField(query, { query = it }, "Search tool results", Modifier.padding(vertical = Space.sm))
                if (filtered.isEmpty()) {
                    EmptyState(Icons.Filled.History, "No tool runs yet", "Results from AI tools and voice sessions will stay here after you leave their screens.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        items(filtered, key = { it.id }) { run ->
                            ToolRunCard(
                                run = run,
                                expanded = expandedId == run.id,
                                onToggle = { expandedId = if (expandedId == run.id) null else run.id },
                                onCopy = { clipboard.setText(run.output.ifBlank { run.input }, scope) },
                                onSave = { scope.launch { app.container.db.savedOutputDao().upsert(SavedOutput(content = run.output.ifBlank { run.input }, label = run.toolName)) } },
                                onContinue = { onContinueInChat(run.output.ifBlank { run.input }) },
                                onShare = {
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, run.toolName)
                                        putExtra(Intent.EXTRA_TEXT, run.output.ifBlank { run.input })
                                    }, "Share result"))
                                },
                                onRerun = { onRerun(run.toolRoute) },
                                onDelete = { scope.launch { app.container.db.toolRunDao().softDelete(run.id) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolRunCard(
    run: ToolRun,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onContinue: () -> Unit,
    onShare: () -> Unit,
    onRerun: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onToggle, colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
        Column(Modifier.fillMaxWidth().padding(Space.md)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(run.toolName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        run.state.name.lowercase().replaceFirstChar { it.titlecase() } +
                            (run.modelName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (run.state) {
                            ToolRunState.FAILED -> MaterialTheme.colorScheme.error
                            ToolRunState.RUNNING -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(run.updatedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                run.output.ifBlank { run.errorMessage ?: run.input },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) 20 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.sm),
            )
            if (expanded) {
                Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    RunAction(Icons.Filled.ContentCopy, "Copy", onCopy)
                    RunAction(Icons.Filled.Save, "Save", onSave)
                    RunAction(Icons.Filled.PlayArrow, "Continue", onContinue)
                    RunAction(Icons.Filled.Share, "Share", onShare)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    AssistChip(onClick = onRerun, label = { Text("Re-run") })
                    AssistChip(onClick = onDelete, label = { Text("Remove") }, leadingIcon = { Icon(Icons.Filled.Delete, null, Modifier.size(16.dp)) })
                }
            }
        }
    }
}

@Composable
private fun RunAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) })
}
