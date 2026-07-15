package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vervan.chat.data.db.entities.PromptTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptTemplateDao {
    @Query("SELECT * FROM prompt_templates WHERE deletedAt IS NULL ORDER BY name ASC")
    fun observeAll(): Flow<List<PromptTemplate>>

    @Query("SELECT * FROM prompt_templates WHERE name = :name AND deletedAt IS NULL LIMIT 1")
    suspend fun findByName(name: String): PromptTemplate?

    @Query("SELECT * FROM prompt_templates WHERE id = :id")
    suspend fun get(id: String): PromptTemplate?

    // Recycle bin coverage (Phase 6, spec §34).
    @Query("SELECT * FROM prompt_templates WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<PromptTemplate>>

    @Query("DELETE FROM prompt_templates WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: PromptTemplate)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(templates: List<PromptTemplate>)

    @Delete
    suspend fun delete(template: PromptTemplate)
}
