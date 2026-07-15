package com.vervan.chat.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.theme.Space

data class CreateAction(val icon: ImageVector, val label: String, val description: String = "", val group: String = "Create", val onClick: () -> Unit)

/** The center nav "Create" action — a bottom sheet grid, matching the mockup's Create
 * sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(sheetState: SheetState, actions: List<CreateAction>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg).padding(bottom = Space.md)) {
            Text("Create", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Space.xs))
            Text(
                "Start content here; other buttons across Vervan are shortcuts into these same flows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sm).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            actions.groupBy { it.group }.forEach { (group, groupedActions) ->
                item(key = "group-$group") {
                    VervanSectionHeader(group, modifier = Modifier.padding(horizontal = Space.sm))
                }
                items(groupedActions, key = { "${it.group}-${it.label}" }) { action ->
                    androidx.compose.material3.Card(
                        onClick = action.onClick,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                            }
                        )
                    }
                }
            }
        }
    }
}
