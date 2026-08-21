package com.vervan.chat.ui.nav

import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
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
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space
import androidx.annotation.StringRes

data class CreateAction(
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int = 0,
    @param:StringRes val groupRes: Int = R.string.create_group_start,
    val quickStart: Boolean = false,
    val onClick: () -> Unit
)

/** The center nav "Create" action — a bottom sheet grid, matching the mockup's Create
 * sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(sheetState: SheetState, actions: List<CreateAction>, onDismiss: () -> Unit) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val visibleActions = if (showAll) actions else actions.filter(CreateAction::quickStart)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg).padding(bottom = Space.md)) {
            Text(stringResource(R.string.action_create), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Space.xs))
            Text(
                stringResource(if (showAll) R.string.create_sheet_all_body else R.string.create_sheet_quick_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sm).padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            visibleActions.groupBy { if (showAll) it.groupRes else R.string.create_group_quick_start }.forEach { (groupRes, groupedActions) ->
                item(key = "group-$groupRes") {
                    VervanSectionHeader(stringResource(groupRes), modifier = Modifier.padding(horizontal = Space.sm))
                }
                items(groupedActions, key = { "${it.groupRes}-${it.labelRes}" }) { action ->
                    androidx.compose.material3.Surface(
                        onClick = action.onClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.sm)
                            .padding(bottom = Space.xs),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 1.dp
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
                            headlineContent = { Text(stringResource(action.labelRes)) },
                            supportingContent = {
                                if (action.descriptionRes != 0) {
                                    Text(stringResource(action.descriptionRes), style = MaterialTheme.typography.bodySmall)
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
                    Text(stringResource(if (showAll) R.string.create_sheet_show_fewer else R.string.create_sheet_more_actions))
                }
            }
        }
    }
}
