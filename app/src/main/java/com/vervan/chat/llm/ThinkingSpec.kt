package com.vervan.chat.llm

import com.vervan.chat.data.db.entities.ModelEngine
import com.vervan.chat.data.db.entities.ModelInfo
import org.json.JSONObject

/**
 * Model-declared thinking behavior. Thinking is not one universal protocol: some models need a
 * system control token, while others only understand a prompt instruction. Keeping the protocol
 * as data lets model import/catalog metadata and the Configure screen teach the app about a new
 * model without adding another model-name branch to the generation loop.
 */
data class ThinkingSpec(
    val activation: Activation = Activation.PROMPT_ONLY,
    val enableText: String? = null,
    val source: Source = Source.DEFAULT,
    /** Optional provider request field for remote reasoning APIs, e.g. `reasoning_effort` or
     * `enable_thinking`. Null uses the OpenAI-compatible `reasoning_effort` default. */
    val remoteParameter: String? = null
) {
    enum class Activation { PROMPT_ONLY, SYSTEM_TOKEN }
    enum class Source { DETECTED, CATALOG, USER, LEGACY, DEFAULT }

    fun toJson(): String = JSONObject()
        .put("version", 1)
        .put("activation", activation.name)
        .put("enableText", enableText ?: JSONObject.NULL)
        .put("remoteParameter", remoteParameter ?: JSONObject.NULL)
        .put("source", source.name)
        .toString()

    companion object {
        private const val GEMMA_4_THINK_TOKEN = "<|think|>"
        private val GEMMA_4_IDENTITY = Regex("gemma[-_\\s]*4(?:\\D|$)", RegexOption.IGNORE_CASE)

        fun fromJson(raw: String?): ThinkingSpec? = runCatching {
            if (raw.isNullOrBlank()) return@runCatching null
            val json = JSONObject(raw)
            val activation = runCatching {
                Activation.valueOf(json.optString("activation", Activation.PROMPT_ONLY.name).uppercase())
            }.getOrDefault(Activation.PROMPT_ONLY)
            val source = runCatching {
                Source.valueOf(json.optString("source", Source.DEFAULT.name).uppercase())
            }.getOrDefault(Source.DEFAULT)
            ThinkingSpec(
                activation = activation,
                enableText = json.optString("enableText", "").takeIf { it.isNotBlank() },
                remoteParameter = json.optString("remoteParameter", "").takeIf { it.isNotBlank() },
                source = source
            )
        }.getOrNull()

        /**
         * Resolves the persisted spec first, then the model's imported chat template, then the
         * narrow legacy fallback for models imported before specs existed. The fallback is only a
         * migration bridge; new model support should arrive through metadata or a catalog entry.
         */
        fun forModel(model: ModelInfo?): ThinkingSpec {
            if (model == null) return ThinkingSpec()
            fromJson(model.thinkingSpecJson)?.let { return it }
            detectFromTemplate(model.chatTemplateOverride)?.let { return it }
            // Older imported rows may still have a null capability because the runtime cannot
            // probe thinking support during import. A declared protocol is enough to activate it;
            // only an explicit user-off value must suppress the compatibility fallback.
            if (model.engine == ModelEngine.LITERT_LM && model.supportsThinking != false) {
                val identity = listOf(model.displayName, model.filePath, model.catalogModelId, model.sourceUrl)
                    .filterNotNull().joinToString(" ")
                if (GEMMA_4_IDENTITY.containsMatchIn(identity)) {
                    return ThinkingSpec(Activation.SYSTEM_TOKEN, GEMMA_4_THINK_TOKEN, Source.LEGACY)
                }
            }
            return ThinkingSpec()
        }

        /** Best-effort, non-executing inspection of a Hugging Face/Jinja chat template. */
        fun detectFromTemplate(template: String?): ThinkingSpec? {
            val text = template?.takeIf { it.isNotBlank() } ?: return null
            return when {
                text.contains(GEMMA_4_THINK_TOKEN) ->
                    ThinkingSpec(Activation.SYSTEM_TOKEN, GEMMA_4_THINK_TOKEN, Source.DETECTED)
                // A template that branches on enable_thinking owns the switch, but this app's
                // engines do not execute arbitrary Jinja arguments. Keep it prompt-driven until
                // an engine adapter can pass the template flag safely.
                text.contains("enable_thinking", ignoreCase = true) ->
                    ThinkingSpec(Activation.PROMPT_ONLY, source = Source.DETECTED)
                else -> null
            }
        }

        /** Reads common HuggingFace-style metadata without executing arbitrary Jinja. This is
         * deliberately schema-tolerant: future model packages can declare thinking through a
         * `chat_template`, `enable_thinking`, `thinking`, or `is_reasoning_model` field without a
         * new model-name branch in the app. */
        fun detectFromMetadata(rawJson: Iterable<String>): ThinkingSpec? {
            rawJson.forEach { raw ->
                val json = runCatching { JSONObject(raw) }.getOrNull()
                // chat_template.jinja is commonly shipped as a standalone file rather than as
                // a JSON field. It is still model metadata, so inspect it directly instead of
                // silently discarding it when the JSON parse fails.
                if (json == null) {
                    detectFromTemplate(raw)?.let { return it }
                    return@forEach
                }
                detectFromTemplate(json.optString("chat_template").takeIf { it.isNotBlank() })?.let { return it }
                val thinkingDeclared = json.optBoolean("enable_thinking", false) ||
                    json.optBoolean("thinking", false) ||
                    json.optBoolean("is_reasoning_model", false)
                if (thinkingDeclared) {
                    return ThinkingSpec(Activation.PROMPT_ONLY, source = Source.DETECTED)
                }
            }
            return null
        }

        fun systemToken(token: String, source: Source = Source.USER) =
            ThinkingSpec(Activation.SYSTEM_TOKEN, token, source)
    }
}
