package com.vervan.chat.server

import android.util.Log
import com.vervan.chat.VervanApp
import com.vervan.chat.data.audit.ToolAuditSanitizer
import com.vervan.chat.data.db.entities.ModelInfo
import com.vervan.chat.data.db.entities.ToolAudit
import com.vervan.chat.llm.ThinkingPolicy
import com.vervan.chat.llm.estimateTokens
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.tools.ToolCallParser
import com.vervan.chat.tools.ToolRegistry
import com.vervan.chat.tools.ToolResult
import com.vervan.chat.tools.ToolRisk
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import com.vervan.chat.validation.InputLimits

/**
 * Runs one `/v1/chat/completions` turn end to end: thinking mode, the multi-hop tool loop, and
 * the answer/reasoning split — everything the native chat gets from `ChatViewModel` that a bare
 * `container.generate()` call does not.
 *
 * Built on the same pure components the ViewModel uses ([ThinkingPolicy], [ToolCallParser],
 * [ToolRegistry], [com.vervan.chat.llm.ThinkingParser] via [ApiStreamSplitter]) rather than
 * calling into the ViewModel or duplicating its persistence/branching logic: the API's unit of
 * work is a stateless request, not a chat tree, so the two share the *policy* while each keeps
 * its own bookkeeping.
 *
 * Emits [Event]s so the streaming and non-streaming server paths consume identical output — the
 * only difference is whether each event is written out as an SSE frame or accumulated into one
 * JSON body.
 *
 * ## Two kinds of tools
 * - **Client tools** — declared by the caller in the request's `tools` array. These are *not*
 *   executed here: the run stops, [Event.ClientToolCalls] is emitted, and the response finishes
 *   with `finish_reason: "tool_calls"` so the client executes them and sends the results back as
 *   `role: "tool"` messages, exactly as OpenAI's own API works.
 * - **App tools** — this device's own [ToolRegistry] (notes, expenses, device state…). These are
 *   executed in-process and the loop continues with the result, so an API client gets the same
 *   agentic behavior the phone UI has. Off unless the user opts in, and writes/external actions
 *   need a second opt-in, because an API caller has no confirmation dialog to approve them
 *   through the way the native tool card does.
 */
class ApiChatRunner(private val app: VervanApp) {

    /** One tool as declared by the caller in `tools[].function`. [parametersJson] is the raw JSON
     * Schema object, passed through to the model verbatim — this app doesn't validate arguments
     * against it (neither does OpenAI; a schema is a hint to the model, not a contract). */
    data class ClientTool(val name: String, val description: String, val parametersJson: String)

    /** `tool_choice`, normalized. [Named] covers both the `{"type":"function","function":
     * {"name":"x"}}` object form and a bare string naming a tool. */
    sealed interface ToolChoice {
        object Auto : ToolChoice
        object None : ToolChoice
        object Required : ToolChoice
        data class Named(val name: String) : ToolChoice
    }

    data class Sampling(
        val temperature: Float,
        val topP: Float,
        val topK: Int,
        val seed: Int?,
        val minP: Float,
        val repetitionPenalty: Float,
        val maxOutputTokens: Int,
        val stopSequences: List<String>
    )

    data class Request(
        val model: ModelInfo,
        /** System turn, RAG context already folded in by the caller. */
        val systemPrompt: String,
        /** Pre-flattened "User: …\nAssistant:" prompt — the fallback for LiteRT-LM and the remote
         * engine, neither of which takes a turn list (see `VervanApp.generate`'s `messages`). */
        val flatPrompt: String,
        val turns: List<Pair<String, String>>,
        val imagePath: String?,
        val audioPath: String?,
        val sampling: Sampling,
        val thinkingMode: String,
        val clientTools: List<ClientTool>,
        val toolChoice: ToolChoice,
        val appToolsEnabled: Boolean,
        val allowWriteTools: Boolean,
        val enabledAppToolIds: Set<String>,
        val maxToolHops: Int
    )

    data class ClientCall(val id: String, val name: String, val argumentsJson: String)

