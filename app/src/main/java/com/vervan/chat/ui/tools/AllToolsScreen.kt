package com.vervan.chat.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.vervan.chat.ui.common.VervanIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanFilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.ModernistScreenHeader
import com.vervan.chat.ui.common.ModernistTag
import com.vervan.chat.ui.common.OperationErrorCard
import com.vervan.chat.VervanApp
import com.vervan.chat.R
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.res.stringResource

private data class ToolEntry(
    val icon: ImageVector,
    val label: String,
    val description: String,
    val route: String,
)

private data class ToolCategory(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val entries: List<ToolEntry>,
)

private val categories = listOf(
    ToolCategory(
        "Talk & translate",
        "Speak, listen, translate, and practise real conversations.",
        Icons.Filled.RecordVoiceOver,
        listOf(
            ToolEntry(Icons.Filled.Mic, "Voice chat", "Have a hands-free local conversation.", "tools/voice-chat"),
            ToolEntry(Icons.Filled.Mic, "Transcribe", "Turn a recording or audio/video file into editable text.", "tools/transcribe"),
            ToolEntry(Icons.Filled.RecordVoiceOver, "Text to speech", "Convert text into spoken audio you can export.", "tools/text-to-speech"),
            ToolEntry(Icons.Filled.Translate, "Translate", "Translate typed text or text from a photo.", "tools/translate"),
            ToolEntry(Icons.Filled.Forum, "Live translator", "Take turns speaking across two languages.", "tools/live-translator"),
            ToolEntry(Icons.Filled.RecordVoiceOver, "Pronunciation coach", "Compare a spoken attempt with a target phrase.", "tools/pronunciation-coach"),
            ToolEntry(Icons.Filled.Forum, "Language practice", "Role-play a practical conversation.", "tools/language-practice"),
            ToolEntry(Icons.Filled.Mic, "Interview practice", "Rehearse questions with focused feedback.", "tools/interview-practice"),
            ToolEntry(Icons.Filled.Mic, "Presentation practice", "Prepare and refine a spoken presentation.", "tools/presentation-practice"),
        ),
    ),
    ToolCategory(
        "Write & create",
        "Turn rough ideas into clear notes, messages, and creative drafts.",
        Icons.Filled.NoteAlt,
        listOf(
            ToolEntry(Icons.Filled.AutoAwesome, "Writing assistant", "Rewrite, shorten, expand, or change tone.", "tools/writing-assistant"),
            // The full Writing workspace ("writing" route) had no entry point anywhere in the
            // app — the screen and its ViewModel existed but were unreachable.
            ToolEntry(Icons.Filled.AutoAwesome, "Writing workspace", "Draft long-form text with saved outputs and revisions.", "writing"),
            ToolEntry(Icons.Filled.NoteAlt, "Smart notes", "Clean notes and extract decisions or tasks.", "tools/smart-notes"),
            ToolEntry(Icons.Filled.Description, "Clipboard assistant", "Work with text already on your clipboard.", "tools/clipboard-assistant"),
            ToolEntry(Icons.Filled.School, "Explain for my level", "Adapt an explanation to your experience.", "tools/explain-level"),
            ToolEntry(Icons.Filled.Mail, "Email composer", "Draft a clear response from key points.", "tools/email-composer"),
        ),
    ),
    ToolCategory(
        "Scan & extract",
        "Capture documents and turn visual information into editable content.",
        Icons.Filled.PhotoCamera,
        listOf(
            ToolEntry(Icons.Filled.PhotoCamera, "Document scanner", "Capture multiple pages and export a PDF.", "tools/document-scanner"),
            ToolEntry(Icons.Filled.Image, "OCR scanner", "Extract editable text from an image.", "tools/ocr-scanner"),
            ToolEntry(Icons.Filled.QrCodeScanner, "QR & barcode scanner", "Decode a QR code or barcode from an image.", "tools/qr-scanner"),
            ToolEntry(Icons.Filled.Description, "Receipt scanner", "Extract totals, items, and payment details.", "tools/receipt-scanner"),
            ToolEntry(Icons.AutoMirrored.Filled.Rule, "Table scanner", "Convert a photographed table to structured data.", "tools/table-scanner"),
            ToolEntry(Icons.Filled.Description, "Smart form filler", "Extract the fields you specify from an image.", "tools/smart-form-filler"),
            ToolEntry(Icons.Filled.Image, "Image caption", "Create alt text or a useful visual description.", "tools/image-caption"),
            ToolEntry(Icons.AutoMirrored.Filled.CompareArrows, "Document comparison", "Compare two versions section by section.", "tools/document-comparison"),
            ToolEntry(Icons.AutoMirrored.Filled.MenuBook, "Chat with a file", "Open a chat grounded in one document.", "tools/chat-with-file"),
        ),
    ),
    ToolCategory(
        "Learn & practise",
        "Study actively with questions, feedback, and recall exercises.",
        Icons.Filled.School,
        listOf(
            ToolEntry(Icons.Filled.School, "Study workspace", "Build and review local flashcard decks.", "study"),
            ToolEntry(Icons.Filled.PhotoCamera, "Flashcards from photo", "Snap notes and turn them into a review deck.", "tools/flashcards-photo"),
            ToolEntry(Icons.Filled.School, "Quiz generator", "Create a five-question interactive quiz.", "tools/quiz-generator"),
            ToolEntry(Icons.Filled.Forum, "Socratic tutor", "Reach the answer through guided questions.", "tools/socratic-tutor"),
            ToolEntry(Icons.Filled.School, "Exam preparation", "Organize revision around an exam goal.", "tools/exam-prep"),
            ToolEntry(Icons.AutoMirrored.Filled.Rule, "Homework checker", "Review an answer and identify gaps.", "tools/homework-checker"),
        ),
    ),
    ToolCategory(
        "Plan & decide",
        "Break down goals and turn uncertainty into the next clear step.",
        Icons.Filled.Event,
        listOf(
            ToolEntry(Icons.Filled.Event, "Daily planner", "Turn priorities into a realistic daily plan.", "tools/daily-planner"),
            ToolEntry(Icons.Filled.Insights, "Goal breakdown", "Convert a goal into milestones and actions.", "tools/goal-breakdown"),
            ToolEntry(Icons.AutoMirrored.Filled.CompareArrows, "Decision assistant", "Compare options using explicit criteria.", "tools/decision-assistant"),
            ToolEntry(Icons.AutoMirrored.Filled.Rule, "Smart checklist", "Generate a practical checklist from an outcome.", "tools/smart-checklist"),
        ),
    ),
    ToolCategory(
        "Build & debug",
        "Understand code, logs, patterns, and technical changes.",
        Icons.Filled.Terminal,
        listOf(
            ToolEntry(Icons.Filled.Terminal, "Developer workspace", "Use a focused local code-assistant workspace.", "dev"),
            ToolEntry(Icons.Filled.Terminal, "Code explainer", "Explain unfamiliar code in plain language.", "tools/code-explainer"),
            ToolEntry(Icons.Filled.Terminal, "Regex & SQL helper", "Draft or explain a query or expression.", "tools/regex-sql-helper"),
            ToolEntry(Icons.Filled.Terminal, "JSON & log analyzer", "Find structure, failures, and likely causes.", "tools/json-log-analyzer"),
        ),
    ),
    ToolCategory(
        "Local AI & data",
        "Manage the local knowledge, models, and reusable context behind your work.",
        Icons.Filled.Psychology,
        listOf(
            ToolEntry(Icons.Filled.History, "Run history", "Resume, save, share, or repeat recent tool results.", "tools/runs"),
            ToolEntry(Icons.Filled.AutoAwesome, "Model capabilities", "See what each installed model can use.", "tools/model-dashboard"),
            ToolEntry(Icons.Filled.AccountTree, "Knowledge graph", "See how your chats, notes, documents, and memories connect.", "graph"),
        ),
    ),
)

