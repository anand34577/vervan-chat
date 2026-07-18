package com.vervan.chat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.theme.vervanBorder
import com.vervan.chat.ui.theme.vervanSubtleDividerColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.settings.AccentTheme
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.swatchColor

private data class SettingsDestination(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
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
    onOpenTools: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val modelCount by app.container.db.modelDao().observeModels().collectAsState(initial = emptyList())
    val memoryCount by app.container.db.memoryDao().observeAll().collectAsState(initial = emptyList())
    val pendingSuggestions by app.container.db.memorySuggestionDao().observePendingCount().collectAsState(initial = 0)
    val activeModel by app.container.db.modelDao().observeActiveModel(ModelRole.GENERATION).collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    val sections = listOf(
        SettingsSection(
            "Experience",
            listOf(
                SettingsDestination(Icons.Filled.Palette, "Appearance", "Theme, accent color, and display options", onOpenAppearance),
                SettingsDestination(Icons.Filled.Tune, "Interaction", "Defaults and device-aware controls", onOpenExperience),
                SettingsDestination(Icons.Filled.AutoAwesome, "AI responses", "Generation, retrieval, context, and sampling", onOpenGeneration),
                SettingsDestination(Icons.Filled.Accessibility, "Accessibility", "Text scale, motion, touch targets, and haptics", onOpenAccessibility),
                SettingsDestination(Icons.Filled.Mic, "Voice", "Read-aloud, playback, and voice models", onOpenVoice)
            )
        ),
        SettingsSection(
            "Local AI & data",
            listOf(
                SettingsDestination(Icons.Filled.AutoAwesome, "Models", "${modelCount.size} installed • ${activeModel?.displayName ?: "none active"}", onOpenModels),
                SettingsDestination(Icons.AutoMirrored.Filled.List, "Model tools", "Choose what the model can call", onOpenTools),
                SettingsDestination(Icons.Filled.Storage, "Storage & data", "Backups, diagnostics, jobs, and indexing", onOpenStorage)
            )
        ),
        SettingsSection(
            "Privacy & personalization",
            listOf(
                SettingsDestination(Icons.Filled.Lock, "Security", "App lock, biometrics, PIN, and auto-lock", onOpenSecurity),
                SettingsDestination(Icons.Outlined.Person, "User profile", "Name, occupation, languages, and preferences", onOpenProfile),
                SettingsDestination(Icons.Filled.Psychology, "Personal memory", "${memoryCount.size} memories saved", onOpenMemory),
                SettingsDestination(Icons.Filled.Lightbulb, "Memory suggestions", "$pendingSuggestions pending review", onOpenMemorySuggestions)
            )
        )
    )
    val visibleSections = sections.mapNotNull { section ->
        val matchesSection = section.title.contains(query, ignoreCase = true)
        val destinations = section.destinations.filter { destination ->
            query.isBlank() || matchesSection || destination.title.contains(query, ignoreCase = true) ||
                destination.subtitle.contains(query, ignoreCase = true)
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
            FeatureHero(
                icon = Icons.Filled.Lock,
                eyebrow = "Local by design",
                title = "Your workspace, your rules",
                body = "Manage local AI, privacy, appearance, and data."
            )
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
                    body = "Try another term or clear the search."
                )
            } else {
                visibleSections.forEach { section ->
                    SectionLabel(section.title)
                    SettingsGroup(section.destinations)
                }
            }
            SectionLabel("About")
            Card(
                Modifier.fillMaxWidth().padding(vertical = Space.xs),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                ListItem(
                    headlineContent = { Text("Vervan Chat") },
                    supportingContent = { Text("Private on-device AI workspace · version 0.1") }
                )
            }
            Card(
                Modifier.fillMaxWidth().padding(top = Space.sm, bottom = Space.xxl),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Text(
                "AI can be wrong. Review important answers and confirm actions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Space.lg)
                )
            }
          }
        }
    }
}

@Composable
private fun SettingsGroup(destinations: List<SettingsDestination>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = vervanBorder()
    ) {
        destinations.forEachIndexed { index, destination ->
            ListItem(
                headlineContent = { Text(destination.title, style = MaterialTheme.typography.titleSmall) },
                supportingContent = {
                    Text(
                        destination.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                },
                leadingContent = {
                    IconAffordance(
                        icon = destination.icon,
                        size = IconAffordanceSize.Default,
                        tint = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = destination.onClick)
            )
            if (index != destinations.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 68.dp),
                    color = vervanSubtleDividerColor()
                )
            }
        }
    }
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
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
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
                modifier = Modifier.padding(start = 8.dp)
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
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun SectionLabel(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Space.lg, bottom = Space.sm)
            .semantics { heading() }
    )
}

@Composable
fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = vervanBorder()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            IconAffordance(
                icon = icon,
                size = IconAffordanceSize.Default,
                tint = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
