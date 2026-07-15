package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One executed tool call, recorded for audit (spec §16.3 step 13 / §2.6 "the application
 * owns … audit history"). The model *proposes*; the app *executes* and *records*. This row is
 * the durable proof of what actually happened, queryable from Diagnostics.
 */
@Entity(tableName = "tool_audit")
data class ToolAudit(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val paramsJson: String,
    val success: Boolean,
    val summary: String,
    val risk: String,
    val chatId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