    sealed interface Event {
        /** Visible answer text, already stripped of thinking and tool markup. */
        data class Answer(val text: String) : Event
        /** Model reasoning, for `delta.reasoning_content` / `message.reasoning_content`. */
        data class Reasoning(val text: String) : Event
        /** An app tool ran on-device. Reported so a client can show what happened; it is *not* an
         * OpenAI tool_call (the client has nothing to execute). */
        data class AppToolExecuted(val name: String, val params: JSONObject, val result: ToolResult) : Event
        /** The model asked for caller-declared tools; the turn ends here. */
        data class ClientToolCalls(val calls: List<ClientCall>) : Event
        /** Terminal event. [finishReason] is one of `stop`, `length`, `tool_calls`. */
        data class Done(val finishReason: String, val completionTokens: Int) : Event
    }

    fun run(request: Request): Flow<Event> = flow {
        val isReasoningModel = request.model.supportsThinking == true
        val engine = request.model.engine
        val instruction = ThinkingPolicy.reasoningInstruction(request.thinkingMode, engine, isReasoningModel)
        val assistantPrefill = ThinkingPolicy.assistantPrefillFor(request.thinkingMode, engine, isReasoningModel)
        val reasoningBudget = ThinkingPolicy.reasoningBudgetFor(request.thinkingMode, engine, isReasoningModel)

        val systemPrompt = buildString {
            append(request.systemPrompt)
            if (instruction.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(instruction)
            }
            val toolInstructions = toolInstructions(request)
            if (toolInstructions.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(toolInstructions)
            }
        }.trim()

        // Extra turns accumulated across tool hops: the model's own tool-calling reply, then the
        // result it gets back. Rebuilt into the turn list each hop so the model sees the whole
        // exchange, the same way the native loop re-reads chat history per hop.
        val extraTurns = mutableListOf<Pair<String, String>>()
        var totalCompletionTokens = 0
        // Per-hop, not cumulative: `length` means *this* generation was cut off at the cap, and a
        // multi-hop tool run legitimately sums past the cap without any hop being truncated.
        var lastHopTokens = 0

        for (hop in 0..request.maxToolHops) {
            val lastHop = hop == request.maxToolHops
            val turns = buildList {
                if (systemPrompt.isNotBlank()) add("system" to systemPrompt)
                addAll(request.turns)
                addAll(extraTurns)
            }
            val flatPrompt = if (extraTurns.isEmpty()) request.flatPrompt else buildString {
                append(request.flatPrompt.removeSuffix("Assistant:"))
                extraTurns.forEach { (role, content) ->
                    append(role.replaceFirstChar(Char::uppercase)).append(": ").append(content).append('\n')
                }
                append("Assistant:")
            }

            val splitter = ApiStreamSplitter()
            // Attachments belong to the user's turn only — replaying them on every tool hop would
            // re-encode the same image into the context repeatedly (and llama.cpp rejects a second
            // image past `maxNumImages`).
            val imagePath = request.imagePath.takeIf { hop == 0 }
            val audioPath = request.audioPath.takeIf { hop == 0 }
            app.container.generate(
                request.model, flatPrompt, imagePath, audioPath,
                request.sampling.temperature, request.sampling.topP, request.sampling.topK,
                request.sampling.seed, request.sampling.minP, request.sampling.repetitionPenalty,
                request.sampling.maxOutputTokens, request.sampling.stopSequences,
                assistantPrefill = assistantPrefill,
                systemPrompt = systemPrompt,
                reasoningBudget = reasoningBudget,
                messages = turns
            ).collect { chunk ->
                val (answer, reasoning) = splitter.push(chunk)
                if (reasoning.isNotEmpty()) emit(Event.Reasoning(reasoning))
                if (answer.isNotEmpty()) emit(Event.Answer(answer))
            }
            val (tailAnswer, tailReasoning) = splitter.finish()
            if (tailReasoning.isNotEmpty()) emit(Event.Reasoning(tailReasoning))
            if (tailAnswer.isNotEmpty()) emit(Event.Answer(tailAnswer))

            val output = splitter.rawText
            lastHopTokens = estimateTokens(output)
            totalCompletionTokens += lastHopTokens
            // The prefill is part of the prompt, not of the output, but llama.cpp continues from
            // inside the `<think>` block it opened — so the raw text starts mid-reasoning with no
            // opening tag for the parser to find. Re-attaching it makes the block well-formed.
            val parseable = if (assistantPrefill != null) assistantPrefill + output else output

            val parsed = ToolCallParser.parseAll(parseable)
            if (parsed.calls.isEmpty()) {
                if (parsed.malformed.isNotEmpty() && !lastHop && toolsAdvertised(request)) {
                    // Same recovery the native loop does: tell the model its block didn't parse
                    // rather than silently dropping a call it believes it made.
                    extraTurns += "assistant" to output
                    extraTurns += "user" to MALFORMED_TOOL_FEEDBACK
                    continue
                }
                emit(Event.Done(finishReasonFor(request, lastHopTokens), totalCompletionTokens))
                return@flow
            }

            // Caller-declared tools win over same-named app tools: the client explicitly said it
            // provides that tool for this request, and executing our own instead would silently
            // run different code than the caller asked for.
            val clientNames = request.clientTools.map { it.name }.toSet()
            val clientCalls = parsed.calls.filter { it.name in clientNames }
            if (clientCalls.isNotEmpty()) {
                emit(
                    Event.ClientToolCalls(
                        clientCalls.map {
                            ClientCall("call_${UUID.randomUUID().toString().replace("-", "").take(24)}", it.name, it.params.toString())
                        }
                    )
                )
                emit(Event.Done("tool_calls", totalCompletionTokens))
                return@flow
            }

            if (!toolsAdvertised(request)) {
                // Nothing was on offer this turn, so a `<tool_call>` block is just something the
                // model made up. It's already hidden from the answer text; re-prompting about a
                // tool that was never advertised would only waste a hop.
                emit(Event.Done(finishReasonFor(request, lastHopTokens), totalCompletionTokens))
                return@flow
            }
            val call = parsed.calls.first()
            val tool = ToolRegistry.find(call.name)?.takeIf { request.appToolsEnabled && it.name in request.enabledAppToolIds }
            if (tool == null) {
                if (lastHop) {
                    emit(Event.Done("stop", totalCompletionTokens))
                    return@flow
                }
                extraTurns += "assistant" to output
                extraTurns += "user" to "Tool \"${call.name}\" is not available. Answer without it, or call one of the listed tools."
                continue
            }
            if (tool.risk != ToolRisk.READ_ONLY && !request.allowWriteTools) {
                if (lastHop) {
                    emit(Event.Done("stop", totalCompletionTokens))
                    return@flow
                }
                extraTurns += "assistant" to output
                extraTurns += "user" to
                    "Tool \"${tool.name}\" changes data or opens another app, which isn't allowed over the API. " +
                    "Answer without it, or use a read-only tool."
                continue
            }

            val result = try {
                tool.execute(app, call.params)
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (t: Throwable) {
                Log.w(TAG, "app tool '${tool.name}' failed", t)
                ToolResult(false, "Tool failed: ${t.toUserMessage()}")
            }
            recordAudit(tool.name, call.params, result, tool.risk.name)
            emit(Event.AppToolExecuted(tool.name, call.params, result))

            if (lastHop) {
                // Out of hops: the result still reaches the client as a tool event, but there's no
                // budget left for the model to write an answer from it.
                emit(Event.Done("stop", totalCompletionTokens))
                return@flow
            }
            extraTurns += "assistant" to output
            extraTurns += "user" to "Tool ${tool.name} result: ${result.summary}"
        }
        emit(Event.Done("stop", totalCompletionTokens))
    }

    /** `length` whenever the model produced at least the requested cap — the only signal available
     * here, since neither native bridge reports *why* it stopped. Both engines stop exactly at the
     * cap, so hitting it is the truncation case in practice. */
    private fun finishReasonFor(request: Request, hopTokens: Int): String =
        if (hopTokens >= request.sampling.maxOutputTokens) "length" else "stop"

    private fun toolsAdvertised(request: Request): Boolean =
        request.toolChoice != ToolChoice.None && (request.clientTools.isNotEmpty() || request.appToolsEnabled)

    /**
     * The prompt block that teaches the model this app's `<tool_call>` protocol and lists what it
     * may call. Client tools are described in full (name, description, JSON Schema) because the
     * caller sent those schemas expecting the model to see them; app tools reuse
     * [ToolRegistry.catalogDescription]'s discovery pointer instead of a full dump, for the same
     * prompt-budget reason documented there.
     */
    private fun toolInstructions(request: Request): String {
        if (request.toolChoice == ToolChoice.None) return ""
        val sections = mutableListOf<String>()
        if (request.clientTools.isNotEmpty()) {
            sections += buildString {
                appendLine(
                    "You can call tools by emitting a block exactly like this, on its own line and nothing else: " +
                        "<tool_call>{\"tool\": \"tool_name\", \"params\": {\"param\": \"value\"}}</tool_call>"
                )
                appendLine("Available tools:")
                request.clientTools.forEach { t ->
                    appendLine("- ${t.name}: ${t.description}")
                    appendLine("  parameters (JSON Schema): ${t.parametersJson}")
                }
            }
        }
        if (request.appToolsEnabled) {
            val catalog = ToolRegistry.catalogDescription(request.enabledAppToolIds)
            if (catalog.isNotBlank()) sections += catalog
        }
        if (sections.isEmpty()) return ""
        when (val choice = request.toolChoice) {
            is ToolChoice.Required -> sections += "You must call one of the tools above before answering."
            is ToolChoice.Named -> sections += "You must call the tool \"${choice.name}\" before answering."
            else -> Unit
        }
        return sections.joinToString("\n")
    }

    /** Same durable audit row the native tool path writes — an API-driven tool run must not be
     * invisible in Diagnostics just because no one was looking at the phone when it happened. */
    private suspend fun recordAudit(toolName: String, params: JSONObject, result: ToolResult, risk: String) {
        runCatching {
            app.container.db.toolAuditDao().insert(
                ToolAudit(
                    toolName = toolName,
                    paramsJson = ToolAuditSanitizer.sanitize(params),
                    success = result.success,
                    summary = ToolAuditSanitizer.sanitizeSummary(result.summary),
                    risk = risk,
                    chatId = null
                )
            )
        }.onFailure { Log.w(TAG, "could not record tool audit for $toolName", it) }
    }

    companion object {
        private const val TAG = "ApiChatRunner"
        private const val MALFORMED_TOOL_FEEDBACK =
            "Your <tool_call> block could not be parsed. Reply again using exactly this format, with valid " +
                "JSON and no extra text inside the tags: <tool_call>{\"tool\": \"tool_name\", \"params\": {...}}</tool_call>"

        /** Parses the request's `tools` array. Only `type: "function"` entries exist in the spec
         * today; anything else is ignored rather than rejected, so a client sending a future tool
         * type still gets an answer instead of a 400. */
        fun parseClientTools(tools: JSONArray?): List<ClientTool> {
            if (tools == null) return emptyList()
            val out = mutableListOf<ClientTool>()
            for (i in 0 until minOf(tools.length(), InputLimits.API_MAX_TOOLS)) {
                val entry = tools.optJSONObject(i) ?: continue
                if (entry.has("type") && entry.optString("type") != "function") continue
                val fn = entry.optJSONObject("function") ?: continue
                val name = fn.optString("name").takeIf { it.isNotBlank() && it.length <= 128 } ?: continue
                val description = fn.optString("description").ifBlank { "(no description provided)" }
                val parameters = (fn.optJSONObject("parameters") ?: JSONObject()).toString()
                if (description.length > 2_000 || parameters.length > InputLimits.API_MAX_TOOL_PARAMETER_CHARS) continue
                out += ClientTool(
                    name = name,
                    description = description,
                    parametersJson = parameters
                )
            }
            return out
        }

        fun parseToolChoice(raw: Any?): ToolChoice = when {
            raw == null || raw == JSONObject.NULL -> ToolChoice.Auto
            raw is String -> when (raw.lowercase()) {
                "none" -> ToolChoice.None
                "required", "any" -> ToolChoice.Required
                "auto" -> ToolChoice.Auto
                else -> ToolChoice.Named(raw)
            }
            raw is JSONObject -> raw.optJSONObject("function")?.optString("name")
                ?.takeIf { it.isNotBlank() }?.let { ToolChoice.Named(it) } ?: ToolChoice.Auto
            else -> ToolChoice.Auto
        }
    }
}
