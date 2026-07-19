package com.vervan.chat.ui.quickaction

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
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
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.OperationProgressCard
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.setSensitiveText
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.ui.theme.vervanSubtleDividerColor
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
    var selectedAction by remember { mutableStateOf<WritingAction?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(Space.lg),
        shape = VervanExtraShapes.hero,
        color = SurfaceRole.Overlay.containerColor(),
        border = SurfaceRole.Overlay.border(),
        shadowElevation = SurfaceRole.Overlay.shadowElevation
    ) {
        Column(Modifier.padding(Space.xl).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAffordance(icon = Icons.Filled.AutoAwesome, size = IconAffordanceSize.Default)
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    Text("QUICK ACTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Edit with Vervan", style = MaterialTheme.typography.titleMedium)
                }
            }
            Card(
                Modifier.fillMaxWidth().padding(top = Space.md),
                colors = SurfaceRole.Sunken.cardColors(),
                border = SurfaceRole.Sunken.border()
            ) {
                Text(
                    originalText.take(300),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Space.md)
                )
            }
            Row(
                Modifier.padding(top = Space.md).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                listOf(
                    WritingAction.REWRITE, WritingAction.SHORTEN, WritingAction.EXPAND,
                    WritingAction.SIMPLIFY, WritingAction.FIX_GRAMMAR, WritingAction.TRANSLATE
                ).forEach { action ->
                    VervanFilterChip(
                        selected = selectedAction == action,
                        onClick = {
                            selectedAction = action
                            if (action != WritingAction.TRANSLATE || targetLanguage.isNotBlank()) {
                                vm.run(action, originalText, targetLanguage)
                            }
                        },
                        label = { Text(action.label) },
                        enabled = !running
                    )
                }
            }
            if (selectedAction == WritingAction.TRANSLATE) {
                OutlinedTextField(
                    value = targetLanguage,
                    onValueChange = { targetLanguage = it },
                    label = { Text("Translate to") },
                    placeholder = { Text("e.g. Spanish") },
                    singleLine = true,
                    enabled = !running,
                    trailingIcon = {
                        TextButton(
                            onClick = { vm.run(WritingAction.TRANSLATE, originalText, targetLanguage) },
                            enabled = !running && targetLanguage.isNotBlank()
                        ) { Text("Go") }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = Space.md)
                )
            }
            if (running) {
                OperationProgressCard(
                    title = "Preparing your text",
                    body = "Applying the selected action locally.",
                    modifier = Modifier.padding(top = Space.lg)
                )
            }
            error?.let {
                OperationErrorCard(
                    title = "Couldn't generate a result",
                    message = it,
                    recovery = "Your text is safe. Check the model, then try again.",
                    modifier = Modifier.padding(top = Space.md)
                )
            }
            if (revision.isNotBlank()) {
                HorizontalDivider(Modifier.padding(vertical = Space.md), color = vervanSubtleDividerColor())
                Text("RESULT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(revision, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs))
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { clipboard.setSensitiveText(revision, scope) }) { Text("Copy") }
                    if (canInsertBack) {
                        FilledTonalButton(onClick = { onInsertBack(revision) }) { Text("Replace") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
