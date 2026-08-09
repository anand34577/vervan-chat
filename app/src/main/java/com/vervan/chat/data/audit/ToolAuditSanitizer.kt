package com.vervan.chat.data.audit

import org.json.JSONArray
import org.json.JSONObject

/** Keeps audit structure useful without persisting prompt, note, email, or calendar content. */
object ToolAuditSanitizer {
    fun sanitize(params: JSONObject): String {
        val redacted = JSONObject()
        params.keys().forEach { key ->
            redacted.put(key, sanitizeValue(params.opt(key)))
        }
        return redacted.toString()
    }

    /**
     * A [com.vervan.chat.tools.ToolResult]'s summary is the tool's *output*, and for a
     * content-returning tool that output IS the sensitive payload — `search_notes` returns note
     * bodies, `search_documents` returns document passages, `recall_memory` returns remembered
     * facts, the daily-report tool returns calendar entries, and `read_clipboard` returns the
     * clipboard verbatim (whatever the user last copied: a password, a 2FA code, a recovery
     * phrase). Persisting that into `tool_audit` for the 30 days before
     * [com.vervan.chat.data.db.dao.ToolAuditDao.purgeBefore] reaches it, while [sanitize] carefully
     * redacts the far-less-sensitive *parameters*, had the sensitivity backwards.
     *
     * An audit trail's job is to record that an action happened, not to re-store its payload — the
     * conversation itself already holds the content the user actually saw. So the summary is
     * reduced to a length descriptor, leaving `toolName`/`success`/`risk`/`chatId`/`createdAt` as
     * the durable record of what ran.
     */
    fun sanitizeSummary(summary: String): String =
        if (summary.isEmpty()) "" else "<redacted:${summary.length} chars>"

    private fun sanitizeValue(value: Any?): Any = when (value) {
        null, JSONObject.NULL -> JSONObject.NULL
        is Boolean, is Number -> value
        is String -> "<redacted:${value.length} chars>"
        is JSONArray -> "<redacted array:${value.length()} items>"
        is JSONObject -> "<redacted object:${value.length()} fields>"
        else -> "<redacted>"
    }
}
