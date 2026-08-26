@file:Suppress("LocalContextGetResourceValueCall")

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vervan.chat.VervanApp
import com.vervan.chat.data.db.entities.MessageRole
import com.vervan.chat.data.db.entities.traits
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.SectionCard
import com.vervan.chat.ui.common.SectionRow
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.common.rememberThumbnail
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatInfoScreen(chatId: String, onBack: () -> Unit, onOpenDocument: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VervanApp
    val vm: ChatInfoViewModel = viewModel(factory = viewModelFactory {
        initializer { ChatInfoViewModel(app, chatId) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val chat = state.chat
    val messages = state.messages
    val documents = state.documents
    val personas = state.personas
    val models = state.models
    val workspaces = state.workspaces
    val knowledgeBases = state.knowledgeBases
    val activeModel = state.activeModel
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val loadError by vm.error.collectAsStateWithLifecycle()
    val imagePaths = remember(messages) { messages.mapNotNull { it.imagePath }.distinct() }
    val sharedDocumentIds = remember(messages) { messages.mapNotNull { it.documentId }.toSet() }
    val sharedDocuments = remember(documents, sharedDocumentIds) { documents.filter { it.id in sharedDocumentIds } }
    // URL.findAll over every message body is O(total characters) — memoize so an unrelated
    // recomposition (e.g. tapping an image to set previewPath) doesn't re-scan the whole
    // transcript. Same reasoning for the word counts below, which used to compile a fresh
    // Regex("\\s+") per message and run twice (user + assistant) on each recomposition.
    val links = remember(messages) {
        messages.flatMap { message ->
            URL.findAll(visibleMessageText(message.content, message.role == MessageRole.USER))
                .map { it.value }
                .toList()
        }.distinct()
    }

    // Conversation stats (WhatsApp-info-style counters) — computed from the visible turns only.
    val visible = remember(messages) { messages.filter { it.role != MessageRole.SYSTEM } }
    val userCount = visible.count { it.role == MessageRole.USER }
    val aiCount = visible.count { it.role == MessageRole.ASSISTANT }
    val (userWords, aiWords) = remember(visible) {
        val whitespace = Regex("\\s+")
        fun words(role: MessageRole) = visible.filter { it.role == role }.sumOf { message ->
            visibleMessageText(message.content, role == MessageRole.USER)
                .split(whitespace)
                .count { it.isNotBlank() }
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
    val persona = (explicitPersona ?: workspacePersona)?.name ?: stringResource(com.vervan.chat.R.string.persona_unavailable)
    val model = (models.find { it.id == chat?.modelId } ?: activeModel)?.displayName ?: stringResource(com.vervan.chat.R.string.home_no_generation_model)
    val selectedModel = models.find { it.id == chat?.modelId } ?: activeModel
    val modelRunsOnDevice = selectedModel?.traits?.runsOnDevice != false
    val latestResponseModel = visible.lastOrNull { it.role == MessageRole.ASSISTANT && it.modelName != null }?.modelName
    val sourceNames = chat?.kbIdList().orEmpty().mapNotNull { id -> knowledgeBases.find { it.id == id }?.name }

    var previewPath by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.vervan.chat.R.string.chat_info_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.vervan.chat.R.string.action_back)) } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
            when {
                loadError != null -> OperationErrorCard(
                    title = stringResource(com.vervan.chat.R.string.chat_info_unavailable),
                    message = loadError.orEmpty(),
                    recovery = stringResource(com.vervan.chat.R.string.chat_info_recovery),
                    actionLabel = stringResource(com.vervan.chat.R.string.action_retry),
                    onAction = vm::retry,
                    modifier = Modifier.padding(Space.md)
                )
                isLoading -> LoadingSkeletonList(rows = 8, modifier = Modifier.padding(Space.md))
                chat == null -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = stringResource(com.vervan.chat.R.string.chat_info_not_found),
                    body = stringResource(com.vervan.chat.R.string.chat_info_not_found_body),
                    modifier = Modifier.fillMaxSize(),
                    centered = true,
                    actionLabel = stringResource(com.vervan.chat.R.string.action_back),
                    onAction = onBack
                )
                else -> LazyColumn(
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
                            chat?.title ?: stringResource(com.vervan.chat.R.string.chat_info_default_chat),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        val subtitle = stringResource(com.vervan.chat.R.string.ui_chatinfo_persona_model, persona, latestResponseModel ?: model)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        // Status pills — only the ones that actually apply, mirroring the chat's
                        // own header language ("Private · on device", Incognito, Pinned, Archived).
                        // FlowRow so 3-4 pills wrap onto a second line on narrow devices instead of
                        // being squeezed or clipped.
                        FlowRow(
                            Modifier.fillMaxWidth().padding(top = Space.xs),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(Space.xs)
                        ) {
                            StatusPill(
                                when {
                                    selectedModel == null -> stringResource(com.vervan.chat.R.string.home_no_generation_model)
                                    modelRunsOnDevice -> stringResource(com.vervan.chat.R.string.chat_info_private_on_device)
                                    else -> stringResource(com.vervan.chat.R.string.chat_info_remote_data_warning)
                                },
                                if (modelRunsOnDevice) Icons.Filled.Lock else Icons.Filled.LockOpen
                            )
                            if (chat?.isTemporary == true) StatusPill(stringResource(com.vervan.chat.R.string.chat_info_incognito), Icons.Filled.VisibilityOff)
                            if (chat?.pinned == true) StatusPill(stringResource(com.vervan.chat.R.string.chat_filter_pinned), Icons.Filled.PushPin)
                            if (chat?.archived == true) StatusPill(stringResource(com.vervan.chat.R.string.chat_filter_archived), Icons.Outlined.Inventory2)
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
                            StatCell(Icons.AutoMirrored.Filled.Chat, visible.size.toString(), stringResource(com.vervan.chat.R.string.chat_info_messages))
                            StatCell(Icons.Filled.Description, wordCount.toString(), stringResource(com.vervan.chat.R.string.chat_info_words))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            StatCell(Icons.Filled.AttachFile, attachmentCount.toString(), stringResource(com.vervan.chat.R.string.chat_info_attachments))
                            StatCell(Icons.Filled.Bolt, compactNumber(generatedTokens), stringResource(com.vervan.chat.R.string.chat_info_ai_tokens))
                        }
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        SectionLabel(stringResource(com.vervan.chat.R.string.chat_info_conversation_insights))
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
                        SectionLabel(stringResource(com.vervan.chat.R.string.chat_info_last_seven_days))
                        ActivityChart(activity)
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        SectionLabel(stringResource(com.vervan.chat.R.string.chat_info_shared_items))
                        AttachmentOverview(imagePaths.size, sharedDocuments.size, audioCount, links.size)
                    }
                }

                // ── Configuration ─────────────────────────────────────────────
                item {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        SectionLabel(stringResource(com.vervan.chat.R.string.chat_info_configuration))
                        SectionCard(
                            items = listOf<@Composable () -> Unit>(
                                {
                                    SectionRow(
                                        icon = Icons.Filled.Dashboard,
                                        title = stringResource(com.vervan.chat.R.string.workspace_name),
                                        subtitle = workspace?.name ?: stringResource(com.vervan.chat.R.string.chat_info_workspace_unavailable)
                                    )
                                },
                                {
                                    SectionRow(
                                        icon = Icons.Filled.Bolt,
                                        title = stringResource(com.vervan.chat.R.string.chat_info_latest_response_model),
                                        subtitle = latestResponseModel ?: stringResource(com.vervan.chat.R.string.chat_info_no_response)
                                    )
                                },
                                {
                                    SectionRow(
                                        icon = Icons.Filled.AutoAwesome,
                                        title = stringResource(com.vervan.chat.R.string.chat_info_model_setting),
                                        subtitle = if (chat?.modelId != null) stringResource(com.vervan.chat.R.string.chat_info_selected_for_chat, model) else stringResource(com.vervan.chat.R.string.chat_info_app_default, model)
                                    )
                                },
                                {
                                    SectionRow(
                                        icon = Icons.Filled.Psychology,
                                        title = stringResource(com.vervan.chat.R.string.chat_persona),
                                        subtitle = if (chat?.personaId != null) stringResource(com.vervan.chat.R.string.chat_info_persona_selected, persona)
                                        else stringResource(com.vervan.chat.R.string.chat_info_persona_inherited, persona, workspace?.name ?: stringResource(com.vervan.chat.R.string.workspace_one_space))
                                    )
                                },
                                {
                                    SectionRow(
                                        icon = Icons.Filled.Bolt,
                                        title = stringResource(com.vervan.chat.R.string.chat_info_response_profile),
                                        subtitle = (chat?.profile ?: "BALANCED").lowercase().replaceFirstChar { it.uppercase() }
                                    )
                                },
                                {
                                    val chatModel = models.find { it.id == chat?.modelId } ?: activeModel
                                    val mode = com.vervan.chat.llm.ThinkingPolicy.effectiveThinkingMode(
                                        chat?.thinkingMode, chatModel?.defaultThinkingMode, chatModel?.supportsThinking
                                    )
                                    val modeLabel = mode.lowercase().replaceFirstChar { it.uppercase() }
                                    SectionRow(
                                        icon = Icons.Filled.Psychology,
                                        title = stringResource(com.vervan.chat.R.string.chat_info_thinking),
                                        subtitle = if (chat?.thinkingMode == null) stringResource(com.vervan.chat.R.string.chat_info_model_default, modeLabel) else modeLabel
                                    )
                                },
                                {
                                    val count = chat?.kbIdList()?.size ?: 0
                                    SectionRow(
                                        icon = Icons.AutoMirrored.Filled.MenuBook,
                                        title = stringResource(com.vervan.chat.R.string.chat_sources),
                                        subtitle = if (chat?.sourceGrounded == true && count > 0)
                                            sourceNames.takeIf { it.isNotEmpty() }?.joinToString(", ")
                                                ?: stringResource(com.vervan.chat.R.string.chat_info_source_count, count, if (count == 1) "" else "s")
                                        else stringResource(com.vervan.chat.R.string.chat_info_not_grounded)
                                    )
                                }
                            )
                        )
                    }
                }

                // ── Timeline ──────────────────────────────────────────────────
                item {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        SectionLabel(stringResource(com.vervan.chat.R.string.chat_info_timeline))
                        SectionCard(
                            items = listOf<@Composable () -> Unit>(
                                {
                                    SectionRow(
                                        icon = Icons.Filled.CalendarToday,
                                        title = stringResource(com.vervan.chat.R.string.chat_info_created),
                                        subtitle = chat?.createdAt?.let { dateFmt.format(java.util.Date(it)) } ?: "—"
                                    )
                                },
                                {
                                    SectionRow(
                                        icon = Icons.Filled.Schedule,
                                        title = stringResource(com.vervan.chat.R.string.chat_info_last_activity),
                                        subtitle = lastActivity?.let { dateFmt.format(java.util.Date(it)) } ?: stringResource(com.vervan.chat.R.string.chat_info_no_messages)
                                    )
                                },
                                {
                                    SectionRow(
                                        icon = Icons.Filled.Bolt,
                                        title = stringResource(com.vervan.chat.R.string.chat_info_local_generation),
                                        subtitle = if (generatedReplies.isEmpty()) stringResource(com.vervan.chat.R.string.chat_info_no_measured_replies) else
                                            stringResource(com.vervan.chat.R.string.chat_info_replies_total, generatedReplies.size, formatDuration(generationMs))
                                    )
                                }
                            )
                        )
                    }
                }

                // ── Shared media ──────────────────────────────────────────────
                item { SectionLabel(stringResource(com.vervan.chat.R.string.chat_info_shared_media, imagePaths.size), Modifier.padding(horizontal = Space.lg)) }
                if (imagePaths.isEmpty()) item { EmptyLine(stringResource(com.vervan.chat.R.string.chat_info_no_shared_images)) }
                // WhatsApp-style grid of small thumbnails; tap opens the in-app preview.
                items(imagePaths.chunked(3), key = { it.first() }) { rowPaths ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.lg),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        rowPaths.forEach { path ->
                            val bitmap = rememberThumbnail(path, 240)
                            Card(
                                Modifier.size(108.dp).clickable { previewPath = path },
                                colors = SurfaceRole.Raised.cardColors(),
                                border = SurfaceRole.Raised.border()
                            ) {
                                if (bitmap != null) {
                                    Image(bitmap, stringResource(com.vervan.chat.R.string.chat_shared_image), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Filled.Image, stringResource(com.vervan.chat.R.string.chat_shared_image), Modifier.fillMaxSize().padding(Space.xxl))
                                }
                            }
                        }
                    }
                }

                // ── Documents ─────────────────────────────────────────────────
                item { SectionLabel(stringResource(com.vervan.chat.R.string.chat_info_documents, sharedDocuments.size), Modifier.padding(horizontal = Space.lg)) }
                if (sharedDocuments.isEmpty()) item { EmptyLine(stringResource(com.vervan.chat.R.string.chat_info_no_shared_documents)) }
                items(sharedDocuments, key = { it.id }) { doc ->
                    MediaListRow(
                        icon = Icons.Filled.Description,
                        title = doc.displayName,
                        subtitle = stringResource(com.vervan.chat.R.string.ui_chatinfo_document_status, doc.mimeType, doc.status.name.lowercase()),
                        onClick = { onOpenDocument(doc.id) }
                    )
                }

                // ── Shared links ──────────────────────────────────────────────
                item { SectionLabel(stringResource(com.vervan.chat.R.string.chat_info_shared_links, links.size), Modifier.padding(horizontal = Space.lg)) }
                if (links.isEmpty()) item { EmptyLine(stringResource(com.vervan.chat.R.string.chat_info_no_shared_links)) }
                items(links, key = { it }) { link ->
                    MediaListRow(
                        icon = Icons.Filled.Link,
                        title = link,
                        subtitle = null,
                        onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                                .onFailure {
                                    android.widget.Toast.makeText(context, context.getString(com.vervan.chat.R.string.chat_info_no_app_for_link), android.widget.Toast.LENGTH_LONG).show()
                                }
                        }
                    )
                }
                }
            }
        }
    }
    previewPath?.let { path ->
        FullScreenImagePreview(path = path, title = stringResource(com.vervan.chat.R.string.chat_shared_image), onDismiss = { previewPath = null })
    }
}

