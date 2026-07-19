package com.vervan.chat.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSearchField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.tools.ToolDefinition
import com.vervan.chat.tools.ToolRegistry
import com.vervan.chat.tools.ToolRisk
import com.vervan.chat.ui.common.StatusChip
import com.vervan.chat.ui.common.StatusTone
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole

/**
 * Global tool catalog — every [ToolRegistry] entry, searchable, each individually switchable.
 * A chat can still override any of these just for itself (see the "Chat tools" sheet opened
 * from the chat screen's mode menu, [com.vervan.chat.ui.chat.ChatToolsDialog]) — this screen is
 * only the default every new chat inherits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })
    val disabledIds by vm.disabledToolIds.collectAsState()
    val alwaysDateTime by vm.alwaysIncludeDateTime.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = remember(query) {
        if (query.isBlank()) ToolRegistry.tools
        else ToolRegistry.tools.filter { it.name.contains(query, true) || it.description.contains(query, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tools") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
          Column(Modifier.fillMaxSize()) {
            Text(
                "Choose which tools chats can use by default. Each chat can override this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )

            Card(
                Modifier.fillMaxWidth().padding(bottom = Space.xs),
                colors = SurfaceRole.Card.cardColors(),
                border = SurfaceRole.Card.border()
            ) {
                Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Always tell the model the date & time", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Include the current date and time in every prompt.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = alwaysDateTime, onCheckedChange = { vm.setAlwaysIncludeDateTime(it) })
                }
            }

            VervanSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search model tools",
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.name }) { tool ->
                    ToolRow(
                        tool = tool,
                        enabled = tool.name !in disabledIds,
                        onToggle = { vm.setToolEnabled(tool.name, it) }
                    )
                }
            }
          }
        }
    }
}

@Composable
private fun ToolRow(tool: ToolDefinition, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = Space.xs),
        colors = SurfaceRole.Card.cardColors(),
        border = SurfaceRole.Card.border()
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tool.name, style = MaterialTheme.typography.titleSmall)
                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusChip(
                    label = riskLabel(tool.risk),
                    tone = when (tool.risk) {
                        ToolRisk.READ_ONLY -> StatusTone.Ready
                        ToolRisk.REVERSIBLE_WRITE -> StatusTone.Warning
                        ToolRisk.EXTERNAL_ACTION -> StatusTone.Info
                    },
                    modifier = Modifier.padding(top = Space.xs)
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

internal fun riskLabel(risk: ToolRisk): String = when (risk) {
    ToolRisk.READ_ONLY -> "Read-only"
    ToolRisk.REVERSIBLE_WRITE -> "Writes data (undoable)"
    ToolRisk.EXTERNAL_ACTION -> "Opens another app"
}
