package com.vervan.chat.ui.settings

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.ScrollablePage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.ContentCard
import com.vervan.chat.data.settings.AccentTheme
import com.vervan.chat.data.settings.ThemeMode
import com.vervan.chat.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })

    val themeMode by vm.themeMode.collectAsState()
    val accentTheme by vm.accentTheme.collectAsState()
    val fontScale by vm.fontScale.collectAsState()
    val oledTrueBlack by vm.oledTrueBlack.collectAsState()
    val hapticsEnabled by vm.hapticsEnabled.collectAsState()
    val dynamicColor by vm.dynamicColor.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            ContentCard {
                Column(Modifier.padding(Space.lg)) {
                    Text("Theme", style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            VervanFilterChip(
                                selected = themeMode == mode,
                                onClick = { vm.setThemeMode(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Use device color (Material You)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
                            Switch(checked = dynamicColor, onCheckedChange = { vm.setDynamicColor(it) })
                        }
                    }
                    Text(
                        "Accent color",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dynamicColor) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            "Custom accent colors are ignored while device color is on.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("OLED true black (dark theme)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Switch(checked = oledTrueBlack, onCheckedChange = { vm.setOledTrueBlack(it) })
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Haptic feedback", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Switch(checked = hapticsEnabled, onCheckedChange = { vm.setHapticsEnabled(it) })
                    }
                    Text("Font size", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = fontScale, onValueChange = { vm.setFontScale(it) },
                            valueRange = 0.85f..1.5f,
                            modifier = Modifier.weight(1f).semantics {
                                contentDescription = "Font size, ${(fontScale * 100).toInt()} percent"
                            }
                        )
                        Text("${(fontScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
