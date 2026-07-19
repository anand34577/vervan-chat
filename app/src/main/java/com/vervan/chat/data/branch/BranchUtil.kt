package com.vervan.chat.data.branch

import com.vervan.chat.data.db.entities.Message

/**
 * Pure tree-walk helpers over a chat's full message list (every branch, not just the
 * active one). no recursive SQL, no cached child-map — `all` is walked fresh
 * each call. Fine at chat-sized message counts (tens to low hundreds); revisit if a
 * chat's total message count (across all branches) ever gets large enough to matter.
 */
object BranchUtil {
    private fun childrenOf(all: List<Message>, parentId: String?): List<Message> =
        all.filter { it.parentId == parentId }.sortedBy { it.createdAt }

    /** Root-to-[leafId] path, in order. Empty if [leafId] isn't found. */
    fun pathTo(all: List<Message>, leafId: String?): List<Message> {
        if (leafId == null) return emptyList()
        val byId = all.associateBy { it.id }
        val path = mutableListOf<Message>()
        val visited = mutableSetOf<String>()
        var current = byId[leafId]
        while (current != null && visited.add(current.id)) {
            path += current
            current = current.parentId?.let { byId[it] }
        }
        return path.asReversed()
    }

    /** Walks down from [startId], following the most-recently-created child at each step. */
    fun deepestTip(all: List<Message>, startId: String): String {
        var current = startId
        val visited = mutableSetOf<String>()
        while (visited.add(current)) {
            val children = childrenOf(all, current)
            if (children.isEmpty()) return current
            current = children.maxBy { it.createdAt }.id
        }
        return current
    }

    /** This message and its siblings (same parent), oldest first. */
    fun siblingsOf(all: List<Message>, messageId: String): List<Message> {
        val message = all.find { it.id == messageId } ?: return emptyList()
        return childrenOf(all, message.parentId)
    }

    /** (1-based position, total count) among siblings — for a "‹ 2/3 ›" style indicator. */
    fun siblingPosition(all: List<Message>, messageId: String): Pair<Int, Int> {
        val siblings = siblingsOf(all, messageId)
        val index = siblings.indexOfFirst { it.id == messageId }
        return (index + 1) to siblings.size
    }

    /** Every message paired with its depth (0 = root), in depth-first order — a tree view's data source. */
    fun flattenTree(all: List<Message>): List<Pair<Message, Int>> {
        val result = mutableListOf<Pair<Message, Int>>()
        val visited = mutableSetOf<String>()
        fun visit(parentId: String?, depth: Int) {
            for (child in childrenOf(all, parentId)) {
                if (!visited.add(child.id)) continue
                result += child to depth
                visit(child.id, depth + 1)
            }
        }
        visit(null, 0)
        return result
    }
}
