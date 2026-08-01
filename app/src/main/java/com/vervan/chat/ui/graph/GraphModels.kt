package com.vervan.chat.ui.graph

enum class GraphNodeType { WORKSPACE, PROJECT, FOLDER, CHAT, NOTE, KNOWLEDGE_BASE, DOCUMENT, MEMORY, PERSONA }

data class GraphNode(val id: String, val type: GraphNodeType, val label: String)

/** One hop away from the current center node, with the relation that connects them
 * ("in project", "cites", "recalled", ...) shown as the edge label. */
data class GraphEdge(val node: GraphNode, val relation: String)
