package com.vervan.chat.tools

import android.Manifest
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.vervan.chat.data.db.entities.Expense
import com.vervan.chat.data.db.entities.Memory
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.retrieval.RetrievalMode
import com.vervan.chat.system.toUserMessage
import com.vervan.chat.ui.common.setSensitiveText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Every tool the model can call is 100% on-device — nothing here makes a network request or
 * sends anything off the phone (the app's only network paths are all user-initiated: model
 * downloads, the local API server, the Model Store). [list_tools]/[tool_details] are the entry
 * point: [catalogDescription] never dumps the full name+params+description for every tool into
 * the prompt (that cost was paid on every single turn whether or not a tool was ever called) —
 * instead the model calls `list_tools` to see names + one-line summaries grouped by
 * [ToolCategory], then `tool_details(name)` for one tool's exact parameters right before using
 * it.
 */
object ToolRegistry {
    /** Always available once tools are enabled at all — the entry point into everything else,
     * so [runGenerationLoop] never has to special-case them: they're gateable/disableable the
     * same as any other tool via [ChatToolsDialog], they're just also how the model finds out
     * what else it can call. */
    val META_TOOL_NAMES = setOf("list_tools", "search_tools", "tool_details")

    val tools: List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "list_tools",
            description = "List the tools currently available, grouped by category, with a one-line summary each.",
            paramNames = emptyList(),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DISCOVERY,
            execute = { app, _ ->
                val disabled = app.container.settingsRepository.disabledToolIds.first()
                val visible = tools.filter { it.name !in disabled && it.name !in META_TOOL_NAMES }
                if (visible.isEmpty()) {
                    ToolResult(true, "No other tools are currently available.")
                } else {
                    val body = visible.groupBy { it.category }
                        .toSortedMap(compareBy { it.ordinal })
                        .entries.joinToString("\n") { (category, group) ->
                            "${category.label}:\n" + group.joinToString("\n") { "- ${it.name}: ${it.description.take(80)}" }
                        }
                    ToolResult(true, body)
                }
            }
        ),
        // list_tools's full grouped dump doesn't scale as the catalog grows past a handful of
        // categories — this is the actual "search" step: one keyword against name+description,
        // so the model can jump straight to "what handles timers" without scanning every
        // category first. Same shape/cost as list_tools (names + one-line summaries only), just
        // pre-filtered.
        ToolDefinition(
            name = "search_tools",
            description = "Find tools matching a keyword (e.g. \"timer\", \"calendar\", \"expense\") by name or description.",
            paramNames = listOf("query"),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DISCOVERY,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "search_tools needs a non-empty 'query'")
                val disabled = app.container.settingsRepository.disabledToolIds.first()
                val matches = tools.filter {
                    it.name !in disabled && it.name !in META_TOOL_NAMES &&
                        (query in it.name || it.description.contains(query, ignoreCase = true))
                }
                if (matches.isEmpty()) ToolResult(true, "No tools matched \"$query\". Call list_tools to see everything available.")
                else ToolResult(true, matches.joinToString("\n") { "- ${it.name}: ${it.description.take(80)}" })
            }
        ),
        ToolDefinition(
            name = "tool_details",
            description = "Get a tool's full description and parameter names before calling it.",
            paramNames = listOf("name"),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DISCOVERY,
            execute = { app, params ->
                val name = params.optString("name")
                if (name.isBlank()) return@ToolDefinition ToolResult(false, "tool_details needs a non-empty 'name'")
                val disabled = app.container.settingsRepository.disabledToolIds.first()
                val tool = tools.find { it.name == name && it.name !in META_TOOL_NAMES }
                when {
                    tool == null -> ToolResult(false, "No tool named \"$name\". Call list_tools to see what's available.")
                    name in disabled -> ToolResult(false, "\"$name\" is disabled in Settings.")
                    else -> ToolResult(true, "${tool.name}(${tool.paramNames.joinToString()}): ${tool.description}")
                }
            }
        ),
        ToolDefinition(
            name = "search_notes",
            description = "Search the user's notes by title or content.",
            paramNames = listOf("query"),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DATA,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "search_notes needs a non-empty 'query'")
                val notes = app.container.db.noteDao().search(query).take(5)
                if (notes.isEmpty()) ToolResult(true, "No notes matched \"$query\".")
                else ToolResult(true, notes.joinToString("\n") { "- ${it.title}: ${it.content.take(150)}" })
            }
        ),
        ToolDefinition(
            name = "search_chats",
            description = "Search the user's chat titles.",
            paramNames = listOf("query"),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DATA,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "search_chats needs a non-empty 'query'")
                val chats = app.container.db.chatDao().search(query).take(5)
                if (chats.isEmpty()) ToolResult(true, "No chats matched \"$query\".")
                else ToolResult(true, chats.joinToString("\n") { "- ${it.title}" })
            }
        ),
        ToolDefinition(
            name = "create_note",
            description = "Create a new note with a title and content.",
            paramNames = listOf("title", "content"),
            risk = ToolRisk.REVERSIBLE_WRITE,
            category = ToolCategory.PRODUCTIVITY,
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
            category = ToolCategory.PRODUCTIVITY,
            execute = { app, params ->
                val text = params.optString("text")
                if (text.isBlank()) return@ToolDefinition ToolResult(false, "remember needs a non-empty 'text'")
                val saved = app.container.memoryRepository.upsert(Memory(text = text))
                ToolResult(true, "Saved to memory${if (saved.indexed) " and semantic index" else ""}: $text")
            }
        ),
        ToolDefinition(
            name = "search_knowledge",
            description = "Search passages across the user's local knowledge bases.",
            paramNames = listOf("query"),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DATA,
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
            category = ToolCategory.DATA,
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
            category = ToolCategory.PRODUCTIVITY,
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
            category = ToolCategory.ACTION,
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
            category = ToolCategory.ACTION,
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
            category = ToolCategory.ACTION,
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
        // set_timer only covers a duration from now — "wake me at 7am" has no home without a
        // clock-time alarm. Distinct AlarmClock action, same open-for-review-only shape.
        ToolDefinition(
            name = "set_alarm",
            description = "Open the Android clock app with an alarm configured for a specific time of day.",
            paramNames = listOf("hour", "minute", "label"),
            risk = ToolRisk.EXTERNAL_ACTION,
            category = ToolCategory.ACTION,
            execute = { app, params ->
                val hour = params.optInt("hour", -1)
                val minute = params.optInt("minute", 0)
                if (hour !in 0..23 || minute !in 0..59) {
                    return@ToolDefinition ToolResult(false, "set_alarm needs 'hour' (0-23) and 'minute' (0-59)")
                }
                launch(app, Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, params.optString("label"))
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }, "alarm")
            }
        ),
        // draft_email and open_map each hardcode one destination app; share_text hands text to
        // whatever app the user picks from the system share sheet (SMS, notes, other chat apps)
        // instead of the model needing a dedicated tool per target app.
        ToolDefinition(
            name = "share_text",
            description = "Open the system share sheet with text, letting the user pick which app to send it to.",
            paramNames = listOf("text"),
            risk = ToolRisk.EXTERNAL_ACTION,
            category = ToolCategory.ACTION,
            execute = { app, params ->
                val text = params.optString("text")
                if (text.isBlank()) return@ToolDefinition ToolResult(false, "share_text needs non-empty 'text'")
                val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) },
                    null
                )
                launch(app, chooser, "share sheet")
            }
        ),
        ToolDefinition(
            name = "create_calendar_event",
            description = "Open the calendar app with an event prefilled. Does not save without user confirmation there.",
            paramNames = listOf("title", "startMillis", "endMillis", "location"),
            risk = ToolRisk.EXTERNAL_ACTION,
            category = ToolCategory.ACTION,
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
        // On-device data sources — each gated on its own Settings toggle (off by
        // default) in addition to the OS runtime permission; a model call against a source the
        // user hasn't opted into gets a graceful no, not a crash or a permission-request popup
        // mid-conversation. See gatedResult() below.
        ToolDefinition(
            name = "search_calendar",
            description = "Search the user's on-device calendar events by title, from now onward.",
            paramNames = listOf("query"),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DEVICE,
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
            name = "calculate",
            description = "Evaluate an arithmetic expression (+ - * / parentheses, decimals) and return the result.",
            paramNames = listOf("expression"),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DEVICE,
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
            category = ToolCategory.DATA,
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
            category = ToolCategory.DATA,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "search_memories needs a non-empty 'query'")
                val recall = app.container.memoryRepository.search(query)
                if (recall.matches.isEmpty()) ToolResult(true, "No remembered facts matched \"$query\".")
                else ToolResult(
                    true,
                    recall.matches.joinToString("\n", "Matched memories (${recall.mode.name.lowercase()}):\n") { "- ${it.memory.text}" }
                )
            }
        ),
        ToolDefinition(
            name = "device_status",
            description = "Read live device status: battery, storage free, memory, and network connectivity.",
            paramNames = emptyList(),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DEVICE,
            execute = { app, _ ->
                if (!app.container.settingsRepository.deviceStatusToolEnabled.first()) {
                    return@ToolDefinition ToolResult(false, "Device status is off. Enable it in Settings → Privacy & security.")
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
                    val wifiOn = (app.getSystemService(android.content.Context.WIFI_SERVICE) as? WifiManager)?.let {
                        @Suppress("DEPRECATION") runCatching { it.isWifiEnabled }.getOrNull()
                    }
                    ToolResult(
                        true,
                        "Battery: ${battery ?: "unknown"}%. Storage free: ${freeBytes / (1024 * 1024)} MB. " +
                            "RAM available: ${mem.availMem / (1024 * 1024)} MB of ${mem.totalMem / (1024 * 1024)} MB. " +
                            "Network: $connectivity. Wi-Fi: ${if (wifiOn == true) "on" else if (wifiOn == false) "off" else "unknown"}."
                    )
                }
            }
        ),
        // A local model has no real-time clock of its own — "what's today's date" or "how long
        // until X" needs this instead of the model guessing from training data.
        ToolDefinition(
            name = "current_datetime",
            description = "Get the current on-device date and time.",
            paramNames = emptyList(),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DEVICE,
            execute = { _, _ ->
                // FULL date + SHORT time carried no zone at all, so "what time is it" came back
                // ambiguous and anything timezone-dependent was unanswerable. Emit the zone name,
                // the UTC offset and the IANA id explicitly — a model can't infer any of them.
                val now = java.util.Date()
                val zone = java.util.TimeZone.getDefault()
                val stamp = java.text.SimpleDateFormat("EEEE, d MMMM yyyy 'at' h:mm a zzz (XXX)", java.util.Locale.getDefault())
                    .apply { timeZone = zone }
                    .format(now)
                ToolResult(true, "$stamp — timezone ${zone.id}")
            }
        ),
        ToolDefinition(
            name = "unit_convert",
            description = "Convert a numeric value between common length, weight, or temperature units " +
                "(m, km, mi, ft, in, cm, kg, g, lb, oz, c, f, k).",
            paramNames = listOf("value", "from", "to"),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DEVICE,
            execute = { _, params ->
                val value = params.optDouble("value", Double.NaN)
                val from = params.optString("from")
                val to = params.optString("to")
                if (value.isNaN() || from.isBlank() || to.isBlank()) {
                    ToolResult(false, "unit_convert needs numeric 'value' and non-empty 'from'/'to' units")
                } else {
                    try {
                        ToolResult(true, "$value $from = ${UnitConversion.convert(value, from, to)} $to")
                    } catch (e: IllegalArgumentException) {
                        ToolResult(false, e.message ?: "Couldn't convert $from to $to")
                    }
                }
            }
        ),
        ToolDefinition(
            name = "generate_barcode",
            description = "Generate a barcode image encoding the given text, in any of these 'format's: " +
                "qr (default — use it only if the user said QR or didn't name a kind), " +
                "code128, code39, code93, codabar, itf, pdf417, aztec, data_matrix (any of these take arbitrary text), " +
                "ean13, ean8, upc_a, upc_e (fixed-length numeric product barcodes only — 'text' must be that many digits). " +
                "Always pass the 'format' the user actually asked for by name (e.g. \"a Code 128 barcode\" -> format=code128) " +
                "instead of defaulting to qr.",
            paramNames = listOf("text", "format"),
            risk = ToolRisk.REVERSIBLE_WRITE,
            category = ToolCategory.DEVICE,
            execute = { app, params ->
                val text = params.optString("text")
                if (text.isBlank()) return@ToolDefinition ToolResult(false, "generate_barcode needs non-empty 'text'")
                if (text.length > 2000) return@ToolDefinition ToolResult(false, "generate_barcode needs 'text' under 2000 characters")
                val format = BarcodeFormats.parse(params.optString("format").ifBlank { "qr" })
                    ?: return@ToolDefinition ToolResult(false, "Unknown 'format'. Use one of: ${BarcodeFormats.NAMES.keys.joinToString()}")
                withContext(Dispatchers.IO) {
                    try {
                        val dir = java.io.File(app.filesDir, "images").apply { mkdirs() }
                        val file = java.io.File(dir, "barcode-generated-${System.currentTimeMillis()}.png")
                        com.vervan.chat.model.BarcodeExtractor.generate(text, format, file)
                        with(com.vervan.chat.model.BarcodeExtractor) {
                            ToolResult(true, "Generated a ${format.label()} code encoding \"$text\". Shown to the user above; saved on-device.", imagePath = file.absolutePath)
                        }
                    } catch (e: Exception) {
                        ToolResult(false, "Couldn't generate that code: ${e.toUserMessage()}. If it's a numeric product barcode (EAN/UPC), check the digit count is right for that format.")
                    }
                }
            }
        ),
        ToolDefinition(
            name = "scan_qr_code",
            description = "Decode any QR code or barcode present in the image attached to this turn of the conversation. No parameters — it automatically uses that attachment.",
            paramNames = emptyList(),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DEVICE,
            execute = { _, params ->
                val imagePath = params.optString("_imagePath").takeIf { it.isNotBlank() }
                    ?: return@ToolDefinition ToolResult(false, "No image is attached to this conversation turn to scan. Ask the user to attach a photo of the QR code or barcode first.")
                withContext(Dispatchers.IO) {
                    val decoded = runCatching { com.vervan.chat.model.BarcodeExtractor.extractFromImage(java.io.File(imagePath)) }
                        .getOrElse { return@withContext ToolResult(false, "Couldn't read that image: ${it.toUserMessage()}") }
                    if (decoded.isBlank()) ToolResult(true, "No QR code or barcode was found in the attached image.")
                    else ToolResult(true, "Decoded:\n$decoded")
                }
            }
        ),
        ToolDefinition(
            name = "random_number",
            description = "Generate a random integer between min and max (inclusive), e.g. for a dice roll or a coin flip.",
            paramNames = listOf("min", "max"),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DEVICE,
            execute = { _, params ->
                val min = params.optInt("min", 1)
                val max = params.optInt("max", 100)
                if (min > max) ToolResult(false, "random_number needs 'min' <= 'max'")
                else ToolResult(true, (min..max).random().toString())
            }
        ),
        ToolDefinition(
            name = "read_clipboard",
            description = "Read the current text on the device clipboard.",
            paramNames = emptyList(),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.DEVICE,
            execute = { app, _ ->
                val clip = (app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
                    ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                if (clip.isNullOrBlank()) ToolResult(true, "Clipboard is empty.") else ToolResult(true, clip)
            }
        ),
        ToolDefinition(
            name = "write_clipboard",
            description = "Copy text to the device clipboard.",
            paramNames = listOf("text"),
            risk = ToolRisk.REVERSIBLE_WRITE,
            category = ToolCategory.DEVICE,
            execute = { app, params ->
                val text = params.optString("text")
                if (text.isBlank()) return@ToolDefinition ToolResult(false, "write_clipboard needs non-empty 'text'")
                val manager = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                manager.setSensitiveText(text, "Vervan Chat")
                ToolResult(true, "Copied to clipboard.")
            }
        ),
        // Tasks reuse the existing Note entity/table tagged "task" (and "task-done" once
        // completed) instead of a new entity+DAO+migration — Notes already has full CRUD,
        // search, and a UI, so a task is really just a note with a status.
        ToolDefinition(
            name = "create_task",
            description = "Create a to-do task.",
            paramNames = listOf("text"),
            risk = ToolRisk.REVERSIBLE_WRITE,
            category = ToolCategory.PRODUCTIVITY,
            execute = { app, params ->
                val text = params.optString("text")
                if (text.isBlank()) return@ToolDefinition ToolResult(false, "create_task needs non-empty 'text'")
                app.container.db.noteDao().upsert(Note(title = text.take(80), content = text, tags = "task"))
                ToolResult(true, "Added task: $text")
            }
        ),
        ToolDefinition(
            name = "list_tasks",
            description = "List open (not yet completed) to-do tasks.",
            paramNames = emptyList(),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.PRODUCTIVITY,
            execute = { app, _ ->
                val tasks = app.container.db.noteDao().getTaskNotes()
                    .filter { "task" in it.tags.split(",") }
                if (tasks.isEmpty()) ToolResult(true, "No open tasks.")
                else ToolResult(true, tasks.joinToString("\n") { "- ${it.title}" })
            }
        ),
        ToolDefinition(
            name = "complete_task",
            description = "Mark a to-do task as completed, by matching its text.",
            paramNames = listOf("query"),
            risk = ToolRisk.REVERSIBLE_WRITE,
            category = ToolCategory.PRODUCTIVITY,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "complete_task needs a non-empty 'query'")
                val task = app.container.db.noteDao().getTaskNotes()
                    .firstOrNull { "task" in it.tags.split(",") && it.title.contains(query, true) }
                    ?: return@ToolDefinition ToolResult(false, "No open task matched \"$query\".")
                app.container.db.noteDao().upsert(task.copy(tags = "task-done"))
                ToolResult(true, "Completed task: ${task.title}")
            }
        ),
        // create_note had no counterpart — a model that filed the wrong note, or a duplicate,
        // had no way to undo it short of the user opening Notes themselves. Soft-delete via the
        // existing deletedAt column, same as the Notes UI's own delete path — no new DAO method.
        ToolDefinition(
            name = "delete_note",
            description = "Delete a note by matching its title or content.",
            paramNames = listOf("query"),
            risk = ToolRisk.REVERSIBLE_WRITE,
            category = ToolCategory.PRODUCTIVITY,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "delete_note needs a non-empty 'query'")
                val note = app.container.db.noteDao().search(query).firstOrNull()
                    ?: return@ToolDefinition ToolResult(false, "No note matched \"$query\".")
                app.container.db.noteDao().upsert(note.copy(deletedAt = System.currentTimeMillis()))
                ToolResult(true, "Deleted note \"${note.title}\".")
            }
        ),
        // Mirrors delete_note — a task added by mistake shouldn't have to be completed just to
        // get it off the list.
        ToolDefinition(
            name = "delete_task",
            description = "Delete a to-do task by matching its text, without marking it complete.",
            paramNames = listOf("query"),
            risk = ToolRisk.REVERSIBLE_WRITE,
            category = ToolCategory.PRODUCTIVITY,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "delete_task needs a non-empty 'query'")
                val task = app.container.db.noteDao().getTaskNotes()
                    .firstOrNull { "task" in it.tags.split(",") && it.title.contains(query, true) }
                    ?: return@ToolDefinition ToolResult(false, "No open task matched \"$query\".")
                app.container.db.noteDao().upsert(task.copy(deletedAt = System.currentTimeMillis()))
                ToolResult(true, "Deleted task: ${task.title}")
            }
        ),
        ToolDefinition(
            name = "open_app",
            description = "Launch another installed app by name.",
            paramNames = listOf("name"),
            risk = ToolRisk.EXTERNAL_ACTION,
            category = ToolCategory.ACTION,
            execute = { app, params ->
                val name = params.optString("name")
                if (name.isBlank()) return@ToolDefinition ToolResult(false, "open_app needs a non-empty 'name'")
                withContext(Dispatchers.IO) {
                    val pm = app.packageManager
                    val launcherQuery = android.content.Intent(android.content.Intent.ACTION_MAIN)
                        .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    val launchable = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        pm.queryIntentActivities(
                            launcherQuery,
                            android.content.pm.PackageManager.ResolveInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.queryIntentActivities(launcherQuery, 0)
                    }
                    val match = launchable.firstOrNull {
                        it.loadLabel(pm).toString().contains(name, ignoreCase = true)
                    }
                    val launchIntent = match?.activityInfo?.packageName?.let(pm::getLaunchIntentForPackage)
                    if (launchIntent == null) ToolResult(false, "No installed app matched \"$name\".")
                    else launch(app, launchIntent, match.loadLabel(pm).toString())
                }
            }
        ),
        ToolDefinition(
            name = "log_expense",
            description = "Log an expense to the running expense ledger.",
            paramNames = listOf("merchant", "amount", "currency", "category", "paymentMethod"),
            risk = ToolRisk.REVERSIBLE_WRITE,
            category = ToolCategory.PRODUCTIVITY,
            execute = { app, params ->
                val merchant = params.optString("merchant")
                val amount = params.optDouble("amount", Double.NaN)
                if (merchant.isBlank() || amount.isNaN()) return@ToolDefinition ToolResult(false, "log_expense needs non-empty 'merchant' and numeric 'amount'")
                app.container.db.expenseDao().upsert(
                    Expense(
                        merchant = merchant, amount = amount,
                        currency = params.optString("currency"), category = params.optString("category"),
                        paymentMethod = params.optString("paymentMethod")
                    )
                )
                ToolResult(true, "Logged expense: $merchant, $amount ${params.optString("currency")}".trim())
            }
        ),
        ToolDefinition(
            name = "list_expenses",
            description = "List recent logged expenses, optionally filtered by category, with a running total.",
            paramNames = listOf("category"),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.PRODUCTIVITY,
            execute = { app, params ->
                val category = params.optString("category")
                val expenses = if (category.isBlank()) app.container.db.expenseDao().observeAll().first().take(20)
                else app.container.db.expenseDao().getByCategory(category)
                if (expenses.isEmpty()) return@ToolDefinition ToolResult(true, "No expenses logged" + (if (category.isNotBlank()) " for \"$category\"." else "."))
                val total = expenses.sumOf { it.amount }
                ToolResult(
                    true,
                    expenses.joinToString("\n") { "- ${it.merchant}: ${it.amount} ${it.currency}".trim() } +
                        "\nTotal: $total ${expenses.first().currency}".trim()
                )
            }
        ),
        ToolDefinition(
            name = "plan_my_day",
            description = "A morning briefing combining today's calendar events, open tasks, and device status.",
            paramNames = emptyList(),
            risk = ToolRisk.READ_ONLY,
            category = ToolCategory.PRODUCTIVITY,
            execute = { app, _ ->
                val sections = mutableListOf<String>()
                val settings = app.container.settingsRepository
                if (settings.calendarToolEnabled.first() && ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    withContext(Dispatchers.IO) {
                        val events = mutableListOf<String>()
                        val now = System.currentTimeMillis()
                        val endOfDay = now + 24 * 3_600_000L
                        app.contentResolver.query(
                            CalendarContract.Events.CONTENT_URI,
                            arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART),
                            "${CalendarContract.Events.DTSTART} BETWEEN ? AND ?", arrayOf(now.toString(), endOfDay.toString()),
                            "${CalendarContract.Events.DTSTART} ASC"
                        )?.use { cursor ->
                            val fmt = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                            while (cursor.moveToNext()) events += "${fmt.format(java.util.Date(cursor.getLong(1)))} — ${cursor.getString(0)}"
                        }
                        if (events.isNotEmpty()) sections += "Today's schedule:\n" + events.joinToString("\n") { "- $it" }
                    }
                }
                val tasks = app.container.db.noteDao().getTaskNotes().filter { "task" in it.tags.split(",") }
                if (tasks.isNotEmpty()) sections += "Open tasks:\n" + tasks.joinToString("\n") { "- ${it.title}" }
                sections += "Device: " + withContext(Dispatchers.IO) {
                    val battery = (app.getSystemService(android.content.Context.BATTERY_SERVICE) as? BatteryManager)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    "Battery ${battery ?: "unknown"}%."
                }
                ToolResult(true, if (sections.isEmpty()) "Nothing to report — no calendar/task sources enabled." else sections.joinToString("\n\n"))
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
        if (!enabledFlow.first()) return ToolResult(false, "$label access is off. Enable it in Settings → Privacy & security.")
        if (ContextCompat.checkSelfPermission(app, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return ToolResult(false, "$label permission hasn't been granted.")
        }
        return ToolResult(true, query())
    }

    fun find(name: String): ToolDefinition? = tools.find { it.name == name }

    /** [enabledIds] filters which tools are advertised to the model — the global Settings →
     * Tools disable list plus any per-chat override (see Chat.toolOverrideMap()), resolved by
     * the caller. Defaults to every tool for callers (like [com.vervan.chat.ui.chat.ChatViewModel.inspectContext])
     * that just want the full catalog's footprint.
     *
     * Always the discovery pointer, never a full per-tool dump — see this file's top doc
     * comment. A prompt that describes every tool's params on every turn doesn't scale past a
     * handful of tools, especially for a small on-device model paying that cost whether or not
     * it ever calls one.
     */
    fun catalogDescription(enabledIds: Set<String> = tools.map { it.name }.toSet()): String {
        val visible = tools.filter { it.name in enabledIds }
        if (visible.isEmpty()) return ""
        // Naming the tools up front (cheap — just names, no descriptions/params) matters: a
        // prompt that only says "call list_tools to see what's available" leaves a small
        // on-device model with nothing concrete in context, and it routinely answers "I don't
        // have tools" from parametric knowledge instead of actually emitting the discovery
        // call. Listing names here is what stops that false negative; list_tools/tool_details
        // still carry the full per-tool cost (see this function's doc comment) for descriptions
        // and params.
        val names = visible.filter { it.name !in META_TOOL_NAMES }.map { it.name }
        val namesLine = if (names.isNotEmpty()) "Available tools: ${names.joinToString(", ")}.\n" else ""
        return "You have access to tools, called by emitting a block like this on its own: " +
            "<tool_call>{\"tool\": \"tool_name\", \"params\": {\"param\": \"value\"}}</tool_call>\n" +
            namesLine +
            "Call search_tools(query) with a keyword for what you need (e.g. \"timer\", \"calendar\") to " +
            "find a specific tool fast, or list_tools for the full grouped list, then tool_details(name) " +
            "for a specific tool's parameters before calling it. Only reach for a tool when you actually need one.\n"
    }

    private fun launch(app: com.vervan.chat.VervanApp, intent: Intent, label: String): ToolResult = try {
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ToolResult(true, "Opened $label for review.")
    } catch (e: Exception) {
        ToolResult(false, "No app can handle $label: ${e.toUserMessage()}")
    }
}

