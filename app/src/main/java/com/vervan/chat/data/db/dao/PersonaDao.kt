package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vervan.chat.data.db.entities.Persona
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas WHERE deletedAt IS NULL ORDER BY isBuiltIn DESC, name ASC")
    fun observePersonas(): Flow<List<Persona>>

    @Query("SELECT * FROM personas WHERE id = :id AND deletedAt IS NULL")
    suspend fun getPersona(id: String): Persona?

    @Query("SELECT * FROM personas WHERE deletedAt IS NULL AND name LIKE '%' || :q || '%' LIMIT 20")
    suspend fun search(q: String): List<Persona>

    // Recycle bin coverage (Phase 6, spec §34).
    @Query("SELECT * FROM personas WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<Persona>>

    @Query("DELETE FROM personas WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @androidx.room.Delete
    suspend fun delete(persona: Persona)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(persona: Persona)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(personas: List<Persona>)
}
