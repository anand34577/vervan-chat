package com.vervan.chat.ui.common

/**
 * "Just now / 5m ago / 3h ago / 2d ago / 12/31/25" — the one relative-timestamp treatment for
 * list rows and cards. Home, Chats, Workspaces, and Workspace Detail each carried a private
 * copy of this; new screens should call this instead of adding a fifth.
 */
fun relativeTime(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - epochMs
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        diff < 7L * 86_400_000L -> "${diff / 86_400_000L}d ago"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(epochMs))
    }
}
