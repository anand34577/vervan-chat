package com.vervan.chat.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vervan.chat.R

/**
 * The one shared confirmation-dialog shape — every destructive/reset/conflict
 * decision in the app used to be a hand-rolled `AlertDialog`, which let button order, cancel
 * placement, and destructive-color usage drift screen to screen. [destructive] swaps the
 * confirm button to the error color; it never doubles as the primary accent for a destructive
 * action.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel ?: stringResource(R.string.action_cancel))
            }
        }
    )
}

/** Shared wording and iconography for archive actions in overflow menus. */
@Composable
fun ArchiveMenuItem(archived: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                stringResource(
                    if (archived) R.string.action_restore_archive else R.string.action_archive
                )
            )
        },
        onClick = onClick
    )
}

/** Soft deletion always says recycle bin; irreversible deletion always says forever. */
@Composable
fun DeleteMenuItem(permanent: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                stringResource(
                    if (permanent) R.string.action_delete_forever else R.string.action_recycle
                ),
                color = MaterialTheme.colorScheme.error
            )
        },
        onClick = onClick
    )
}