/** A single accent-iconed big-number / small-label counter cell in the stat grid. */
@Composable
private fun RowScope.StatCell(icon: ImageVector, value: String, label: String) {
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
            IconAffordance(
                icon = icon,
                size = IconAffordanceSize.Default,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
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
    n < 1_000_000 && n % 1_000 == 0 -> "%dk".format(java.util.Locale.getDefault(), n / 1_000)
    n < 1_000_000 -> "%.1fk".format(java.util.Locale.getDefault(), n / 1_000f)
    n % 1_000_000 == 0 -> "%dM".format(java.util.Locale.getDefault(), n / 1_000_000)
    else -> "%.1fM".format(java.util.Locale.getDefault(), n / 1_000_000f)
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
            BalanceRow(stringResource(com.vervan.chat.R.string.chat_info_message_balance), userMessages, assistantMessages)
            BalanceRow(stringResource(com.vervan.chat.R.string.chat_info_word_balance), userWords, assistantWords)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                InsightMetric(if (generatedReplies == 0) "—" else formatDuration(averageReplyMs), stringResource(com.vervan.chat.R.string.chat_info_avg_reply), Modifier.weight(1f))
                InsightMetric(if (tokensPerSecond <= 0f) "—" else String.format(java.util.Locale.getDefault(), "%.1f/s", tokensPerSecond), stringResource(com.vervan.chat.R.string.chat_info_token_speed), Modifier.weight(1f))
                InsightMetric(interrupted.toString(), stringResource(com.vervan.chat.R.string.chat_info_interrupted), Modifier.weight(1f))
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                stringResource(com.vervan.chat.R.string.chat_info_you_ai_count, user, assistant),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
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
                val gap = Space.sm.toPx()
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
                stringResource(com.vervan.chat.R.string.chat_info_week_messages, points.sumOf { it.count }),
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
                AttachmentMetric(Icons.Filled.Image, images, stringResource(com.vervan.chat.R.string.chat_info_images), colors[0], Modifier.weight(1f))
                AttachmentMetric(Icons.Filled.Description, documents, stringResource(com.vervan.chat.R.string.chat_info_docs), colors[1], Modifier.weight(1f))
                AttachmentMetric(Icons.Filled.GraphicEq, audio, stringResource(com.vervan.chat.R.string.chat_info_audio), colors[2], Modifier.weight(1f))
                AttachmentMetric(Icons.Filled.Link, links, stringResource(com.vervan.chat.R.string.chat_info_links), colors[3], Modifier.weight(1f))
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
    milliseconds < 60_000L -> String.format(java.util.Locale.getDefault(), "%.1fs", milliseconds / 1000f)
    else -> "${milliseconds / 60_000}m ${milliseconds / 1000 % 60}s"
}

/** Compact status chip used in the hero header. */
@Composable
private fun StatusPill(text: String, icon: ImageVector) {
    Surface(
        shape = MaterialTheme.shapes.small,
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

private val URL = Regex("""https?://[^\s<>\"]+""", RegexOption.IGNORE_CASE)
