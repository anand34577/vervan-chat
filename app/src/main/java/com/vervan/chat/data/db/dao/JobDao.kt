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
}
