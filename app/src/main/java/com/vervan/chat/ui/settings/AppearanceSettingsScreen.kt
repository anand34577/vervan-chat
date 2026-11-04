package com.vervan.chat.ui.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.ScrollablePage
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.ContentCard
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.data.settings.AccentTheme
import com.vervan.chat.data.settings.ThemeMode
import com.vervan.chat.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })

    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val accentTheme by vm.accentTheme.collectAsStateWithLifecycle()
    val oledTrueBlack by vm.oledTrueBlack.collectAsStateWithLifecycle()
    val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_settings_appearance)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            FeatureHero(
                icon = Icons.Filled.Palette,
                eyebrow = "Personalize your workspace",
                title = stringResource(R.string.appearance_settings_make_vervan_feel_at_home),
                body = stringResource(R.string.appearance_settings_choose_the_visual_mood_color)
            )
            ContentCard {
                Column(
                    Modifier.padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.lg)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text(stringResource(R.string.appearance_settings_theme), style = MaterialTheme.typography.bodyMedium)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                VervanFilterChip(
                                    selected = themeMode == mode,
                                    onClick = { vm.setThemeMode(mode) },
                                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Row(
                            Modifier.fillMaxWidth().toggleable(value = dynamicColor, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = { vm.setDynamicColor(it) }).heightIn(min = 48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.appearance_settings_use_device_color_material_you), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = Space.sm))
                            Switch(checked = dynamicColor, onCheckedChange = null)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text(
                            stringResource(R.string.appearance_settings_accent_color),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (dynamicColor) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Space.md)
                        ) {
                            AccentTheme.entries.forEach { accent ->
                                AccentSwatch(
                                    accent = accent,
                                    selected = accentTheme == accent,
                                    onClick = { vm.setAccentTheme(accent) }
                                )
                            }
                        }
                        if (dynamicColor) {
                            Text(
                                stringResource(R.string.appearance_settings_custom_accent_colors_are_ignored),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = oledTrueBlack, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = { vm.setOledTrueBlack(it) }).heightIn(min = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.appearance_settings_oled_true_black_dark_theme), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = Space.sm))
                        Switch(checked = oledTrueBlack, onCheckedChange = null)
                    }
                }
            }
        }
    }
}
