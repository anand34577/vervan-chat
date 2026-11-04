package com.vervan.chat.ui.onboarding

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vervan.chat.R
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.llm.ModelProfileType
import com.vervan.chat.modeldownload.CatalogModel
import com.vervan.chat.modeldownload.ModelCatalog
import com.vervan.chat.data.db.entities.ModelStatus
import com.vervan.chat.system.DeviceSoc
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.rememberReducedMotion
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanMotion
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * First run, in three short steps: what Vervan promises, a model that fits this phone (downloaded
 * right here with one tap), and a starting response style. Everything else lives in Settings.
 *
 * The download keeps running in the background service, so the user can finish onboarding (or
 * leave it) while it completes; the chat composer enables itself once the model is ready.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, onImportModel: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var selectedProfile by rememberSaveable { mutableStateOf("BALANCED") }
    val reducedMotion = rememberReducedMotion()

    val totalRam = remember {
        ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem
    }
    // Re-read whenever download state changes: a finished download (or space freed meanwhile)
    // must update the "not enough space" check instead of keeping the first-frame value.
    val downloadStatesForStorage by app.container.modelDownloadRepository.uiStates.collectAsStateWithLifecycle(initialValue = emptyList())
    val freeStorage = remember(downloadStatesForStorage.map { it.status }) {
        runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(-1L)
    }
    val hasInstalledModel by remember {
        app.container.db.modelDao().observeModels().map { models -> models.any { it.role == ModelRole.GENERATION } }
    }.collectAsStateWithLifecycle(initialValue = false)
    // A model is "set up" once one is installed or a download is queued/running/finished — the
    // point after which leaving the model step no longer strands the user without one.
    val modelSetUp = hasInstalledModel || downloadStatesForStorage.any {
        it.status != ModelStatus.NOT_DOWNLOADED && it.status != ModelStatus.FAILED
    }
    val options = remember(totalRam) { onboardingModelOptions(totalRam) }
    val recommendation = remember(totalRam) { recommendModel(totalRam) }
    var selectedModelId by rememberSaveable { mutableStateOf(recommendation?.model?.modelId) }

    val pageCount = 3
    // System back steps back through the pages, like the on-screen Back button, instead of
    // closing the app from step 2 or 3.
    androidx.activity.compose.BackHandler(enabled = page > 0) { page-- }
    fun finish() {
        scope.launch {
            app.container.settingsRepository.setDefaultProfile(selectedProfile)
            onDone()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        PageContainer(maxContentWidth = 600.dp) {
            Column(Modifier.fillMaxSize().padding(vertical = Space.lg)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StepIndicator(current = page, count = pageCount)
                    Spacer(Modifier.weight(1f))
                    if (page < pageCount - 1) {
                        TextButton(onClick = ::finish) { Text(stringResource(R.string.onboarding_skip)) }
                    }
                }

                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(snap()) togetherWith fadeOut(snap())
                        } else {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(VervanMotion.emphasized(360)) { w -> dir * w / 6 } + fadeIn(VervanMotion.emphasized(360))) togetherWith
                                (slideOutHorizontally(VervanMotion.emphasized(360)) { w -> -dir * w / 6 } + fadeOut(VervanMotion.emphasized(180)))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = "onboardingPage"
                ) { target ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.xxl),
                        verticalArrangement = Arrangement.spacedBy(Space.lg)
                    ) {
                        when (target) {
                            0 -> WelcomePage()
                            1 -> ModelPage(
                                options = options,
                                recommendedId = recommendation?.model?.modelId,
                                selectedId = selectedModelId,
                                onSelect = { selectedModelId = it },
                                totalRam = totalRam,
                                freeStorage = freeStorage,
                                onImportModel = onImportModel
                            )
                            else -> StylePage(selectedProfile = selectedProfile, onSelect = { selectedProfile = it })
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    if (page > 0) TextButton(onClick = { page-- }) { Text(stringResource(R.string.onboarding_back)) }
                    Spacer(Modifier.weight(1f))
                    val advance: () -> Unit = { if (page == pageCount - 1) finish() else page++ }
                    // On the model step with nothing downloading or installed, the in-page Download
                    // button is the one primary action. A second filled "Continue" next to it read
                    // as "this sets up the model" but only moved on, leaving the first chat with
                    // no model to run.
                    if (page == 1 && !modelSetUp) {
                        OutlinedButton(onClick = advance, modifier = Modifier.heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.onboarding_set_up_later))
                        }
                    } else {
                        Button(onClick = advance, modifier = Modifier.heightIn(min = 52.dp)) {
                            Text(stringResource(if (page == pageCount - 1) R.string.onboarding_start else R.string.onboarding_continue))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun stringResource(id: Int, vararg args: Any): String = androidx.compose.ui.res.stringResource(id, *args)

@Composable
private fun StepIndicator(current: Int, count: Int) {
    val stepLabel = stringResource(R.string.onboarding_step_of, current + 1, count)
    Row(
        Modifier.semantics { contentDescription = stepLabel },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val width by animateDpAsState(if (index == current) 28.dp else 8.dp, label = "step")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (index <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
private fun WelcomePage() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(88.dp)
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
    Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.displaySmall)
    Text(
        stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.md), modifier = Modifier.padding(top = Space.sm)) {
        PromiseRow(Icons.Filled.Lock, stringResource(R.string.onboarding_promise_private_title), stringResource(R.string.onboarding_promise_private_body))
        PromiseRow(Icons.Filled.WifiOff, stringResource(R.string.onboarding_promise_offline_title), stringResource(R.string.onboarding_promise_offline_body))
        PromiseRow(Icons.Filled.Bolt, stringResource(R.string.onboarding_promise_fast_title), stringResource(R.string.onboarding_promise_fast_body))
    }
    Text(
        stringResource(R.string.onboarding_mistakes_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PromiseRow(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModelPage(
    options: List<CatalogModel>,
    recommendedId: String?,
    selectedId: String?,
    onSelect: (String) -> Unit,
    totalRam: Long,
    freeStorage: Long,
    onImportModel: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    val downloads by app.container.modelDownloadRepository.uiStates.collectAsStateWithLifecycle(initialValue = emptyList())
    // A model imported from a file (or installed before onboarding) means there is nothing left
    // to download here.
    val installedModel by remember {
        app.container.db.modelDao().observeModels().map { models -> models.firstOrNull { it.role == ModelRole.GENERATION } }
    }.collectAsStateWithLifecycle(initialValue = null)

    Text(stringResource(R.string.onboarding_model_title), style = MaterialTheme.typography.headlineMedium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.onboarding_device_summary, formatGb(totalRam), if (freeStorage >= 0) formatGb(freeStorage) else "?") +
                if (DeviceSoc.hasLiteRtNpu) " · " + stringResource(R.string.onboarding_npu_available) else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (options.isEmpty()) {
        Text(stringResource(R.string.onboarding_no_models), style = MaterialTheme.typography.bodyMedium)
    }
    options.forEach { model ->
        val state = downloads.find { it.modelId == model.modelId && it.version == model.version }
        ModelOptionCard(
            model = model,
            recommended = model.modelId == recommendedId,
            selected = model.modelId == selectedId,
            fits = totalRam >= (model.minimumRamBytes ?: 0L),
            status = state?.status,
            progress = state?.let { s -> s.totalBytes?.takeIf { it > 0 }?.let { s.downloadedBytes.toFloat() / it } },
            onSelect = { onSelect(model.modelId) }
        )
    }

    val selected = options.find { it.modelId == selectedId }
    val imported = installedModel
    if (imported != null && downloads.none { it.status == ModelStatus.READY }) {
        StatusLine(Icons.Filled.CheckCircle, stringResource(R.string.onboarding_model_installed, imported.displayName))
    } else if (selected != null) {
        val state = downloads.find { it.modelId == selected.modelId && it.version == selected.version }
        val busy = state?.status != null && state.status != ModelStatus.NOT_DOWNLOADED && state.status != ModelStatus.FAILED
        val ready = state?.status == ModelStatus.READY
        val notEnoughSpace = freeStorage in 0 until (selected.totalExpectedBytes ?: 0L)
        when {
            ready -> StatusLine(Icons.Filled.CheckCircle, stringResource(R.string.onboarding_model_ready))
            busy -> Text(
                stringResource(R.string.onboarding_downloading_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {
                Button(
                    onClick = { scope.launch { app.container.modelDownloadRepository.startDownload(selected.modelId, selected.version) } },
                    enabled = !notEnoughSpace,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(stringResource(R.string.onboarding_download_button, selected.displayName, formatGb(selected.totalExpectedBytes ?: 0L)))
                }
                if (notEnoughSpace) {
                    Text(stringResource(R.string.onboarding_not_enough_space), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                selected.licenseName?.let { license ->
                    Text(
                        stringResource(R.string.onboarding_license_note, license),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.then(
                            if (selected.licenseUrl != null) Modifier.selectable(selected = false, role = Role.Button, onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(selected.licenseUrl))) }
                            }) else Modifier
                        )
                    )
                }
            }
        }
    }
    OutlinedButton(onClick = onImportModel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_more_models))
    }
}

@Composable
private fun ModelOptionCard(
    model: CatalogModel,
    recommended: Boolean,
    selected: Boolean,
    fits: Boolean,
    status: ModelStatus?,
    progress: Float?,
    onSelect: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null)
                Column(Modifier.weight(1f).padding(start = Space.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text(model.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (recommended) Badge(stringResource(R.string.onboarding_recommended))
                    }
                    Text(
                        listOfNotNull(
                            model.tier?.label,
                            model.totalExpectedBytes?.let { formatGb(it) + " GB" },
                            if (!fits) stringResource(R.string.onboarding_tight_memory) else null
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (fits) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(model.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (status) {
                null, ModelStatus.NOT_DOWNLOADED -> Unit
                ModelStatus.READY -> StatusLine(Icons.Filled.CheckCircle, stringResource(R.string.onboarding_model_ready))
                ModelStatus.FAILED -> Text(stringResource(R.string.onboarding_download_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else -> if (progress != null) {
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = Space.xs))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = Space.xs))
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StylePage(selectedProfile: String, onSelect: (String) -> Unit) {
    Text(stringResource(R.string.onboarding_style_title), style = MaterialTheme.typography.headlineMedium)
    Text(
        stringResource(R.string.onboarding_style_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ModelProfileType.entries.forEach { profile ->
        val selected = selectedProfile == profile.id
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(profile.id) })
        ) {
            Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null)
                Column(Modifier.weight(1f).padding(start = Space.sm)) {
                    Text(profile.label, style = MaterialTheme.typography.titleMedium)
                    Text(profile.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Text(
        stringResource(R.string.onboarding_style_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start
    )
}

/** A starting generation model chosen for a device, plus whether it comfortably fits. */
internal data class ModelRecommendation(val model: CatalogModel, val fits: Boolean, val reason: String)

/**
 * The short list onboarding shows: the recommended model, one lighter fallback, and an NPU build
 * when the catalogue has one for this chip. Keeps the choice to two or three cards.
 */
internal fun onboardingModelOptions(
    totalRamBytes: Long,
    catalog: List<CatalogModel> = ModelCatalog.availableOnThisDevice
): List<CatalogModel> {
    val generation = catalog.filter { it.category == ModelRole.GENERATION && it.enabled }
    val recommended = recommendModel(totalRamBytes, generation)?.model ?: return emptyList()
    val bySize = generation.filter { it.npuSocs.isEmpty() }.sortedBy { it.totalExpectedBytes ?: 0L }
    val lighter = bySize.lastOrNull { (it.totalExpectedBytes ?: 0L) < (recommended.totalExpectedBytes ?: 0L) && it.modelId != recommended.modelId }
    val npu = generation.firstOrNull { it.npuSocs.isNotEmpty() && it.modelId != recommended.modelId }
    return listOfNotNull(recommended, npu, lighter).distinctBy { it.modelId }
}

/**
 * Picks a starting generation model for a device with [totalRamBytes] of RAM: an NPU build for
 * this chip when one fits, otherwise the largest catalog model whose estimated RAM need fits,
 * else the smallest (flagged as tight). Estimated need = declared [CatalogModel.minimumRamBytes],
 * or ~1.3x the download size when undeclared. Returns null when the catalog ships no generation
 * model. Pure and catalog-driven, so new models are auto-considered.
 */
internal fun recommendModel(
    totalRamBytes: Long,
    catalog: List<CatalogModel> = ModelCatalog.availableOnThisDevice
): ModelRecommendation? {
    val candidates = catalog.filter { it.category == ModelRole.GENERATION && it.enabled }
    if (candidates.isEmpty()) return null
    fun needBytes(m: CatalogModel): Long = m.minimumRamBytes ?: ((m.totalExpectedBytes ?: 0L) * 13 / 10)
    // A build compiled for this phone's NPU (the catalogue only lists those for a matching chip)
    // beats a generic one of any size: faster and far easier on the battery.
    candidates.filter { it.npuSocs.isNotEmpty() && totalRamBytes >= needBytes(it) }
        .maxByOrNull { it.totalExpectedBytes ?: 0L }
        ?.let { return ModelRecommendation(it, fits = true, reason = "Built for this phone's NPU: fast replies with low battery use.") }
    val bySizeDesc = candidates.filter { it.npuSocs.isEmpty() }.ifEmpty { candidates }.sortedByDescending { it.totalExpectedBytes ?: 0L }
    val best = bySizeDesc.firstOrNull { totalRamBytes >= needBytes(it) }
    return if (best != null) {
        ModelRecommendation(best, fits = true, reason = "Runs comfortably on your ${formatGb(totalRamBytes)} GB of RAM.")
    } else {
        ModelRecommendation(bySizeDesc.last(), fits = false, reason = "The lightest option. Memory may be tight on this device.")
    }
}

internal fun formatGb(bytes: Long): String = if (bytes % (1024L * 1024 * 1024) == 0L) {
    // Whole-GB numbers read better without the ".0": "8 GB" instead of "8.0 GB".
    (bytes / (1024L * 1024 * 1024)).toString()
} else {
    String.format(Locale.US, "%.1f", bytes / (1024.0 * 1024 * 1024))
}
