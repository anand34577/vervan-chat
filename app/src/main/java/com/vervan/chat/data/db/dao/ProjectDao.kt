package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vervan.chat.data.db.entities.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE deletedAt IS NULL ORDER BY name ASC")
    fun observeAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: String): Flow<Project?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): Project?

    // Recycle bin coverage (Phase 6, spec §34).
    @Query("SELECT * FROM projects WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<Project>>

    @Query("DELETE FROM projects WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Query("UPDATE projects SET personaId = NULL WHERE personaId = :personaId")
    suspend fun clearPersona(personaId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: Project)

    @Delete
    suspend fun delete(project: Project)
}
