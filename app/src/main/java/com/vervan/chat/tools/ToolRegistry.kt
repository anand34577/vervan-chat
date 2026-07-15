package com.vervan.chat.tools

import android.Manifest
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.vervan.chat.data.db.entities.Memory
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.retrieval.RetrievalMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object ToolRegistry {
    val tools: List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "search_notes",
            description = "Search the user's notes by title or content.",
            paramNames = listOf("query"),
            risk = ToolRisk.READ_ONLY,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "search_notes needs a non-empty 'query'")
                val notes = app.container.db.noteDao().observeAll().first()
                    .filter { it.title.contains(query, true) || it.content.contains(query, true) }
                    .take(5)
                if (notes.isEmpty()) ToolResult(true, "No notes matched \"$query\".")
                else ToolResult(true, notes.joinToString("\n") { "- ${it.title}: ${it.content.take(150)}" })
            }
        ),
        ToolDefinition(
            name = "search_chats",
            description = "Search the user's chat titles.",
            paramNames = listOf("query"),
            risk = ToolRisk.READ_ONLY,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "search_chats needs a non-empty 'query'")
                val chats = app.container.db.chatDao().observeChats().first()
                    .filter { it.title.contains(query, true) }
                    .take(5)
                if (chats.isEmpty()) ToolResult(true, "No chats matched \"$query\".")
                else ToolResult(true, chats.joinToString("\n") { "- ${it.title}" })
            }
        ),
        ToolDefinition(
            name = "create_note",
            description = "Create a new note with a title and content.",
            paramNames = listOf("title", "content"),
            risk = ToolRisk.REVERSIBLE_WRITE,
            execute = { app, params ->
                val title = params.optString("title").ifBlank { "Untitled note" }
                val content = params.optString("content")
                app.container.db.noteDao().upsert(Note(title = title, content = content))
                ToolResult(true, "Created note \"$title\".")
            }
        ),
        ToolDefinition(
            name = "remember",
            description = "Save a fact about the user as a persistent global memory.",
            paramNames = listOf("text"),
            risk = ToolRisk.REVERSIBLE_WRITE,
            execute = { app, params ->
                val text = params.optString("text")
                if (text.isBlank()) return@ToolDefinition ToolResult(false, "remember needs a non-empty 'text'")
                app.container.db.memoryDao().upsert(Memory(text = text))
                ToolResult(true, "Remembered: $text")
            }
        ),
        ToolDefinition(
            name = "search_knowledge",
            description = "Search passages across the user's local knowledge bases.",
            paramNames = listOf("query"),
            risk = ToolRisk.READ_ONLY,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "search_knowledge needs a non-empty 'query'")
                val ids = app.container.db.knowledgeBaseDao().observeAll().first().map { it.id }
                val passages = app.container.retrievalEngine.retrieve(ids, query, RetrievalMode.KEYWORD, topK = 5)
                ToolResult(true, passages.joinToString("\n") { "- ${it.documentName}: ${it.excerpt.take(180)}" }.ifBlank { "No local passage matched \"$query\"." })
            }
        ),
        ToolDefinition(
            name = "project_details",
            description = "Read a project's instructions and contents by project name.",
            paramNames = listOf("name"),
            risk = ToolRisk.READ_ONLY,
            execute = { app, params ->
                val name = params.optString("name")
                val project = app.container.db.projectDao().observeAll().first().firstOrNull { it.name.equals(name, true) }
                    ?: return@ToolDefinition ToolResult(true, "No project named \"$name\".")
                ToolResult(true, "${project.name}\nInstructions: ${project.instructions.ifBlank { "None" }}")
            }
        ),
        ToolDefinition(
            name = "save_output",
            description = "Save text to the user's output library.",
            paramNames = listOf("label", "content"),
            risk = ToolRisk.REVERSIBLE_WRITE,
            execute = { app, params ->
                val content = params.optString("content")
                if (content.isBlank()) return@ToolDefinition ToolResult(false, "save_output needs non-empty 'content'")
                app.container.db.savedOutputDao().upsert(SavedOutput(content = content, label = params.optString("label")))
                ToolResult(true, "Saved output to Library.")
            }
        ),
        ToolDefinition(
            name = "draft_email",
            description = "Open the email app with a recipient, subject, and body prefilled. Does not send.",
            paramNames = listOf("to", "subject", "body"),
            risk = ToolRisk.EXTERNAL_ACTION,
            execute = { app, params ->
                launch(app, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(params.optString("to"))}")).apply {
                    putExtra(Intent.EXTRA_SUBJECT, params.optString("subject"))
                    putExtra(Intent.EXTRA_TEXT, params.optString("body"))
                }, "email draft")
            }
        ),
        ToolDefinition(
            name = "open_map",
            description = "Open a location or search query in the user's map app.",
            paramNames = listOf("query"),
            risk = ToolRisk.EXTERNAL_ACTION,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "open_map needs a non-empty 'query'")
                launch(app, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}")), "map")
            }
        ),
        ToolDefinition(
            name = "set_timer",
            description = "Open the Android clock app with a timer configured.",
            paramNames = listOf("seconds", "label"),
            risk = ToolRisk.EXTERNAL_ACTION,
            execute = { app, params ->
                val seconds = params.optInt("seconds")
                if (seconds <= 0) return@ToolDefinition ToolResult(false, "set_timer needs positive 'seconds'")
                launch(app, Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, params.optString("label"))
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }, "timer")
            }
        ),
        ToolDefinition(
            name = "create_calendar_event",
            description = "Open the calendar app with an event prefilled. Does not save without user confirmation there.",
            paramNames = listOf("title", "startMillis", "endMillis", "location"),
            risk = ToolRisk.EXTERNAL_ACTION,
            execute = { app, params ->
                val start = params.optLong("startMillis")
                if (start <= 0) return@ToolDefinition ToolResult(false, "create_calendar_event needs epoch-millisecond 'startMillis'")
                launch(app, Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                    putExtra(CalendarContract.Events.TITLE, params.optString("title"))
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, params.optLong("endMillis", start + 3_600_000))
                    putExtra(CalendarContract.Events.EVENT_LOCATION, params.optString("location"))
                }, "calendar event")
            }
        ),
        // On-device data sources (Phase G) — each gated on its own Settings toggle (off by
        // default) in addition to the OS runtime permission; a model call against a source the
        // user hasn't opted into gets a graceful no, not a crash or a permission-request popup
        // mid-conversation. See gatedResult() below.
        ToolDefinition(
            name = "search_contacts",
            description = "Search the user's on-device contacts by name.",
            paramNames = listOf("query"),
            risk = ToolRisk.READ_ONLY,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "search_contacts needs a non-empty 'query'")
                gatedResult(app, app.container.settingsRepository.contactsToolEnabled, Manifest.permission.READ_CONTACTS, "Contacts") {
                    withContext(Dispatchers.IO) {
                        val results = mutableListOf<String>()
                        app.contentResolver.query(
                            ContactsContract.Contacts.CONTENT_URI,
                            arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                            "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?", arrayOf("%$query%"),
                            "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
                        )?.use { cursor ->
                            while (cursor.moveToNext() && results.size < 10) results += cursor.getString(0)
                        }
                        if (results.isEmpty()) "No contacts matched \"$query\"." else results.joinToString("\n") { "- $it" }
                    }
                }
            }
        ),
        ToolDefinition(
            name = "search_calendar",
            description = "Search the user's on-device calendar events by title, from now onward.",
            paramNames = listOf("query"),
            risk = ToolRisk.READ_ONLY,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "search_calendar needs a non-empty 'query'")
                gatedResult(app, app.container.settingsRepository.calendarToolEnabled, Manifest.permission.READ_CALENDAR, "Calendar") {
                    withContext(Dispatchers.IO) {
                        val results = mutableListOf<String>()
                        val now = System.currentTimeMillis()
                        app.contentResolver.query(
                            CalendarContract.Events.CONTENT_URI,
                            arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART),
                            "${CalendarContract.Events.TITLE} LIKE ? AND ${CalendarContract.Events.DTSTART} >= ?",
                            arrayOf("%$query%", now.toString()),
                            "${CalendarContract.Events.DTSTART} ASC"
                        )?.use { cursor ->
                            val fmt = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                            while (cursor.moveToNext() && results.size < 10) {
                                results += "${cursor.getString(0)} — ${fmt.format(java.util.Date(cursor.getLong(1)))}"
                            }
                        }
                        if (results.isEmpty()) "No upcoming events matched \"$query\"." else results.joinToString("\n") { "- $it" }
                    }
                }
            }
        ),
        ToolDefinition(
            name = "search_sms",
            description = "Search the user's on-device SMS messages by content.",
            paramNames = listOf("query"),
            risk = ToolRisk.READ_ONLY,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "search_sms needs a non-empty 'query'")
                gatedResult(app, app.container.settingsRepository.smsToolEnabled, Manifest.permission.READ_SMS, "SMS") {
                    withContext(Dispatchers.IO) {
                        val results = mutableListOf<String>()
                        app.contentResolver.query(
                            Telephony.Sms.CONTENT_URI,
                            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                            "${Telephony.Sms.BODY} LIKE ?", arrayOf("%$query%"),
                            "${Telephony.Sms.DATE} DESC"
                        )?.use { cursor ->
                            while (cursor.moveToNext() && results.size < 10) {
                                results += "${cursor.getString(0)}: ${cursor.getString(1).take(150)}"
                            }
                        }
                        if (results.isEmpty()) "No messages matched \"$query\"." else results.joinToString("\n") { "- $it" }
                    }
                }
            }
        ),
        ToolDefinition(
            name = "calculate",
            description = "Evaluate an arithmetic expression (+ - * / parentheses, decimals) and return the result.",
            paramNames = listOf("expression"),
            risk = ToolRisk.READ_ONLY,
            execute = { _, params ->
                val expr = params.optString("expression")
                if (expr.isBlank()) return@ToolDefinition ToolResult(false, "calculate needs a non-empty 'expression'")
                try {
                    ToolResult(true, "$expr = ${ArithmeticEvaluator.evaluate(expr)}")
                } catch (e: Exception) {
                    ToolResult(false, "Couldn't evaluate \"$expr\": ${e.message}")
                }
            }
        ),
        ToolDefinition(
            name = "list_models",
            description = "List the models installed on-device, which one is active, and their capabilities.",
            paramNames = emptyList(),
            risk = ToolRisk.READ_ONLY,
            execute = { app, _ ->
                val models = app.container.db.modelDao().observeModels().first()
                if (models.isEmpty()) ToolResult(true, "No models installed.")
                else ToolResult(true, models.joinToString("\n") { m ->
                    "- ${m.displayName} (${m.role.name.lowercase()})${if (m.isActive) " [active]" else ""}"
                })
            }
        ),
        ToolDefinition(
            name = "search_memories",
            description = "Search facts previously remembered about the user.",
            paramNames = listOf("query"),
            risk = ToolRisk.READ_ONLY,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "search_memories needs a non-empty 'query'")
                val matches = app.container.db.memoryDao().observeAll().first()
                    .filter { it.text.contains(query, true) }.take(10)
                if (matches.isEmpty()) ToolResult(true, "No remembered facts matched \"$query\".")
                else ToolResult(true, matches.joinToString("\n") { "- ${it.text}" })
            }
        ),
        ToolDefinition(
            name = "device_status",
            description = "Read live device status: battery, storage free, memory, and network connectivity.",
            paramNames = emptyList(),
            risk = ToolRisk.READ_ONLY,
            execute = { app, _ ->
                if (!app.container.settingsRepository.deviceStatusToolEnabled.first()) {
                    return@ToolDefinition ToolResult(false, "Device status is disabled in Settings → Security.")
                }
                withContext(Dispatchers.IO) {
                    val battery = (app.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager)
                        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    val mem = android.app.ActivityManager.MemoryInfo().also {
                        (app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(it)
                    }
                    val freeBytes = StatFs(app.filesDir.path).availableBytes
                    val connectivity = (app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                        ?.let { cm ->
                            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                            when {
                                caps == null -> "offline"
                                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                                else -> "connected"
                            }
                        } ?: "unknown"
                    ToolResult(
                        true,
                        "Battery: ${battery ?: "unknown"}%. Storage free: ${freeBytes / (1024 * 1024)} MB. " +
                            "RAM available: ${mem.availMem / (1024 * 1024)} MB of ${mem.totalMem / (1024 * 1024)} MB. " +
                            "Network: $connectivity."
                    )
                }
            }
        )
    )

    /** Shared permission-gating shape for [ToolResult]-returning execute lambdas — checks the
     * app-level Settings toggle first (off by default for every Phase G source), then the OS
     * runtime permission, before running [query]. Neither check is skippable by the model. */
    private suspend fun gatedResult(
        app: com.vervan.chat.VervanApp,
        enabledFlow: kotlinx.coroutines.flow.Flow<Boolean>,
        permission: String,
        label: String,
        query: suspend () -> String
    ): ToolResult {
        if (!enabledFlow.first()) return ToolResult(false, "$label access is disabled in Settings → Security.")
        if (ContextCompat.checkSelfPermission(app, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return ToolResult(false, "$label permission hasn't been granted.")
        }
        return ToolResult(true, query())
    }

    fun find(name: String): ToolDefinition? = tools.find { it.name == name }

    /** [enabledIds] filters which tools are advertised to the model — the global Settings →
     * Tools disable list plus any per-chat override (see Chat.toolOverrideMap()), resolved by
     * the caller. Defaults to every tool for callers (like [com.vervan.chat.ui.chat.ChatViewModel.inspectContext])
     * that just want the full catalog's footprint. */
    fun catalogDescription(enabledIds: Set<String> = tools.map { it.name }.toSet()): String {
        val visible = tools.filter { it.name in enabledIds }
        if (visible.isEmpty()) return ""
        return buildString {
            appendLine("You can call tools by emitting a block like this on its own, when it helps answer the request:")
            appendLine("<tool_call>{\"tool\": \"tool_name\", \"params\": {\"param\": \"value\"}}</tool_call>")
            appendLine("Available tools:")
            visible.forEach { t -> appendLine("- ${t.name}(${t.paramNames.joinToString()}): ${t.description}") }
            appendLine("Only emit a tool call when you actually need one. Otherwise answer normally.")
        }
    }

    private fun launch(app: com.vervan.chat.VervanApp, intent: Intent, label: String): ToolResult = try {
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ToolResult(true, "Opened $label for review.")
    } catch (e: Exception) {
        ToolResult(false, "No app can handle $label: ${e.message}")
    }
}
