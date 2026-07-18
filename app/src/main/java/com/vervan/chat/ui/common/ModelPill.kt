package com.vervan.chat.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.VervanExtraShapes
import com.vervan.chat.ui.theme.vervanBorder

/**
 * Tappable model chip used in the chat top bar and ChatContextStrip. One tap → dropdown to
 * switch active model — replaces the previous "overflow → Mode & model → Chat model row → list"
 * three-tap flow.
 *
 * Visually distinct from generic chips: a small filled dot signals engine type
 * (amber = LiteRT-LM, blue/violet = llama.cpp GGUF), giving the multi-engine LLM app the
 * identity cue the previous design entirely lacked. Tap target hits the 44dp minimum even when
 * the model name is short.
 *
 * The "no model" variant is a primary-toned call-to-action pill ("Choose a model") so users
 * landing on a fresh chat know what to do without reading the placeholder.
 */
@Composable
fun ModelPill(
    label: String,
    engineKind: ModelEngineKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoaded: Boolean = false,
    expanded: Boolean = false
) {
    val isCta = label.isBlank()
    val containerColor = if (isCta) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (isCta) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "pillChevron")

    Row(
        modifier = modifier
            .clip(VervanExtraShapes.pill)
            .background(containerColor)
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (isCta) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                shape = VervanExtraShapes.pill
            )
            .padding(horizontal = Space.md, vertical = 6.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label.ifBlank { "Choose a model" }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        if (isCta) {
            Icon(Icons.Filled.Memory, null, tint = contentColor, modifier = Modifier.size(16.dp))
        } else {
            EngineDot(engineKind, loaded = isLoaded)
        }
        Text(
            text = label.ifBlank { "Choose a model" },
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AnimatedVisibility(!isCta) {
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp).rotate(rotation)
            )
        }
    }
}

/** Tiny dot communicating which engine a model uses, color-coded consistently across the app. */
@Composable
fun EngineDot(kind: ModelEngineKind, modifier: Modifier = Modifier, loaded: Boolean = false) {
    val color = when (kind) {
        ModelEngineKind.LiteRTLM -> MaterialTheme.colorScheme.primary
        ModelEngineKind.LlamaCpp -> MaterialTheme.colorScheme.tertiary
        ModelEngineKind.Embedding -> MaterialTheme.colorScheme.secondary
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (loaded) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape) else Modifier)
    )
}

/** Engine kind abstraction. Lives in the UI layer (not the data layer) because the same enum
 *  also describes how to color a chip on screens that haven't loaded a ModelInfo (e.g. catalog
 *  rows in ModelManager before download). */
enum class ModelEngineKind { LiteRTLM, LlamaCpp, Embedding }
