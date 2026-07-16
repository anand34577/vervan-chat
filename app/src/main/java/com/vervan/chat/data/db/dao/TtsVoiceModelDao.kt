package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.TtsVoiceModel
import kotlinx.coroutines.flow.Flow

@Dao
interface TtsVoiceModelDao : BaseDao<TtsVoiceModel> {
    @Query("SELECT * FROM tts_voice_models ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<TtsVoiceModel>>

    @Query("SELECT * FROM tts_voice_models WHERE engine = :engine AND language = :language LIMIT 1")
    suspend fun getByEngine(engine: String, language: String): TtsVoiceModel?

    @Query("SELECT * FROM tts_voice_models WHERE id = :id")
    suspend fun get(id: String): TtsVoiceModel?
}