data class SearchableTool(val label: String, val description: String, val route: String)
internal val searchableTools: List<SearchableTool> = categories.flatMap { category ->
    category.entries.map { SearchableTool(it.label, it.description, it.route) }
}

/** A category-first directory: tools are organized by the job the user is trying to do, not by
 * implementation type or the order features were added. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllToolsScreen(onNavigate: (String) -> Unit, onBack: (() -> Unit)? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as VervanApp
    val vm: AllToolsViewModel = viewModel(factory = viewModelFactory { initializer { AllToolsViewModel(app) } })
    val activeModel by vm.activeModel.collectAsStateWithLifecycle()
    val recentRuns by vm.recentRuns.collectAsStateWithLifecycle()
    val dataError by vm.error.collectAsStateWithLifecycle()
    val legacyPrefs = remember { context.getSharedPreferences("vervan", 0) }
    // Migrate the old raw-SharedPreferences set once, then keep DataStore as the single durable
    // source alongside the rest of the app's user settings.
    val legacyFavorites = remember { legacyPrefs.getStringSet("tool_favorites", emptySet()).orEmpty().toSet() }
    val favorites by app.container.settingsRepository.toolFavorites.collectAsStateWithLifecycle(initialValue = legacyFavorites)
    val persistenceScope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (legacyFavorites.isNotEmpty()) {
            app.container.settingsRepository.setToolFavorites(legacyFavorites)
        }
        legacyPrefs.edit().remove("tool_favorites").apply()
    }
    fun toggleFavorite(route: String) {
        val next = if (route in favorites) favorites - route else favorites + route
        persistenceScope.launch { app.container.settingsRepository.setToolFavorites(next) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedCategories by remember { mutableStateOf(emptySet<String>()) }
    // Pinned tools, in the catalog's own order, shown only in the unfiltered default view so the
    // section doesn't fight an active search or category filter.
    val pinnedEntries = remember(favorites) {
        categories.flatMap { it.entries }.filter { it.route in favorites }
    }
    val showPinned = pinnedEntries.isNotEmpty() && query.isBlank() && selectedCategory == null
    val recentEntries = remember(recentRuns) {
        val byRoute = categories.flatMap { it.entries }.associateBy { it.route }
        recentRuns.mapNotNull { byRoute[it.toolRoute] }.distinctBy { it.route }.take(4)
    }
    val showRecent = recentEntries.isNotEmpty() && query.isBlank() && selectedCategory == null
    fun readiness(entry: ToolEntry): String {
        val noModelNeeded = setOf(
            "tools/document-scanner", "tools/ocr-scanner", "tools/qr-scanner", "tools/chat-with-file", "study",
            "knowledge", "models", "memory", "collections", "tools/runs",
        )
        if (entry.route in noModelNeeded) return "Ready"
        val model = activeModel ?: return "Needs model"
        val needsVision = entry.route in setOf(
            "tools/image-caption", "tools/receipt-scanner", "tools/table-scanner",
            "tools/smart-form-filler", "tools/flashcards-photo",
        )
        return if (needsVision && model.supportsVision == false) "Needs vision model" else "Ready"
    }
    val visibleCategories = remember(query, selectedCategory) {
        categories.mapNotNull { category ->
            val entries = category.entries.filter { entry ->
                (selectedCategory == null || selectedCategory == category.title) &&
                    (query.isBlank() || listOf(entry.label, entry.description, category.title)
                        .any { it.contains(query, ignoreCase = true) })
            }
            category.copy(entries = entries).takeIf { entries.isNotEmpty() }
        }
    }
    val visibleCount = visibleCategories.sumOf { it.entries.size }

    Scaffold(
        // The navigation shell already reserves the bottom navigation and gesture area.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_tools)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.action_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
            LazyVerticalGrid(
                // Tools are a directory, not a dashboard of floating tiles. A single scan-width
                // list keeps the task title primary and makes descriptions readable.
                columns = GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = Space.lg, bottom = Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                item(key = "modernist-header", span = { GridItemSpan(maxLineSpan) }) {
                    ModernistScreenHeader(
                        eyebrow = stringResource(R.string.ui_alltoolsscreen_299_task_directory),
                        title = stringResource(R.string.ui_alltoolsscreen_300_find_a_task),
                        body = stringResource(R.string.ui_alltoolsscreen_301_start_with_the_outcome_you_want_vervan_keeps),
                        trailing = { ModernistTag("$visibleCount TOOLS", active = visibleCount > 0) }
                    )
                }
                item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                    VervanSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.tools_search_placeholder),
                    )
                }
                item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                    val filterListState = rememberLazyListState()
                    val filterScrollScope = rememberCoroutineScope()
                    Box(Modifier.fillMaxWidth()) {
                        LazyRow(
                            state = filterListState,
                            contentPadding = PaddingValues(end = 56.dp),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            item {
                                VervanFilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { selectedCategory = null },
                                    label = { Text(stringResource(R.string.action_all)) },
                                )
                            }
                            items(categories, key = { it.title }) { category ->
                                VervanFilterChip(
                                    selected = selectedCategory == category.title,
                                    onClick = { selectedCategory = category.title },
                                    label = { Text(category.title) },
                                    leadingIcon = if (selectedCategory == category.title) {
                                        { Icon(category.icon, null, Modifier.size(18.dp)) }
                                    } else null,
                                )
                            }
                        }
                        if (filterListState.canScrollForward) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .background(
                                        Brush.horizontalGradient(
                                            0f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                            0.45f to MaterialTheme.colorScheme.surface,
                                        ),
                                    )
                                    .padding(start = Space.lg),
                            ) {
                                IconButton(
                                    onClick = {
                                        filterScrollScope.launch {
                                            val target = (filterListState.firstVisibleItemIndex + 2)
                                                .coerceAtMost(categories.size)
                                            filterListState.animateScrollToItem(target)
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = stringResource(R.string.tools_show_more_categories),
                                    )
                                }
                            }
                        }
                    }
                }

                if (dataError != null) {
                    item(key = "status-error", span = { GridItemSpan(maxLineSpan) }) {
                        OperationErrorCard(
                            title = stringResource(R.string.tools_data_unavailable),
                            message = dataError ?: stringResource(R.string.tools_data_unavailable_message),
                            recovery = stringResource(R.string.tools_data_unavailable_recovery),
                            modifier = Modifier.padding(top = Space.sm),
                            actionLabel = stringResource(R.string.action_retry),
                            onAction = vm::retry
                        )
                    }
                }

                if (showPinned) {
                    item(key = "pinned-header", span = { GridItemSpan(maxLineSpan) }) {
                        VervanSectionHeader(
                            title = stringResource(R.string.chat_filter_pinned),
                            count = pinnedEntries.size,
                            topPadding = 0.dp,
                        )
                    }
                    items(pinnedEntries, key = { "pinned-${it.route}" }) { entry ->
                        ToolCard(
                            entry = entry,
                            isFavorite = true,
                            onToggleFavorite = { toggleFavorite(entry.route) },
                            onClick = { onNavigate(entry.route) },
                            readiness = readiness(entry),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (showRecent) {
                    item(key = "recent-header", span = { GridItemSpan(maxLineSpan) }) {
                        VervanSectionHeader("Recent", count = recentEntries.size, topPadding = if (showPinned) Space.lg else 0.dp)
                    }
                    items(recentEntries, key = { "recent-${it.route}" }) { entry ->
                        ToolCard(
                            entry = entry,
                            isFavorite = entry.route in favorites,
                            onToggleFavorite = { toggleFavorite(entry.route) },
                            onClick = { onNavigate(entry.route) },
                            readiness = readiness(entry),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (visibleCategories.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            icon = Icons.Filled.GridView,
                            title = stringResource(R.string.ui_alltoolsscreen_423_no_tools_found),
                            body = stringResource(R.string.ui_alltoolsscreen_424_try_another_term_or_choose_all),
                            modifier = Modifier.fillMaxWidth().padding(vertical = Space.xxl),
                            centered = true,
                        )
                    }
                } else {
                    visibleCategories.forEachIndexed { index, category ->
                        val browsingCatalog = query.isBlank() && selectedCategory == null
                        val expanded = !browsingCatalog || category.title in expandedCategories
                        val categoryEntries = if (showPinned) {
                            category.entries.filterNot { it.route in favorites }
                        } else category.entries
                        item(key = "header-${category.title}", span = { GridItemSpan(maxLineSpan) }) {
                            ToolCategoryHeading(
                                category = category,
                                count = categoryEntries.size,
                                actionLabel = if (browsingCatalog) if (expanded) "Hide" else "Show" else null,
                                onAction = if (browsingCatalog) {
                                    {
                                        expandedCategories = if (expanded) {
                                            expandedCategories - category.title
                                        } else {
                                            expandedCategories + category.title
                                        }
                                    }
                                } else null,
                                topPadding = if (!showPinned && index == 0) 0.dp else Space.lg,
                            )
                        }
                        if (expanded) {
                            items(categoryEntries, key = { it.route }) { entry ->
                                ToolCard(
                                    entry = entry,
                                    isFavorite = entry.route in favorites,
                                    onToggleFavorite = { toggleFavorite(entry.route) },
                                    onClick = { onNavigate(entry.route) },
                                    readiness = readiness(entry),
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                    item(key = "end", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "$visibleCount local tools",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = Space.sm, bottom = Space.xxl),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCategoryHeading(
    category: ToolCategory,
    count: Int,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = topPadding, bottom = Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        IconAffordance(
            icon = category.icon,
            size = IconAffordanceSize.Compact,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        )
        Column(Modifier.weight(1f).padding(start = Space.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(category.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction, modifier = Modifier.padding(start = Space.xs)) { Text(actionLabel) }
                }
            }
            Text(
                category.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

@Composable
private fun ToolCard(
    entry: ToolEntry,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    readiness: String,
    modifier: Modifier = Modifier,
) {
    // Every navigable tool uses the same semantic icon badge. Category colour is reserved for
    // status/selection, so the catalog remains coherent across themes and accessibility modes.
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAffordance(
                icon = entry.icon,
                size = IconAffordanceSize.Default,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (readiness != "Ready") {
                        Text(
                            readiness,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isFavorite) "Unpin ${entry.label}" else "Pin ${entry.label}",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
        }
    }
}
