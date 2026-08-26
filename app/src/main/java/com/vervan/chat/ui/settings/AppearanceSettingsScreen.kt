package com.vervan.chat.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.vervan.chat.ui.common.VervanToggle as Switch
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.ScrollablePage
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

    val themeMode by vm.themeMode.collectAsState()
    val accentTheme by vm.accentTheme.collectAsState()
    val oledTrueBlack by vm.oledTrueBlack.collectAsState()
    val dynamicColor by vm.dynamicColor.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.vervan.chat.R.string.appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.vervan.chat.R.string.action_back)) }
                }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            FeatureHero(
                icon = Icons.Filled.Palette,
                eyebrow = stringResource(com.vervan.chat.R.string.appearance_eyebrow),
                title = stringResource(com.vervan.chat.R.string.appearance_hero_title),
                body = stringResource(com.vervan.chat.R.string.appearance_hero_body)
            )
            ContentCard {
                Column(
                    Modifier.padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.lg)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text(stringResource(com.vervan.chat.R.string.appearance_theme), style = MaterialTheme.typography.bodyMedium)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                VervanFilterChip(
                                    selected = themeMode == mode,
                                    onClick = { vm.setThemeMode(mode) },
                                    label = {
                                        Text(stringResource(when (mode) {
                                            ThemeMode.SYSTEM -> com.vervan.chat.R.string.appearance_theme_system
                                            ThemeMode.LIGHT -> com.vervan.chat.R.string.appearance_theme_light
                                            ThemeMode.DARK -> com.vervan.chat.R.string.appearance_theme_dark
                                        }))
                                    }
                                )
                            }
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(com.vervan.chat.R.string.appearance_device_color), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = Space.sm))
                            Switch(checked = dynamicColor, onCheckedChange = { vm.setDynamicColor(it) })
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text(
                            stringResource(com.vervan.chat.R.string.appearance_accent_color),
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
                                    enabled = !dynamicColor,
                                    onClick = { vm.setAccentTheme(accent) }
                                )
                            }
                        }
                        if (dynamicColor) {
                            Text(
                                stringResource(com.vervan.chat.R.string.appearance_dynamic_accent_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(com.vervan.chat.R.string.appearance_oled_black),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (themeMode == ThemeMode.LIGHT) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).padding(end = Space.sm),
                        )
                        Switch(
                            checked = oledTrueBlack,
                            onCheckedChange = { vm.setOledTrueBlack(it) },
                            enabled = themeMode != ThemeMode.LIGHT,
                        )
                    }
                }
            }
            LanguageCard()
        }
    }
}

/** Hands off to the OS's own per-app language picker (Settings -> App info -> Language) rather
 * than reimplementing a language switcher in-app — the OS one already persists the choice per-app,
 * restarts activities with the new locale, and lists exactly what locales_config.xml declares
 * (see AndroidManifest's android:localeConfig). Only available on Android 13+ (LocaleManager);
 * older Android has no per-app language API, so this card is replaced with a note there — the app
 * still follows the system language as it always has. */
@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    ContentCard {
        Row(
            Modifier
                .fillMaxWidth()
                .let { m ->
                    if (android.os.Build.VERSION.SDK_INT >= 33) m.clickable {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS)
                                .setData(android.net.Uri.fromParts("package", context.packageName, null))
                        )
                    } else m
                }
                .padding(Space.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text(stringResource(com.vervan.chat.R.string.appearance_language), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        stringResource(com.vervan.chat.R.string.appearance_language_android)
                    else
                        stringResource(com.vervan.chat.R.string.appearance_language_older_android),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
