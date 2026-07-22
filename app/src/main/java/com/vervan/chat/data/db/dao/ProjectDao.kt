package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao : BaseDao<Project> {
    @Query("SELECT * FROM projects WHERE deletedAt IS NULL ORDER BY name ASC")
    fun observeAll(): Flow<List<Project>>

    /** Projects browser is scoped to the active workspace (projects now live inside a workspace,
     *  same as chats and folders). observeAll stays for cross-workspace callers (backup, name
     *  labels, tool lookup). */
    @Query("SELECT * FROM projects WHERE workspaceId = :workspaceId AND deletedAt IS NULL ORDER BY name ASC")
    fun observeForWorkspace(workspaceId: String): Flow<List<Project>>

    /** Soft-deletes every project in a workspace when the workspace itself is deleted — keeps
     *  projects from outliving their workspace as orphans. Recoverable from the recycle bin. */
    @Query("UPDATE projects SET deletedAt = :now WHERE workspaceId = :workspaceId AND deletedAt IS NULL")
    suspend fun softDeleteForWorkspace(workspaceId: String, now: Long)

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: String): Flow<Project?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): Project?

    // Recycle bin coverage.
    @Query("SELECT * FROM projects WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<Project>>

    @Query("DELETE FROM projects WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Query("UPDATE projects SET personaId = NULL WHERE personaId = :personaId")
    suspend fun clearPersona(personaId: String)
}