/** Backs the `generate_barcode` tool's 'format' param — the human-typed name a model would
 * plausibly use, mapped to ZXing's enum. */
private object BarcodeFormats {
    val NAMES: Map<String, com.google.zxing.BarcodeFormat> = mapOf(
        "qr" to com.google.zxing.BarcodeFormat.QR_CODE,
        "code128" to com.google.zxing.BarcodeFormat.CODE_128,
        "code39" to com.google.zxing.BarcodeFormat.CODE_39,
        "code93" to com.google.zxing.BarcodeFormat.CODE_93,
        "codabar" to com.google.zxing.BarcodeFormat.CODABAR,
        "ean13" to com.google.zxing.BarcodeFormat.EAN_13,
        "ean8" to com.google.zxing.BarcodeFormat.EAN_8,
        "itf" to com.google.zxing.BarcodeFormat.ITF,
        "upc_a" to com.google.zxing.BarcodeFormat.UPC_A,
        "upc_e" to com.google.zxing.BarcodeFormat.UPC_E,
        "pdf417" to com.google.zxing.BarcodeFormat.PDF_417,
        "aztec" to com.google.zxing.BarcodeFormat.AZTEC,
        "data_matrix" to com.google.zxing.BarcodeFormat.DATA_MATRIX,
    )

