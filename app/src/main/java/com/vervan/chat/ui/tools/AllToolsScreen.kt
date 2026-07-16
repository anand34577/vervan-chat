package com.vervan.chat.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContactPage
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
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.FeatureHero
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
            ToolEntry(Icons.Filled.NoteAlt, "Smart notes", "Clean notes and extract decisions or tasks.", "tools/smart-notes"),
            ToolEntry(Icons.Filled.Description, "Clipboard assistant", "Work with text already on your clipboard.", "tools/clipboard-assistant"),
            ToolEntry(Icons.Filled.School, "Explain for my level", "Adapt an explanation to your experience.", "tools/explain-level"),
            ToolEntry(Icons.Filled.Mail, "Email composer", "Draft a clear response from key points.", "tools/email-composer"),
            ToolEntry(Icons.Filled.AutoAwesome, "Story & idea studio", "Develop concepts, plots, and creative directions.", "tools/story-studio"),
            ToolEntry(Icons.Filled.Image, "Social post assistant", "Shape an idea for a social caption or post.", "tools/social-post"),
            ToolEntry(Icons.Filled.RestaurantMenu, "Recipe assistant", "Plan a recipe from ingredients or constraints.", "tools/recipe-assistant"),
            ToolEntry(Icons.Filled.Mail, "Gift & message assistant", "Write a thoughtful message for an occasion.", "tools/gift-message"),
        ),
    ),
    ToolCategory(
        "Scan & extract",
        "Capture documents and turn visual information into editable content.",
        Icons.Filled.PhotoCamera,
        listOf(
            ToolEntry(Icons.Filled.PhotoCamera, "Document scanner", "Capture multiple pages and export a PDF.", "tools/document-scanner"),
            ToolEntry(Icons.Filled.Image, "OCR scanner", "Extract editable text from an image.", "tools/ocr-scanner"),
            ToolEntry(Icons.Filled.ContactPage, "Business card scanner", "Extract contact details into editable fields.", "tools/business-card-scanner"),
            ToolEntry(Icons.Filled.Description, "Receipt scanner", "Extract totals, items, and payment details.", "tools/receipt-scanner"),
            ToolEntry(Icons.AutoMirrored.Filled.Rule, "Table scanner", "Convert a photographed table to structured data.", "tools/table-scanner"),
            ToolEntry(Icons.Filled.ContactPage, "Smart form filler", "Extract the fields you specify from an image.", "tools/smart-form-filler"),
            ToolEntry(Icons.Filled.Image, "Image caption", "Create alt text or a useful visual description.", "tools/image-caption"),
            ToolEntry(Icons.AutoMirrored.Filled.CompareArrows, "Document comparison", "Compare two versions section by section.", "tools/document-comparison"),
        ),
    ),
    ToolCategory(
        "Learn & practise",
        "Study actively with questions, feedback, and recall exercises.",
        Icons.Filled.School,
        listOf(
            ToolEntry(Icons.Filled.School, "Study workspace", "Build and review local flashcard decks.", "study"),
            ToolEntry(Icons.Filled.School, "Quiz generator", "Create a five-question interactive quiz.", "tools/quiz-generator"),
            ToolEntry(Icons.Filled.Forum, "Socratic tutor", "Reach the answer through guided questions.", "tools/socratic-tutor"),
            ToolEntry(Icons.Filled.School, "Exam preparation", "Organize revision around an exam goal.", "tools/exam-prep"),
            ToolEntry(Icons.AutoMirrored.Filled.Rule, "Homework checker", "Review an answer and identify gaps.", "tools/homework-checker"),
            ToolEntry(Icons.Filled.Insights, "Concept mapper", "Connect ideas into a structured outline.", "tools/concept-mapper"),
            ToolEntry(Icons.Filled.Psychology, "Memory trainer", "Create recall prompts from study material.", "tools/memory-trainer"),
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
            ToolEntry(Icons.Filled.Insights, "Habit reflection", "Reflect on a habit and plan an adjustment.", "tools/habit-reflection"),
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
            ToolEntry(Icons.Filled.Terminal, "Commit message", "Summarize a change as a useful commit message.", "tools/commit-message"),
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
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
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
                title = { Text("Tools") },
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
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                    FeatureHero(
                        icon = Icons.Filled.GridView,
                        eyebrow = "On-device toolkit",
                        title = "Choose what you want to accomplish",
                        body = "${categories.sumOf { it.entries.size }} tools grouped into clear, practical sections. Everything runs locally.",
                        modifier = Modifier.padding(top = Space.lg),
                    )
                }
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
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("All") },
                            )
                        }
                        items(categories, key = { it.title }) { category ->
                            FilterChip(
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

                if (visibleCategories.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            icon = Icons.Filled.GridView,
                            title = "No tools found",
                            body = "Try a different search or choose All categories.",
                            modifier = Modifier.padding(vertical = Space.xxl),
                        )
                    }
                } else {
                    visibleCategories.forEach { category ->
                        item(key = "header-${category.title}", span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                VervanSectionHeader(
                                    title = category.title,
                                    count = if (query.isNotBlank() || selectedCategory != null) category.entries.size else null,
                                )
                                Text(
                                    category.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = Space.xs),
                                )
                            }
                        }
                        items(category.entries, key = { it.route }) { entry ->
                            ToolCard(entry = entry, onClick = { onNavigate(entry.route) })
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
private fun ToolCard(entry: ToolEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
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
