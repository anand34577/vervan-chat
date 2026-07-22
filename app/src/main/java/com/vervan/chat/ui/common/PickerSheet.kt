package com.vervan.chat.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space

data class PickerOption<T>(val value: T, val label: String, val supporting: String? = null)

/**
 * PickerSheet — the shared single/multi-select bottom sheet with an optional search
 * field, used wherever a user chooses among items (persona, model, folder, retrieval mode)
 * instead of every screen building its own `ModalBottomSheet` + filter logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PickerSheet(
    title: String,
    options: List<PickerOption<T>>,
    selected: Set<T>,
    onDismiss: () -> Unit,
    onSelectionChange: (Set<T>) -> Unit,
    multiSelect: Boolean = false,
    searchable: Boolean = options.size > 6
) {
    val sheetState = rememberModalBottomSheetState()
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) options else options.filter {
        it.label.contains(query, ignoreCase = true) || it.supporting?.contains(query, ignoreCase = true) == true
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Space.md))
            if (searchable) {
                VervanSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search",
                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm)
                )
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(filtered, key = { it.label }) { option ->
                    val isSelected = option.value in selected
                    ListItem(
                        headlineContent = { Text(option.label) },
                        supportingContent = option.supporting?.let { { Text(it) } },
                        leadingContent = if (multiSelect) {
                            { Checkbox(checked = isSelected, onCheckedChange = null) }
                        } else null,
                        trailingContent = if (!multiSelect && isSelected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (multiSelect) {
                                onSelectionChange(if (isSelected) selected - option.value else selected + option.value)
                            } else {
                                onSelectionChange(setOf(option.value))
                                onDismiss()
                            }
                        }
                    )
                }
            }
        }
    }
}
