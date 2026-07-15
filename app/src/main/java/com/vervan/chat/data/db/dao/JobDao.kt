package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vervan.chat.data.db.entities.JobRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY updatedAt DESC LIMIT 100")
    fun observeAll(): Flow<List<JobRecord>>

    @Query("SELECT * FROM jobs WHERE state IN ('WAITING','PREPARING','RUNNING','PAUSED') ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<JobRecord>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: String): JobRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: JobRecord)

    @Update
    suspend fun update(job: JobRecord)

    @Query("DELETE FROM jobs WHERE state IN ('COMPLETED','FAILED','CANCELLED') AND updatedAt < :cutoff")
    suspend fun purgeFinishedBefore(cutoff: Long)
}
