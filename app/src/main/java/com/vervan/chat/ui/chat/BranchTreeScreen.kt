package com.vervan.chat.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.common.ChipTone
import com.vervan.chat.ui.common.SemanticChip
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.data.branch.BranchUtil
import com.vervan.chat.data.db.entities.Message
import com.vervan.chat.data.db.entities.MessageRole

/**
 * Every branch of the chat as an indented list (depth-first, ponytail: no actual graph
 * rendering — a tree only gets a handful of forks deep in practice, indentation reads
 * fine at that scale). Tapping a node jumps the active leaf straight there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchTreeScreen(chatId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: ChatViewModel = viewModel(factory = viewModelFactory {
        initializer { ChatViewModel(app, chatId) }
    })
    val allMessages by vm.allMessages.collectAsState()
    val chat by vm.chat.collectAsState()
    val activePath = remember(allMessages, chat?.activeLeafId) {
        BranchUtil.pathTo(allMessages, chat?.activeLeafId).map { it.id }.toSet()
    }
    val rows = remember(allMessages) { BranchUtil.flattenTree(allMessages) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val activeIndex = remember(rows, chat?.activeLeafId) { rows.indexOfFirst { it.first.id == chat?.activeLeafId } }
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.scrollToItem(activeIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Branch tree") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { scope.launch { if (activeIndex >= 0) listState.animateScrollToItem(activeIndex) } }) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "Return to active branch")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(8.dp), state = listState) {
            items(rows, key = { it.first.id }) { (message, depth) ->
                TreeRow(
                    message = message,
                    depth = depth,
                    isActive = message.id in activePath,
                    isCurrentLeaf = message.id == chat?.activeLeafId,
                    onClick = { vm.jumpTo(message.id); onBack() }
                )
            }
        }
    }
}

@Composable
private fun TreeRow(message: Message, depth: Int, isActive: Boolean, isCurrentLeaf: Boolean, onClick: () -> Unit) {
    val label = when (message.role) {
        MessageRole.USER -> "User"
        MessageRole.ASSISTANT -> "Assistant"
        MessageRole.SYSTEM -> "Tool result"
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 2.dp, bottom = 2.dp)) {
        if (depth > 0) {
            // ponytail: per-row tick marks, not a real tree-graph layout
            val lineColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.width((depth * 16).dp).fillMaxHeight()) {
                for (level in 0 until depth) {
                    val x = (level * 16).dp.toPx() + 8.dp.toPx()
                    drawLine(color = lineColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 2.dp.toPx())
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                    if (isCurrentLeaf) SemanticChip("Current", ChipTone.Success)
                }
                Text(message.content.take(120).ifBlank { "…" }, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}
