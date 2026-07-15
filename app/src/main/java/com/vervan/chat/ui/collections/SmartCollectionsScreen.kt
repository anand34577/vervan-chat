package com.vervan.chat.ui.collections

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.EmptyState

/**
 * §7.6.9 — one screen with filter chips, not the list-then-detail navigation this screen used
 * to have (tapping a collection pushed a second "detail" route). Chips are local UI-only
 * selection state, so switching between them never adds a back-stack entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCollectionsScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenKnowledge: (String) -> Unit
) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: SmartCollectionsViewModel = viewModel(factory = viewModelFactory { initializer { SmartCollectionsViewModel(app) } })
    var selected by remember { mutableStateOf(SmartCollection.entries.first()) }
    val contents by remember(selected) { vm.contents(selected) }.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart collections") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmartCollection.entries.forEach { col ->
                    FilterChip(selected = selected == col, onClick = { selected = col }, label = { Text(col.label) })
                }
            }
            Text(
                "Automatic · ${selected.description}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            if (contents.total == 0) {
                EmptyState(
                    icon = Icons.Filled.Collections,
                    title = "Nothing here yet",
                    body = "This collection fills in automatically as it finds matching chats, notes, and documents."
                )
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(contents.chats, key = { "c-${it.id}" }) { chat ->
                        Card(onClick = { onOpenChat(chat.id) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(chat.title, modifier = Modifier.padding(10.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    items(contents.notes, key = { "n-${it.id}" }) { note ->
                        Card(onClick = { onOpenNote(note.id) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(note.title, modifier = Modifier.padding(10.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    items(contents.documents, key = { "d-${it.id}" }) { doc ->
                        Card(onClick = { onOpenKnowledge(doc.knowledgeBaseId) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(doc.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(doc.status.name, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
