package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.MemorySuggestion
import com.vervan.chat.data.db.entities.MemorySuggestionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MemorySuggestionDao : BaseDao<MemorySuggestion> {
    @Query("SELECT * FROM memory_suggestions WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: MemorySuggestionStatus = MemorySuggestionStatus.PENDING): Flow<List<MemorySuggestion>>

    @Query("SELECT * FROM memory_suggestions WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun observePending(): Flow<List<MemorySuggestion>>

    @Query("SELECT COUNT(*) FROM memory_suggestions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM memory_suggestions WHERE id = :id")
    suspend fun get(id: String): MemorySuggestion?

    @Query("SELECT * FROM memory_suggestions WHERE `key` = :key AND status = 'PENDING' LIMIT 1")
    suspend fun getPendingByKey(key: String): MemorySuggestion?
}
