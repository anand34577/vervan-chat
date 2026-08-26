package com.vervan.chat.voice

import java.io.File
import com.vervan.chat.validation.InputLimits

/** Converts arbitrary text into one WAV file via a chosen [TtsEngine] — the Text-to-Speech
 * screen's batch (not realtime-playback) synthesis path. Reuses [SentenceChunker] purely as a
 * text splitter here (no streaming/overlap benefit for a one-shot batch job): [synthesizeSentences]
 * synthesizes each sentence independently so a failed one doesn't lose the rest — the caller
 * (see [com.vervan.chat.ui.tools.TextToSpeechViewModel]) can retry just that sentence via
 * [retrySentence], then [mergeToFile] concatenates whatever's ready (with [pauseMs] of silence
 * between sentences) into one WAV via [WavPcmDecoder.encode]. */
object TtsFileGenerator {
    data class Progress(val sentenceIndex: Int, val totalSentences: Int)

    /** [audio] is null when this sentence's synthesis failed — see [retrySentence]. */
    data class SentenceResult(val text: String, val audio: TtsAudio?)

    fun splitSentences(text: String): List<String> {
        require(text.length <= InputLimits.TTS_TEXT_CHARS) { "Text is too long for speech" }
        val sentences = mutableListOf<String>()
        SentenceChunker { sentences.add(it) }.apply { append(text); flush() }
        require(sentences.size <= InputLimits.TTS_MAX_SENTENCES) { "Text contains too many sentences" }
        return sentences
    }

    /** Synthesizes every sentence independently — a failure on one (engine hiccup, empty output)
     * leaves that entry's [SentenceResult.audio] null instead of aborting the whole document. */
    suspend fun synthesizeSentences(
        sentences: List<String>,
        engine: TtsEngine,
        lang: String,
        onProgress: (Progress) -> Unit = {}
    ): List<SentenceResult> = sentences.mapIndexed { index, sentence ->
        onProgress(Progress(index, sentences.size))
        SentenceResult(sentence, synthesizeOne(sentence, engine, lang))
    }.also { onProgress(Progress(sentences.size, sentences.size)) }

    suspend fun retrySentence(sentence: String, engine: TtsEngine, lang: String): TtsAudio? =
        synthesizeOne(sentence, engine, lang)

    private suspend fun synthesizeOne(sentence: String, engine: TtsEngine, lang: String): TtsAudio? =
        com.vervan.chat.system.runCatchingPreservingCancellation {
            engine.synthesize(markdownToSpeechText(sentence), lang)
        }
            .getOrNull()
            ?.takeIf { it.samples.isNotEmpty() }

    /** Concatenates every sentence that has audio (silently skipping ones that still don't —
     * callers decide whether "finish anyway" is acceptable) into [outputFile], with [pauseMs] of
     * silence inserted between sentences. Throws if nothing has audio yet. */
    fun mergeToFile(results: List<SentenceResult>, outputFile: File, pauseMs: Int = 250): File {
        val sampleRate = results.firstNotNullOfOrNull { it.audio?.sampleRateHz }
            ?: throw IllegalStateException("The selected voice produced no audio")
        require(pauseMs in 0..5_000) { "Pause must be between 0 and 5000 milliseconds" }
        val estimatedSamples = results.sumOf { it.audio?.samples?.size?.toLong() ?: 0L } +
            (sampleRate.toLong() * pauseMs / 1000L * results.size)
        require(estimatedSamples <= InputLimits.MAX_DECODED_AUDIO_BYTES / 2) {
            "The generated audio would be too large to save safely"
        }
        val silence = ShortArray((sampleRate * pauseMs / 1000).coerceAtLeast(0))
        val samples = ArrayList<Short>(results.sumOf { it.audio?.samples?.size ?: 0 } + silence.size * results.size)
        results.forEachIndexed { index, r ->
            r.audio?.samples?.let { for (s in it) samples.add(s) }
            if (index != results.lastIndex && r.audio != null) for (s in silence) samples.add(s)
        }
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(WavPcmDecoder.encode(samples.toShortArray(), sampleRate))
        return outputFile
    }
}
