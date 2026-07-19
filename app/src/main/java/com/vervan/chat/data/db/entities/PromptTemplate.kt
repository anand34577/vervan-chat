package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "prompt_templates")
data class PromptTemplate(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    // Slash-command name, no leading "/", e.g. "summarize" for "/summarize"
    val name: String,
    val description: String = "",
    // one placeholder, "{{input}}", filled with whatever the user typed after
    // the command name. No typed-variable system (dates, selects, files, ...) — add one
    // if templates ever need more than "wrap my text in this instruction".
    val body: String,
    val isBuiltIn: Boolean = false,
    // Recycle bin coverage (Phase 6, spec §34).
    val deletedAt: Long? = null
) {
    fun expand(input: String): String =
        if (body.contains("{{input}}")) body.replace("{{input}}", input) else "$body\n\n$input"
}
