package com.vervan.chat.ui.common

import androidx.compose.ui.res.stringResource
import com.vervan.chat.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import com.vervan.chat.ui.common.VervanTextButton as TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanBreakpoints
import com.vervan.chat.ui.theme.VervanContentWidth
import com.vervan.chat.ui.theme.ModernistTokens
import com.vervan.chat.ui.theme.vervanSuccess
import com.vervan.chat.ui.theme.vervanWarning

enum class StatusTone { Ready, Running, Warning, Error, Info }

/** Keeps phone layouts comfortably padded and prevents tablet content from stretching edge-to-edge. */
@Composable
fun PageContainer(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = VervanContentWidth.wide,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val horizontalPadding = if (maxWidth < VervanBreakpoints.medium) {
            ModernistTokens.Component.phoneGutter
        } else {
            ModernistTokens.Component.tabletGutter
        }
        Column(
            Modifier
                .widthIn(max = maxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
        ) { content() }
    }
}

/** Standard body for detail, form, settings, and editor screens. It owns the content width,
 * adaptive gutters, vertical rhythm, and scroll behavior so each destination does not recreate
 * a slightly different phone-only column. */
@Composable
fun ScrollablePage(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = VervanContentWidth.standard,
    content: @Composable ColumnScope.() -> Unit
) {
    PageContainer(
        modifier = modifier.padding(contentPadding),
        maxContentWidth = maxContentWidth
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.sm),
            content = content
        )
    }
}

/** The one app-bar treatment used across the product: quiet, layered, and easy to scan. */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun VervanTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        modifier = modifier,
        title = { Box(Modifier.semantics { heading() }) { title() } },
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior
    )
}

/** One search-field treatment for app bars, lists, sheets, and in-content filtering. */
@Composable
fun VervanSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
            contentDescription = placeholder
        },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                VervanIconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_vervancomponents_172_clear_search))
                }
            }
        } else null,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

/** Compact destination context. It is a real task-introduction surface, not a decorative banner. */
@Composable
fun FeatureHero(
    icon: ImageVector,
    eyebrow: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < 440.dp
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
        ) {
            if (compact) {
                Column(
                    Modifier.fillMaxWidth().padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconAffordance(
                            icon = icon,
                            size = IconAffordanceSize.Feature,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                        trailing?.let {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { it() }
                        }
                    }
                    HeroCopy(eyebrow = eyebrow, title = title, body = body)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(Space.xl),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconAffordance(
                        icon = icon,
                        size = IconAffordanceSize.Feature,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                    HeroCopy(
                        eyebrow = eyebrow,
                        title = title,
                        body = body,
                        modifier = Modifier.weight(1f).padding(start = Space.md),
                    )
                    trailing?.invoke()
                }
            }
        }
    }
}

@Composable
private fun HeroCopy(
    eyebrow: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusTone.color(): Color = when (this) {
    StatusTone.Ready -> MaterialTheme.colorScheme.vervanSuccess
    StatusTone.Running -> MaterialTheme.colorScheme.primary
    StatusTone.Warning -> MaterialTheme.colorScheme.vervanWarning
    StatusTone.Error -> MaterialTheme.colorScheme.error
    StatusTone.Info -> MaterialTheme.colorScheme.secondary
}

private fun StatusTone.icon(): ImageVector = when (this) {
    StatusTone.Ready -> Icons.Filled.CheckCircle
    StatusTone.Running -> Icons.Filled.Sync
    StatusTone.Warning -> Icons.Filled.Warning
    StatusTone.Error -> Icons.Filled.Error
    StatusTone.Info -> Icons.Filled.Info
}

@Composable
fun VervanSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    topPadding: Dp = Space.lg,
    bottomPadding: Dp = Space.sm,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = topPadding, bottom = bottomPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OverflowTooltipText(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() }
            )
            count?.let {
                Text(
                    "$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.sm)
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.padding(start = Space.sm)
            ) { Text(actionLabel, maxLines = 1) }
        }
    }
}

/**
 * A task/list row is the default interaction surface for collections, workspaces, notes, and
 * other navigable records. It keeps hierarchy in the row itself instead of turning every record
 * into a floating card. Selection is expressed by a tonal row state; separation comes from a
 * predictable surface role and generous rhythm rather than a wall of dividers.
 */
