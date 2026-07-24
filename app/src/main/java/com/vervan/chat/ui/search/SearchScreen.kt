package com.vervan.chat.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.LoadingSkeletonList
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.theme.Space

private enum class SearchScope(val label: String) {
    All("All"), Chats("Chats"), Messages("Messages"), Content("Content"), Organize("Organize"), Reusable("Reusable"), Tools("Tools")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenKnowledge: (String) -> Unit,
    onOpenPersona: (String) -> Unit,
    onOpenDocument: (String) -> Unit = onOpenKnowledge,
    onOpenMemory: (String) -> Unit = {},
    onOpenMessage: (String, String) -> Unit = { chatId, _ -> onOpenChat(chatId) },
    onOpenProject: (String) -> Unit = {},
    onOpenWorkspace: (String) -> Unit = {},
    onOpenFolder: (String) -> Unit = {},
    onOpenTemplate: (String) -> Unit = {},
    onOpenWorkflow: (String) -> Unit = {},
    onOpenSavedOutput: (String) -> Unit = {},
    onOpenTool: (String) -> Unit = {},
    onOpenToolRun: (String) -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SearchViewModel = viewModel(factory = viewModelFactory { initializer { SearchViewModel(app) } })
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val searching by vm.searching.collectAsState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    var scope by remember { mutableStateOf(SearchScope.All) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // A bare OutlinedTextField as the title used to inherit that field's own
                    // ~56dp min-height and default (large) type scale, pushing the app bar tall
                    // and the placeholder text oversized — a compact, height-constrained,
                    // borderless field reads as a search bar instead of a full form field.
                    VervanSearchField(
                        value = query,
                        onValueChange = { if (it.length <= ValidationLimits.SEARCH_QUERY) vm.setQuery(it) },
                        placeholder = "Search everything on this device",
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
          Column(Modifier.fillMaxSize()) {
            if (query.isNotBlank()) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(vertical = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    items(SearchScope.entries) { s ->
                        com.vervan.chat.ui.common.VervanFilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.label) })
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    query.isBlank() -> EmptyState(
                        icon = Icons.Filled.Search,
                        title = "Search your private workspace",
                        body = "Find conversations, messages, projects, files, tools, workflows, and saved work locally."
                    )
                    searching -> LoadingSkeletonList(
                        rows = 6,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.lg)
                    )
                    results.isEmpty -> EmptyState(
                        icon = Icons.Filled.Search,
                        title = "No results for \"$query\"",
                        body = "Try fewer words or another filter."
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        if (results.chats.isNotEmpty() && (scope == SearchScope.All || scope == SearchScope.Chats)) {
                            item { GroupLabel("Chats") }
                            items(results.chats, key = { "c_" + it.id }) { ResultRow(Icons.AutoMirrored.Filled.Chat, it.title, "Conversation", query) { onOpenChat(it.id) } }
                        }
                        if (results.notes.isNotEmpty() && (scope == SearchScope.All || scope == SearchScope.Content)) {
                            item { GroupLabel("Notes") }
                            items(results.notes, key = { "n_" + it.id }) { ResultRow(Icons.Filled.Edit, it.title, it.content.take(100), query) { onOpenNote(it.id) } }
                        }
                        if (results.documents.isNotEmpty() && (scope == SearchScope.All || scope == SearchScope.Content)) {
                            item { GroupLabel("Documents") }
                            items(results.documents, key = { "d_" + it.id }) { ResultRow(Icons.Filled.Description, it.displayName, "Local document", query) { onOpenDocument(it.id) } }
                        }
                        if (results.personas.isNotEmpty() && (scope == SearchScope.All || scope == SearchScope.Reusable)) {
                            item { GroupLabel("Personas") }
                            items(results.personas, key = { "p_" + it.id }) { ResultRow(Icons.Outlined.Person, it.name, it.description, query) { onOpenPersona(it.id) } }
                        }
                        if (results.messages.isNotEmpty() && (scope == SearchScope.All || scope == SearchScope.Messages)) {
                            item { GroupLabel("Messages") }
                            items(results.messages, key = { "m_" + it.id }) { ResultRow(Icons.AutoMirrored.Filled.Chat, it.content.take(100), "Message in a conversation", query) { onOpenMessage(it.chatId, it.id) } }
                        }
                        if (results.memories.isNotEmpty() && (scope == SearchScope.All || scope == SearchScope.Reusable)) {
                            item { GroupLabel("Memory") }
                            items(results.memories, key = { "mem_" + it.id }) { ResultRow(Icons.Filled.Psychology, it.text.take(100), "Saved memory", query) { onOpenMemory(it.id) } }
                        }
                        if ((scope == SearchScope.All || scope == SearchScope.Organize) && results.workspaces.isNotEmpty()) {
                            item { GroupLabel("Workspaces") }
                            items(results.workspaces, key = { "ws_" + it.id }) { ResultRow(Icons.Filled.Workspaces, it.name, it.description, query) { onOpenWorkspace(it.id) } }
                        }
                        if ((scope == SearchScope.All || scope == SearchScope.Organize) && results.projects.isNotEmpty()) {
                            item { GroupLabel("Projects") }
                            items(results.projects, key = { "proj_" + it.id }) { ResultRow(Icons.AutoMirrored.Filled.MenuBook, it.name, it.instructions, query) { onOpenProject(it.id) } }
                        }
                        if ((scope == SearchScope.All || scope == SearchScope.Organize) && results.folders.isNotEmpty()) {
                            item { GroupLabel("Folders") }
                            items(results.folders, key = { "folder_" + it.id }) { ResultRow(Icons.Filled.Folder, it.name, "Workspace folder", query) { onOpenFolder(it.id) } }
                        }
                        if ((scope == SearchScope.All || scope == SearchScope.Content) && results.knowledgeBases.isNotEmpty()) {
                            item { GroupLabel("Knowledge bases") }
                            items(results.knowledgeBases, key = { "kb_" + it.id }) { ResultRow(Icons.AutoMirrored.Filled.MenuBook, it.name, it.description, query) { onOpenKnowledge(it.id) } }
                        }
                        if ((scope == SearchScope.All || scope == SearchScope.Reusable) && results.templates.isNotEmpty()) {
                            item { GroupLabel("Prompt templates") }
                            items(results.templates, key = { "tpl_" + it.id }) { ResultRow(Icons.Filled.AutoAwesome, "/${it.name}", it.description.ifBlank { it.body.take(100) }, query) { onOpenTemplate(it.id) } }
                        }
                        if ((scope == SearchScope.All || scope == SearchScope.Reusable) && results.workflows.isNotEmpty()) {
                            item { GroupLabel("Workflows") }
                            items(results.workflows, key = { "wf_" + it.id }) { ResultRow(Icons.Filled.GridView, it.name, it.description, query) { onOpenWorkflow(it.id) } }
                        }
                        if ((scope == SearchScope.All || scope == SearchScope.Reusable) && results.savedOutputs.isNotEmpty()) {
                            item { GroupLabel("Saved outputs") }
                            items(results.savedOutputs, key = { "saved_" + it.id }) { ResultRow(Icons.Filled.Description, it.label.ifBlank { "Saved output" }, it.content.take(100), query) { onOpenSavedOutput(it.id) } }
                        }
                        if ((scope == SearchScope.All || scope == SearchScope.Tools) && results.tools.isNotEmpty()) {
                            item { GroupLabel("Tools") }
                            items(results.tools, key = { "tool_" + it.route }) { ResultRow(Icons.Filled.GridView, it.label, it.description, query) { onOpenTool(it.route) } }
                        }
                        if ((scope == SearchScope.All || scope == SearchScope.Tools) && results.toolRuns.isNotEmpty()) {
                            item { GroupLabel("Recent tool results") }
                            items(results.toolRuns, key = { "run_" + it.id }) { ResultRow(Icons.Filled.History, it.toolName, it.output.ifBlank { it.input }.take(100), query) { onOpenToolRun(it.id) } }
                        }
                        if (scope != SearchScope.All &&
                            when (scope) {
                                SearchScope.Chats -> results.chats.isEmpty()
                                SearchScope.Messages -> results.messages.isEmpty()
                                SearchScope.Content -> results.notes.isEmpty() && results.documents.isEmpty() && results.knowledgeBases.isEmpty()
                                SearchScope.Organize -> results.workspaces.isEmpty() && results.projects.isEmpty() && results.folders.isEmpty()
                                SearchScope.Reusable -> results.personas.isEmpty() && results.memories.isEmpty() && results.templates.isEmpty() && results.workflows.isEmpty() && results.savedOutputs.isEmpty()
                                SearchScope.Tools -> results.tools.isEmpty() && results.toolRuns.isEmpty()
                                SearchScope.All -> false
                            }
                        ) {
                            item {
                                Text(
                                    "No ${scope.label.lowercase()} match \"$query\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Space.xxl)
                                )
                            }
                        }
                    }
                }
            }
          }
        }
    }
}

@Composable
private fun GroupLabel(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Space.lg, bottom = Space.xs).semantics { heading() })
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, subtitle: String = "", query: String = "", onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        colors = com.vervan.chat.ui.theme.SurfaceRole.Card.cardColors(),
        border = com.vervan.chat.ui.theme.SurfaceRole.Card.border()
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            IconAffordance(icon = icon, size = IconAffordanceSize.Compact)
            Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                val highlightedTitle = if (query.isBlank()) {
                    androidx.compose.ui.text.AnnotatedString(title)
                } else {
                    val index = title.indexOf(query, ignoreCase = true)
                    buildAnnotatedString {
                        if (index < 0) append(title) else {
                            append(title.substring(0, index))
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                                append(title.substring(index, index + query.length))
                            }
                            append(title.substring(index + query.length))
                        }
                    }
                }
                Text(highlightedTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
