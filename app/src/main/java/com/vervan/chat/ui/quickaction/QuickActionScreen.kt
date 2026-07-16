package com.vervan.chat.ui.quickaction

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.ErrorCard
import com.vervan.chat.ui.common.setSensitiveText
import com.vervan.chat.ui.writing.WritingAction
import com.vervan.chat.ui.writing.WritingWorkspaceViewModel

/**
 * Compact popup shown by [QuickActionActivity] for `ACTION_PROCESS_TEXT`. Reuses
 * [WritingWorkspaceViewModel] wholesale rather than re-implementing prompt/generation logic —
 * this is the exact same rewrite/summarize/translate one-shot flow the full Writing workspace
 * screen already provides, just with the selection pre-filled and a compact layout.
 */
@Composable
fun QuickActionScreen(
    originalText: String,
    canInsertBack: Boolean,
    onInsertBack: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: WritingWorkspaceViewModel = viewModel(factory = viewModelFactory { initializer { WritingWorkspaceViewModel(app) } })
    val revision by vm.revision.collectAsState()
    val running by vm.running.collectAsState()
    val error by vm.error.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var targetLanguage by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp
    ) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Vervan Chat", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    originalText.take(300),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Row(
                Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    WritingAction.REWRITE, WritingAction.SHORTEN, WritingAction.EXPAND,
                    WritingAction.SIMPLIFY, WritingAction.FIX_GRAMMAR, WritingAction.TRANSLATE
                ).forEach { action ->
                    FilterChip(
                        selected = false,
                        onClick = { vm.run(action, originalText, targetLanguage) },
                        label = { Text(action.label) },
                        enabled = !running
                    )
                }
            }
            if (running) {
                Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error?.let { ErrorCard(title = "Couldn't generate", body = it, modifier = Modifier.padding(top = 12.dp)) }
            if (revision.isNotBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(revision, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { clipboard.setSensitiveText(revision, scope) }) { Text("Copy") }
                    if (canInsertBack) {
                        TextButton(onClick = { onInsertBack(revision) }) { Text("Replace") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