    fun parse(name: String): com.google.zxing.BarcodeFormat? = NAMES[name.trim().lowercase().replace("-", "_").replace(" ", "_")]
}

/** Backs the `unit_convert` tool — a fixed, small conversion table rather than a units library,
 * since length/weight/temperature covers the vast majority of what a model actually gets asked. */
private object UnitConversion {
    private val LENGTH_TO_METERS = mapOf(
        "m" to 1.0, "meter" to 1.0, "meters" to 1.0,
        "km" to 1000.0, "kilometer" to 1000.0, "kilometers" to 1000.0,
        "cm" to 0.01, "centimeter" to 0.01, "centimeters" to 0.01,
        "mm" to 0.001, "millimeter" to 0.001, "millimeters" to 0.001,
        "mi" to 1609.344, "mile" to 1609.344, "miles" to 1609.344,
        "ft" to 0.3048, "foot" to 0.3048, "feet" to 0.3048,
        "in" to 0.0254, "inch" to 0.0254, "inches" to 0.0254
    )
    private val WEIGHT_TO_KG = mapOf(
        "kg" to 1.0, "kilogram" to 1.0, "kilograms" to 1.0,
        "g" to 0.001, "gram" to 0.001, "grams" to 0.001,
        "lb" to 0.453592, "lbs" to 0.453592, "pound" to 0.453592, "pounds" to 0.453592,
        "oz" to 0.0283495, "ounce" to 0.0283495, "ounces" to 0.0283495
    )
    private val TEMPERATURE = setOf("c", "celsius", "f", "fahrenheit", "k", "kelvin")

    fun convert(value: Double, from: String, to: String): Double {
        val f = from.trim().lowercase()
        val t = to.trim().lowercase()
        val result = when {
            f in LENGTH_TO_METERS && t in LENGTH_TO_METERS -> value * LENGTH_TO_METERS.getValue(f) / LENGTH_TO_METERS.getValue(t)
            f in WEIGHT_TO_KG && t in WEIGHT_TO_KG -> value * WEIGHT_TO_KG.getValue(f) / WEIGHT_TO_KG.getValue(t)
            f in TEMPERATURE && t in TEMPERATURE -> convertTemperature(value, f, t)
            else -> throw IllegalArgumentException("Unsupported or mismatched units: $from -> $to")
        }
        return kotlin.math.round(result * 1000) / 1000
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        val celsius = when {
            from.startsWith("c") -> value
            from.startsWith("f") -> (value - 32) * 5.0 / 9.0
            else -> value - 273.15
        }
        return when {
            to.startsWith("c") -> celsius
            to.startsWith("f") -> celsius * 9.0 / 5.0 + 32
            else -> celsius + 273.15
        }
    }
}
