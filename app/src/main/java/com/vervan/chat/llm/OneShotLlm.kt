package com.vervan.chat.llm

import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.data.db.entities.ToolRun
import com.vervan.chat.data.db.entities.ToolRunState
import com.vervan.chat.modelload.LoadTrigger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ToolRunContext(
    val route: String,
    val name: String,
    val input: String? = null,
)

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
    suspend fun run(
        app: VervanApp,
        prompt: String,
        imagePath: String? = null,
        audioPath: String? = null,
        model: ModelInfo? = null,
        runContext: ToolRunContext? = null,
    ): String? {
        val flow = stream(app, prompt, imagePath, audioPath, model, runContext) ?: return null
        val out = StringBuilder()
        flow.collect { out.append(it) }
        return out.toString()
    }

    /** Model-aware streaming counterpart used by tools that update their UI token by token. */
    suspend fun stream(
        app: VervanApp,
        prompt: String,
        imagePath: String? = null,
        audioPath: String? = null,
        model: ModelInfo? = null,
        runContext: ToolRunContext? = null,
    ): Flow<String>? {
        val model = model ?: app.container.db.modelDao().getActiveModel(ModelRole.GENERATION) ?: return null
        val run = runContext?.let {
            ToolRun(
                toolRoute = it.route,
                toolName = it.name,
                input = it.input ?: prompt,
                modelId = model.id,
                modelName = model.displayName,
                backend = model.lastWorkingBackend.name,
            ).also { row -> app.container.db.toolRunDao().upsert(row) }
        }
        val source = try {
            val loaded = app.container.modelLoadCoordinator.ensureLoaded(model, LoadTrigger.CHAT_SEND)
            check(loaded.success) { loaded.errorMessage ?: "Could not load ${model.displayName}" }
            check(imagePath == null || app.container.visionEnabled(model)) { "${model.displayName} does not support image input on this device" }
            check(audioPath == null || app.container.audioEnabled(model)) { "${model.displayName} does not support audio input on this device" }
            val params = resolveGenerationParams(model, app.container.settingsRepository)
            app.container.generate(
                model, prompt, imagePath, audioPath,
                params.temperature, params.topP, params.topK, params.seed,
                params.minP, params.repetitionPenalty, params.maxOutputTokens, params.stopSequences
            )
        } catch (t: Throwable) {
            run?.let {
                app.container.db.toolRunDao().update(
                    it.copy(state = ToolRunState.FAILED, errorMessage = t.message, updatedAt = System.currentTimeMillis())
                )
            }
            throw t
        }
        if (run == null) return source
        return flow {
            val output = StringBuilder()
            try {
                source.collect { chunk ->
                    output.append(chunk)
                    emit(chunk)
                }
                app.container.db.toolRunDao().update(
                    run.copy(output = output.toString(), state = ToolRunState.COMPLETED, updatedAt = System.currentTimeMillis())
                )
            } catch (cancelled: CancellationException) {
                app.container.db.toolRunDao().update(
                    run.copy(output = output.toString(), state = ToolRunState.INTERRUPTED, updatedAt = System.currentTimeMillis())
                )
                throw cancelled
            } catch (t: Throwable) {
                app.container.db.toolRunDao().update(
                    run.copy(
                        output = output.toString(),
                        state = ToolRunState.FAILED,
                        errorMessage = t.message,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                throw t
            }
        }
    }
}
