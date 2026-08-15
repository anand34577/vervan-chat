package com.vervan.chat.ui.knowledge

import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.theme.vervanDividerColor

/**
 * Expanded-width knowledge browser. The collection stays visible while a source is inspected,
 * matching the chat master-detail behavior and preserving context on tablets and foldables.
 * Phone navigation continues to use the existing push-based routes.
 */
@Composable
fun KnowledgeTwoPaneScreen(onOpenDocument: (String) -> Unit) {
    var selectedKbId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectedKbId != null) { selectedKbId = null }

    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .widthIn(min = 320.dp, max = 420.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            KnowledgeScreen(onOpenKb = { selectedKbId = it })
        }
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = vervanDividerColor()
        )
        Box(Modifier.fillMaxHeight().weight(1f)) {
            val kbId = selectedKbId
            if (kbId != null) {
                KnowledgeBaseDetailScreen(
                    kbId = kbId,
                    onBack = { selectedKbId = null },
                    onOpenDocument = onOpenDocument,
                    showBackButton = false
                )
            } else {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = stringResource(R.string.ui_knowledgetwopanescreen_59_pick_a_knowledge_base),
                    body = stringResource(R.string.ui_knowledgetwopanescreen_60_choose_a_source_collection_on_the_left_to_vi),
                    modifier = Modifier.fillMaxSize(),
                    centered = true
                )
            }
        }
    }
}
