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

    private fun childrenByParent(all: List<Message>): Map<String?, List<Message>> =
        all.groupBy { it.parentId }.mapValues { (_, children) -> children.sortedBy { it.createdAt } }

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
        val childrenIndex = childrenByParent(all)
        var current = startId
        val visited = mutableSetOf<String>()
        while (visited.add(current)) {
            val children = childrenIndex[current].orEmpty()
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

    /** Bulk form of [siblingPosition] — every message's (position, sibling count) in one grouped
     * pass instead of two O(n) scans (siblingsOf's filter + indexOfFirst) per message. Calling
     * [siblingPosition] once per rendered row inside a message list is the case this exists for:
     * that list recomposes on every streamed token (~80ms), so per-row O(n) scans there become
     * an O(n^2) cost across the whole visible list on every one of those recompositions — this
     * reduces it to one O(n) pass, memoizable by the caller alongside the message list itself. */
    fun siblingPositions(all: List<Message>): Map<String, Pair<Int, Int>> {
        val byParent = childrenByParent(all)
        val result = HashMap<String, Pair<Int, Int>>(all.size)
        for (siblings in byParent.values) {
            siblings.forEachIndexed { index, message -> result[message.id] = (index + 1) to siblings.size }
        }
        return result
    }

    /** Every message paired with its depth (0 = root), in depth-first order — a tree view's data source. */
    fun flattenTree(all: List<Message>): List<Pair<Message, Int>> {
        val childrenIndex = childrenByParent(all)
        val result = mutableListOf<Pair<Message, Int>>()
        val visited = mutableSetOf<String>()
        fun visit(parentId: String?, depth: Int) {
            for (child in childrenIndex[parentId].orEmpty()) {
                if (!visited.add(child.id)) continue
                result += child to depth
                visit(child.id, depth + 1)
            }
        }
        visit(null, 0)
        return result
    }
}
