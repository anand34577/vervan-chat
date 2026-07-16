package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.FlashcardSet
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardSetDao : BaseDao<FlashcardSet> {
    @Query("SELECT * FROM flashcard_sets ORDER BY lastStudiedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<FlashcardSet>>

    @Query("SELECT * FROM flashcard_sets WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): FlashcardSet?

    @Query("SELECT * FROM flashcard_sets WHERE id = :id")
    suspend fun get(id: String): FlashcardSet?

    @Query("DELETE FROM flashcard_sets WHERE name = :name")
    suspend fun deleteByName(name: String)
}
