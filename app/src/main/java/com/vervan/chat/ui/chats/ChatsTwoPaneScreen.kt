package com.vervan.chat.ui.chats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.chat.ChatScreen
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.theme.Space
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat

/**
 * Master-detail layout for the Chats tab on expanded-width windows (tablet/foldable, spec
 * §4) — the list stays on screen while a chat is open, instead of a full-screen push. Kept
 * as a separate composable from [ChatListScreen] rather than threading pane-mode through it,
 * so the phone path (the vast majority of real usage) is untouched.
 *
 * selection is local state, not nav-controller state — deep links into a specific
 * chat (from Home, Search, share sheet) still push a full-screen [ChatScreen] on top rather
 * than landing inside this pane; only navigating via the Chats tab itself uses the split view.
 *
 * Improvements over the previous version:
 *  - `rememberSaveable` for selectedChatId so a config change (rotation, dark-mode toggle)
 *    doesn't drop the open conversation mid-task.
 *  - System Back intercepts to clear the pane first (instead of leaving the Chats tab entirely
 *    with the chat still visually open).
 *  - The detail placeholder is no longer bare "Select a chat" — it's a proper EmptyState with
 *    an icon, copy, and a clear affordance. Same pattern as Apple Mail / Slack.
 *  - The master pane is `widthIn(min=320, max=420)` instead of a fixed 360dp so it scales
 *    correctly on both 600dp foldables and 1600dp tablets.
 */
@Composable
fun ChatsTwoPaneScreen(
    onOpenBranchTree: (String) -> Unit,
    onOpenPassage: (String) -> Unit = {},
    onOpenChatInfo: (String) -> Unit = {},
    onOpenDocument: (String) -> Unit = {},
    onOpenModels: () -> Unit = {}
) {
    var selectedChatId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectedChatId != null) { selectedChatId = null }

    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .widthIn(min = 320.dp, max = 420.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            ChatListScreen(onOpenChat = { chatId -> selectedChatId = chatId })
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        Box(Modifier.fillMaxHeight().weight(1f)) {
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
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "Pick a conversation",
                    body = "Select a chat on the left to continue it here. New chats open in this pane too.",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
