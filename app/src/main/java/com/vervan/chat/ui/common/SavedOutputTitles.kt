package com.vervan.chat.ui.common

import com.vervan.chat.data.db.entities.SavedOutput
import com.vervan.chat.ui.chat.savedOutputTitle

private val UUID_LABEL = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/**
 * Bookmarks made from a chat store their source message id in [SavedOutput.label] (that is how
 * the chat finds them again). Showing that id, or a generic "Saved output" for every one, made
 * the Library list unreadable; a user-written label is shown as-is, otherwise the first line of
 * the saved text.
 */
fun SavedOutput.displayTitle(fallback: String): String =
    label.takeIf { it.isNotBlank() && !UUID_LABEL.matches(it) }
        ?: savedOutputTitle(content).ifBlank { fallback }
