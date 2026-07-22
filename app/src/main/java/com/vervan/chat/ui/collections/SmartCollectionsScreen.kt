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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.vervan.chat.ui.common.VervanFilterChip
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.StatusChip
import com.vervan.chat.ui.common.StatusTone
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole

/**
 * one screen with filter chips, not the list-then-detail navigation this screen used
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
        PageContainer(Modifier.padding(padding), maxContentWidth = 840.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                SmartCollection.entries.forEach { col ->
                    VervanFilterChip(selected = selected == col, onClick = { selected = col }, label = { Text(col.label) })
                }
            }
            Text(
                "Automatic · ${selected.description}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Space.xs)
            )
            if (contents.total == 0) {
                EmptyState(
                    icon = Icons.Filled.Collections,
                    title = "Nothing here yet",
                    body = "Matching chats, notes, and documents appear here automatically."
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.sm)) {
                    items(contents.chats, key = { "c-${it.id}" }) { chat ->
                        CollectionRow(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            title = chat.title,
                            onClick = { onOpenChat(chat.id) }
                        )
                    }
                    items(contents.notes, key = { "n-${it.id}" }) { note ->
                        CollectionRow(
                            icon = Icons.AutoMirrored.Filled.Note,
                            title = note.title,
                            onClick = { onOpenNote(note.id) }
                        )
                    }
                    items(contents.documents, key = { "d-${it.id}" }) { doc ->
                        CollectionRow(
                            icon = Icons.Filled.Description,
                            title = doc.displayName,
                            onClick = { onOpenKnowledge(doc.knowledgeBaseId) },
                            trailing = {
                                StatusChip(
                                    label = doc.status.name.lowercase().replaceFirstChar { it.uppercase() },
                                    tone = when (doc.status.name) {
                                        "READY" -> StatusTone.Ready
                                        "FAILED" -> StatusTone.Error
                                        else -> StatusTone.Running
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CollectionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        colors = SurfaceRole.Card.cardColors(),
        border = SurfaceRole.Card.border()
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            IconAffordance(icon, size = IconAffordanceSize.Compact)
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Space.md)
            )
            trailing?.invoke()
        }
    }
}
