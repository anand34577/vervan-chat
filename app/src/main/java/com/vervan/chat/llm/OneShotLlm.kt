package com.vervan.chat.llm

import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.ModelRole

/**
 * Non-conversational single-prompt generation — same "load active model if needed, collect the
 * whole stream" pattern as [TitleGenerator], reused by the Translation and Voice Chat tool
 * screens so neither duplicates model-loading/collection boilerplate. Not part of the chat
 * history/buildPrompt() path — callers pass a complete, self-contained prompt.
 */
object OneShotLlm {

    /** Null return means no generation model is available/active. */
    suspend fun run(app: VervanApp, prompt: String, imagePath: String? = null, audioPath: String? = null): String? {
        val model = app.container.db.modelDao().getActiveModel(ModelRole.GENERATION) ?: return null
        val engine = app.container.llmEngine
        var out = ""
        app.container.withLlm {
            if (engine.loadedModelPath != model.filePath) engine.load(model.filePath)
            engine.generate(prompt, imagePath, audioPath).collect { out += it }
        }
        return out
    }
}
