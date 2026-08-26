package com.vervan.chat.ui.settings

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
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import com.vervan.chat.ui.common.VervanToggle as Switch
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    val fontScale by vm.fontScale.collectAsState()
    val hapticsEnabled by vm.hapticsEnabled.collectAsState()
    val largeTouchTargets by vm.largeTouchTargets.collectAsState()
    val highContrast by vm.highContrast.collectAsState()
    val reducedMotion = rememberReducedMotion()
    val textSizeDescription = stringResource(
        com.vervan.chat.R.string.accessibility_text_size_description,
        String.format(java.util.Locale.getDefault(), "%.0f", fontScale * 100)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.vervan.chat.R.string.accessibility_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.vervan.chat.R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            FeatureHero(
                icon = Icons.Filled.TouchApp,
                eyebrow = stringResource(com.vervan.chat.R.string.accessibility_eyebrow),
                title = stringResource(com.vervan.chat.R.string.accessibility_hero_title),
                body = stringResource(com.vervan.chat.R.string.accessibility_hero_body)
            )
            SectionLabel(stringResource(com.vervan.chat.R.string.accessibility_reading))
            androidx.compose.material3.Card {
                Column(Modifier.padding(Space.lg)) {
                    ListItem(
                        headlineContent = { Text(stringResource(com.vervan.chat.R.string.accessibility_text_size)) },
                        supportingContent = { Text(stringResource(com.vervan.chat.R.string.accessibility_text_size_support, String.format(java.util.Locale.getDefault(), "%.0f", fontScale * 100))) },
                        leadingContent = { Icon(Icons.Filled.TextFields, contentDescription = null) }
                    )
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

            SectionLabel(stringResource(com.vervan.chat.R.string.accessibility_contrast))
            SectionCard(
                items = listOf(
                    {
                        SectionRow(
                            title = stringResource(com.vervan.chat.R.string.accessibility_high_contrast),
                            subtitle = stringResource(com.vervan.chat.R.string.accessibility_high_contrast_body),
                            icon = Icons.Filled.Contrast,
                            trailing = { Switch(checked = highContrast, onCheckedChange = vm::setHighContrast) }
                        )
                    }
                )
            )

            SectionLabel(stringResource(com.vervan.chat.R.string.accessibility_interaction))
            SectionCard(
                items = listOf(
                    {
                        SectionRow(
                            title = stringResource(com.vervan.chat.R.string.accessibility_large_targets),
                            subtitle = stringResource(com.vervan.chat.R.string.accessibility_large_targets_body),
                            icon = Icons.Filled.TouchApp,
                            trailing = { Switch(checked = largeTouchTargets, onCheckedChange = vm::setLargeTouchTargets) }
                        )
                    },
                    {
                        SectionRow(
                            title = stringResource(com.vervan.chat.R.string.accessibility_haptics),
                            subtitle = stringResource(com.vervan.chat.R.string.accessibility_haptics_body),
                            icon = Icons.Filled.Vibration,
                            trailing = { Switch(checked = hapticsEnabled, onCheckedChange = vm::setHapticsEnabled) }
                        )
                    }
                )
            )

            SectionLabel(stringResource(com.vervan.chat.R.string.accessibility_motion))
            SectionCard(
                items = listOf(
                    {
                        SectionRow(
                            title = if (reducedMotion) stringResource(com.vervan.chat.R.string.accessibility_reduced_motion_on) else stringResource(com.vervan.chat.R.string.accessibility_reduced_motion_off),
                            subtitle = stringResource(com.vervan.chat.R.string.accessibility_motion_body),
                            icon = Icons.Filled.Animation
                        )
                    }
                )
            )
            Text(
                stringResource(com.vervan.chat.R.string.accessibility_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.lg)
            )
        }
    }
}
