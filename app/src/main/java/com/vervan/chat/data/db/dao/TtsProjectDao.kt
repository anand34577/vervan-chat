package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.TtsProject
import kotlinx.coroutines.flow.Flow

@Dao
interface TtsProjectDao : BaseDao<TtsProject> {
    @Query("SELECT * FROM tts_projects ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TtsProject>>

    @Query("SELECT * FROM tts_projects WHERE id = :id")
    suspend fun get(id: String): TtsProject?

    @Query("DELETE FROM tts_projects WHERE id = :id")
    suspend fun deleteById(id: String)
}
