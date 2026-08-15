package com.vervan.chat.ui.models

import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import android.app.ActivityManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import com.vervan.chat.ui.common.VervanButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanOutlinedButton as OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.dao.ModelSpeedStat
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.collectAsState
import com.vervan.chat.ui.settings.formatBytes
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import kotlinx.coroutines.launch
import kotlin.math.sqrt

data class ModelMemoryEstimate(val weightsGb: Float, val kvCacheGb: Float, val runtimeGb: Float) {
    val totalGb: Float get() = weightsGb + kvCacheGb + runtimeGb
}

internal fun estimateModelMemory(parametersB: Float, quantBits: Int, contextTokens: Int): ModelMemoryEstimate {
    val weights = parametersB * quantBits / 8f * 1.08f
    val scale = sqrt((parametersB / 7f).coerceAtLeast(0.08f))
    val kvCache = 1.25f * (contextTokens / 8192f) * scale
    val runtime = 0.55f + parametersB * 0.035f
    return ModelMemoryEstimate(weights, kvCache, runtime)
}

private enum class FitLevel(val titleRes: Int, val bodyRes: Int) {
    EXCELLENT(R.string.ui_modelcalculatorscreen_fit_comfortable, R.string.ui_modelcalculatorscreen_fit_comfortable_body),
    GOOD(R.string.ui_modelcalculatorscreen_fit_good, R.string.ui_modelcalculatorscreen_fit_good_body),
    TIGHT(R.string.ui_modelcalculatorscreen_fit_tight, R.string.ui_modelcalculatorscreen_fit_tight_body),
    TOO_LARGE(R.string.ui_modelcalculatorscreen_fit_too_large, R.string.ui_modelcalculatorscreen_fit_too_large_body)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelCalculatorScreen(onBack: () -> Unit, onBrowseModels: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val installedModels by app.container.db.modelDao().observeModels().collectAsState(initial = emptyList())
    val speedStats by app.container.db.messageDao().observeModelSpeedStats().collectAsState(initial = emptyList())
    val memory = remember {
        ActivityManager.MemoryInfo().also(context.getSystemService(ActivityManager::class.java)::getMemoryInfo)
    }
    val totalRamGb = memory.totalMem / 1_073_741_824f
    val availableRamGb = (memory.availMem - memory.threshold).coerceAtLeast(0L) / 1_073_741_824f
    val safeBudgetGb = minOf(totalRamGb * 0.58f, availableRamGb * 0.9f).coerceAtLeast(0.5f)
    var parametersB by remember { mutableFloatStateOf(defaultParameters(totalRamGb)) }
    var quantBits by remember { mutableIntStateOf(4) }
    val contexts = listOf(2048, 4096, 8192, 16384, 32768)
    var contextIndex by remember { mutableIntStateOf(1) }
    val estimate = estimateModelMemory(parametersB, quantBits, contexts[contextIndex])
    val ratio = estimate.totalGb / safeBudgetGb.coerceAtLeast(0.1f)
    val fit = when {
        ratio <= 0.65f -> FitLevel.EXCELLENT
        ratio <= 0.85f -> FitLevel.GOOD
        ratio <= 1f -> FitLevel.TIGHT
        else -> FitLevel.TOO_LARGE
    }
    val suggested = suggestedParameters(safeBudgetGb, quantBits, contexts[contextIndex])
    val statsByModelId = speedStats.associateBy { it.modelId }
    val safeBudgetBytes = (safeBudgetGb * 1_073_741_824f).toLong()
    // "Fastest installed GENERATION model that has actually been used and comfortably fits" —
    // real measured tok/s beats a synthetic benchmark pass at answering "what should I use",
    // and it's already sitting in every assistant reply's own generationMs/tokenCount (see
    // MessageDao.observeModelSpeedStats). A model never measured yet simply can't be recommended
    // this way — no cold-start guess substitutes for it, this card just stays empty until then.
    val bestMeasuredModel = installedModels
        .filter { it.role == ModelRole.GENERATION }
        .mapNotNull { model -> statsByModelId[model.id]?.let { model to it } }
        .filter { (model, _) -> model.fileSizeBytes <= safeBudgetBytes }
        .maxByOrNull { (_, stat) -> stat.tokensPerSecond }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.model_calculator_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.action_back)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        ScrollablePage(padding) {
            Text(stringResource(R.string.ui_modelcalculatorscreen_134_what_can_this_device_run), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Adjust model size, precision, and context to estimate memory before downloading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs, bottom = Space.lg)
            )

