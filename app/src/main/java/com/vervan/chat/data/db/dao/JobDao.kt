package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.JobRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao : BaseDao<JobRecord> {
    @Query("SELECT * FROM jobs ORDER BY updatedAt DESC LIMIT 100")
    fun observeAll(): Flow<List<JobRecord>>

    @Query("SELECT * FROM jobs WHERE state IN ('WAITING','PREPARING','RUNNING','PAUSED') ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<JobRecord>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: String): JobRecord?

    @Query("DELETE FROM jobs WHERE state IN ('COMPLETED','FAILED','CANCELLED') AND updatedAt < :cutoff")
    suspend fun purgeFinishedBefore(cutoff: Long)

    @Query("DELETE FROM jobs WHERE state IN ('COMPLETED','FAILED','CANCELLED')")
    suspend fun clearFinished()

    @Query("UPDATE jobs SET state = 'CANCELLED', detail = 'Stopped by user', updatedAt = :timestamp WHERE id = :id AND state IN ('WAITING','PREPARING','RUNNING','PAUSED')")
    suspend fun requestStop(id: String, timestamp: Long = System.currentTimeMillis())

    // Cold-start recovery — nothing can genuinely still be WAITING/PREPARING/RUNNING/PAUSED from
    // a *previous* process (every job's coroutine dies with it), so any row still in one of those
    // states at the next launch is orphaned: its own FAILED/CANCELLED write got cut off by the
    // same process death, not still in flight. Same "recover from process death" concern as
    // messageDao().getUnfinished() in VervanApp's cold-start housekeeping, just for jobs.
    @Query("UPDATE jobs SET state = 'FAILED', detail = 'Interrupted — the app closed before this finished. Retry it.', updatedAt = :timestamp WHERE state IN ('WAITING','PREPARING','RUNNING','PAUSED')")
    suspend fun failOrphanedActive(timestamp: Long = System.currentTimeMillis())
}
