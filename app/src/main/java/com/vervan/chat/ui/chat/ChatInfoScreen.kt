package com.vervan.chat.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.ModelRole
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SectionCard
import com.vervan.chat.ui.common.SectionRow
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.vervanAccentFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(chatId: String, onBack: () -> Unit, onOpenDocument: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val chat by app.container.db.chatDao().observeChat(chatId).collectAsState(initial = null)
    val messages by app.container.db.messageDao().observeMessages(chatId).collectAsState(initial = emptyList())
    val documents by app.container.db.documentDao().observeAll().collectAsState(initial = emptyList())
    val personas by app.container.db.personaDao().observePersonas().collectAsState(initial = emptyList())
    val models by app.container.db.modelDao().observeModels().collectAsState(initial = emptyList())
    val workspaces by app.container.db.workspaceDao().observeAll().collectAsState(initial = emptyList())
    val knowledgeBases by app.container.db.knowledgeBaseDao().observeAll().collectAsState(initial = emptyList())
    val activeModel by app.container.db.modelDao().observeActiveModel(ModelRole.GENERATION).collectAsState(initial = null)
    val imagePaths = remember(messages) { messages.mapNotNull { it.imagePath }.distinct() }
    val sharedDocumentIds = remember(messages) { messages.mapNotNull { it.documentId }.toSet() }
    val sharedDocuments = remember(documents, sharedDocumentIds) { documents.filter { it.id in sharedDocumentIds } }
    // URL.findAll over every message body is O(total characters) — memoize so an unrelated
    // recomposition (e.g. tapping an image to set previewPath) doesn't re-scan the whole
    // transcript. Same reasoning for the word counts below, which used to compile a fresh
    // Regex("\\s+") per message and run twice (user + assistant) on each recomposition.
    val links = remember(messages) { messages.flatMap { message -> URL.findAll(message.content).map { it.value }.toList() }.distinct() }

    // Conversation stats (WhatsApp-info-style counters) — computed from the visible turns only.
    val visible = remember(messages) { messages.filter { it.role != MessageRole.SYSTEM } }
    val userCount = visible.count { it.role == MessageRole.USER }
    val aiCount = visible.count { it.role == MessageRole.ASSISTANT }
    val (userWords, aiWords) = remember(visible) {
        val whitespace = Regex("\\s+")
        fun words(role: MessageRole) = visible.filter { it.role == role }.sumOf { message ->
            message.content.split(whitespace).count { it.isNotBlank() }
        }
        words(MessageRole.USER) to words(MessageRole.ASSISTANT)
    }
    val wordCount = userWords + aiWords
    val audioCount = messages.count { it.audioPath != null }
    val attachmentCount = imagePaths.size + sharedDocuments.size + audioCount
    val generatedReplies = remember(visible) { visible.filter { it.role == MessageRole.ASSISTANT && it.generationMs != null } }
    val generationMs = generatedReplies.sumOf { it.generationMs ?: 0L }
    val generatedTokens = generatedReplies.sumOf { it.tokenCount ?: 0 }
    val averageReplyMs = if (generatedReplies.isNotEmpty()) generationMs / generatedReplies.size else 0L
    val tokensPerSecond = if (generationMs > 0) generatedTokens / (generationMs / 1000f) else 0f
    val interruptedCount = visible.count { it.state != com.vervan.chat.data.db.entities.MessageState.COMPLETE }
    val activity = remember(visible) { sevenDayActivity(visible.map { it.createdAt }) }
    val dateFmt = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
    val lastActivity = visible.maxOfOrNull { it.createdAt }

    val workspace = workspaces.find { it.id == chat?.workspaceId }
    val explicitPersona = personas.find { it.id == chat?.personaId }
    val workspacePersona = personas.find { it.id == workspace?.personaId }
    val persona = (explicitPersona ?: workspacePersona)?.name ?: "Persona unavailable"
    val model = (models.find { it.id == chat?.modelId } ?: activeModel)?.displayName ?: "No generation model"
    val latestResponseModel = visible.lastOrNull { it.role == MessageRole.ASSISTANT && it.modelName != null }?.modelName
    val sourceNames = chat?.kbIdList().orEmpty().mapNotNull { id -> knowledgeBases.find { it.id == id }?.name }

    var previewPath by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat info") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.lg)
            ) {
                // ── Hero header ───────────────────────────────────────────────
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        // Brand-gradient identity mark — the same Aurora gradient the nav dock and
                        // chat avatar carry, so the info screen reads as part of the product rather
                        // than a flat settings page.
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(com.vervan.chat.ui.theme.vervanBrandGradient()),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Text(
                            chat?.title ?: "Chat",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        val subtitle = "$persona · ${latestResponseModel ?: model}"
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        // Status pills — only the ones that actually apply, mirroring the chat's
                        // own header language ("Private · on device", Incognito, Pinned, Archived).
                        Row(
                            Modifier.fillMaxWidth().padding(top = Space.xs),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill("Private · on device", Icons.Filled.Lock)
                            if (chat?.isTemporary == true) StatusPill("Incognito", Icons.Filled.VisibilityOff)
                            if (chat?.pinned == true) StatusPill("Pinned", Icons.Filled.PushPin)
                            if (chat?.archived == true) StatusPill("Archived", Icons.Outlined.Inventory2)
                        }
                    }
                }

                // ── Stat grid ─────────────────────────────────────────────────
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = Space.lg),
                        verticalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            StatCell(Icons.AutoMirrored.Filled.Chat, visible.size.toString(), "Messages", vervanAccentFor(1))
                            StatCell(Icons.Filled.Description, wordCount.toString(), "Words", vervanAccentFor(2))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            StatCell(Icons.Filled.AttachFile, attachmentCount.toString(), "Attachments", vervanAccentFor(3))
                            StatCell(Icons.Filled.Bolt, compactNumber(generatedTokens), "AI tokens", vervanAccentFor(0))
                        }
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        SectionLabel("Conversation insights")
                        ConversationInsightsCard(
                            userMessages = userCount,
                            assistantMessages = aiCount,
                            userWords = userWords,
                            assistantWords = aiWords,
                            averageReplyMs = averageReplyMs,
                            tokensPerSecond = tokensPerSecond,
                            generatedReplies = generatedReplies.size,
                            interrupted = interruptedCount
                        )
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        SectionLabel("Last 7 days")
                        ActivityChart(activity)
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        SectionLabel("Shared items")
                        AttachmentOverview(imagePaths.size, sharedDocuments.size, audioCount, links.size)
                    }
                }

                // ── Configuration ─────────────────────────────────────────────
                item {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        SectionLabel("Configuration")
                        SectionCard(
                            items = listOf<@Composable () -> Unit>(
                                {
                                    SectionRow(
                                        icon = Icons.Filled.Dashboard,
                                        title = "Workspace",
                                        subtitle = workspace?.name ?: "Workspace unavailable"
                                    )
                                },
                                {
                                    SectionRow(
                                        icon = Icons.Filled.Bolt,
                                        title = "Latest response model",
                                        subtitle = latestResponseModel ?: "No generated response yet"
                                    )
                                },
                                {
                                    SectionRow(
                                        icon = Icons.Filled.AutoAwesome,
                                        title = "Model setting",
                                        subtitle = if (chat?.modelId != null) "$model · selected for this chat" else "$model · app default"
                                    )
                                },
                                {
                                    SectionRow(
                                        icon = Icons.Filled.Psychology,
                                        title = "Persona",
                                        subtitle = if (chat?.personaId != null) "$persona · selected for this chat"
                                        else "$persona · inherited from ${workspace?.name ?: "space"}"
                                    )
                                },
                                {
                                    SectionRow(
                                        icon = Icons.Filled.Bolt,
                                        title = "Response profile",
                                        subtitle = (chat?.profile ?: "BALANCED").lowercase().replaceFirstChar { it.uppercase() }
                                    )
                                },
                                {
                                    val mode = chat?.thinkingMode ?: "OFF"
                                    SectionRow(
                                        icon = Icons.Filled.Psychology,
                                        title = "Thinking",
                                        subtitle = if (mode == "OFF") "Off" else mode.lowercase().replaceFirstChar { it.uppercase() }
                                    )
                                },
                                {
                                    val count = chat?.kbIdList()?.size ?: 0
                                    SectionRow(
                                        icon = Icons.AutoMirrored.Filled.MenuBook,
                                        title = "Sources",
                                        subtitle = if (chat?.sourceGrounded == true && count > 0)
                                            sourceNames.takeIf { it.isNotEmpty() }?.joinToString(", ")
                                                ?: "$count source${if (count == 1) "" else "s"} · grounded"
                                        else "Not grounded"
                                    )
                                }
                            )
                        )
                    }
                }

                // ── Timeline ──────────────────────────────────────────────────
                item {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        SectionLabel("Timeline")
                        SectionCard(
                            items = listOf<@Composable () -> Unit>(
                                {
                                    SectionRow(
                                        icon = Icons.Filled.CalendarToday,
                                        title = "Created",
                                        subtitle = chat?.createdAt?.let { dateFmt.format(java.util.Date(it)) } ?: "—"
                                    )
                                },
                                {
                                    SectionRow(
                                        icon = Icons.Filled.Schedule,
                                        title = "Last activity",
                                        subtitle = lastActivity?.let { dateFmt.format(java.util.Date(it)) } ?: "No messages yet"
                                    )
                                },
                                {
                                    SectionRow(
                                        icon = Icons.Filled.Bolt,
                                        title = "Local generation",
                                        subtitle = if (generatedReplies.isEmpty()) "No measured replies yet" else
                                            "${generatedReplies.size} replies · ${formatDuration(generationMs)} total"
                                    )
                                }
                            )
                        )
                    }
                }

                // ── Shared media ──────────────────────────────────────────────
                item { SectionLabel("Shared media · ${imagePaths.size}", Modifier.padding(horizontal = Space.lg)) }
                if (imagePaths.isEmpty()) item { EmptyLine("No shared images") }
                // WhatsApp-style grid of small thumbnails; tap opens the in-app preview.
                items(imagePaths.chunked(3), key = { it.first() }) { rowPaths ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.lg),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        rowPaths.forEach { path ->
                            val bitmap = com.vervan.chat.model.ImageUtils.decodeThumbnail(path, 240)?.asImageBitmap()
                            Card(
                                Modifier.size(108.dp).clickable { previewPath = path },
                                colors = SurfaceRole.Raised.cardColors(),
                                border = SurfaceRole.Raised.border()
                            ) {
                                if (bitmap != null) {
                                    Image(bitmap, "Shared image", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Filled.Image, "Shared image", Modifier.fillMaxSize().padding(28.dp))
                                }
                            }
                        }
                    }
                }

                // ── Documents ─────────────────────────────────────────────────
                item { SectionLabel("Documents · ${sharedDocuments.size}", Modifier.padding(horizontal = Space.lg)) }
                if (sharedDocuments.isEmpty()) item { EmptyLine("No shared documents") }
                items(sharedDocuments, key = { it.id }) { doc ->
                    MediaListRow(
                        icon = Icons.Filled.Description,
                        title = doc.displayName,
                        subtitle = "${doc.mimeType} · ${doc.status.name.lowercase()}",
                        onClick = { onOpenDocument(doc.id) }
                    )
                }

                // ── Shared links ──────────────────────────────────────────────
                item { SectionLabel("Shared links · ${links.size}", Modifier.padding(horizontal = Space.lg)) }
                if (links.isEmpty()) item { EmptyLine("No shared links") }
                items(links, key = { it }) { link ->
                    MediaListRow(
                        icon = Icons.Filled.Link,
                        title = link,
                        subtitle = null,
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } }
                    )
                }
            }
        }
    }
    previewPath?.let { path ->
        FullScreenImagePreview(path = path, title = "Shared image", onDismiss = { previewPath = null })
    }
}

