package com.vervan.chat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.SectionLabel
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.R
import com.vervan.chat.data.settings.AccentTheme
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.ModernistMetricStrip
import com.vervan.chat.ui.common.ModernistScreenHeader
import com.vervan.chat.ui.common.ModernistTag
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.OverflowTooltipText
import com.vervan.chat.ui.common.SectionCard
import com.vervan.chat.ui.common.SectionRow
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.swatchColor

private const val GITHUB_REPOSITORY_URL = "https://github.com/anand34577/vervan-chat"

private data class SettingsDestination(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val searchTerms: List<String> = emptyList(),
    val onClick: () -> Unit
)

private data class SettingsSection(
    val title: String,
    val destinations: List<SettingsDestination>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenModels: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenMemorySuggestions: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenExperience: () -> Unit = {},
    onOpenAccessibility: () -> Unit = {},
    onOpenGeneration: () -> Unit = {},
    onOpenVoice: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenTools: () -> Unit = {},
    onOpenHelp: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SettingsOverviewViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsOverviewViewModel(app) } }
    )
    val modelCount by vm.models.collectAsStateWithLifecycle()
    val memoryCount by vm.memories.collectAsStateWithLifecycle()
    val pendingSuggestions by vm.pendingSuggestions.collectAsStateWithLifecycle()
    val activeModel by vm.activeModel.collectAsStateWithLifecycle()
    val userName by vm.userName.collectAsStateWithLifecycle()
    val userOccupation by vm.userOccupation.collectAsStateWithLifecycle()
    // Live build label from PackageInfo — the footer used to hardcode "version 0.1", which drifted
    // from the actual release on every bump. Read once; it can't change during the session.
    val versionLabel = remember {
        runCatching {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            "version ${info.versionName}"
        }.getOrDefault("version 0.1")
    }
    var query by rememberSaveable { mutableStateOf("") }
    val sections = listOf(
        SettingsSection(
            stringResource(R.string.settings_section_personalize),
            listOf(
                SettingsDestination(
                    Icons.Filled.Palette, stringResource(R.string.settings_appearance), stringResource(R.string.settings_appearance_summary),
                    listOf("dark", "light", "OLED", "Material You", "display"), onOpenAppearance
                ),
                SettingsDestination(
                    Icons.Filled.Accessibility, stringResource(R.string.settings_accessibility), stringResource(R.string.settings_accessibility_summary),
                    listOf("TalkBack", "screen reader", "vibration"), onOpenAccessibility
                )
            )
        ),
        SettingsSection(
            stringResource(R.string.settings_section_ai_chat),
            listOf(
                SettingsDestination(
                    Icons.Filled.AutoAwesome, stringResource(R.string.settings_ai_models),
                    stringResource(R.string.settings_ai_models_summary, modelCount.size, activeModel?.displayName ?: stringResource(R.string.settings_none_active)),
                    listOf("download", "import", "load", "active model", "GGUF"), onOpenModels
                ),
                SettingsDestination(
                    Icons.Filled.Tune, stringResource(R.string.settings_chat_behavior), stringResource(R.string.settings_chat_behavior_summary),
                    listOf("interaction", "expert mode", "automatic", "battery", "thermal"), onOpenExperience
                ),
                SettingsDestination(
                    Icons.Filled.AutoAwesome, stringResource(R.string.settings_responses_search), stringResource(R.string.settings_responses_search_summary),
                    listOf("generation", "sampling", "temperature", "top p", "semantic", "keyword", "summary"), onOpenGeneration
                ),
                SettingsDestination(
                    Icons.Filled.Mic, stringResource(R.string.settings_voice_speech), stringResource(R.string.settings_voice_speech_summary),
                    listOf("microphone", "speech to text", "text to speech", "read aloud", "Whisper", "Piper", "Kokoro"), onOpenVoice
                ),
                SettingsDestination(
                    Icons.AutoMirrored.Filled.List, stringResource(R.string.nav_tools), stringResource(R.string.settings_tools_summary),
                    listOf("model tools", "calculator", "date", "time"), onOpenTools
                )
            )
        ),
        SettingsSection(
            stringResource(R.string.settings_section_memory),
            listOf(
                SettingsDestination(
                    Icons.Filled.Psychology, stringResource(R.string.entity_memory), stringResource(R.string.settings_memory_summary, memoryCount.size),
                    listOf("personal memory", "remember", "facts"), onOpenMemory
                ),
                SettingsDestination(
                    Icons.Filled.Lightbulb, stringResource(R.string.settings_memory_suggestions), stringResource(R.string.settings_memory_suggestions_summary, pendingSuggestions),
                    listOf("pending", "learned"), onOpenMemorySuggestions
                )
            )
        ),
        SettingsSection(
            stringResource(R.string.settings_section_privacy_data),
            listOf(
                SettingsDestination(
                    Icons.Filled.Lock, stringResource(R.string.security_title), stringResource(R.string.settings_privacy_summary),
                    listOf("biometrics", "PIN", "auto lock", "screenshots", "API server", "panic wipe"), onOpenSecurity
                ),
                SettingsDestination(
                    Icons.Filled.Storage, stringResource(R.string.settings_storage_backup), stringResource(R.string.settings_storage_summary),
                    listOf("export", "restore", "recycle bin", "jobs", "index", "cache"), onOpenStorage
                )
            )
        ),
        SettingsSection(
            stringResource(R.string.settings_section_help),
            listOf(
                SettingsDestination(
                    Icons.AutoMirrored.Filled.Help,
                    stringResource(R.string.settings_help_troubleshooting),
                    stringResource(R.string.settings_help_summary),
                    listOf("problem", "error", "failed", "stuck", "support", "guide", "how to"),
                    onOpenHelp
                )
            )
        )
    )
    val visibleSections = sections.mapNotNull { section ->
        val matchesSection = section.title.contains(query, ignoreCase = true)
        val destinations = section.destinations.filter { destination ->
            query.isBlank() || matchesSection || destination.title.contains(query, ignoreCase = true) ||
                destination.subtitle.contains(query, ignoreCase = true) ||
                destination.searchTerms.any { it.contains(query, ignoreCase = true) }
        }
        section.copy(destinations = destinations).takeIf { destinations.isNotEmpty() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
          Column(
              Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.lg),
              verticalArrangement = Arrangement.spacedBy(Space.lg)
          ) {
            // Profile header — the modern settings anchor: who this workspace belongs to, with a
            // one-tap path to the full profile. Replaces the generic hero card, which repeated
            // the privacy message the About footer already carries.
            ModernistScreenHeader(
                eyebrow = stringResource(R.string.settings_preferences_eyebrow),
                title = stringResource(R.string.settings_workspace_title),
                body = stringResource(R.string.settings_workspace_body),
                trailing = { ModernistTag(stringResource(R.string.settings_local_tag), active = true) }
            )
            ModernistMetricStrip(
                metrics = listOf(
                    stringResource(R.string.settings_metric_model) to (activeModel?.displayName ?: stringResource(R.string.settings_none_active).uppercase()),
                    stringResource(R.string.settings_metric_installed) to modelCount.size.toString(),
                    stringResource(R.string.settings_metric_memories) to memoryCount.size.toString(),
                    stringResource(R.string.settings_metric_review) to pendingSuggestions.toString()
                )
            )
            Surface(
                onClick = onOpenProfile,
                modifier = Modifier.fillMaxWidth(),
                shape = com.vervan.chat.ui.theme.VervanExtraShapes.hero,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Row(Modifier.fillMaxWidth().padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(com.vervan.chat.ui.theme.vervanBrandGradient(), MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = userName.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
                        if (initial != null) {
                            Text(initial.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = Space.lg)) {
                        OverflowTooltipText(
                            text = userName.trim().ifBlank { stringResource(R.string.settings_profile_setup) },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            userOccupation.trim().ifBlank { stringResource(R.string.settings_profile_add_details) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            VervanSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.settings_search_placeholder),
            )
            if (visibleSections.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Tune,
                    title = stringResource(R.string.settings_no_results),
                    body = stringResource(R.string.settings_no_results_body),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    centered = true
                )
            } else {
                visibleSections.forEach { section ->
                    Column(Modifier.fillMaxWidth()) {
                        SectionLabel(section.title, topPadding = 0.dp, bottomPadding = Space.sm)
                        SettingsGroup(section.destinations)
                    }
                }
            }
            if (query.isBlank()) {
                Column(Modifier.fillMaxWidth()) {
                    SectionLabel(stringResource(R.string.settings_about), topPadding = 0.dp, bottomPadding = Space.sm)
                    com.vervan.chat.ui.common.SectionCard(
                        items = listOf(
                        {
                            com.vervan.chat.ui.common.SectionRow(
                                title = stringResource(R.string.app_name),
                                icon = Icons.Filled.AutoAwesome,
                                subtitle = stringResource(R.string.ui_settingsscreen_about_subtitle, versionLabel)
                            )
                        },
                        {
                            com.vervan.chat.ui.common.SectionRow(
                                title = stringResource(R.string.settings_source_code),
                                subtitle = stringResource(R.string.ui_settingsscreen_340_github_com_anand34577_vervan_chat),
                                icon = Icons.Filled.Code,
                                onClick = {
                                    // applicationContext startActivity needs NEW_TASK for an outbound
                                    // view intent; runCatching swallows the rare no-handler case
                                    // (a device with no browser) instead of crashing Settings.
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(GITHUB_REPOSITORY_URL)
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { app.startActivity(intent) }
                                },
                                trailing = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        },
                        {
                            Text(
                                stringResource(R.string.settings_about_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(Space.lg)
                            )
                        }
                        )
                    )
                }
            }
          }
        }
    }
}

@Composable
private fun SettingsGroup(destinations: List<SettingsDestination>) {
    SectionCard(
        items = destinations.map { destination ->
            {
                SectionRow(
                    title = destination.title,
                    subtitle = destination.subtitle,
                    icon = destination.icon,
                    iconSize = IconAffordanceSize.Default,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    onClick = destination.onClick,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    )
}

@Composable
fun GenerationSlider(
    label: String,
    value: Float,
    format: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = Space.sm)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val sliderDescription = stringResource(R.string.ui_modeledit_slider_value, label, String.format(format, value))
            Slider(
                value = value, onValueChange = onChange, valueRange = range, steps = steps,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = sliderDescription
                }
            )
            Text(
                String.format(format, value), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = Space.sm)
            )
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
fun AccentSwatch(
    accent: AccentTheme,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val label = accent.name.lowercase().replaceFirstChar { it.uppercase() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = Space.xs)
    ) {
        val swatchColor = accent.swatchColor()
        Box(
            Modifier.size(36.dp)
                .clip(MaterialTheme.shapes.small)
                .background(swatchColor, MaterialTheme.shapes.small)
                .then(
                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            // The swatch's own brightness decides the checkmark color, not a theme token — these
            // are fixed accent colors independent of light/dark mode, so onSurface/onBackground
            // would drift out of contrast against them depending on which theme is active.
            if (selected) {
                val checkTint = if (swatchColor.luminance() > 0.5f) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
                Icon(Icons.Filled.Check, contentDescription = null, tint = checkTint, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = Space.xs)
        )
    }
}

@Composable
fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    SectionCard(
        modifier = Modifier.padding(vertical = Space.sm),
        items = listOf(
            {
                SectionRow(
                    title = title,
                    subtitle = subtitle,
                    icon = icon,
                    iconSize = IconAffordanceSize.Default,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
                    onClick = onClick,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        )
    )
}
