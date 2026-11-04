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
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.rememberReducedMotion
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.SectionCard
import com.vervan.chat.ui.common.SectionLabel
import com.vervan.chat.ui.common.SectionRow
import com.vervan.chat.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })
    val fontScale by vm.fontScale.collectAsStateWithLifecycle()
    val hapticsEnabled by vm.hapticsEnabled.collectAsStateWithLifecycle()
    val largeTouchTargets by vm.largeTouchTargets.collectAsStateWithLifecycle()
    val highContrast by vm.highContrast.collectAsStateWithLifecycle()
    val reducedMotion = rememberReducedMotion()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accessibility_settings_accessibility)) },
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
                icon = Icons.Filled.TouchApp,
                eyebrow = "Comfort and clarity",
                title = stringResource(R.string.accessibility_settings_make_every_control_easier_to),
                body = stringResource(R.string.accessibility_settings_adjust_text_contrast_touch_targets)
            )
            SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_accessibility_settings_reading))
            androidx.compose.material3.Card {
                Column(Modifier.padding(Space.lg)) {
                    ListItem(
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        headlineContent = { Text(stringResource(R.string.accessibility_settings_text_size)) },
                        supportingContent = { Text(stringResource(R.string.accessibility_text_size_value, String.format("%.0f", fontScale * 100))) },
                        leadingContent = { Icon(Icons.Filled.TextFields, contentDescription = null) }
                    )
                    val textSizeDescription = stringResource(R.string.accessibility_text_size_slider, String.format("%.0f", fontScale * 100))
                    Slider(
                        value = fontScale,
                        onValueChange = { vm.setFontScale(it) },
                        valueRange = 0.85f..1.5f,
                        steps = 12,
                        modifier = Modifier.semantics {
                            contentDescription = textSizeDescription
                        }
                    )
                }
            }

            SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_accessibility_settings_contrast))
            SectionCard(
                items = listOf(
                    {
                        SectionRow(
                            modifier = Modifier.toggleable(value = highContrast, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = vm::setHighContrast),
                            title = stringResource(R.string.accessibility_settings_high_contrast),
                            subtitle = stringResource(R.string.accessibility_settings_makes_muted_text_and_borders),
                            icon = Icons.Filled.Contrast,
                            trailing = { Switch(checked = highContrast, onCheckedChange = null) }
                        )
                    }
                )
            )

            SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_accessibility_settings_interaction))
            SectionCard(
                items = listOf(
                    {
                        SectionRow(
                            modifier = Modifier.toggleable(value = largeTouchTargets, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = vm::setLargeTouchTargets),
                            title = stringResource(R.string.accessibility_settings_large_touch_targets),
                            subtitle = stringResource(R.string.accessibility_settings_use_larger_controls_and_roomier),
                            icon = Icons.Filled.TouchApp,
                            trailing = { Switch(checked = largeTouchTargets, onCheckedChange = null) }
                        )
                    },
                    {
                        SectionRow(
                            modifier = Modifier.toggleable(value = hapticsEnabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = vm::setHapticsEnabled),
                            title = stringResource(R.string.accessibility_settings_haptic_feedback),
                            subtitle = stringResource(R.string.accessibility_settings_vibrate_briefly_for_key_actions),
                            icon = Icons.Filled.Vibration,
                            trailing = { Switch(checked = hapticsEnabled, onCheckedChange = null) }
                        )
                    }
                )
            )

            SectionLabel(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.hdr_accessibility_settings_motion))
            SectionCard(
                items = listOf(
                    {
                        SectionRow(
                            title = if (reducedMotion) stringResource(R.string.accessibility_settings_reduced_motion_is_on) else stringResource(R.string.accessibility_settings_reduced_motion_is_off),
                            subtitle = stringResource(R.string.accessibility_settings_uses_your_android_animation_setting),
                            icon = Icons.Filled.Animation
                        )
                    }
                )
            )
            Text(
                stringResource(R.string.accessibility_settings_vervan_uses_talkback_labels_large),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.lg)
            )
        }
    }
}
