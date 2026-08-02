package com.vervan.chat.voice

import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.modelload.LoadTrigger
import kotlinx.coroutines.flow.first
import java.io.File

/** One-shot offline transcription used by the editable chat-composer dictation flow. */
object OfflineDictationTranscriber {
    data class TranscriptionResult(val text: String, val engineLabel: String)

    suspend fun transcribe(
        app: VervanApp,
        wavFile: File,
        generationModelId: String? = null,
    ): Result<TranscriptionResult> = runCatching {
        val decoded = WavPcmDecoder.decode(wavFile.readBytes())
        require(decoded.samples.isNotEmpty()) { "No speech was recorded" }
        val model = generationModelId?.let { app.container.db.modelDao().get(it) }
            ?: app.container.db.modelDao().getActiveModel(ModelRole.GENERATION)
        val resolution = SttEnginePolicy.resolve(
            app,
            modelSupportsAudio = model != null && model.supportsAudio != false
        )
        check(resolution.isAvailable) {
            resolution.unavailableReason ?: "No speech-to-text engine is available"
        }

        val whisper = WhisperCppSttEngine(
            app,
            app.container.db.ttsVoiceModelDao(),
            app.container.settingsRepository
        )
        try {
            for (candidate in resolution.candidates) {
                when (candidate) {
                    SttEngineChoice.WHISPER_CPP -> if (whisper.isReady()) {
                        whisper.transcribe(decoded.samples, decoded.sampleRateHz)
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { return@runCatching TranscriptionResult(it, candidate.label) }
                    }
                    SttEngineChoice.MODEL_AUDIO -> {
                        model ?: continue
                        val durationMs = decoded.samples.size * 1_000L / decoded.sampleRateHz
                        if (durationMs > MODEL_AUDIO_MAX_MS) continue
                        val loaded = app.container.modelLoadCoordinator.ensureLoaded(model, LoadTrigger.VOICE_SESSION)
                        val checkedModel = app.container.db.modelDao().get(model.id) ?: model
                        if (!loaded.success || !app.container.audioEnabled(checkedModel)) continue
                        val params = com.vervan.chat.llm.resolveGenerationParams(checkedModel, app.container.settingsRepository)
                        val text = StringBuilder()
                        app.container.generate(
                            model = checkedModel,
                            prompt = TRANSCRIBE_PROMPT,
                            imagePath = null,
                            audioPath = wavFile.absolutePath,
                            temperature = params.temperature,
                            topP = params.topP,
                            topK = params.topK,
                            seed = params.seed,
                            minP = params.minP,
                            repetitionPenalty = params.repetitionPenalty,
                            maxOutputTokens = params.maxOutputTokens,
                            stopSequences = params.stopSequences
                        ).collect { text.append(it) }
                        text.toString().trim().takeIf { it.isNotEmpty() }
                            ?.let { return@runCatching TranscriptionResult(it, candidate.label) }
                    }
                    SttEngineChoice.ANDROID -> {
                        val language = app.container.settingsRepository.voiceInputLanguage.first()
                        val maxSeconds = (decoded.samples.size / decoded.sampleRateHz + 10).coerceAtMost(180)
                        AndroidSystemSttRecognizer.recognizeAudioFile(
                            app, wavFile, language, maxSeconds
                        ).getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                            ?.let { return@runCatching TranscriptionResult(it, candidate.label) }
                    }
                }
            }
        } finally {
            whisper.release()
        }
        error("No speech was recognized with the configured engine")
    }

    private const val MODEL_AUDIO_MAX_MS = 30_000L
    private const val TRANSCRIBE_PROMPT =
        "Transcribe exactly what was said in this audio. Output only the raw transcript, nothing else — no commentary, no translation."
}
