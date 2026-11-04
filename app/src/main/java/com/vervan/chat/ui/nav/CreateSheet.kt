package com.vervan.chat.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole

data class CreateAction(
    val icon: ImageVector,
    val label: String,
    val description: String = "",
    val group: String = "Create",
    /** Shown in the short "Quick start" view. A flag rather than a label match, so renaming or
     * translating an action can't silently drop it from the quick view. */
    val quick: Boolean = false,
    val onClick: () -> Unit
)

/** The center nav "Create" action — a bottom sheet grid, matching the mockup's Create
 * sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(sheetState: SheetState, actions: List<CreateAction>, onDismiss: () -> Unit) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val visibleActions = if (showAll) actions else actions.filter { it.quick }
    val quickStartLabel = androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.create_group_quick)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg).padding(bottom = Space.md)) {
            Text(androidx.compose.ui.res.stringResource(com.vervan.chat.R.string.action_create), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Space.xs))
            Text(
                androidx.compose.ui.res.stringResource(if (showAll) com.vervan.chat.R.string.create_sheet_all_body else com.vervan.chat.R.string.create_sheet_quick_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sm).padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            visibleActions.groupBy { if (showAll) it.group else quickStartLabel }.forEach { (group, groupedActions) ->
                item(key = "group-$group") {
                    VervanSectionHeader(group, modifier = Modifier.padding(horizontal = Space.sm))
                }
                items(groupedActions, key = { "${it.group}-${it.label}" }) { action ->
                    androidx.compose.material3.Card(
                        onClick = action.onClick,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sm),
                        colors = SurfaceRole.Card.cardColors(),
                        border = SurfaceRole.Card.border()
                    ) {
                        ListItem(
                            leadingContent = {
                                com.vervan.chat.ui.common.IconAffordance(
                                    icon = action.icon,
                                    size = com.vervan.chat.ui.common.IconAffordanceSize.Default,
                                    tint = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.64f)
                                )
                            },
                            headlineContent = { Text(action.label) },
                            supportingContent = {
                                if (action.description.isNotBlank()) {
                                    Text(action.description, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
            item(key = "more-actions") {
                TextButton(
                    onClick = { showAll = !showAll },
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
                ) {
                    Text(androidx.compose.ui.res.stringResource(if (showAll) com.vervan.chat.R.string.create_show_fewer else com.vervan.chat.R.string.create_show_more))
                }
            }
        }
    }
}
