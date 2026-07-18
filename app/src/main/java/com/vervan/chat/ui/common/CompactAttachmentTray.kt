package com.vervan.chat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space

/**
 * Compact attachment chips rendered above the composer when the user has pending attachments
 * (image / OCR / document / audio / quoted text). Replaces the previous pattern of stacking
 * each pending attachment as a full-width row up to 220dp tall — three attachments used to eat
 * 600dp of vertical real estate above the keyboard. Now they're 56dp chips in a single FlowRow,
 * matching WhatsApp/Telegram/iMessage conventions.
 *
 * Each chip: small thumbnail/icon + label + dismiss X. Tap opens a full preview (caller handles).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactAttachmentTray(
    attachments: List<PendingAttachment>,
    onDismiss: (PendingAttachment) -> Unit,
    onTap: (PendingAttachment) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return
    FlowRow(
        modifier = modifier.padding(bottom = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        attachments.forEach { attachment ->
            AttachmentChip(attachment, onDismiss = { onDismiss(attachment) }, onClick = { onTap(attachment) })
        }
    }
}

@Composable
private fun AttachmentChip(attachment: PendingAttachment, onDismiss: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(start = Space.sm, end = Space.xs)
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (attachment.thumbnail != null) {
                // Caller-supplied thumbnail composable (image bitmap / preview / etc).
                attachment.thumbnail()
            } else {
                Icon(attachment.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            text = attachment.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = Space.xs)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove ${attachment.label}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Caller-supplied description of a pending attachment. [thumbnail] is a composable lambda so
 *  the caller can render a real image preview if available; pass null to fall back to [icon]. */
data class PendingAttachment(
    val id: String,
    val label: String,
    val icon: ImageVector = Icons.Filled.Description,
    val thumbnail: (@Composable () -> Unit)? = null
)

/** Sensible presets for the common attachment kinds. */
fun imageAttachment(id: String, label: String = "Photo", thumbnail: (@Composable () -> Unit)? = null) =
    PendingAttachment(id, label, Icons.Filled.Image, thumbnail)
fun documentAttachment(id: String, label: String = "Document") = PendingAttachment(id, label, Icons.Filled.Description)
fun pdfAttachment(id: String, label: String = "PDF") = PendingAttachment(id, label, Icons.Filled.PictureAsPdf)
fun audioAttachment(id: String, label: String = "Audio") = PendingAttachment(id, label, Icons.Filled.Mic)
