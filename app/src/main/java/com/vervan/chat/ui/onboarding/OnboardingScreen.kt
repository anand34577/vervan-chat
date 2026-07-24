package com.vervan.chat.ui.onboarding

import android.app.ActivityManager
import android.os.Build
import android.os.StatFs
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.snap
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.llm.ModelProfileType
import com.vervan.chat.modeldownload.CatalogModel
import com.vervan.chat.modeldownload.ModelCatalog
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.rememberReducedMotion
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.ui.theme.VervanMotion
import com.vervan.chat.ui.theme.vervanBorder
import kotlinx.coroutines.launch

/**
 * A short setup path. Detailed controls remain discoverable in Settings and Model Manager.
 *
 * Three decisions only: understand the privacy promise, prepare a suitable local model, and
 * choose a starting response profile. Runtime and file-format detail belongs in Model Manager.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit, onImportModel: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var selectedProfile by rememberSaveable { mutableStateOf("BALANCED") }
    val reducedMotion = rememberReducedMotion()

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
    // Answer the hardest new-user question ("which model fits my phone?") instead of leaving them
    // to guess in Model Manager. Pure, size/RAM-driven, so it auto-considers any generation model
    // added to the catalog later.
    val recommendation = recommendModel(memory.totalMem)

    val pages = listOf(
        OnboardPage(
            icon = Icons.Filled.AutoAwesome,
            title = "Your private AI workspace",
            body = "Chat, write, study, and work with documents—privately on your device.",
            note = "AI can make mistakes. Review important answers and actions.",
            accentTone = OnboardAccentTone.Primary
        ),
        OnboardPage(
            icon = Icons.Filled.Security,
            title = "Choose a model for this device",
            body = "Android ${Build.VERSION.RELEASE}  •  $abi\n$ramGb GB RAM  •  $storageGb GB storage free\n$capabilityBlurb",
            note = "Vervan checks every package. Advanced runtime and import options stay in Model Manager.",
            accentTone = OnboardAccentTone.Tertiary,
            importButton = true
        ),
        OnboardPage(
            icon = Icons.Filled.Tune,
            title = "Choose your starting balance",
            body = "Choose a balance of speed, answer depth, and battery use, then start your first conversation.",
            note = "Balanced suits most devices. You can change this later in Settings.",
            accentTone = OnboardAccentTone.Primary,
            profilePicker = true
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
                // Skip — persistent escape hatch. Persists whatever profile is selected (default
                // or hand-picked) the same as "Get started" does — Skip previously discarded a
                // profile the user had already tapped.
                TextButton(onClick = {
                    scope.launch { app.container.settingsRepository.setDefaultProfile(selectedProfile) }
                    onDone()
                }) { Text("Skip") }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = Space.md).semantics {
                    contentDescription = "Step ${page + 1} of ${pages.size}"
                },
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
            androidx.compose.animation.AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (reducedMotion) {
                        androidx.compose.animation.fadeIn(snap()) togetherWith
                            androidx.compose.animation.fadeOut(snap())
                    } else {
                        val dir = if (targetState > initialState) 1 else -1
                        (androidx.compose.animation.slideInHorizontally(VervanMotion.emphasized(380)) { w -> dir * w / 5 } +
                            androidx.compose.animation.fadeIn(VervanMotion.emphasized(380))) togetherWith
                            (androidx.compose.animation.slideOutHorizontally(VervanMotion.emphasized(380)) { w -> -dir * w / 5 } +
                                androidx.compose.animation.fadeOut(VervanMotion.emphasized(200)))
                    }
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
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = Space.xxl),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = vervanBorder()
                    ) {
                        Text(p.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Space.lg))
                    }
                    if (p.importButton) {
                        recommendation?.let { OnboardRecommendationCard(it) }
                        Button(
                            onClick = onImportModel,
                            modifier = Modifier.fillMaxWidth().padding(top = Space.lg)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                            Text(
                                if (recommendation != null) "Download in Model Manager" else "Browse models to download",
                                Modifier.padding(start = Space.sm)
                            )
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
    val profilePicker: Boolean = false
)

/** A starting generation model chosen for a device, plus whether it comfortably fits. */
internal data class ModelRecommendation(val model: CatalogModel, val fits: Boolean, val reason: String)

/**
 * Picks a starting generation model for a device with [totalRamBytes] of RAM: the largest catalog
 * model whose estimated RAM need fits, else the smallest (flagged as tight). Estimated need =
 * declared [CatalogModel.minimumRamBytes], or ~1.3x the download size when undeclared (weights
 * resident + KV/activation overhead — a rough but conservative floor). Returns null when the
 * catalog ships no generation model. Pure and catalog-driven, so new models are auto-considered.
 */
internal fun recommendModel(
    totalRamBytes: Long,
    catalog: List<CatalogModel> = ModelCatalog.all
): ModelRecommendation? {
    val candidates = catalog.filter { it.category == ModelRole.GENERATION && it.enabled }
    if (candidates.isEmpty()) return null
    fun needBytes(m: CatalogModel): Long = m.minimumRamBytes ?: ((m.totalExpectedBytes ?: 0L) * 13 / 10)
    val bySizeDesc = candidates.sortedByDescending { it.totalExpectedBytes ?: 0L }
    val best = bySizeDesc.firstOrNull { totalRamBytes >= needBytes(it) }
    return if (best != null) {
        ModelRecommendation(best, fits = true, reason = "Runs comfortably on your ${formatGb(totalRamBytes)} GB of RAM.")
    } else {
        ModelRecommendation(bySizeDesc.last(), fits = false, reason = "The lightest option — memory may be tight on this device.")
    }
}

@Composable
private fun OnboardRecommendationCard(rec: ModelRecommendation) {
    val sizeGb = rec.model.totalExpectedBytes?.let { formatGb(it) }
    val onContainer = if (rec.fits) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = Space.lg),
        colors = CardDefaults.cardColors(
            containerColor = if (rec.fits) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = vervanBorder()
    ) {
        Column(Modifier.padding(Space.lg)) {
            Text(
                if (rec.fits) "Recommended for your device" else "Best fit for your device",
                style = MaterialTheme.typography.labelMedium,
                color = if (rec.fits) onContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (sizeGb != null) "${rec.model.displayName}  •  $sizeGb GB download" else rec.model.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
                modifier = Modifier.padding(top = Space.xs)
            )
            Text(
                rec.reason,
                style = MaterialTheme.typography.bodySmall,
                color = if (rec.fits) onContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs)
            )
        }
    }
}

private fun formatGb(bytes: Long): String = if (bytes % (1024L * 1024 * 1024) == 0L) {
    // Whole-GB numbers read better without the ".0" — "8 GB" instead of "8.0 GB".
    (bytes / (1024L * 1024 * 1024)).toString()
} else {
    String.format("%.1f", bytes / (1024.0 * 1024 * 1024))
}
