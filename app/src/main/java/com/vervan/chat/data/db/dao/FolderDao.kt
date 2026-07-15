package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vervan.chat.data.db.entities.Folder
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY name ASC")
    fun observeAll(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: String): Folder?

    @Query("SELECT * FROM folders WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE workspaceId = :workspaceId AND deletedAt IS NULL ORDER BY name ASC")
    fun observeForWorkspace(workspaceId: String): Flow<List<Folder>>

    @Query("SELECT COUNT(*) FROM folders WHERE workspaceId = :workspaceId AND deletedAt IS NULL")
    fun observeCountForWorkspace(workspaceId: String): Flow<Int>

    // Workspace deletion cascade (§13): every folder inside a deleted workspace is deleted too.
    @Query("DELETE FROM folders WHERE workspaceId = :workspaceId")
    suspend fun deleteForWorkspace(workspaceId: String)

    @Query("DELETE FROM folders WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: Folder)

    @Update
    suspend fun update(folder: Folder)

    // Model migration relink (spec §11.12) — points every folder default at the new model
    // artifact in one statement instead of round-tripping each Folder row through update().
    @Query("UPDATE folders SET defaultModelId = :newModelId WHERE defaultModelId = :oldModelId")
    suspend fun relinkDefaultModel(oldModelId: String, newModelId: String)

    // B14: model deletion used to never check for references — clears the default instead of
    // leaving a folder silently pointing at a deleted model id.
    @Query("UPDATE folders SET defaultModelId = NULL WHERE defaultModelId = :modelId")
    suspend fun clearDefaultModel(modelId: String)

    @Query("UPDATE folders SET defaultPersonaId = NULL WHERE defaultPersonaId = :personaId")
    suspend fun clearDefaultPersona(personaId: String)

    @Delete
    suspend fun delete(folder: Folder)
}
