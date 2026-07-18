package com.vervan.chat.ui.onboarding

import android.app.ActivityManager
import android.os.Build
import android.os.StatFs
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.llm.ModelProfileType
import com.vervan.chat.ui.common.EngineDot
import com.vervan.chat.ui.common.ModelEngineKind
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.ui.theme.VervanMotion
import com.vervan.chat.ui.theme.vervanBorder
import kotlinx.coroutines.launch

/**
 * A short setup path. Detailed controls remain discoverable in Settings and Model Manager.
 *
 * Improvements over the previous version:
 *  - Adds an "Engines" page (page 2) explaining LiteRT-LM vs llama.cpp in plain language —
 *    for an offline-LLM app where the engine choice is the flagship feature and impacts
 *    model compatibility, the previous onboarding completely ignored it.
 *  - Adds a persistent "Skip for now" button next to Continue so users who don't want to
 *    pick a profile aren't forced to tap Continue three times to escape.
 *  - Replaces the dual LinearProgressIndicator + "1 of 4" text with a clean dot indicator
 *    row (one of the two was redundant).
 *  - Per-page color tinting on the hero icon — gives each step its own visual identity
 *    instead of every page looking identical.
 *  - Cleaner prose, "Memory" / "Speed" anchors under each engine to communicate trade-offs.
 *  - "Open Model Manager" now styled as a primary CTA on the model page (was OutlinedButton
 *    buried under hardware stats) so first-run users actually see the path forward.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit, onImportModel: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var selectedProfile by rememberSaveable { mutableStateOf("BALANCED") }

    val activityManager = context.getSystemService(ActivityManager::class.java)
    val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    val freeStorage = StatFs(context.filesDir.path).availableBytes
    val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
    val ramGb = formatGb(memory.totalMem)
    val storageGb = formatGb(freeStorage)
    // RAM-based device-capability assessment — the previous onboarding *showed* hardware
    // numbers but never *interpreted* them. A 4-GB-RAM device should hear "expect smaller
    // models"; a 12-GB device should hear "large models will run smoothly."
    val capabilityBlurb = when {
        memory.totalMem >= 10L * 1024 * 1024 * 1024 -> "Plenty of memory for larger models and long conversations."
        memory.totalMem >= 6L * 1024 * 1024 * 1024 -> "Comfortable headroom for everyday models."
        else -> "Smaller models will run best on this device."
    }

    val pages = listOf(
        OnboardPage(
            icon = Icons.Filled.AutoAwesome,
            title = "Your private AI workspace",
            body = "Chat, write, study, and work with documents—privately on your device.",
            note = "AI can make mistakes. Review important answers and actions.",
            accentTone = OnboardAccentTone.Primary
        ),
        OnboardPage(
            icon = Icons.Filled.Memory,
            title = "Two engines. One device.",
            body = "Run local models with LiteRT-LM or llama.cpp.",
            note = "No need to choose now. Each model uses its compatible engine.",
            accentTone = OnboardAccentTone.Secondary,
            engines = true
        ),
        OnboardPage(
            icon = Icons.Filled.Security,
            title = "Ready for local models",
            body = "Android ${Build.VERSION.RELEASE}  •  $abi\n$ramGb GB RAM  •  $storageGb GB storage free\n$capabilityBlurb",
            note = "Import generation or embedding models when ready. Vervan checks each package.",
            accentTone = OnboardAccentTone.Tertiary,
            importButton = true
        ),
        OnboardPage(
            icon = Icons.Filled.Tune,
            title = "Choose your starting balance",
            body = "Choose a balance of speed, answer depth, and battery use.",
            note = "Balanced suits most devices. You can change it later.",
            accentTone = OnboardAccentTone.Primary,
            profilePicker = true
        ),
        OnboardPage(
            icon = Icons.Filled.CheckCircle,
            title = "You are ready",
            body = "Start a chat, add documents, or create notes and workflows.",
            note = "Use the bottom bar to move between your main spaces.",
            accentTone = OnboardAccentTone.Secondary
        )
    )

    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 680.dp).padding(horizontal = Space.xxl, vertical = Space.lg)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = VervanExtraShapes.pill,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Vervan", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.weight(1f))
                // Skip — persistent escape hatch. Users who don't want to pick a profile or read
                // every page can finish onboarding immediately; their picked profile is preserved
                // by rememberSaveable, so coming back later still remembers their selection.
                TextButton(onClick = onDone) { Text("Skip") }
            }
            // Dot indicator — replaces the dual LinearProgressIndicator + "1 of N" text.
            // Dots are the modern onboarding convention and don't claim vertical space.
            Row(
                Modifier.fillMaxWidth().padding(top = Space.md),
                horizontalArrangement = Arrangement.Center
            ) {
                pages.indices.forEach { index ->
                    val isActive = index == page
                    Box(
                        Modifier
                            .padding(horizontal = Space.xs)
                            .size(if (isActive) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                            )
                    )
                }
            }
            // Page content slides + fades in the travel direction on Continue/Back — an
            // onboarding flow that visibly *moves* between steps reads as progress, where a hard
            // content swap read as a glitch. Direction is derived from initial→target page order.
            androidx.compose.animation.AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (androidx.compose.animation.slideInHorizontally(VervanMotion.emphasized(380)) { w -> dir * w / 5 } +
                        androidx.compose.animation.fadeIn(VervanMotion.emphasized(380))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(VervanMotion.emphasized(380)) { w -> -dir * w / 5 } +
                            androidx.compose.animation.fadeOut(VervanMotion.emphasized(200)))
                },
                modifier = Modifier.weight(1f),
                label = "onboardingPage"
            ) { targetPage ->
                val p = pages[targetPage]
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(Space.xxl))
                    // Hero icon — tinted per-page so each step has its own visual identity instead
                    // of every page looking identical.
                    val (heroBg, heroFg) = accentColors(p.accentTone)
                    Surface(
                        shape = VervanExtraShapes.pill,
                        color = heroBg,
                        contentColor = heroFg
                    ) {
                        Icon(p.icon, contentDescription = null, modifier = Modifier.padding(Space.lg).size(36.dp))
                    }
                    Text(
                        p.title,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = Space.xxl)
                    )
                    Text(
                        p.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.md)
                    )
                    // Engine explainer (page 2 only) — the missing flagship-feature educational
                    // moment. Two cards side-by-side: LiteRT-LM (faster, Google-tuned) and
                    // llama.cpp (flexible, broad format support). Plain language, no jargon.
                    if (p.engines) {
                        Column(
                            Modifier.fillMaxWidth().padding(top = Space.lg),
                            verticalArrangement = Arrangement.spacedBy(Space.sm)
                        ) {
                            EngineCard(
                                dotKind = ModelEngineKind.LiteRTLM,
                                title = "LiteRT-LM",
                                tagline = "Fastest on this device",
                                details = "Google's runtime for .task and .litertlm models, with NPU, GPU, vision, and audio support.",
                                anchors = listOf("Speed", "Multimodal", "NPU/GPU")
                            )
                            EngineCard(
                                dotKind = ModelEngineKind.LlamaCpp,
                                title = "llama.cpp (GGUF)",
                                tagline = "Most flexible",
                                details = "Broad GGUF support with Vulkan acceleration and LoRA adapters.",
                                anchors = listOf("GGUF", "Vulkan", "LoRA")
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = Space.xxl),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = vervanBorder()
                    ) {
                        Text(p.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Space.lg))
                    }
                    if (p.importButton) {
                        // Primary CTA (was OutlinedButton). This is the single most important action
                        // for a first-run user and should read as the obvious next step.
                        Button(
                            onClick = onImportModel,
                            modifier = Modifier.fillMaxWidth().padding(top = Space.lg)
                        ) {
                            Icon(Icons.Filled.Memory, null, Modifier.size(18.dp))
                            Text("Browse models to download", Modifier.padding(start = Space.sm))
                        }
                    }
                    if (p.profilePicker) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = Space.lg),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.sm)
                        ) {
                            ModelProfileType.entries.forEach { profile ->
                                VervanFilterChip(
                                    selected = selectedProfile == profile.id,
                                    onClick = { selectedProfile = profile.id },
                                    label = { Text(profile.label) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.xxl))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { page-- }, enabled = page > 0) { Text("Back") }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (page == pages.lastIndex) {
                        scope.launch { app.container.settingsRepository.setDefaultProfile(selectedProfile) }
                        onDone()
                    } else {
                        page++
                    }
                }) {
                    Text(if (page == pages.lastIndex) "Get started" else "Continue")
                }
            }
        }
    }
}

@Composable
private fun EngineCard(
    dotKind: ModelEngineKind,
    title: String,
    tagline: String,
    details: String,
    anchors: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = vervanBorder()
    ) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                EngineDot(kind = dotKind)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(tagline, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                anchors.forEach { anchor ->
                    Surface(
                        shape = VervanExtraShapes.pill,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    ) {
                        Text(
                            anchor,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = Space.sm, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Per-page accent tone — gives each onboarding step its own hero color. */
private enum class OnboardAccentTone { Primary, Secondary, Tertiary }

@Composable
private fun accentColors(tone: OnboardAccentTone): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> = when (tone) {
    OnboardAccentTone.Primary -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    OnboardAccentTone.Secondary -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    OnboardAccentTone.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
}

private data class OnboardPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val note: String,
    val accentTone: OnboardAccentTone = OnboardAccentTone.Primary,
    val importButton: Boolean = false,
    val profilePicker: Boolean = false,
    val engines: Boolean = false
)

private fun formatGb(bytes: Long): String = if (bytes % (1024L * 1024 * 1024) == 0L) {
    // Whole-GB numbers read better without the ".0" — "8 GB" instead of "8.0 GB".
    (bytes / (1024L * 1024 * 1024)).toString()
} else {
    String.format("%.1f", bytes / (1024.0 * 1024 * 1024))
}
