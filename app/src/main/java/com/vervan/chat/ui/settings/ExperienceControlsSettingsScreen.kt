package com.vervan.chat.ui.settings

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val expertMode by vm.expertMode.collectAsStateWithLifecycle()
    val contextLimit by vm.contextTokenLimit.collectAsStateWithLifecycle()
    val responseLength by vm.responseLength.collectAsStateWithLifecycle()
    val preferredBackend by vm.preferredBackend.collectAsStateWithLifecycle()
    val deviceAwarePerformance by vm.deviceAwarePerformance.collectAsStateWithLifecycle()
    val autoModelSelectionEnabled by vm.autoModelSelectionEnabled.collectAsStateWithLifecycle()
    val fastCapableRoutingEnabled by vm.fastCapableRoutingEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.experience_controls_settings_chat_behavior)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            FeatureHero(
                icon = Icons.Filled.Tune,
                eyebrow = "Calm by default, powerful on demand",
                title = stringResource(R.string.experience_controls_settings_shape_how_vervan_works),
                body = stringResource(R.string.experience_controls_settings_keep_the_everyday_experience_si),
                modifier = Modifier.padding(bottom = Space.sm)
            )
            SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_experience_controls_settings_mode))
            androidx.compose.material3.Card(Modifier.padding(top = Space.sm)) {
                ListItem(
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.experience_controls_settings_expert_mode)) },
                    supportingContent = { Text(stringResource(R.string.experience_controls_settings_show_advanced_model_and_respons)) },
                    leadingContent = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    trailingContent = {
                        com.vervan.chat.ui.common.LabeledSwitch(
                            label = stringResource(R.string.experience_controls_settings_expert_mode),
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
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.toggleable(value = autoModelSelectionEnabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = vm::setAutoModelSelectionEnabled),
                    headlineContent = { Text(stringResource(R.string.experience_controls_settings_choose_models_automatically)) },
                    supportingContent = { Text(stringResource(R.string.experience_controls_settings_selects_a_suitable_installed_mo)) },
                    leadingContent = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    trailingContent = {
                        Switch(checked = autoModelSelectionEnabled, onCheckedChange = null)
                    }
                )
                if (autoModelSelectionEnabled) {
                    androidx.compose.material3.HorizontalDivider()
                    ListItem(
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.toggleable(value = fastCapableRoutingEnabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = vm::setFastCapableRoutingEnabled),
                        headlineContent = { Text(stringResource(R.string.experience_controls_settings_route_short_long_messages_diffe)) },
                        supportingContent = {
                            Text(
                                "Uses a smaller model for short messages and a larger one for long ones. Switching costs a brief reload."
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Speed, contentDescription = null) },
                        trailingContent = {
                            Switch(checked = fastCapableRoutingEnabled, onCheckedChange = null)
                        }
                    )
                }
            }

            androidx.compose.material3.Card(Modifier.padding(top = Space.sm)) {
                ListItem(
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.toggleable(value = deviceAwarePerformance, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = vm::setDeviceAwarePerformance),
                    headlineContent = { Text(stringResource(R.string.experience_controls_settings_adapt_to_device_conditions)) },
                    supportingContent = { Text(stringResource(R.string.experience_controls_settings_reduces_demand_when_android_rep)) },
                    leadingContent = { Icon(Icons.Filled.Speed, contentDescription = null) },
                    trailingContent = {
                        Switch(checked = deviceAwarePerformance, onCheckedChange = null)
                    }
                )
            }

            SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_experience_controls_settings_current_defaults))
            SettingsRow(Icons.Filled.Memory, "Context capacity", "$contextLimit tokens") {}
            SettingsRow(Icons.Filled.Speed, "Response length", responseLength.lowercase().replaceFirstChar { it.uppercase() }) {}
            SettingsRow(Icons.Filled.Tune, "Performance", preferredBackend.lowercase().replaceFirstChar { it.uppercase() }) {}

            SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_experience_controls_settings_more_settings))
            SettingsRow(
                Icons.Filled.Tune,
                "Generation settings",
                if (expertMode) stringResource(R.string.experience_controls_settings_sampling_context_and_llama_cpp) else stringResource(R.string.experience_controls_settings_response_style_length_and_searc),
                onOpenGeneration
            )
            SettingsRow(
                Icons.Filled.Memory,
                "Per-model settings",
                if (expertMode) stringResource(R.string.experience_controls_settings_raw_overrides_for_each_installe) else stringResource(R.string.experience_controls_settings_easy_model_specific_presets),
                onOpenModels
            )

            Text(
                stringResource(R.string.experience_controls_settings_custom_values_stay_unchanged_un),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.lg)
            )
        }
    }
}
