package com.vervan.chat.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.theme.vervanBorder
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space

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
            ToolEntry(Icons.Filled.Tune, "Workflows", "Run reusable multi-step AI processes.", "workflows"),
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
            ToolEntry(Icons.AutoMirrored.Filled.MenuBook, "Knowledge bases", "Ground answers in your own local documents.", "knowledge"),
            ToolEntry(Icons.Filled.AutoAwesome, "Model capabilities", "See what each installed model can use.", "tools/model-dashboard"),
            ToolEntry(Icons.Filled.AutoAwesome, "Models", "Import, configure, load, and benchmark models.", "models"),
            ToolEntry(Icons.Filled.Psychology, "Memory", "Review reusable facts saved for future chats.", "memory"),
            ToolEntry(Icons.Filled.GridView, "Smart collections", "Browse dynamic groups such as pinned or attached chats.", "collections"),
        ),
    ),
)

/** A category-first directory: tools are organized by the job the user is trying to do, not by
 * implementation type or the order features were added. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllToolsScreen(onNavigate: (String) -> Unit, onBack: (() -> Unit)? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("vervan", 0) }
    // Favorites persist in the shared prefs the app already uses (no schema change): a set of
    // tool routes. Held in state so toggling re-renders immediately; prefs is the durable source
    // of truth and is reloaded on process restart.
    var favorites by remember { mutableStateOf(prefs.getStringSet("tool_favorites", emptySet())!!.toSet()) }
    fun toggleFavorite(route: String) {
        val next = if (route in favorites) favorites - route else favorites + route
        favorites = next
        prefs.edit().putStringSet("tool_favorites", next).apply()
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
    // Stable per-category color so a tool's icon is the same hue wherever it appears (grid or
    // pinned), turning a wall of identical amber chips into a scannable, colorful directory.
    val entryAccent = remember {
        categories.flatMapIndexed { i, cat -> cat.entries.map { it.route to com.vervan.chat.ui.theme.vervanAccentFor(i) } }.toMap()
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tools")
                        Text(
                            "From quick fixes to big ideas",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        PageContainer(Modifier.padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = Space.lg, bottom = Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                    VervanSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search tools and outcomes",
                    )
                }
                item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        item {
                            VervanFilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("All") },
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
                }

                if (showPinned) {
                    item(key = "pinned-header", span = { GridItemSpan(maxLineSpan) }) {
                        VervanSectionHeader(
                            title = "Pinned",
                            count = pinnedEntries.size,
                            topPadding = 0.dp,
                        )
                    }
                    items(pinnedEntries, key = { "pinned-${it.route}" }) { entry ->
                        ToolCard(
                            entry = entry,
                            accent = entryAccent[entry.route],
                            isFavorite = true,
                            onToggleFavorite = { toggleFavorite(entry.route) },
                            onClick = { onNavigate(entry.route) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (visibleCategories.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            icon = Icons.Filled.GridView,
                            title = "No tools found",
                            body = "Try another term or choose All.",
                            modifier = Modifier.padding(vertical = Space.xxl),
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
                            Column {
                                VervanSectionHeader(
                                    title = category.title,
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
                                Text(
                                    category.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = Space.xs),
                                )
                            }
                        }
                        if (expanded) {
                            items(categoryEntries, key = { it.route }) { entry ->
                                ToolCard(
                                    entry = entry,
                                    accent = entryAccent[entry.route],
                                    isFavorite = entry.route in favorites,
                                    onToggleFavorite = { toggleFavorite(entry.route) },
                                    onClick = { onNavigate(entry.route) },
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
private fun ToolCard(
    entry: ToolEntry,
    accent: com.vervan.chat.ui.theme.VervanAccent?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = accent?.container ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    val onContainer = accent?.onContainer ?: MaterialTheme.colorScheme.onPrimaryContainer
    // A faint wash of the category accent over the surface — cards stay calm but each category
    // reads as its own colour family, matching the icon chip.
    val cardContainer = accent?.container?.copy(alpha = 0.16f)
        ?.compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
        ?: MaterialTheme.colorScheme.surfaceContainerLow
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 112.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainer),
        border = vervanBorder(),
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = container,
                    contentColor = onContainer,
                ) {
                    Icon(entry.icon, null, Modifier.padding(Space.sm).size(20.dp))
                }
                Text(
                    entry.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = Space.md),
                )
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isFavorite) "Unpin ${entry.label}" else "Pin ${entry.label}",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.sm),
            )
        }
    }
}
