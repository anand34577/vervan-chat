package com.vervan.chat.ui.common

import com.vervan.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.vervan.chat.ui.theme.SurfaceRole
import com.vervan.chat.ui.theme.VervanBreakpoints
import com.vervan.chat.ui.theme.VervanBorderProminence
import com.vervan.chat.ui.theme.VervanContentWidth
import com.vervan.chat.ui.theme.vervanBorder
import com.vervan.chat.ui.theme.vervanDividerColor
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
        val horizontalPadding = if (maxWidth < VervanBreakpoints.medium) Space.lg else Space.xxl
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

/** The one app-bar treatment used across the product: compact, tonal when scrolled,
 * and separated from content without spending vertical space on decorative headers. */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun VervanTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    Column(modifier) {
        TopAppBar(
            title = { Box(Modifier.semantics { heading() }) { title() } },
            navigationIcon = navigationIcon,
            actions = actions,
            windowInsets = windowInsets,
            colors = colors,
            scrollBehavior = scrollBehavior
        )
        HorizontalDivider(color = vervanDividerColor())
    }
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
    TextField(
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
                androidx.compose.material3.IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.vervan_clear_search))
                }
            }
        } else null,
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

/** Compact destination context. It replaces oversized hero cards on app screens. */
@Composable
fun FeatureHero(
    icon: ImageVector,
    @Suppress("UNUSED_PARAMETER") eyebrow: String,
    title: String?,
    body: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    dismissible: Boolean = true,
    /** Persists the dismissal. Defaults to [title]; pass the old title when dropping a title that
     * only repeated the app bar, so cards people already closed stay closed. */
    dismissKey: String = title ?: body
) {
    // Orientation is for the first visits. Once dismissed it stays gone, so people who know the
    // screen get their space back instead of scrolling past the same card.
    //
    // Deliberately compact: one small icon, an optional short title, two lines of body. The old
    // version (eyebrow + title + body + large icon) took ~120dp on every tool screen, and its
    // title usually repeated the app bar right above it. The eyebrow parameter is kept so the 28
    // call sites didn't all have to change, but it isn't drawn: its wording varied screen to
    // screen ("On-device assistant", "Scan and extract", ...) without telling the user anything.
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember { context.getSharedPreferences("ui_hints", android.content.Context.MODE_PRIVATE) }
    val hintKey = "hero_dismissed_" + dismissKey
    var dismissed by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(runCatching { prefs.getBoolean(hintKey, false) }.getOrDefault(false))
    }
    if (dismissible && dismissed) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = SurfaceRole.Card.cardColors(),
        border = SurfaceRole.Card.border()
    ) {
        Row(Modifier.fillMaxWidth().padding(start = Space.md, top = Space.sm, bottom = Space.sm, end = if (dismissible) Space.xs else Space.md), verticalAlignment = Alignment.CenterVertically) {
            IconAffordance(
                icon = icon,
                size = IconAffordanceSize.Compact,
                tint = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            )
            Column(
                Modifier.weight(1f).padding(start = Space.md),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (!title.isNullOrBlank()) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailing?.invoke()
            if (dismissible) {
                androidx.compose.material3.IconButton(onClick = {
                    dismissed = true
                    runCatching { prefs.edit().putBoolean(hintKey, true).apply() }
                }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.guide_dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
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
                    stringResource(R.string.vervan_arg1, it),
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
            ) { Text(actionLabel, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
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
    Card(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.lg)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Icon(tone.icon(), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (actionLabel != null && onAction != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onAction) { Text(actionLabel) }
                }
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
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 72.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = vervanBorder(VervanBorderProminence.Standard)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Space.md),
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
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    Card(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.lg)) {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}

/** Set by the navigation shell so any error card can send the user straight to the Models
 * screen instead of telling them (in prose) to go and find it. */
val LocalOpenModels = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** A failure message that explains both what happened and the next safe action. When the failure is
 * "no model is ready" and the app can navigate there, the fix is one tap away. */
@Composable
fun OperationErrorCard(
    title: String,
    message: String,
    recovery: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val openModels = LocalOpenModels.current
    val noModel = message.startsWith("No model is ready") || message.startsWith("No vision model is ready") ||
        message == stringResource(R.string.error_no_model)
    val useModelsAction = noModel && openModels != null
    ErrorCard(
        title = title,
        body = stringResource(R.string.vervan_arg1_arg2, message, recovery),
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        actionLabel = if (useModelsAction) stringResource(R.string.vervan_open_ai_models) else actionLabel,
        onAction = if (useModelsAction) openModels else onAction
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
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            onValueChange(if (newValue.length > maxLength) newValue.take(maxLength) else newValue)
        },
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        prefix = prefix?.let { { Text(it) } },
        isError = overLimit,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        textStyle = textStyle,
        // No supporting row at all when there is nothing to say: an empty one still reserves
        // a line of height under the field.
        supportingText = if (!supportingText.isNullOrEmpty() || nearLimit) {{
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (atLimit) stringResource(R.string.vervan_limit_reached_shorten_this_text) else supportingText.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (atLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // A counter is only useful when the limit is close; showing "0 / 60" under every
                // field is visual noise on forms.
                if (nearLimit) Text(
                    "${compactCount(count)} / ${compactCount(maxLength)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = counterColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = Space.sm).semantics {
                        contentDescription = "$count of $maxLength characters used"
                    }
                )
            }
        }} else null
    )
}
