package com.vervan.chat.tools

import com.vervan.chat.VervanApp
import org.json.JSONObject

enum class ToolRisk { READ_ONLY, REVERSIBLE_WRITE, EXTERNAL_ACTION }

data class ToolResult(val success: Boolean, val summary: String)

/**
 * A tool the model can ask the app to run. [execute] is only ever called by
 * [ToolExecutor] after risk-appropriate confirmation (see spec 16.4) — the model
 * emitting a call is a request, never an authorization.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val paramNames: List<String>,
    val risk: ToolRisk,
    val execute: suspend (app: VervanApp, params: JSONObject) -> ToolResult
)

data class ToolCall(val name: String, val params: JSONObject, val rawBlock: String)
