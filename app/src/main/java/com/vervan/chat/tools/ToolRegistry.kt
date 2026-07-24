package com.vervan.chat.tools

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Process
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.vervan.chat.data.db.entities.Expense
import com.vervan.chat.data.db.entities.Memory
import com.vervan.chat.data.db.entities.Note
import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.retrieval.RetrievalMode
import com.vervan.chat.system.toUserMessage
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
                val saved = app.container.memoryRepository.upsert(Memory(text = text))
                ToolResult(true, "Saved to memory${if (saved.indexed) " and semantic index" else ""}: $text")
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
        // The one outbound-network tool in the catalog (the only other intentional network
        // call paths in the app are model downloads, the local API server, and the model
        // store — all user-initiated, none model-initiated). Knowledge Graph returns entity
        // facts (people/places/things) — strong for "who/what is X" the local model's
        // training data is weak on, useless for "find recent news about Y" or arbitrary web
        // pages, and the description below tells the model exactly that so it doesn't reach
        // for this when the user actually wants a web page. EXTERNAL_ACTION because the
        // request leaves the device, same risk tier as draft_email/open_map. Gated on its
        // own Settings toggle (Settings → Security → Web search) plus a configured API key;
        // a model call against an unconfigured/off web search gets a graceful no.
        ToolDefinition(
            name = "web_search",
            description = "Look up entity facts (people, places, organizations, things) from Google's " +
                "Knowledge Graph over the network. Best for \"who/what is X\" the model is unsure of; " +
                "not for finding web pages, recent news, prices, or current events.",
            paramNames = listOf("query"),
            risk = ToolRisk.EXTERNAL_ACTION,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "web_search needs a non-empty 'query'")
                if (!app.container.settingsRepository.webSearchToolEnabled.first()) {
                    return@ToolDefinition ToolResult(false, "Web search is disabled in Settings → Security.")
                }
                val client = app.container.knowledgeGraphClient
                try {
                    val entities = withContext(Dispatchers.IO) { client.search(query) }
                    ToolResult(true, client.formatResult(query, entities))
                } catch (e: IllegalArgumentException) {
                    ToolResult(false, "Web search isn't configured: ${e.message}")
                } catch (e: java.io.IOException) {
                    ToolResult(false, "Web search failed (network or API error): ${e.message ?: e.javaClass.simpleName}")
                }
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
        // On-device data sources — each gated on its own Settings toggle (off by
        // default) in addition to the OS runtime permission; a model call against a source the
        // user hasn't opted into gets a graceful no, not a crash or a permission-request popup
        // mid-conversation. See gatedResult() below.
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
            execute = { _, _ ->
                val fmt = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.FULL, java.text.DateFormat.SHORT)
                ToolResult(true, fmt.format(java.util.Date()))
            }
        ),
        ToolDefinition(
            name = "unit_convert",
            description = "Convert a numeric value between common length, weight, or temperature units " +
                "(m, km, mi, ft, in, cm, kg, g, lb, oz, c, f, k).",
            paramNames = listOf("value", "from", "to"),
            risk = ToolRisk.READ_ONLY,
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
            name = "random_number",
            description = "Generate a random integer between min and max (inclusive), e.g. for a dice roll or a coin flip.",
            paramNames = listOf("min", "max"),
            risk = ToolRisk.READ_ONLY,
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
            execute = { app, params ->
                val text = params.optString("text")
                if (text.isBlank()) return@ToolDefinition ToolResult(false, "write_clipboard needs non-empty 'text'")
                val manager = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                manager.setPrimaryClip(android.content.ClipData.newPlainText("Vervan Chat", text))
                ToolResult(true, "Copied to clipboard.")
            }
        ),
        ToolDefinition(
            name = "search_files",
            description = "Search filenames in the user's Downloads folder.",
            paramNames = listOf("query"),
            risk = ToolRisk.READ_ONLY,
            execute = { app, params ->
                val query = params.optString("query")
                if (query.isBlank()) return@ToolDefinition ToolResult(false, "search_files needs a non-empty 'query'")
                if (!app.container.settingsRepository.filesToolEnabled.first()) {
                    return@ToolDefinition ToolResult(false, "Files access is disabled in Settings → Security.")
                }
                // Downloads is a shared MediaStore collection every app can query by filename
                // without READ_EXTERNAL_STORAGE on Android 13+ (that permission isn't even
                // requestable there — see the manifest's maxSdkVersion note); below 13, the
                // Settings toggle above already required granting it.
                if (android.os.Build.VERSION.SDK_INT <= 32 &&
                    ContextCompat.checkSelfPermission(app, Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    return@ToolDefinition ToolResult(false, "Files permission hasn't been granted.")
                }
                withContext(Dispatchers.IO) {
                    val results = mutableListOf<String>()
                    runCatching {
                        app.contentResolver.query(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.DATE_MODIFIED),
                            "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?", arrayOf("%$query%"),
                            "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                        )?.use { cursor ->
                            while (cursor.moveToNext() && results.size < 10) results += cursor.getString(0)
                        }
                    }
                    ToolResult(true, if (results.isEmpty()) "No files in Downloads matched \"$query\"." else results.joinToString("\n") { "- $it" })
                }
            }
        ),
        ToolDefinition(
            name = "current_location",
            description = "Get the device's last known approximate location (latitude/longitude only, no address).",
            paramNames = emptyList(),
            risk = ToolRisk.READ_ONLY,
            execute = { app, _ ->
                gatedResult(app, app.container.settingsRepository.locationToolEnabled, Manifest.permission.ACCESS_COARSE_LOCATION, "Location") {
                    withContext(Dispatchers.IO) {
                        // Recheck at the platform call site as the permission can be revoked after
                        // gatedResult's initial check. runCatching below handles the remaining race.
                        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            "Location permission hasn't been granted."
                        } else {
                            val lm = app.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
                            @android.annotation.SuppressLint("MissingPermission") // Checked above; runCatching handles revocation races.
                            val location = runCatching {
                                lm.getProviders(true).asSequence()
                                    .mapNotNull { provider -> lm.getLastKnownLocation(provider) }
                                    .maxByOrNull { it.time }
                            }.getOrNull()
                            if (location == null) "No recent location available."
                            else "Latitude ${"%.4f".format(location.latitude)}, longitude ${"%.4f".format(location.longitude)} " +
                                "(as of ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(location.time))})."
                        }
                    }
                }
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
        ToolDefinition(
            name = "open_app",
            description = "Launch another installed app by name.",
            paramNames = listOf("name"),
            risk = ToolRisk.EXTERNAL_ACTION,
            execute = { app, params ->
                val name = params.optString("name")
                if (name.isBlank()) return@ToolDefinition ToolResult(false, "open_app needs a non-empty 'name'")
                withContext(Dispatchers.IO) {
                    val pm = app.packageManager
                    val match = pm.getInstalledApplications(android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
                        .firstOrNull { pm.getApplicationLabel(it).toString().contains(name, true) }
                    val launchIntent = match?.let { pm.getLaunchIntentForPackage(it.packageName) }
                    if (launchIntent == null) ToolResult(false, "No installed app matched \"$name\".")
                    else launch(app, launchIntent, pm.getApplicationLabel(match).toString())
                }
            }
        ),
        ToolDefinition(
            name = "screen_time_summary",
            description = "Summarize today's on-device screen time by app.",
            paramNames = emptyList(),
            risk = ToolRisk.READ_ONLY,
            execute = { app, _ ->
                if (!app.container.settingsRepository.screenTimeToolEnabled.first()) {
                    return@ToolDefinition ToolResult(false, "Screen time is disabled in Settings → Security.")
                }
                val appOps = app.getSystemService(android.content.Context.APP_OPS_SERVICE) as AppOpsManager
                val hasAccess = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName) == AppOpsManager.MODE_ALLOWED
                if (!hasAccess) return@ToolDefinition ToolResult(false, "Usage-access permission hasn't been granted.")
                withContext(Dispatchers.IO) {
                    val usm = app.getSystemService(android.content.Context.USAGE_STATS_SERVICE) as UsageStatsManager
                    val cal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
                    }
                    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
                    val pm = app.packageManager
                    val top = stats.filter { it.totalTimeInForeground > 0 }
                        .sortedByDescending { it.totalTimeInForeground }
                        .take(8)
                        .map { stat ->
                            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString() }.getOrDefault(stat.packageName)
                            "$label: ${stat.totalTimeInForeground / 60_000} min"
                        }
                    ToolResult(true, if (top.isEmpty()) "No usage recorded yet today." else top.joinToString("\n") { "- $it" })
                }
            }
        ),
        ToolDefinition(
            name = "log_expense",
            description = "Log an expense to the running expense ledger.",
            paramNames = listOf("merchant", "amount", "currency", "category", "paymentMethod"),
            risk = ToolRisk.REVERSIBLE_WRITE,
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
        ToolResult(false, "No app can handle $label: ${e.toUserMessage()}")
    }
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
