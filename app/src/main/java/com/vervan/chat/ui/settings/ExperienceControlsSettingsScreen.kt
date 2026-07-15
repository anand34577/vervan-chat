package com.vervan.chat.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.SystemStatusStrip
import com.vervan.chat.ui.common.StatusTone
import com.vervan.chat.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperienceControlsSettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })
    val expertMode by vm.expertMode.collectAsState()
    val contextLimit by vm.contextTokenLimit.collectAsState()
    val responseLength by vm.responseLength.collectAsState()
    val preferredBackend by vm.preferredBackend.collectAsState()
    var confirmExpert by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Experience & controls") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Space.lg)
        ) {
            SystemStatusStrip(
                title = if (expertMode) "Expert mode active" else "Standard mode",
                body = if (expertMode) {
                    "Exact model, retrieval, context, and generation controls are visible. Switching back preserves custom values."
                } else {
                    "Recommended outcome presets stay up front. Exact custom values remain preserved in the background."
                },
                tone = if (expertMode) StatusTone.Info else StatusTone.Ready
            )

            SectionLabel("Mode")
            androidx.compose.material3.Card(Modifier.padding(top = Space.xs)) {
                ListItem(
                    headlineContent = { Text("Expert mode") },
                    supportingContent = { Text("Show exact model, context, retrieval, generation, and diagnostic controls.") },
                    leadingContent = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = expertMode,
                            onCheckedChange = { enabled ->
                                if (enabled && !expertMode) confirmExpert = true else vm.setExpertMode(enabled)
                            }
                        )
                    }
                )
            }

            SectionLabel("Current resolved defaults")
            SettingsRow(Icons.Filled.Memory, "Context capacity", "$contextLimit tokens · preserved when modes change") {}
            SettingsRow(Icons.Filled.Speed, "Response length", responseLength.lowercase().replaceFirstChar { it.uppercase() }) {}
            SettingsRow(Icons.Filled.Tune, "Performance", preferredBackend.lowercase().replaceFirstChar { it.uppercase() }) {}

            Text(
                "Standard presets never silently overwrite exact values. Choose a preset in Generation & retrieval to replace a custom expert value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.lg)
            )
        }
    }

    if (confirmExpert) {
        AlertDialog(
            onDismissRequest = { confirmExpert = false },
            title = { Text("Enable Expert mode?") },
            text = { Text("Your current settings will be preserved. You can switch back to Standard mode at any time without losing custom values.") },
            confirmButton = {
                TextButton(onClick = { vm.setExpertMode(true); confirmExpert = false }) { Text("Enable") }
            },
            dismissButton = { TextButton(onClick = { confirmExpert = false }) { Text("Cancel") } }
        )
    }
}
