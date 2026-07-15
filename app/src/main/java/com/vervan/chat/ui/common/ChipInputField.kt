package com.vervan.chat.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space

/**
 * §6/§9 chip-entry field for list-of-short-strings inputs (note tags, user interests,
 * avoid-topics) — replaces comma-parsed free text so each item respects its own length limit
 * and the list respects a max item count, with the count always visible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipInputField(
    items: List<String>,
    onItemsChange: (List<String>) -> Unit,
    label: String,
    maxItemLength: Int,
    maxItemCount: Int,
    modifier: Modifier = Modifier,
    placeholder: String = "Add and press enter"
) {
    var draft by remember { mutableStateOf("") }
    val atLimit = items.size >= maxItemCount
    fun commit() {
        val value = draft.trim().take(maxItemLength)
        if (value.isNotEmpty() && !atLimit && value !in items) {
            onItemsChange(items + value)
        }
        draft = ""
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            "$label (${items.size}/$maxItemCount)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (items.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                items.forEach { item ->
                    InputChip(
                        selected = false,
                        onClick = { onItemsChange(items - item) },
                        label = { Text(item) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = "Remove $item", modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= maxItemLength) draft = it },
            enabled = !atLimit,
            placeholder = { Text(if (atLimit) "Limit reached" else placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            supportingText = { Text("${draft.length} / $maxItemLength", style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
        )
    }
}
