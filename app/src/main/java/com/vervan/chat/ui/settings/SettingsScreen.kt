package com.vervan.chat.ui.settings

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.settings.AccentTheme
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.FeatureHero
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
            stringResource(R.string.st_personalize),
            listOf(
                SettingsDestination(
                    Icons.Filled.Palette, stringResource(R.string.st_appearance), stringResource(R.string.st_appearance_sub),
                    listOf("dark", "light", "OLED", "Material You", "display"), onOpenAppearance
                ),
                SettingsDestination(
                    Icons.Filled.Accessibility, stringResource(R.string.st_accessibility), stringResource(R.string.st_accessibility_sub),
                    listOf("TalkBack", "screen reader", "vibration"), onOpenAccessibility
                )
            )
        ),
        SettingsSection(
            stringResource(R.string.st_ai_chat),
            listOf(
                SettingsDestination(
                    Icons.Filled.AutoAwesome, stringResource(R.string.st_ai_models),
                    if (modelCount.isEmpty()) stringResource(R.string.st_ai_models_none)
                    else stringResource(R.string.st_ai_models_sub, modelCount.size, activeModel?.displayName ?: stringResource(R.string.st_none_active)),
                    listOf("download", "import", "load", "active model", "GGUF"), onOpenModels
                ),
                SettingsDestination(
                    Icons.Filled.Tune, stringResource(R.string.st_chat_behavior), stringResource(R.string.st_chat_behavior_sub),
                    listOf("interaction", "expert mode", "automatic", "battery", "thermal"), onOpenExperience
                ),
                SettingsDestination(
                    Icons.Filled.FindInPage, stringResource(R.string.st_responses), stringResource(R.string.st_responses_sub),
                    listOf("generation", "sampling", "temperature", "top p", "semantic", "keyword", "summary"), onOpenGeneration
                ),
                SettingsDestination(
                    Icons.Filled.Mic, stringResource(R.string.st_voice), stringResource(R.string.st_voice_sub),
                    listOf("microphone", "speech to text", "text to speech", "read aloud", "Whisper", "Piper", "Kokoro"), onOpenVoice
                ),
                SettingsDestination(
                    Icons.AutoMirrored.Filled.List, stringResource(R.string.st_tools), stringResource(R.string.st_tools_sub),
                    listOf("model tools", "calculator", "date", "time"), onOpenTools
                )
            )
        ),
        SettingsSection(
            stringResource(R.string.st_memory_section),
            listOf(
                SettingsDestination(
                    Icons.Filled.Psychology, stringResource(R.string.st_memory), stringResource(R.string.st_memory_sub, memoryCount.size),
                    listOf("personal memory", "remember", "facts"), onOpenMemory
                ),
                SettingsDestination(
                    Icons.Filled.Lightbulb, stringResource(R.string.st_memory_suggestions), stringResource(R.string.st_suggestions_sub, pendingSuggestions),
                    listOf("pending", "learned"), onOpenMemorySuggestions
                )
            )
        ),
        SettingsSection(
            stringResource(R.string.st_privacy_data),
            listOf(
                SettingsDestination(
                    Icons.Filled.Lock, stringResource(R.string.st_privacy_security), stringResource(R.string.st_privacy_sub),
                    listOf("biometrics", "PIN", "auto lock", "screenshots", "API server", "panic wipe"), onOpenSecurity
                ),
                SettingsDestination(
                    Icons.Filled.Storage, stringResource(R.string.st_storage), stringResource(R.string.st_storage_sub),
                    listOf("export", "restore", "recycle bin", "jobs", "index", "cache"), onOpenStorage
                )
            )
        ),
        SettingsSection(
            stringResource(R.string.st_help_section),
            listOf(
                SettingsDestination(
                    Icons.AutoMirrored.Filled.Help,
                    stringResource(R.string.st_help),
                    stringResource(R.string.st_help_sub),
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
                title = { Text(stringResource(R.string.home_settings)) },
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
            Card(
                onClick = onOpenProfile,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = com.vervan.chat.ui.theme.SurfaceRole.Raised.cardColors(),
                border = com.vervan.chat.ui.theme.SurfaceRole.Raised.border()
            ) {
                Row(Modifier.fillMaxWidth().padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(com.vervan.chat.ui.theme.vervanBrandFill(), CircleShape),
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
                            text = userName.trim().ifBlank { stringResource(R.string.st_profile_setup) },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            userOccupation.trim().ifBlank { stringResource(R.string.st_profile_hint) },
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
                placeholder = stringResource(R.string.settings_search_settings),
            )
            if (query.isBlank()) {
                SettingsSetupCard(
                    activeModelName = activeModel?.displayName,
                    modelCount = modelCount.size,
                    memoryCount = memoryCount.size,
                    pendingSuggestions = pendingSuggestions,
                    onClick = onOpenModels
                )
            }
            if (visibleSections.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Tune,
                    title = stringResource(R.string.settings_no_settings_found),
                    body = stringResource(R.string.settings_try_a_simpler_term_such)
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
                    SectionLabel(stringResource(R.string.st_about), topPadding = 0.dp, bottomPadding = Space.sm)
                    com.vervan.chat.ui.common.SectionCard(
                        items = listOf(
                        {
                            com.vervan.chat.ui.common.SectionRow(
                                title = stringResource(R.string.app_name),
                                icon = Icons.Filled.AutoAwesome,
                                subtitle = stringResource(R.string.settings_private_ai_workspace_with_local, versionLabel)
                            )
                        },
                        {
                            com.vervan.chat.ui.common.SectionRow(
                                title = stringResource(R.string.settings_source_code_on_github),
                                subtitle = stringResource(R.string.settings_github_com_anand34577_vervan_chat),
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
                                stringResource(R.string.settings_chats_and_documents_are_stored),
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
private fun SettingsSetupCard(
    activeModelName: String?,
    modelCount: Int,
    memoryCount: Int,
    pendingSuggestions: Int,
    onClick: () -> Unit
) {
    // The whole summary opens the models screen: it is the one place a "no model" state is fixed.
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = com.vervan.chat.ui.theme.SurfaceRole.Card.cardColors(),
        border = com.vervan.chat.ui.theme.SurfaceRole.Card.border()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                Modifier.weight(1f).padding(start = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                Text(stringResource(R.string.settings_your_setup), style = MaterialTheme.typography.titleSmall)
                Text(
                    activeModelName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.settings_no_generation_model_selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$modelCount model${if (modelCount == 1) "" else "s"} · " +
                        "$memoryCount memor${if (memoryCount == 1) "y" else "ies"} saved" +
                        if (pendingSuggestions > 0) " · $pendingSuggestions to review" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pendingSuggestions > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(destinations: List<SettingsDestination>) {
    SectionCard(
        items = destinations.map { destination ->
            {
                // Stable categorical accent per destination (hashed from the title, so it
                // survives search filtering; distinct hues make the hub scannable at a glance.
                val accent = com.vervan.chat.ui.theme.vervanAccentFor(destination.title.hashCode())
                SectionRow(
                    title = destination.title,
                    subtitle = destination.subtitle,
                    icon = destination.icon,
                    iconSize = IconAffordanceSize.Default,
                    iconTint = accent.onContainer,
                    iconContainerColor = accent.container,
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
            Slider(
                value = value, onValueChange = onChange, valueRange = range, steps = steps,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "$label, ${String.format(format, value)}"
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
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

@Composable
fun AccentSwatch(accent: AccentTheme, selected: Boolean, onClick: () -> Unit) {
    val label = accent.name.lowercase().replaceFirstChar { it.uppercase() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = Space.xs)
    ) {
        val swatchColor = accent.swatchColor()
        Box(
            Modifier.size(36.dp)
                .background(swatchColor, CircleShape)
                .then(
                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
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
