package com.vervan.chat.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vervan.chat.VervanApp
import com.vervan.chat.ui.common.EmptyState
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A one-hop "local graph" (Obsidian-style) centered on a single node at a time, rather than
 * rendering the whole app's containers at once — see [KnowledgeGraphViewModel]'s doc comment for
 * why. Tapping a neighbor recenters the graph on it; the center card's own icon button jumps into
 * that entity's real screen via [onOpenEntity].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeGraphScreen(onBack: () -> Unit, onOpenEntity: (GraphNode) -> Unit) {
    val app = LocalContext.current.applicationContext as VervanApp
    val vm: KnowledgeGraphViewModel = viewModel(factory = viewModelFactory { initializer { KnowledgeGraphViewModel(app) } })
    val center by vm.center.collectAsState()
    val neighbors by vm.neighbors.collectAsState()
    val loading by vm.loading.collectAsState()
    val canGoBack by vm.canGoBack.collectAsState()
    val query by vm.query.collectAsState()
    val searchResults by vm.searchResults.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    VervanSearchField(
                        value = query,
                        onValueChange = vm::setQuery,
                        placeholder = "Jump to anything"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (canGoBack) vm.back() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (query.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    Text(
                        "No matches",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Space.lg)
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(searchResults, key = { it.type.name + it.id }) { node ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { vm.open(node) }
                                    .padding(horizontal = Space.lg, vertical = Space.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(nodeIcon(node.type), contentDescription = null, tint = nodeColor(node.type))
                                Column(Modifier.padding(start = Space.md).weight(1f)) {
                                    Text(node.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(node.type.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            androidx.compose.material3.HorizontalDivider()
                        }
                    }
                }
            } else if (center == null) {
                EmptyState(icon = Icons.Filled.AutoAwesome, title = "Nothing to graph yet", body = "Create a workspace, chat, or note to see connections here.")
            } else {
                GraphCanvas(
                    center = center!!,
                    neighbors = neighbors,
                    loading = loading,
                    onOpenEntity = onOpenEntity,
                    onSelectNeighbor = vm::open
                )
            }
        }
    }
}

@Composable
private fun GraphCanvas(
    center: GraphNode,
    neighbors: List<GraphEdge>,
    loading: Boolean,
    onOpenEntity: (GraphNode) -> Unit,
    onSelectNeighbor: (GraphNode) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
        val centerOffset = Offset(widthPx / 2f, heightPx / 2f)
        val radiusPx = min(widthPx, heightPx) / 2f * 0.68f
        val positions = neighbors.mapIndexed { index, edge ->
            val angle = (2 * Math.PI * index / neighbors.size.coerceAtLeast(1)) - Math.PI / 2
            edge to Offset(
                centerOffset.x + (radiusPx * cos(angle)).toFloat(),
                centerOffset.y + (radiusPx * sin(angle)).toFloat()
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            positions.forEach { (_, pos) ->
                drawLine(
                    color = Color.Gray.copy(alpha = 0.35f),
                    start = centerOffset,
                    end = pos,
                    strokeWidth = 2f
                )
            }
        }

        // Center node — the focused entity. Its own icon button opens the real screen for it;
        // tapping elsewhere on the graph just explores (recenters).
        with(androidx.compose.ui.platform.LocalDensity.current) {
            NodeChip(
                node = center,
                emphasized = true,
                modifier = Modifier.offset(
                    x = (centerOffset.x - 70.dp.toPx() / 2).toDp(),
                    y = (centerOffset.y - 24.dp.toPx()).toDp()
                ),
                trailingAction = { IconButton(onClick = { onOpenEntity(center) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open") } }
            )
            positions.forEach { (edge, pos) ->
                Column(
                    modifier = Modifier.offset(
                        x = (pos.x - 60.dp.toPx() / 2).toDp(),
                        y = (pos.y - 32.dp.toPx()).toDp()
                    ).widthIn(max = 120.dp)
                ) {
                    Text(
                        edge.relation,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    NodeChip(node = edge.node, emphasized = false, onClick = { onSelectNeighbor(edge.node) })
                }
            }
        }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(Space.lg))
        }
        if (!loading && neighbors.isEmpty()) {
            Text(
                "No connections found for this item yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(y = 8.dp).padding(Space.lg)
            )
        }
    }
}

@Composable
private fun NodeChip(
    node: GraphNode,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier.widthIn(max = if (emphasized) 180.dp else 120.dp),
        onClick = onClick ?: {},
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) nodeColor(node.type).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(Modifier.padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = nodeColor(node.type).copy(alpha = 0.25f)) {
                Icon(nodeIcon(node.type), contentDescription = null, tint = nodeColor(node.type), modifier = Modifier.padding(4.dp))
            }
            Text(
                node.label,
                style = if (emphasized) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Space.xs).weight(1f, fill = false)
            )
            trailingAction?.invoke()
        }
    }
}

private fun nodeIcon(type: GraphNodeType): ImageVector = when (type) {
    GraphNodeType.WORKSPACE -> Icons.Filled.Workspaces
    GraphNodeType.PROJECT -> Icons.AutoMirrored.Filled.MenuBook
    GraphNodeType.FOLDER -> Icons.Filled.Folder
    GraphNodeType.CHAT -> Icons.AutoMirrored.Filled.Chat
    GraphNodeType.NOTE -> Icons.Filled.Edit
    GraphNodeType.KNOWLEDGE_BASE -> Icons.AutoMirrored.Filled.MenuBook
    GraphNodeType.DOCUMENT -> Icons.Filled.Description
    GraphNodeType.MEMORY -> Icons.Filled.Psychology
    GraphNodeType.PERSONA -> Icons.Outlined.Person
}

@Composable
private fun nodeColor(type: GraphNodeType): Color = when (type) {
    GraphNodeType.WORKSPACE -> MaterialTheme.colorScheme.primary
    GraphNodeType.PROJECT -> MaterialTheme.colorScheme.tertiary
    GraphNodeType.FOLDER -> MaterialTheme.colorScheme.secondary
    GraphNodeType.CHAT -> MaterialTheme.colorScheme.primary
    GraphNodeType.NOTE -> MaterialTheme.colorScheme.tertiary
    GraphNodeType.KNOWLEDGE_BASE -> MaterialTheme.colorScheme.secondary
    GraphNodeType.DOCUMENT -> MaterialTheme.colorScheme.secondary
    GraphNodeType.MEMORY -> MaterialTheme.colorScheme.tertiary
    GraphNodeType.PERSONA -> MaterialTheme.colorScheme.primary
}