            DeviceCard(memory.totalMem, memory.availMem, safeBudgetGb)
            if (bestMeasuredModel != null) {
                val (model, stat) = bestMeasuredModel
                BestMeasuredModelCard(model, stat) {
                    scope.launch {
                        app.container.modelLoadCoordinator.setDefault(model)
                        snackbarHostState.showSnackbar("${model.displayName} set as default")
                    }
                }
            }
            FitCard(fit, ratio, estimate.totalGb, safeBudgetGb)

            CalculatorCard("Model size", "${formatParameterCount(parametersB)} parameters") {
                Slider(
                    value = parametersB,
                    onValueChange = { parametersB = (it * 2).toInt() / 2f },
                    valueRange = 0.5f..32f,
                    steps = 62
                )
            Text(stringResource(R.string.ui_modelcalculatorscreen_161_larger_models_may_answer_better_but_use_more), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            CalculatorCard(stringResource(R.string.ui_modelcalculatorscreen_quantization), stringResource(R.string.ui_modelcalculatorscreen_bits, quantBits)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    listOf(2, 3, 4, 5, 8).forEach { bits ->
                        VervanFilterChip(selected = quantBits == bits, onClick = { quantBits = bits }, label = { Text(stringResource(R.string.ui_modelcalculatorscreen_bits, bits)) })
                    }
                }
                Text(stringResource(R.string.ui_modelcalculatorscreen_170_4_bit_is_the_best_starting_point_for_most_ph), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            CalculatorCard(stringResource(R.string.ui_modelcalculatorscreen_context_window), formatContext(contexts[contextIndex])) {
                Slider(
                    value = contextIndex.toFloat(),
                    onValueChange = { contextIndex = it.toInt().coerceIn(contexts.indices) },
                    valueRange = 0f..contexts.lastIndex.toFloat(),
                    steps = contexts.size - 2
                )
                Text(stringResource(R.string.ui_modelcalculatorscreen_180_longer_context_remembers_more_conversation_b), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            MemoryBreakdown(estimate)
            RecommendationCard(suggested, quantBits, contexts[contextIndex], fit)
            // The one thing this screen was missing: it told you what fits, then stopped. Stash
            // the computed budget for Model Manager to pick up once (see PendingModelBrowseFilter)
            // so "Browse models" actually opens pre-filtered instead of a generic model list.
            Button(
                onClick = {
                    com.vervan.chat.modeldownload.PendingModelBrowseFilter.stash((safeBudgetGb * 1_073_741_824f).toLong())
                    onBrowseModels()
                },
                modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.ui_modelcalculatorscreen_197_browse_models_that_fit), modifier = Modifier.padding(start = Space.sm))
            }
            Text(
                "Estimate only. Model architecture, accelerator support, and thermal limits can change real performance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Space.lg)
            )
        }
    }
}

@Composable
private fun DeviceCard(totalBytes: Long, availableBytes: Long, budgetGb: Float) {
    Card(Modifier.fillMaxWidth(), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(Modifier.padding(start = Space.md)) {
                    Text(stringResource(R.string.ui_modelcalculatorscreen_216_your_hardware), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ui_modelcalculatorscreen_android_details, Build.VERSION.RELEASE, Build.SUPPORTED_ABIS.firstOrNull() ?: stringResource(R.string.ui_modelcalculatorscreen_unknown_cpu)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                MiniStat(formatBytes(totalBytes), stringResource(R.string.ui_modelcalculatorscreen_total_ram), Modifier.weight(1f))
                MiniStat(formatBytes(availableBytes), stringResource(R.string.ui_modelcalculatorscreen_available_now), Modifier.weight(1f))
                MiniStat(String.format("%.1f GB", budgetGb), stringResource(R.string.ui_modelcalculatorscreen_safe_model_budget), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BestMeasuredModelCard(model: ModelInfo, stat: ModelSpeedStat, onSetDefault: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = Space.md),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Speed, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text(stringResource(R.string.ui_modelcalculatorscreen_238_best_measured_on_this_device), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    model.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = Space.xs)
                )
                Text(
                    stringResource(R.string.ui_modelcalculatorscreen_speed_summary, String.format("%.1f", stat.tokensPerSecond), stat.samples, if (stat.samples == 1) stringResource(R.string.ui_modelcalculatorscreen_reply) else stringResource(R.string.ui_modelcalculatorscreen_replies)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = Space.xs)
                )
                if (!model.isActive) {
                    OutlinedButton(onClick = onSetDefault, modifier = Modifier.padding(top = Space.sm)) {
                        Text(stringResource(R.string.model_set_default))
                    }
                }
            }
        }
    }
}

@Composable
private fun FitCard(fit: FitLevel, ratio: Float, requiredGb: Float, budgetGb: Float) {
    val color = when (fit) {
        FitLevel.EXCELLENT, FitLevel.GOOD -> MaterialTheme.colorScheme.primary
        FitLevel.TIGHT -> MaterialTheme.colorScheme.tertiary
        FitLevel.TOO_LARGE -> MaterialTheme.colorScheme.error
    }
    Card(Modifier.fillMaxWidth().padding(top = Space.md), colors = SurfaceRole.Raised.cardColors(), border = SurfaceRole.Raised.border()) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stackValue = maxWidth < 430.dp
                if (stackValue) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                if (fit == FitLevel.TOO_LARGE) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                null,
                                tint = color,
                            )
                            Column(Modifier.weight(1f).padding(start = Space.sm)) {
                                Text(stringResource(fit.titleRes), style = MaterialTheme.typography.titleMedium, color = color)
                                Text(stringResource(fit.bodyRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            String.format("%.1f GB", requiredGb),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (fit == FitLevel.TOO_LARGE) Icons.Filled.Warning else Icons.Filled.CheckCircle, null, tint = color)
                        Column(Modifier.weight(1f).padding(start = Space.sm)) {
                            Text(stringResource(fit.titleRes), style = MaterialTheme.typography.titleMedium, color = color)
                            Text(stringResource(fit.bodyRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(String.format("%.1f GB", requiredGb), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                drawRoundRect(track, size = size, cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(color, size = Size(size.width * ratio.coerceIn(0f, 1f), size.height), cornerRadius = CornerRadius(size.height / 2))
            }
            Text(stringResource(R.string.ui_modelcalculatorscreen_estimated_memory, String.format("%.1f GB", budgetGb)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalculatorCard(title: String, value: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = Space.md), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

@Composable
private fun MemoryBreakdown(estimate: ModelMemoryEstimate) {
    val total = estimate.totalGb.coerceAtLeast(0.01f)
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
    Card(Modifier.fillMaxWidth().padding(top = Space.md), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(stringResource(R.string.ui_modelcalculatorscreen_333_estimated_memory), style = MaterialTheme.typography.titleMedium)
            Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                val values = listOf(estimate.weightsGb, estimate.kvCacheGb, estimate.runtimeGb)
                var x = 0f
                values.forEachIndexed { index, value ->
                    val width = size.width * value / total
                    drawRect(colors[index], topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = Size(width, size.height))
                    x += width
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                MemoryPart("Model", estimate.weightsGb, colors[0], Modifier.weight(1f))
                MemoryPart("Context", estimate.kvCacheGb, colors[1], Modifier.weight(1f))
                MemoryPart("Runtime", estimate.runtimeGb, colors[2], Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MemoryPart(label: String, value: Float, color: Color, modifier: Modifier) {
    Column(modifier) {
        Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) { Canvas(Modifier.size(10.dp)) { drawCircle(color) } }
        Text(String.format("%.1f GB", value), style = MaterialTheme.typography.titleSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecommendationCard(suggested: Float, bits: Int, context: Int, fit: FitLevel) {
    Card(
        Modifier.fillMaxWidth().padding(top = Space.md),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Speed, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text(stringResource(R.string.ui_modelcalculatorscreen_370_recommended_starting_point), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Up to ${formatParameterCount(suggested)} at $bits-bit with ${formatContext(context)} context.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = Space.xs)
                )
                Text(
                    if (fit == FitLevel.TOO_LARGE) "Choose a smaller GGUF or a LiteRT-LM model for this device." else "GGUF is widely compatible. Optimized LiteRT-LM models may run faster.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = Space.sm)
                )
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun defaultParameters(totalRamGb: Float): Float = when {
    totalRamGb >= 12f -> 7f
    totalRamGb >= 8f -> 4f
    totalRamGb >= 6f -> 3f
    else -> 1.5f
}

private fun suggestedParameters(budgetGb: Float, bits: Int, context: Int): Float =
    listOf(0.5f, 1f, 1.5f, 2f, 3f, 4f, 7f, 8f, 13f, 14f, 20f, 27f, 32f)
        .lastOrNull { estimateModelMemory(it, bits, context).totalGb <= budgetGb * 0.85f } ?: 0.5f

private fun formatParameterCount(value: Float): String = if (value % 1f == 0f) "${value.toInt()}B" else String.format("%.1fB", value)
private fun formatContext(tokens: Int): String = if (tokens >= 1024) "${tokens / 1024}K tokens" else "$tokens tokens"
