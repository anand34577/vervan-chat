package com.vervan.chat.ui.models

import android.app.ActivityManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.common.ScrollablePage
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.settings.formatBytes
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
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

private enum class FitLevel(val title: String, val body: String) {
    EXCELLENT("Comfortable fit", "Plenty of memory remains for Android and longer chats."),
    GOOD("Good fit", "This configuration should run reliably on this device."),
    TIGHT("Tight fit", "It may run, but other apps should be closed first."),
    TOO_LARGE("Too large", "Reduce model size, context, or quantization bits.")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelCalculatorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model calculator") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        ScrollablePage(padding) {
            Text("What can this device run?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Adjust model size, precision, and context to estimate memory before downloading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs, bottom = Space.lg)
            )

            DeviceCard(memory.totalMem, memory.availMem, safeBudgetGb)
            FitCard(fit, ratio, estimate.totalGb, safeBudgetGb)

            CalculatorCard("Model size", "${formatParameterCount(parametersB)} parameters") {
                Slider(
                    value = parametersB,
                    onValueChange = { parametersB = (it * 2).toInt() / 2f },
                    valueRange = 0.5f..32f,
                    steps = 62
                )
                Text("Larger models can be more capable but need more memory and run slower.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            CalculatorCard("Quantization", "$quantBits-bit") {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    listOf(2, 3, 4, 5, 8).forEach { bits ->
                        VervanFilterChip(selected = quantBits == bits, onClick = { quantBits = bits }, label = { Text("$bits-bit") })
                    }
                }
                Text("4-bit is the best starting point for most phones.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            CalculatorCard("Context window", formatContext(contexts[contextIndex])) {
                Slider(
                    value = contextIndex.toFloat(),
                    onValueChange = { contextIndex = it.toInt().coerceIn(contexts.indices) },
                    valueRange = 0f..contexts.lastIndex.toFloat(),
                    steps = contexts.size - 2
                )
                Text("Longer context remembers more conversation but increases memory use.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            MemoryBreakdown(estimate)
            RecommendationCard(suggested, quantBits, contexts[contextIndex], fit)
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
                    Text("Your hardware", style = MaterialTheme.typography.titleMedium)
                    Text("Android ${Build.VERSION.RELEASE} · ${Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown CPU"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                MiniStat(formatBytes(totalBytes), "Total RAM", Modifier.weight(1f))
                MiniStat(formatBytes(availableBytes), "Available now", Modifier.weight(1f))
                MiniStat(String.format("%.1f GB", budgetGb), "Safe model budget", Modifier.weight(1f))
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (fit == FitLevel.TOO_LARGE) Icons.Filled.Warning else Icons.Filled.CheckCircle, null, tint = color)
                Column(Modifier.weight(1f).padding(start = Space.sm)) {
                    Text(fit.title, style = MaterialTheme.typography.titleMedium, color = color)
                    Text(fit.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(String.format("%.1f GB", requiredGb), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                drawRoundRect(track, size = size, cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(color, size = Size(size.width * ratio.coerceIn(0f, 1f), size.height), cornerRadius = CornerRadius(size.height / 2))
            }
            Text("Estimated memory · safe budget ${String.format("%.1f GB", budgetGb)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Estimated memory", style = MaterialTheme.typography.titleMedium)
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
            Column(Modifier.padding(start = Space.md)) {
                Text("Recommended starting point", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Up to ${formatParameterCount(suggested)} at $bits-bit with ${formatContext(context)} context.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = Space.xs)
                )
                Text(
                    if (fit == FitLevel.TOO_LARGE) "Try this smaller size in GGUF or a LiteRT-LM model optimized for your device." else "GGUF offers broad compatibility; LiteRT-LM may be faster when an optimized model is available.",
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