/** A single accent-iconed big-number / small-label counter cell in the stat grid. */
@Composable
private fun RowScope.StatCell(icon: ImageVector, value: String, label: String, accent: com.vervan.chat.ui.theme.VervanAccent) {
    Card(
        Modifier.weight(1f),
        colors = SurfaceRole.Raised.cardColors(),
        border = SurfaceRole.Raised.border()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Box(
                Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(accent.container),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent.onContainer, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** Large counts read better abbreviated ("12.4k") in a compact stat cell. */
private fun compactNumber(n: Int): String = when {
    n < 1000 -> n.toString()
    n < 1_000_000 -> "%.1fk".format(n / 1000f).replace(".0k", "k")
    else -> "%.1fM".format(n / 1_000_000f).replace(".0M", "M")
}

@Composable
private fun ConversationInsightsCard(
    userMessages: Int,
    assistantMessages: Int,
    userWords: Int,
    assistantWords: Int,
    averageReplyMs: Long,
    tokensPerSecond: Float,
    generatedReplies: Int,
    interrupted: Int
) {
    Card(Modifier.fillMaxWidth(), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            BalanceRow("Message balance", userMessages, assistantMessages)
            BalanceRow("Word balance", userWords, assistantWords)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                InsightMetric(if (generatedReplies == 0) "—" else formatDuration(averageReplyMs), "Avg. reply", Modifier.weight(1f))
                InsightMetric(if (tokensPerSecond <= 0f) "—" else String.format("%.1f/s", tokensPerSecond), "Token speed", Modifier.weight(1f))
                InsightMetric(interrupted.toString(), "Interrupted", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BalanceRow(label: String, user: Int, assistant: Int) {
    val total = (user + assistant).coerceAtLeast(1)
    val userFraction = user.toFloat() / total
    val userColor = MaterialTheme.colorScheme.tertiary
    val assistantColor = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("You $user · AI $assistant", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(assistantColor, size = size, cornerRadius = radius)
            drawRoundRect(userColor, size = androidx.compose.ui.geometry.Size(size.width * userFraction, size.height), cornerRadius = radius)
        }
    }
}

@Composable
private fun InsightMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

private data class ActivityPoint(val label: String, val count: Int)

@Composable
private fun ActivityChart(points: List<ActivityPoint>) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = points.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    Card(Modifier.fillMaxWidth(), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
        Column(Modifier.padding(Space.lg)) {
            Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                val gap = 8.dp.toPx()
                val barWidth = (size.width - gap * (points.size - 1)) / points.size.coerceAtLeast(1)
                points.forEachIndexed { index, point ->
                    val x = index * (barWidth + gap)
                    val height = if (point.count == 0) 4.dp.toPx() else size.height * point.count / max
                    drawRoundRect(
                        trackColor,
                        topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 3)
                    )
                    drawRoundRect(
                        barColor,
                        topLeft = androidx.compose.ui.geometry.Offset(x, size.height - height),
                        size = androidx.compose.ui.geometry.Size(barWidth, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 3)
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.SpaceBetween) {
                points.forEach { Text(it.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(
                "${points.sumOf { it.count }} messages this week",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.sm)
            )
        }
    }
}

@Composable
private fun AttachmentOverview(images: Int, documents: Int, audio: Int, links: Int) {
    val values = listOf(images, documents, audio, links)
    val total = values.sum().coerceAtLeast(1)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.outline
    )
    Card(Modifier.fillMaxWidth(), colors = SurfaceRole.Card.cardColors(), border = SurfaceRole.Card.border()) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                var x = 0f
                values.forEachIndexed { index, value ->
                    val width = size.width * value / total
                    drawRect(colors[index], topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(width, size.height))
                    x += width
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                AttachmentMetric(Icons.Filled.Image, images, "Images", colors[0], Modifier.weight(1f))
                AttachmentMetric(Icons.Filled.Description, documents, "Docs", colors[1], Modifier.weight(1f))
                AttachmentMetric(Icons.Filled.GraphicEq, audio, "Audio", colors[2], Modifier.weight(1f))
                AttachmentMetric(Icons.Filled.Link, links, "Links", colors[3], Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AttachmentMetric(icon: ImageVector, value: Int, label: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(value.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

private fun sevenDayActivity(timestamps: List<Long>): List<ActivityPoint> {
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val todayStart = calendar.timeInMillis
    val dayMs = 86_400_000L
    val labels = java.text.DateFormatSymbols.getInstance().shortWeekdays
    return (6 downTo 0).map { daysAgo ->
        val start = todayStart - daysAgo * dayMs
        calendar.timeInMillis = start
        ActivityPoint(labels[calendar.get(java.util.Calendar.DAY_OF_WEEK)].take(2), timestamps.count { it in start until (start + dayMs) })
    }
}

private fun formatDuration(milliseconds: Long): String = when {
    milliseconds <= 0L -> "—"
    milliseconds < 1_000L -> "${milliseconds}ms"
    milliseconds < 60_000L -> String.format("%.1fs", milliseconds / 1000f)
    else -> "${milliseconds / 60_000}m ${milliseconds / 1000 % 60}s"
}

/** Compact status chip used in the hero header. */
@Composable
private fun StatusPill(text: String, icon: ImageVector) {
    Surface(
        shape = com.vervan.chat.ui.theme.VervanExtraShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** A shared-media/document/link row on a design-system card with an icon affordance — replaces
 *  the bare transparent ListItems the shared sections used before, so they match the rest of the
 *  app's list rows. */
@Composable
private fun MediaListRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs),
        colors = SurfaceRole.Card.cardColors(),
        border = SurfaceRole.Card.border()
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            com.vervan.chat.ui.common.IconAffordance(icon, size = com.vervan.chat.ui.common.IconAffordanceSize.Compact)
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = Space.sm, top = Space.xs)
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)
    )
}

private fun openFile(context: android.content.Context, file: java.io.File, mimeType: String) {
    if (!file.exists()) return
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Open with…")) }
}

private val URL = Regex("""https?://[^\s<>\"]+""", RegexOption.IGNORE_CASE)
