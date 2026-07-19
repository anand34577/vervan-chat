package com.vervan.chat.llm

import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.modelload.LoadTrigger
import kotlinx.coroutines.flow.Flow

/**
 * Non-conversational single-prompt generation — same "load active model if needed, collect the
 * whole stream" pattern as [TitleGenerator], reused by the Translation and Voice Chat tool
 * screens so neither duplicates model-loading/collection boilerplate. Not part of the chat
 * history/buildPrompt() path — callers pass a complete, self-contained prompt.
 *
 * [model] lets a caller pin a specific generation model instead of the app-wide active one. The
 * background chat features (title + context summary) pass the model the chat just generated with,
 * so this reuses the already-resident model rather than evicting it to load the global default
 * (a full unload+reload swap, and generation with the wrong model) — see [ChatViewModel].
 */
object OneShotLlm {

    /** Null return means no generation model is available/active. */
    suspend fun run(app: VervanApp, prompt: String, imagePath: String? = null, audioPath: String? = null, model: ModelInfo? = null): String? {
        val flow = stream(app, prompt, imagePath, audioPath, model) ?: return null
        val out = StringBuilder()
        flow.collect { out.append(it) }
        return out.toString()
    }

    /** Model-aware streaming counterpart used by tools that update their UI token by token. */
    suspend fun stream(app: VervanApp, prompt: String, imagePath: String? = null, audioPath: String? = null, model: ModelInfo? = null): Flow<String>? {
        val model = model ?: app.container.db.modelDao().getActiveModel(ModelRole.GENERATION) ?: return null
        val loaded = app.container.modelLoadCoordinator.ensureLoaded(model, LoadTrigger.CHAT_SEND)
        check(loaded.success) { loaded.errorMessage ?: "Could not load ${model.displayName}" }
        check(imagePath == null || app.container.visionEnabled(model)) { "${model.displayName} does not support image input on this device" }
        check(audioPath == null || app.container.audioEnabled(model)) { "${model.displayName} does not support audio input on this device" }
        val params = resolveGenerationParams(model, app.container.settingsRepository)
        return app.container.generate(
            model, prompt, imagePath, audioPath,
            params.temperature, params.topP, params.topK, params.seed,
            params.minP, params.repetitionPenalty, params.maxOutputTokens, params.stopSequences
        )
    }
}
