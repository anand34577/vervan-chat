package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class ToolRunState { RUNNING, COMPLETED, INTERRUPTED, FAILED }

/** Durable output from a one-shot AI tool, independent of the tool screen lifecycle. */
@Entity(tableName = "tool_runs", indices = [Index("toolRoute"), Index("updatedAt")])
data class ToolRun(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val toolRoute: String,
    val toolName: String,
    val input: String,
    val output: String = "",
    val state: ToolRunState = ToolRunState.RUNNING,
    val errorMessage: String? = null,
    val modelId: String? = null,
    val modelName: String? = null,
    val backend: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)
