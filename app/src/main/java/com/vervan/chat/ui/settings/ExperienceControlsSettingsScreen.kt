package com.vervan.chat.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.vervan.chat.ui.common.VervanToggle as Switch
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.SystemStatusStrip
import com.vervan.chat.ui.common.StatusTone
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.SectionLabel
import com.vervan.chat.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperienceControlsSettingsScreen(
    onBack: () -> Unit,
    onOpenGeneration: () -> Unit = {},
    onOpenModels: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })
    val expertMode by vm.expertMode.collectAsState()
    val contextLimit by vm.contextTokenLimit.collectAsState()
    val responseLength by vm.responseLength.collectAsState()
    val preferredBackend by vm.preferredBackend.collectAsState()
    val deviceAwarePerformance by vm.deviceAwarePerformance.collectAsState()
    val autoModelSelectionEnabled by vm.autoModelSelectionEnabled.collectAsState()
    val fastCapableRoutingEnabled by vm.fastCapableRoutingEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat behavior") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            FeatureHero(
                icon = Icons.Filled.Tune,
                eyebrow = "Calm by default, powerful on demand",
                title = "Shape how Vervan works",
                body = "Keep the everyday experience simple, or expose more control over models, routing, and response behavior."
            )
            SystemStatusStrip(
                title = if (expertMode) "Expert mode active" else "Standard mode",
                body = if (expertMode) {
                    "Shows exact model, retrieval, context, and generation controls."
                } else {
                    "Shows recommended presets while preserving custom values."
                },
                tone = if (expertMode) StatusTone.Info else StatusTone.Ready
            )

            SectionLabel("Mode")
            androidx.compose.material3.Card(Modifier.padding(top = Space.sm)) {
                ListItem(
                    headlineContent = { Text("Expert mode") },
                    supportingContent = { Text("Show advanced model and response controls.") },
                    leadingContent = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = expertMode,
                            onCheckedChange = { enabled ->
                                vm.setExpertMode(enabled)
                            }
                        )
                    }
                )
            }

            androidx.compose.material3.Card(Modifier.padding(top = Space.sm)) {
                ListItem(
                    headlineContent = { Text("Choose models automatically") },
                    supportingContent = { Text("Selects a suitable installed model for each message. Turn off to choose one yourself.") },
                    leadingContent = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    trailingContent = {
                        Switch(checked = autoModelSelectionEnabled, onCheckedChange = vm::setAutoModelSelectionEnabled)
                    }
                )
                if (autoModelSelectionEnabled) {
                    androidx.compose.material3.HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Route short/long messages differently") },
                        supportingContent = {
                            Text(
                                "For a chat left on Balanced, uses a smaller installed model for short messages " +
                                    "and a larger one for long/complex ones. Switching models mid-conversation " +
                                    "costs a brief reload."
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Speed, contentDescription = null) },
                        trailingContent = {
                            Switch(checked = fastCapableRoutingEnabled, onCheckedChange = vm::setFastCapableRoutingEnabled)
                        }
                    )
                }
            }

            androidx.compose.material3.Card(Modifier.padding(top = Space.sm)) {
                ListItem(
                    headlineContent = { Text("Adapt to device conditions") },
                    supportingContent = { Text("Reduces demand when Android reports low power or high heat.") },
                    leadingContent = { Icon(Icons.Filled.Speed, contentDescription = null) },
                    trailingContent = {
                        Switch(checked = deviceAwarePerformance, onCheckedChange = vm::setDeviceAwarePerformance)
                    }
                )
            }

            SectionLabel("Current defaults")
            SettingsRow(Icons.Filled.Memory, "Context capacity", "$contextLimit tokens") {}
            SettingsRow(Icons.Filled.Speed, "Response length", responseLength.lowercase().replaceFirstChar { it.uppercase() }) {}
            SettingsRow(Icons.Filled.Tune, "Performance", preferredBackend.lowercase().replaceFirstChar { it.uppercase() }) {}

            SectionLabel("More settings")
            SettingsRow(
                Icons.Filled.Tune,
                "Generation settings",
                if (expertMode) "Sampling, context, and llama.cpp controls" else "Response style, length, and search",
                onOpenGeneration
            )
            SettingsRow(
                Icons.Filled.Memory,
                "Per-model settings",
                if (expertMode) "Raw overrides for each installed model" else "Easy model-specific presets",
                onOpenModels
            )

            Text(
                "Custom values stay unchanged until you choose a new preset.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.lg)
            )
        }
    }
}
