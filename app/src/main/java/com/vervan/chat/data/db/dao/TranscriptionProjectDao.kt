package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.TranscriptionProject
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionProjectDao : BaseDao<TranscriptionProject> {
    @Query("SELECT * FROM transcription_projects ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TranscriptionProject>>

    @Query("SELECT * FROM transcription_projects WHERE id = :id")
    suspend fun get(id: String): TranscriptionProject?

    @Query("DELETE FROM transcription_projects WHERE id = :id")
    suspend fun deleteById(id: String)
}
