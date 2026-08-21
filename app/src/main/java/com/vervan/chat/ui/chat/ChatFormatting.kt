package com.vervan.chat.ui.chat

import com.vervan.chat.data.db.entities.Chat
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.MessageState
import com.vervan.chat.data.repo.MemoryRecall
import com.vervan.chat.llm.ClarificationParser
import com.vervan.chat.llm.ThinkingParser
import com.vervan.chat.llm.estimateTokens
import com.vervan.chat.llm.truncateToTokens
import com.vervan.chat.retrieval.SourcePassage
import com.vervan.chat.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure prompt-context helpers extracted out of `ChatViewModel` — history/summary splitting,
 * token-budget trimming, and the JSON serialization of retrieved sources, recalled memories, and
 * tool results. None of these touch ViewModel state, so they live here as testable free functions
 * instead of padding an already-large class.
 */
object ChatFormatting {

    /**
     * Splits [fullHistory] at [Chat.summaryCoversUpToMessageId] — everything at or before that
     * message is represented by [Chat.contextSummary] instead of being sent raw, everything after
     * is untouched. Returns (raw tail, summary text or null). Falls back to the whole history with
     * no summary if the cutoff message can't be found (e.g. a branch switch moved off the path it
     * was computed against) — same as "no summary yet".
     */
    fun historyAfterSummary(fullHistory: List<Message>, chatRow: Chat?): Pair<List<Message>, String?> {
        val summary = chatRow?.contextSummary?.takeIf { it.isNotBlank() } ?: return fullHistory to null
        val coveredIndex = chatRow.summaryCoversUpToMessageId?.let { id -> fullHistory.indexOfFirst { it.id == id } } ?: -1
        return if (coveredIndex >= 0) fullHistory.drop(coveredIndex + 1) to summary else fullHistory to null
    }

    /**
     * Context eviction — drops the oldest turns first once the conversation would blow
     * past ~60% of the model's usable context, instead of growing the prompt unbounded. The rest
     * of the budget covers persona, memories, retrieved sources, and the model's own output. Uses
     * the script-aware estimator (a flat chars/4 overshot ~4x on non-Latin scripts). Always keeps
     * at least the most recent turn.
     */
    fun trimHistoryToBudget(
        history: List<Message>,
        contextLimitTokens: Int,
        includePastThinking: Boolean = false
    ): List<Message> {
        if (history.size <= 1) return history
        val budgetTokens = (contextLimitTokens * 0.6).toInt().coerceAtLeast(50)
        var totalTokens = history.sumOf { estimateTokens(contextMessageContent(it, includePastThinking)) }
        if (totalTokens <= budgetTokens) return history
        val trimmed = history.toMutableList()
        while (trimmed.size > 1 && totalTokens > budgetTokens) {
            totalTokens -= estimateTokens(contextMessageContent(trimmed.removeAt(0), includePastThinking))
        }
        return trimmed
    }

    /**
     * Returns the representation of a stored message that belongs in a future prompt.
     * Assistant reasoning is deliberately excluded by default; the settings toggle opts into
     * the raw assistant content. Tool-call metadata remains part of context in either mode.
     */
    fun contextMessageContent(message: Message, includePastThinking: Boolean): String {
        if (message.role == MessageRole.ASSISTANT &&
            (message.state == MessageState.CANCELLED || message.state == MessageState.FAILED)
        ) return ""

        val base = if (message.role == MessageRole.ASSISTANT && !includePastThinking) {
            val answer = ThinkingParser.parse(message.content).answer
            val clarification = ClarificationParser.parse(answer)
            listOf(clarification.answer, clarification.request?.question)
                .filterNotNull()
                .filter { it.isNotBlank() }
                .joinToString("\n")
        } else {
            message.content
        }

        if (message.role != MessageRole.ASSISTANT || message.toolCallJson.isNullOrBlank()) return base
        val call = runCatching { JSONObject(message.toolCallJson) }.getOrNull() ?: return base
        val name = call.optString("tool").takeIf { it.isNotBlank() } ?: return base
        val params = call.optJSONObject("params") ?: JSONObject()
        val block = "<tool_call>${JSONObject().put("tool", name).put("params", params)}</tool_call>"
        return listOf(base, block).filter { it.isNotBlank() }.joinToString("\n")
    }

    fun trimPassagesToBudget(passages: List<SourcePassage>, contextLimitTokens: Int): List<SourcePassage> {
        if (passages.isEmpty()) return passages
        val budgetTokens = (contextLimitTokens * 0.25f).toInt().coerceAtLeast(200)
        val perPassageTokens = (budgetTokens / passages.size).coerceAtLeast(50)
        return passages.map { passage ->
            passage.copy(excerpt = truncateToTokens(passage.excerpt, perPassageTokens))
        }
    }

    fun sourcesToJson(passages: List<SourcePassage>): String {
        val arr = JSONArray()
        passages.forEach { p ->
            arr.put(
                JSONObject()
                    .put("chunkId", p.chunkId)
                    .put("documentId", p.documentId)
                    .put("documentName", p.documentName)
                    .put("sectionPath", p.sectionPath)
                    .put("excerpt", p.excerpt.take(500))
                    .put("score", p.score)
                    .apply { p.pageNumber?.let { put("pageNumber", it) } }
            )
        }
        return arr.toString()
    }

    fun memoryRecallToJson(recall: MemoryRecall): String? {
        if (recall.matches.isEmpty()) return null
        val recalled = JSONArray()
        recall.matches.forEach { match ->
            recalled.put(
                JSONObject()
                    .put("id", match.memory.id)
                    .put("text", match.memory.text)
                    .put("scope", match.memory.scope.name)
                    .put("score", match.score.toDouble())
            )
        }
        return JSONObject()
            .put("mode", recall.mode.name.lowercase())
            .put("recalled", recalled)
            .put("saved", JSONArray())
            .toString()
    }

    fun toolResultToJson(toolName: String, result: ToolResult): String =
        JSONObject().put("tool", toolName).put("success", result.success).put("summary", result.summary)
            .apply { result.imagePath?.let { put("imagePath", it) } }
            .toString()

    /** Walks parent links up from [from] to the nearest USER message's text (for regenerate/fork,
     * which need the prompt that produced an assistant turn). Empty string if there is none. */
    fun nearestUserText(all: List<Message>, from: Message?): String {
        val byId = all.associateBy { it.id }
        var current = from
        while (current != null) {
            if (current.role == MessageRole.USER) return current.content
            current = current.parentId?.let(byId::get)
        }
        return ""
    }

    /** Same walk as [nearestUserText], but for the image attached to this turn — backs the
     * `scan_qr_code` tool's implicit "the image attached to this turn" context when a tool call
     * is resumed from [ChatViewModel.confirmToolCall] rather than mid-[ChatViewModel.runGenerationLoop]
     * (which already has the turn's imagePath as a plain parameter). */
    fun nearestImagePath(all: List<Message>, from: Message?): String? {
        val byId = all.associateBy { it.id }
        var current = from
        while (current != null) {
            if (current.imagePath != null) return current.imagePath
            current = current.parentId?.let(byId::get)
        }
        return null
    }
}
