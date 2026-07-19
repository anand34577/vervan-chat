package com.vervan.chat.ui.common

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The one selection-mode top bar shape, used everywhere a list supports multi-select (chats,
 * recycle bin, notes, knowledge, library, collections, study sets, folders, workspaces). Title
 * shows the count, a Close icon exits selection (never a "cancel" text button off to the side),
 * a select-all/deselect-all icon always comes first among actions, then whatever a given screen
 * needs beyond delete (archive, move to folder, restore — [extraActions]), then Delete last,
 * always error-tinted. There is deliberately no "enter selection mode" button anywhere — long
 * press on a row is the only entry point, everywhere, matching [selectableItem].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onExit: () -> Unit,
    onDelete: (() -> Unit)? = null,
    deleteEnabled: Boolean = selectedCount > 0,
    deleteContentDescription: String = "Delete selected",
    extraActions: @Composable RowScope.() -> Unit = {}
) {
    VervanTopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.Filled.Close, contentDescription = "Exit selection") } },
        actions = {
            IconButton(onClick = onToggleSelectAll) {
                Icon(
                    if (allSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = if (allSelected) "Deselect all" else "Select all"
                )
            }
            extraActions()
            if (onDelete != null) {
                IconButton(enabled = deleteEnabled, onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = deleteContentDescription, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

/**
 * The one selection gesture, used everywhere: tap selects/opens depending on mode, long-press
 * always enters selection mode starting with this item. Every list screen that supports
 * multi-select should build its row's clickable modifier through this instead of hand-rolling
 * [combinedClickable] again, so the gesture can't quietly drift screen to screen.
 */
fun Modifier.selectableItem(
    selectionMode: Boolean,
    onClick: () -> Unit,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit,
    selectable: Boolean = true
): Modifier = combinedClickable(
    onClick = {
        when {
            selectionMode && selectable -> onToggleSelected()
            !selectionMode -> onClick()
        }
    },
    onLongClick = { if (selectable) onEnterSelection() }
)
