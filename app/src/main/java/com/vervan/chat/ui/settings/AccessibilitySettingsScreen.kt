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
    val fontScale by vm.fontScale.collectAsState()
    val hapticsEnabled by vm.hapticsEnabled.collectAsState()
    val largeTouchTargets by vm.largeTouchTargets.collectAsState()
    val highContrast by vm.highContrast.collectAsState()
    val reducedMotion = rememberReducedMotion()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accessibility") },
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
                icon = Icons.Filled.TouchApp,
                eyebrow = "Comfort and clarity",
                title = "Make every control easier to use",
                body = "Adjust text, contrast, touch targets, feedback, and motion to match how you use your device."
            )
            SectionLabel("Reading")
            androidx.compose.material3.Card {
                Column(Modifier.padding(Space.lg)) {
                    ListItem(
                        headlineContent = { Text("Text size") },
                        supportingContent = { Text("${String.format("%.0f", fontScale * 100)}% · body content supports scaling") },
                        leadingContent = { Icon(Icons.Filled.TextFields, contentDescription = null) }
                    )
                    Slider(
                        value = fontScale,
                        onValueChange = { vm.setFontScale(it) },
                        valueRange = 0.85f..1.5f,
                        steps = 12,
                        modifier = Modifier.semantics {
                            contentDescription = "Text size, ${String.format("%.0f", fontScale * 100)} percent"
                        }
                    )
                }
            }

            SectionLabel("Contrast")
            SectionCard(
                items = listOf(
                    {
                        SectionRow(
                            title = "High contrast",
                            subtitle = "Makes muted text and borders easier to see.",
                            icon = Icons.Filled.Contrast,
                            trailing = { Switch(checked = highContrast, onCheckedChange = vm::setHighContrast) }
                        )
                    }
                )
            )

            SectionLabel("Interaction")
            SectionCard(
                items = listOf(
                    {
                        SectionRow(
                            title = "Large touch targets",
                            subtitle = "Use larger controls and roomier rows.",
                            icon = Icons.Filled.TouchApp,
                            trailing = { Switch(checked = largeTouchTargets, onCheckedChange = vm::setLargeTouchTargets) }
                        )
                    },
                    {
                        SectionRow(
                            title = "Haptic feedback",
                            subtitle = "Vibrate briefly for key actions.",
                            icon = Icons.Filled.Vibration,
                            trailing = { Switch(checked = hapticsEnabled, onCheckedChange = vm::setHapticsEnabled) }
                        )
                    }
                )
            )

            SectionLabel("Motion")
            SectionCard(
                items = listOf(
                    {
                        SectionRow(
                            title = if (reducedMotion) "Reduced motion is on" else "Reduced motion is off",
                            subtitle = "Uses your Android animation setting and simpler transitions.",
                            icon = Icons.Filled.Animation
                        )
                    }
                )
            )
            Text(
                "Vervan uses TalkBack labels, large touch targets, and text-based status cues.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.lg)
            )
        }
    }
}
