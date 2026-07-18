package com.vervan.chat.llm

import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/** Every generation-time sampling/output-shaping value a call site needs, fully resolved (no more
 * `?:` chains at the call site). */
data class GenerationParams(
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val minP: Float,
    val repetitionPenalty: Float,
    val seed: Int?,
    val maxOutputTokens: Int,
    val stopSequences: List<String>
)

/** Three-tier resolution (per-chat override, if any → per-model override → app-wide Settings),
 * same chain `ChatViewModel.runGenerationLoop` already applied ad hoc for temperature/topP/topK —
 * extracted here because `RealtimeVoiceController` needs the identical chain (it previously
 * hardcoded `0.8f/0.95f/40/null`, silently ignoring every model/global setting) and duplicating
 * an 8-field version of this chain a second time was worse than one shared function.
 * [personaTemperature], when supplied, replaces the settings-level temperature fallback (only
 * `ChatViewModel` has a persona to apply `PersonaTraits.temperatureFor` with — `personaTemperature`
 * should already have that applied by the caller). */
suspend fun resolveGenerationParams(
    model: ModelInfo?,
    settings: SettingsRepository,
    chatTemperature: Float? = null,
    chatTopP: Float? = null,
    chatTopK: Int? = null,
    personaTemperature: Float? = null
): GenerationParams = GenerationParams(
    temperature = chatTemperature ?: model?.temperature ?: personaTemperature ?: settings.temperature.first(),
    topP = chatTopP ?: model?.topP ?: settings.topP.first(),
    topK = chatTopK ?: model?.topK ?: settings.topK.first(),
    minP = model?.minP ?: settings.minP.first(),
    repetitionPenalty = model?.repetitionPenalty ?: settings.repetitionPenalty.first(),
    seed = model?.seed ?: settings.randomSeed.first().takeIf { it >= 0 },
    maxOutputTokens = model?.maxOutputTokens ?: settings.maxOutputTokens.first(),
    stopSequences = (model?.stopSequences ?: "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
)

/** Buffers a rolling tail and completes the flow the moment any [stopSequences] appears in the
 * accumulated text, trimming the match (and anything after it) out of what's emitted — so a stop
 * sequence works the same for either engine without either native bridge needing to know about
 * it. A no-op pass-through when [stopSequences] is empty. */
fun Flow<String>.stoppingAt(stopSequences: List<String>): Flow<String> {
    if (stopSequences.isEmpty()) return this
    val longestStop = stopSequences.maxOf { it.length }
    // A private sentinel to break out of `collect` the moment a stop sequence is found — `collect`
    // has no early-exit of its own, and this is the standard way to stop consuming an upstream
    // Flow mid-collection from inside a `flow{}` builder without cancelling the whole coroutine.
    class StopHit : CancellationException()
    return flow {
        val buffer = StringBuilder()
        try {
            collect { chunk ->
                buffer.append(chunk)
                val text = buffer.toString()
                val hit = stopSequences.map { seq -> text.indexOf(seq) }.filter { it >= 0 }.minOrNull()
                if (hit != null) {
                    val safeToEmit = text.substring(0, hit)
                    if (safeToEmit.isNotEmpty()) emit(safeToEmit)
                    buffer.setLength(0)
                    throw StopHit()
                }
                // Keep only enough trailing text to still catch a stop sequence split across the
                // next chunk boundary; everything before that is safe to emit now.
                val keepFrom = (text.length - (longestStop - 1)).coerceAtLeast(0)
                if (keepFrom > 0) {
                    emit(text.substring(0, keepFrom))
                    buffer.setLength(0)
                    buffer.append(text.substring(keepFrom))
                }
            }
        } catch (_: StopHit) {
            // upstream collection stopped deliberately; fall through to flush below
        }
        if (buffer.isNotEmpty()) emit(buffer.toString())
    }
}
