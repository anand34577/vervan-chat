package com.vervan.chat.ui.tools

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.IconAffordance
import com.vervan.chat.ui.common.IconAffordanceSize
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole

@Composable
internal fun ToolIntro(
    icon: ImageVector,
    title: String,
    body: String,
    eyebrow: String = stringResource(R.string.tool_intro_eyebrow),
    modifier: Modifier = Modifier,
) {
    // No trailing ⓘ: it looked tappable but did nothing, and sat next to the dismiss button.
    FeatureHero(
        icon = icon,
        eyebrow = eyebrow,
        // The app bar already names the tool; the intro says what it does in one line.
        title = null,
        dismissKey = title,
        body = body,
        modifier = modifier
    )
}

@Composable
internal fun ToolSection(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = SurfaceRole.Card.cardColors(),
        border = SurfaceRole.Card.border(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    IconAffordance(
                        icon = it,
                        size = IconAffordanceSize.Compact,
                        modifier = Modifier.padding(end = Space.md)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    description?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            content()
        }
    }
}

@Composable
internal fun ToolResultHeader(
    title: String,
    supportingText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = SurfaceRole.Raised.border(),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(Modifier.fillMaxWidth().padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null)
            Column(Modifier.padding(start = Space.md)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(supportingText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** "1 · Add text" — the numbered step heading every tool screen uses, so each tool reads as the
 *  same input → options → run → result flow. [hint] says what the step needs, in one line. */
@Composable
internal fun ToolStep(number: Int?, title: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(modifier.fillMaxWidth().padding(top = Space.lg, bottom = Space.sm)) {
        Text(
            if (number == null) title else "$number · $title",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        hint?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The one main action of a tool: full width, and when it can't run yet it says why underneath
 * ([blockedReason]) instead of just greying out. While [running], it turns into a Stop button.
 */
@Composable
internal fun ToolPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blockedReason: String? = null,
    icon: ImageVector? = null,
    running: Boolean = false,
    onStop: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(top = Space.md)) {
        if (running && onStop != null) {
            androidx.compose.material3.FilledTonalButton(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_stop), modifier = Modifier.padding(start = Space.sm))
            }
        } else {
            androidx.compose.material3.Button(
                onClick = onClick,
                enabled = blockedReason == null && !running,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (running) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
                Text(label, modifier = Modifier.padding(start = if (icon != null || running) Space.sm else 0.dp))
            }
        }
        if (blockedReason != null && !running) {
            Text(
                blockedReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** Camera / gallery (or any two-to-three source buttons) as equal-width buttons on one row. */
@Composable
internal fun ToolSourceRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm), content = content)
}

/** `1,234 / 50,000` under a text field. */
@Composable
internal fun ToolCharCount(length: Int, max: Int) {
    Text(
        "%,d / %,d".format(length, max),
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.End
    )
}

/**
 * Step 1 of every image tool: Camera and Gallery as equal-width buttons, then the chosen image
 * (or a placeholder that says what a good photo looks like). [busy] shows a reading indicator.
 */
@Composable
internal fun ToolImageSource(
    imagePath: String?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    placeholder: String = stringResource(R.string.tool_image_placeholder),
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        ToolSourceRow {
            androidx.compose.material3.OutlinedButton(onClick = onCamera, enabled = enabled && !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                Text(stringResource(R.string.media_camera), modifier = Modifier.padding(start = Space.sm))
            }
            androidx.compose.material3.OutlinedButton(onClick = onGallery, enabled = enabled && !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp))
                Text(stringResource(R.string.media_gallery), modifier = Modifier.padding(start = Space.sm))
            }
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            val bitmap = com.vervan.chat.ui.common.rememberThumbnail(imagePath, 900)
            when {
                busy -> Row(Modifier.fillMaxWidth().padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.tool_image_reading), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Space.md))
                }
                imagePath != null && bitmap != null -> androidx.compose.foundation.Image(
                    bitmap, stringResource(R.string.tool_image_selected),
                    Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(Space.sm),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                else -> Row(Modifier.fillMaxWidth().padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Space.md))
                }
            }
        }
    }
}
