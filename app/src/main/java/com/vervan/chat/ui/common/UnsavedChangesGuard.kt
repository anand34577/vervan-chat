package com.vervan.chat.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R

/**
 * Back handling for editors with an explicit Save button. System back, predictive back and the
 * toolbar arrow (call the returned function from it) all go through here: with no edits it just
 * leaves; with edits it asks "Discard changes?" instead of silently throwing them away.
 */
@Composable
fun rememberUnsavedChangesGuard(hasUnsavedChanges: () -> Boolean, onLeave: () -> Unit): () -> Unit {
    val latestHasChanges by rememberUpdatedState(hasUnsavedChanges)
    val latestOnLeave by rememberUpdatedState(onLeave)
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val attemptLeave: () -> Unit = {
        if (latestHasChanges()) confirmDiscard = true else latestOnLeave()
    }
    BackHandler(enabled = true, onBack = attemptLeave)
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    latestOnLeave()
                }) { Text(stringResource(R.string.unsaved_changes_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.unsaved_changes_keep_editing)) }
            }
        )
    }
    return attemptLeave
}
