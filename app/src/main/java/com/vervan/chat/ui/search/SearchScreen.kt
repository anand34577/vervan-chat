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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.ValidationLimits
import com.vervan.chat.ui.common.VervanSearchField

private enum class SearchScope(val label: String) {
    All("All"), Chats("Chats"), Messages("Messages"), Notes("Notes"), Documents("Documents"), Personas("Personas"), Memory("Memory")
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
    onOpenMemory: (String) -> Unit = {}
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
                        placeholder = "Search chats, notes, documents, personas",
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (query.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchScope.entries.forEach { s ->
                        FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.label) })
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    query.isBlank() -> Column(Modifier.padding(24.dp)) {
                        Text("Search across everything you've made", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    searching -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 24.dp).size(24.dp), strokeWidth = 2.dp)
                    results.isEmpty -> Column(Modifier.padding(24.dp)) {
                        Text("No results for \"$query\"", style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                        if (results.chats.isNotEmpty() && (scope == SearchScope.All || scope == SearchScope.Chats)) {
                            item { GroupLabel("Chats") }
                            items(results.chats, key = { "c_" + it.id }) { ResultRow(Icons.AutoMirrored.Filled.Chat, it.title) { onOpenChat(it.id) } }
                        }
                        if (results.notes.isNotEmpty() && (scope == SearchScope.All || scope == SearchScope.Notes)) {
                            item { GroupLabel("Notes") }
                            items(results.notes, key = { "n_" + it.id }) { ResultRow(Icons.Filled.Edit, it.title) { onOpenNote(it.id) } }
                        }
                        if (results.documents.isNotEmpty() && (scope == SearchScope.All || scope == SearchScope.Documents)) {
                            item { GroupLabel("Documents") }
                            items(results.documents, key = { "d_" + it.id }) { ResultRow(Icons.Filled.Description, it.displayName) { onOpenDocument(it.id) } }
                        }
                        if (results.personas.isNotEmpty() && (scope == SearchScope.All || scope == SearchScope.Personas)) {
                            item { GroupLabel("Personas") }
                            items(results.personas, key = { "p_" + it.id }) { ResultRow(Icons.Outlined.Person, it.name) { onOpenPersona(it.id) } }
                        }
                        if (results.messages.isNotEmpty() && (scope == SearchScope.All || scope == SearchScope.Messages)) {
                            item { GroupLabel("Messages") }
                            items(results.messages, key = { "m_" + it.id }) { ResultRow(Icons.AutoMirrored.Filled.Chat, it.content.take(80)) { onOpenChat(it.chatId) } }
                        }
                        if (results.memories.isNotEmpty() && (scope == SearchScope.All || scope == SearchScope.Memory)) {
                            item { GroupLabel("Memory") }
                            items(results.memories, key = { "mem_" + it.id }) { ResultRow(Icons.Filled.Psychology, it.text.take(80)) { onOpenMemory(it.id) } }
                        }
                        if (scope != SearchScope.All &&
                            when (scope) {
                                SearchScope.Chats -> results.chats.isEmpty()
                                SearchScope.Notes -> results.notes.isEmpty()
                                SearchScope.Documents -> results.documents.isEmpty()
                                SearchScope.Personas -> results.personas.isEmpty()
                                SearchScope.Messages -> results.messages.isEmpty()
                                SearchScope.Memory -> results.memories.isEmpty()
                                SearchScope.All -> false
                            }
                        ) {
                            item {
                                Text(
                                    "No ${scope.label.lowercase()} match \"$query\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 24.dp)
                                )
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
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
        }
    }
}
