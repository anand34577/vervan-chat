package com.vervan.chat.ui.tools

import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    eyebrow: String = "Uses the active model's privacy setting",
    modifier: Modifier = Modifier,
) {
    FeatureHero(
        icon = icon,
        eyebrow = eyebrow,
        title = title,
        body = body,
        modifier = modifier,
        trailing = {
            Icon(
                Icons.Filled.Info,
                contentDescription = stringResource(R.string.ui_toolworkspacecomponents_45_privacy_depends_on_the_active_model),
                tint = MaterialTheme.colorScheme.primary
            )
        }
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
        shape = MaterialTheme.shapes.medium
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
        shape = MaterialTheme.shapes.medium,
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
