package com.vervan.chat.ui.settings

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
import com.vervan.chat.data.db.entities.ModelRole
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
    val modelCount by app.container.db.modelDao().observeModels().collectAsStateWithLifecycle(initialValue = emptyList())
    val memoryCount by app.container.db.memoryDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingSuggestions by app.container.db.memorySuggestionDao().observePendingCount().collectAsStateWithLifecycle(initialValue = 0)
    val activeModel by app.container.db.modelDao().observeActiveModel(ModelRole.GENERATION).collectAsStateWithLifecycle(initialValue = null)
    val userName by app.container.settingsRepository.userName.collectAsStateWithLifecycle(initialValue = "")
    val userOccupation by app.container.settingsRepository.userOccupation.collectAsStateWithLifecycle(initialValue = "")
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
            "Help",
            listOf(
                SettingsDestination(
                    Icons.AutoMirrored.Filled.Help,
                    "Help & troubleshooting",
                    "How-to guides, common fixes, and app diagnostics",
                    listOf("problem", "error", "failed", "stuck", "support", "guide", "how to"),
                    onOpenHelp
                )
            )
        ),
        SettingsSection(
            "Look & feel",
            listOf(
                SettingsDestination(
                    Icons.Filled.Palette, "Appearance", "Theme, colors, and dark mode",
                    listOf("dark", "light", "OLED", "Material You", "display"), onOpenAppearance
                ),
                SettingsDestination(
                    Icons.Filled.Accessibility, "Accessibility", "Text, contrast, motion, and touch size",
                    listOf("TalkBack", "screen reader", "vibration"), onOpenAccessibility
                ),
                SettingsDestination(
                    Icons.Filled.Mic, "Voice & speech", "Dictation, spoken replies, and voice models",
                    listOf("microphone", "speech to text", "text to speech", "read aloud", "Whisper", "Piper", "Kokoro"), onOpenVoice
                )
            )
        ),
        SettingsSection(
            "AI & chat",
            listOf(
                SettingsDestination(
                    Icons.Filled.AutoAwesome, "AI models",
                    "${modelCount.size} installed · ${activeModel?.displayName ?: "none active"}",
                    listOf("download", "import", "load", "active model", "GGUF"), onOpenModels
                ),
                SettingsDestination(
                    Icons.Filled.Tune, "Chat behavior", "Model choice, presets, and device performance",
                    listOf("interaction", "expert mode", "automatic", "battery", "thermal"), onOpenExperience
                ),
                SettingsDestination(
                    Icons.Filled.AutoAwesome, "Responses & search", "Reply style, context, and document search",
                    listOf("generation", "sampling", "temperature", "top p", "semantic", "keyword", "summary"), onOpenGeneration
                ),
                SettingsDestination(
                    Icons.AutoMirrored.Filled.List, "Tools", "Choose what AI can use in chats",
                    listOf("model tools", "calculator", "date", "time"), onOpenTools
                )
            )
        ),
        SettingsSection(
            "Personalization",
            listOf(
                SettingsDestination(
                    Icons.Filled.Psychology, "Memory", "${memoryCount.size} saved",
                    listOf("personal memory", "remember", "facts"), onOpenMemory
                ),
                SettingsDestination(
                    Icons.Filled.Lightbulb, "Memory suggestions", "$pendingSuggestions to review",
                    listOf("pending", "learned"), onOpenMemorySuggestions
                )
            )
        ),
        SettingsSection(
            "Privacy & data",
            listOf(
                SettingsDestination(
                    Icons.Filled.Lock, "Privacy & security", "App lock, permissions, and local access",
                    listOf("biometrics", "PIN", "auto lock", "screenshots", "API server", "panic wipe"), onOpenSecurity
                ),
                SettingsDestination(
                    Icons.Filled.Storage, "Storage & backup", "Space, backups, deleted items, and diagnostics",
                    listOf("export", "restore", "recycle bin", "jobs", "index", "cache"), onOpenStorage
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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
          Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.sm)) {
            // Profile header — the modern settings anchor: who this workspace belongs to, with a
            // one-tap path to the full profile. Replaces the generic hero card, which repeated
            // the privacy message the About footer already carries.
            Card(
                onClick = onOpenProfile,
                modifier = Modifier.fillMaxWidth(),
                shape = com.vervan.chat.ui.theme.VervanExtraShapes.hero,
                colors = com.vervan.chat.ui.theme.SurfaceRole.Raised.cardColors(),
                border = com.vervan.chat.ui.theme.SurfaceRole.Raised.border()
            ) {
                Row(Modifier.fillMaxWidth().padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(com.vervan.chat.ui.theme.vervanBrandGradient(), CircleShape),
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
                            text = userName.trim().ifBlank { "Set up your profile" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            userOccupation.trim().ifBlank { "Add details for more relevant replies" },
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
                placeholder = "Search settings",
                modifier = Modifier.padding(top = Space.lg)
            )
            if (visibleSections.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Tune,
                    title = "No settings found",
                    body = "Try a simpler term, such as “voice,” “backup,” or “model.”"
                )
            } else {
                visibleSections.forEach { section ->
                    SectionLabel(section.title)
                    SettingsGroup(section.destinations)
                }
            }
            SectionLabel("About")
            com.vervan.chat.ui.common.SectionCard(
                modifier = Modifier.padding(bottom = Space.xxl),
                items = listOf(
                    {
                        com.vervan.chat.ui.common.SectionRow(
                            title = "Vervan Chat",
                            icon = Icons.Filled.AutoAwesome,
                            subtitle = "Private on-device AI workspace · $versionLabel"
                        )
                    },
                    {
                        com.vervan.chat.ui.common.SectionRow(
                            title = "Source code on GitHub",
                            subtitle = "github.com/anand34577/vervan-chat",
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
                            "Chats and documents stay on this device. Check important answers before acting on them.",
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
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
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
        modifier = Modifier.padding(vertical = Space.xs),
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
