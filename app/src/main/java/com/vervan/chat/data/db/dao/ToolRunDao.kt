package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.ToolRun
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolRunDao : BaseDao<ToolRun> {
    @Query("SELECT * FROM tool_runs WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ToolRun>>

    @Query("SELECT * FROM tool_runs WHERE deletedAt IS NULL ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 12): Flow<List<ToolRun>>

    @Query("SELECT * FROM tool_runs WHERE id = :id")
    suspend fun get(id: String): ToolRun?

    @Query("UPDATE tool_runs SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())
}
