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
        /** Why this transcription is happening, for [com.vervan.chat.modelload.ModelLoadCoordinator]'s
         * TTL bookkeeping on the MODEL_AUDIO path. Dictation in the app is a user action, so it
         * pins the model as any other user-driven load does; the API server passes
         * [LoadTrigger.API_REQUEST] instead, so a model loaded to serve `/v1/audio/transcriptions`
         * stays TTL-managed rather than becoming permanently resident. */
        loadTrigger: LoadTrigger = LoadTrigger.VOICE_SESSION,
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
                        val loaded = app.container.modelLoadCoordinator.ensureLoaded(model, loadTrigger)
                        val checkedModel = app.container.db.modelDao().get(model.id) ?: model
                        if (!loaded.success || !app.container.audioEnabled(checkedModel)) continue
                        val params = com.vervan.chat.llm.resolveGenerationParams(checkedModel, app.container.settingsRepository)
                        val text = StringBuilder()
                        app.container.generate(
                            model = checkedModel,
                            prompt = TRANSCRIBE_PROMPT,
                            imagePath = null,
                            audioPath = wavFile.absolutePath,
                            temperature = 0f,
                            topP = params.topP,
                            topK = params.topK,
                            seed = params.seed,
                            minP = params.minP,
                            repetitionPenalty = params.repetitionPenalty,
                            maxOutputTokens = params.maxOutputTokens.coerceAtMost(MAX_TRANSCRIPT_TOKENS),
                            stopSequences = (params.stopSequences + TRANSCRIBE_STOP_SEQUENCES).distinct(),
                            systemPrompt = TRANSCRIBE_SYSTEM_PROMPT
                        ).collect { text.append(it) }
                        ModelAudioTranscriptSanitizer.clean(text.toString(), durationMs)
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
    private const val MAX_TRANSCRIPT_TOKENS = 128
    private val TRANSCRIBE_STOP_SEQUENCES = listOf("\n\n", "\nAssistant:", "\nassistant:")
      private const val TRANSCRIBE_SYSTEM_PROMPT =
          "STT mode. Output only the exact spoken words in the language spoken. Never translate, answer, or explain."
      private const val TRANSCRIBE_PROMPT =
          "Transcribe audio only. Preserve the spoken language; do not translate."
}
