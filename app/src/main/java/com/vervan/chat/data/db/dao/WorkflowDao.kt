package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vervan.chat.data.db.entities.Workflow
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowDao : BaseDao<Workflow> {
    @Query("SELECT * FROM workflows WHERE deletedAt IS NULL ORDER BY isBuiltIn DESC, name ASC")
    fun observeAll(): Flow<List<Workflow>>

    @Query("SELECT * FROM workflows WHERE id = :id")
    suspend fun get(id: String): Workflow?

    // Recycle bin coverage.
    @Query("SELECT * FROM workflows WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<Workflow>>

    @Query("DELETE FROM workflows WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(workflows: List<Workflow>)
}
