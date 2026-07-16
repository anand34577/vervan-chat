package com.vervan.chat.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.chat.ChatScreen

/**
 * Master-detail layout for the Chats tab on expanded-width windows (tablet/foldable, spec
 * §4) — the list stays on screen while a chat is open, instead of a full-screen push. Kept
 * as a separate composable from [ChatListScreen] rather than threading pane-mode through it,
 * so the phone path (the vast majority of real usage) is untouched.
 * ponytail: selection is local state, not nav-controller state — deep links into a specific
 * chat (from Home, Search, share sheet) still push a full-screen [ChatScreen] on top rather
 * than landing inside this pane; only navigating via the Chats tab itself uses the split view.
 */
@Composable
fun ChatsTwoPaneScreen(
    onOpenBranchTree: (String) -> Unit,
    onOpenPassage: (String) -> Unit = {},
    onOpenChatInfo: (String) -> Unit = {},
    onOpenDocument: (String) -> Unit = {},
    onOpenModels: () -> Unit = {}
) {
    var selectedChatId by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(360.dp).fillMaxHeight()) {
            ChatListScreen(onOpenChat = { chatId -> selectedChatId = chatId })
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        Box(Modifier.fillMaxHeight()) {
            val chatId = selectedChatId
            if (chatId != null) {
                ChatScreen(
                    chatId = chatId,
                    onBack = { selectedChatId = null },
                    onOpenChatInfo = { onOpenChatInfo(chatId) },
                    onOpenDocument = onOpenDocument,
                    onOpenBranchTree = { onOpenBranchTree(chatId) },
                    onOpenPassage = onOpenPassage,
                    onOpenModels = onOpenModels,
                    onForkChat = { forkedChatId -> selectedChatId = forkedChatId }
                )
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Text("Select a chat", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
