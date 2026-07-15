package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vervan.chat.data.db.entities.ToolAudit
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolAuditDao {
    @Query("SELECT * FROM tool_audit ORDER BY createdAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<ToolAudit>>

    @Query("SELECT COUNT(*) FROM tool_audit")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audit: ToolAudit)

    @Query("DELETE FROM tool_audit WHERE createdAt < :cutoff")
    suspend fun purgeBefore(cutoff: Long)

    @Query("DELETE FROM tool_audit WHERE chatId = :chatId")
    suspend fun deleteForChat(chatId: String)
}
