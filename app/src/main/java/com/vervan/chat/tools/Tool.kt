package com.vervan.chat.tools

import com.vervan.chat.VervanApp
import org.json.JSONObject

enum class ToolRisk { READ_ONLY, REVERSIBLE_WRITE, EXTERNAL_ACTION }

/** Groups [ToolDefinition]s for [ToolRegistry.tools]'s own `list_tools` output — purely
 * organizational, so a model skimming a couple dozen names sees them clustered instead of one
 * flat alphabetical wall. */
enum class ToolCategory(val label: String) {
    DISCOVERY("Discovery"),
    DATA("Search the user's data"),
    PRODUCTIVITY("Notes, tasks & expenses"),
    DEVICE("Device & utilities"),
    ACTION("Open another app")
}

/** [imagePath] is set only by tools that produce a viewable image (currently just
 * `generate_barcode`) — [com.vervan.chat.ui.chat.ChatMessageCards.ToolResultCard] renders it
 * inline instead of the generic text-only result card. Every other tool leaves it null. */
data class ToolResult(val success: Boolean, val summary: String, val imagePath: String? = null)

/**
 * A tool the model can ask the app to run. [execute] is only ever called by
 * [ToolExecutor] after risk-appropriate confirmation — the model
 * emitting a call is a request, never an authorization.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val paramNames: List<String>,
    val risk: ToolRisk,
    val category: ToolCategory,
    val execute: suspend (app: VervanApp, params: JSONObject) -> ToolResult
)

data class ToolCall(val name: String, val params: JSONObject, val rawBlock: String)

/** Threads "the image attached to this conversation turn" into a tool call without the model
 * ever having to name a file path — used only by `scan_qr_code`. Returns a copy so the caller's
 * own copy of [this] (stored/displayed as the tool call's params) is untouched. No-op if there
 * is no such image. */
fun JSONObject.withImagePathContext(imagePath: String?): JSONObject =
    if (imagePath == null) this else JSONObject(toString()).put("_imagePath", imagePath)
