package com.vervan.chat.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.vervan.chat.ui.common.VervanIconButton as IconButton
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
import com.vervan.chat.ui.common.FeatureHero
import com.vervan.chat.ui.common.PageContainer
import com.vervan.chat.ui.common.VervanSearchField
import com.vervan.chat.ui.common.VervanSectionHeader
import com.vervan.chat.ui.common.VervanTopAppBar as TopAppBar
import com.vervan.chat.ui.theme.Space
import com.vervan.chat.ui.theme.SurfaceRole
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
                title = { Text("Knowledge graph") },
                navigationIcon = {
                    IconButton(onClick = { if (canGoBack) vm.back() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        PageContainer(Modifier.fillMaxSize().padding(padding), maxContentWidth = 1040.dp) {
          Column(Modifier.fillMaxSize().padding(top = Space.sm)) {
            FeatureHero(
                icon = Icons.Filled.AutoAwesome,
                eyebrow = "CONNECTED CONTEXT",
                title = "Knowledge graph",
                body = "Explore how your workspace, conversations, sources, and memories connect.",
                modifier = Modifier.padding(bottom = Space.md)
            )
            VervanSearchField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = "Search your connected context",
                modifier = Modifier.padding(bottom = Space.md)
            )
            if (query.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.AutoAwesome,
                        title = "No matches yet",
                        body = "Try a shorter name or search for a workspace, chat, note, document, or memory.",
                        modifier = Modifier.fillMaxSize(),
                        centered = true
                    )
                } else {
                    PageContainer(Modifier.fillMaxSize(), maxContentWidth = 840.dp) {
                        Card(
                            Modifier.fillMaxSize().padding(vertical = Space.sm),
                            colors = SurfaceRole.Card.cardColors(),
                            border = SurfaceRole.Card.border(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            LazyColumn {
                                items(searchResults, key = { it.type.name + it.id }) { node ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clickable { vm.open(node) }
                                            .padding(horizontal = Space.lg, vertical = Space.md),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = nodeColor(node.type).copy(alpha = 0.16f)
                                        ) {
                                            Icon(
                                                nodeIcon(node.type),
                                                contentDescription = null,
                                                tint = nodeColor(node.type),
                                                modifier = Modifier.padding(Space.sm)
                                            )
                                        }
                                        Column(Modifier.padding(start = Space.md).weight(1f)) {
                                            Text(node.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(node.type.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Open ${node.label}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    androidx.compose.material3.HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            } else if (center == null) {
                EmptyState(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Nothing to graph yet",
                    body = "Create a workspace, chat, or note to see connections here.",
                    modifier = Modifier.fillMaxSize(),
                    centered = true
                )
            } else {
                GraphCanvas(
                    center = center!!,
                    neighbors = neighbors,
                    loading = loading,
                    onOpenEntity = onOpenEntity,
                    onSelectNeighbor = vm::open,
                    modifier = Modifier.weight(1f)
                )
            }
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
    onSelectNeighbor: (GraphNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleNeighbors = neighbors.take(8)
    Column(modifier.fillMaxSize()) {
      Surface(
        modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 300.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
      ) {
       BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
        val centerOffset = Offset(widthPx / 2f, heightPx * 0.55f)
        val radiusPx = min(widthPx, heightPx) * 0.30f
        val positions = visibleNeighbors.mapIndexed { index, edge ->
            val angle = (2 * Math.PI * index / visibleNeighbors.size.coerceAtLeast(1)) - Math.PI / 2
            edge to Offset(
                centerOffset.x + (radiusPx * cos(angle)).toFloat(),
                centerOffset.y + (radiusPx * sin(angle)).toFloat()
            )
        }
        val graphLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.10f)
        val gridStep = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }

        Canvas(Modifier.fillMaxSize()) {
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += gridStep
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += gridStep
            }
            positions.forEach { (_, pos) ->
                drawLine(
                    color = graphLineColor,
                    start = centerOffset,
                    end = pos,
                    strokeWidth = 2f
                )
            }
        }

        Card(
            modifier = Modifier.align(Alignment.TopStart).padding(Space.lg),
            colors = SurfaceRole.Card.cardColors(),
            border = SurfaceRole.Card.border(),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(Modifier.padding(horizontal = Space.md, vertical = Space.sm)) {
                Text("CONNECTED CONTEXT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "${neighbors.size} connection${if (neighbors.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Focused on ${center.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Center node — the focused entity. Its own icon button opens the real screen for it;
        // tapping elsewhere on the graph just explores (recenters).
        with(androidx.compose.ui.platform.LocalDensity.current) {
            NodeChip(
                node = center,
                emphasized = true,
                modifier = Modifier.offset(
                    x = (centerOffset.x - 90.dp.toPx()).toDp(),
                    y = (centerOffset.y - 28.dp.toPx()).toDp()
                ),
                trailingAction = { IconButton(onClick = { onOpenEntity(center) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open") } }
            )
            positions.forEach { (edge, pos) ->
                Column(
                    modifier = Modifier.offset(
                        x = (pos.x - 66.dp.toPx()).toDp(),
                        y = (pos.y - 34.dp.toPx()).toDp()
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
            CircularProgressIndicator(modifier = Modifier.align(Alignment.TopEnd).padding(Space.lg))
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
      if (neighbors.isNotEmpty()) {
          VervanSectionHeader("Connections", count = neighbors.size, topPadding = Space.md, bottomPadding = Space.sm)
          LazyRow(
              horizontalArrangement = Arrangement.spacedBy(Space.sm),
              contentPadding = PaddingValues(bottom = Space.sm)
          ) {
              itemsIndexed(neighbors, key = { _, edge -> edge.node.type.name + edge.node.id }) { _, edge ->
                  Surface(
                      onClick = { onSelectNeighbor(edge.node) },
                      shape = MaterialTheme.shapes.small,
                      color = MaterialTheme.colorScheme.surfaceContainerHigh,
                      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                  ) {
                      Row(Modifier.padding(horizontal = Space.sm, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                          Icon(nodeIcon(edge.node.type), contentDescription = null, tint = nodeColor(edge.node.type), modifier = Modifier.size(18.dp))
                          Column(Modifier.padding(start = Space.sm).widthIn(max = 160.dp)) {
                              Text(edge.node.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                              Text(edge.relation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                          }
                      }
                  }
              }
          }
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
        ),
        border = if (emphasized) BorderStroke(1.dp, nodeColor(node.type).copy(alpha = 0.58f)) else SurfaceRole.Raised.border(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.small, color = nodeColor(node.type).copy(alpha = 0.25f)) {
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
