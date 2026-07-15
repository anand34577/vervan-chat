package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** Background job types (spec §32.1). */
enum class JobType { DOCUMENT_INDEXING, OCR, EMBEDDING, BATCH_SUMMARIZE, EXPORT, BACKUP, INDEX_REBUILD, MODEL_VERIFY, BENCHMARK, LONG_GENERATION }

/** Job lifecycle (spec §32.2). */
enum class JobState { WAITING, PREPARING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

/**
 * A durable record of a background job (spec §32.1, §76). Long-running work (document
 * indexing, exports, backups, benchmark) writes its state here so the Job Queue screen can
 * show what's pending/running/done without holding it in memory.
 */
@Entity(tableName = "jobs")
data class JobRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: JobType,
    val label: String,
    val state: JobState = JobState.WAITING,
    val progress: Int = 0,
    val detail: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
