package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.SavedOutput
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedOutputDao : BaseDao<SavedOutput> {
    @Query("SELECT * FROM saved_outputs WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedOutput>>

    // Recycle bin coverage (Phase 6, spec §34).
    @Query("SELECT * FROM saved_outputs WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<SavedOutput>>

    @Query("DELETE FROM saved_outputs WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Query("UPDATE saved_outputs SET sourceChatId = NULL WHERE sourceChatId = :chatId")
    suspend fun clearSourceChat(chatId: String)
}