@Composable
fun ModernistListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = ModernistTokens.Layout.rowMinHeight)
    val rowColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = rowModifier,
            shape = MaterialTheme.shapes.small,
            color = rowColor,
            tonalElevation = if (selected) 2.dp else 0.dp,
            border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) else null,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    } else {
        Surface(
            modifier = rowModifier,
            shape = MaterialTheme.shapes.small,
            color = rowColor,
            tonalElevation = 0.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun StatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier
) {
    val color = tone.color()
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), MaterialTheme.shapes.small)
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(tone.icon(), contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(start = Space.xs)
        )
    }
}

/** A compact activity summary that can sit above the shell navigation bar or chat composer. */
@Composable
fun ActivityStatusPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
    }
}

@Composable
fun SystemStatusStrip(
    title: String,
    body: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val color = tone.color()
    Row(
        modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), MaterialTheme.shapes.small)
            .border(BorderStroke(ModernistTokens.Component.rule, color.copy(alpha = 0.42f)), MaterialTheme.shapes.small)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(Space.lg),
        verticalAlignment = Alignment.Top
    ) {
        Icon(tone.icon(), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = Space.md)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun ActionTile(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    iconContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 104.dp),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAffordance(
                icon = icon,
                size = IconAffordanceSize.Compact,
                tint = iconTint,
                containerColor = iconContainerColor
            )
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                OverflowTooltipText(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


@Composable
fun ErrorCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    SystemStatusStrip(
        title = title,
        body = body,
        tone = StatusTone.Error,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction
    )
}

/** A named, announced busy state for operations that may take more than a moment. */
@Composable
fun OperationProgressCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f), MaterialTheme.shapes.medium)
            .border(BorderStroke(ModernistTokens.Component.rule, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)), MaterialTheme.shapes.medium)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(Space.lg)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        progress?.let {
            LinearProgressIndicator(
                progress = { it.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = Space.md)
            )
            Text(
                "${(it.coerceIn(0f, 1f) * 100).toInt()}% complete",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs)
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** A failure message that explains both what happened and the next safe action. */
@Composable
fun OperationErrorCard(
    title: String,
    message: String,
    recovery: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    ErrorCard(
        title = title,
        body = if (recovery.isBlank()) message else "$message\n$recovery",
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        actionLabel = actionLabel,
        onAction = onAction
    )
}

/** Compact inline feedback for form and dialog validation. Runtime failures use [ErrorCard]. */
@Composable
fun ValidationMessage(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp)
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(start = Space.sm)
        )
    }
}

/** large fields use compact notation ("12.4k / 100k") while the exact value stays
 * available to accessibility services via the field's own semantics. */
private fun compactCount(n: Int): String = when {
    n < 1000 -> n.toString()
    n % 1000 == 0 -> "${n / 1000}k"
    else -> "${"%.1f".format(n / 1000f)}k"
}

@Composable
fun BoundedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    maxLength: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false,
    placeholder: String? = null,
    prefix: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else maxOf(5, minLines),
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    enabled: Boolean = true
) {
    val count = value.length
    // `value` can still exceed maxLength if it was set from outside this field (e.g. loaded
    // from a DB row written before this cap existed) — isError/overLimit stays keyed on that
    // raw count so pre-existing oversized data is still flagged, but new input is clamped below
    // so a fresh keystroke or paste can never grow it further. `atLimit` (using the clamped
    // length) drives the "Limit reached" message so the user sees it exactly when clamping
    // kicks in, not only in the legacy over-limit case.
    val overLimit = count > maxLength
    val atLimit = count >= maxLength
    val nearLimit = count >= (maxLength * 0.8f).toInt()
    val counterColor = when {
        overLimit -> MaterialTheme.colorScheme.error
        nearLimit -> MaterialTheme.colorScheme.vervanWarning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val characterDescription = stringResource(R.string.ui_characters_used, count, maxLength)
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            onValueChange(if (newValue.length > maxLength) newValue.take(maxLength) else newValue)
        },
        modifier = modifier,
        label = label?.let {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it)
                    if (required) {
                        Text(
                            "*",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }
        },
        placeholder = placeholder?.let { { Text(it) } },
        prefix = prefix?.let { { Text(it) } },
        isError = overLimit,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        textStyle = textStyle,
        supportingText = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (atLimit) stringResource(R.string.ui_bounded_text_limit_reached) else supportingText.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (atLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${compactCount(count)} / ${compactCount(maxLength)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = counterColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = Space.sm).semantics {
                        contentDescription = characterDescription
                    }
                )
            }
        }
    )
}
