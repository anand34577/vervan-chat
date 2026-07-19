package com.vervan.chat.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.PageContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelInfo

/** Shows what each installed model declares support for — the same [ModelInfo] fields that
 * already gate the composer's photo/camera/voice buttons and the Tools/Reasoning toggles
 * (see ChatScreen), just surfaced directly instead of only being inferred from what's greyed out. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelCapabilityDashboardScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val models by app.container.db.modelDao().observeModels().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model capabilities") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        if (models.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                ToolIntro(
                    icon = Icons.Filled.Memory,
                    title = "Know what each model can do",
                    body = "Compare model features, context, and compatible runtimes."
                )
                EmptyState(
                    icon = Icons.Filled.Memory,
                    title = "No models to compare",
                    body = "Import a model to see its features and device compatibility."
                )
            }
        } else {
        LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
            item {
                ToolIntro(
                    icon = Icons.Filled.Memory,
                    title = "Know what each model can do",
                    body = "Compare model features, context, and compatible runtimes.",
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            items(models, key = { it.id }) { model ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${model.fileSizeBytes / (1024 * 1024)} MB on disk · context ${model.contextTokens ?: "—"} tokens" +
                                (if (model.isActive) " · Active" else ""),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CapBadge("Text", true)
                            CapBadge("Vision", model.supportsVision)
                            CapBadge("Audio", model.supportsAudio)
                            CapBadge("Tools", model.supportsTools)
                            CapBadge("Thinking", model.supportsThinking)
                            CapBadge("Backend: ${model.lastWorkingBackend.name}", null, neutral = true)
                        }
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun CapBadge(label: String, supported: Boolean?, neutral: Boolean = false) {
    val color = when {
        neutral -> MaterialTheme.colorScheme.surfaceContainer
        supported == true -> MaterialTheme.colorScheme.primaryContainer
        supported == false -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val text = when {
        neutral -> label
        supported == true -> "$label ✓"
        supported == false -> "$label ✗"
        else -> "$label ?"
    }
    Surface(shape = MaterialTheme.shapes.small, color = color) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
