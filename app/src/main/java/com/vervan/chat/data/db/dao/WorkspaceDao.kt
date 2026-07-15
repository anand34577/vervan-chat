package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vervan.chat.data.db.entities.Workspace
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    // Default Workspace always sorts first (§9's "isDefault"/pin-like ordering), then most
    // recently active (spec §8's workspace-switcher "last active time").
    @Query("SELECT * FROM workspaces WHERE archived = 0 ORDER BY isDefault DESC, lastActiveAt DESC")
    fun observeActive(): Flow<List<Workspace>>

    @Query("SELECT * FROM workspaces WHERE archived = 1 ORDER BY lastActiveAt DESC")
    fun observeArchived(): Flow<List<Workspace>>

    @Query("SELECT * FROM workspaces ORDER BY isDefault DESC, lastActiveAt DESC")
    fun observeAll(): Flow<List<Workspace>>

    @Query("SELECT * FROM workspaces WHERE id = :id")
    fun observe(id: String): Flow<Workspace?>

    @Query("SELECT * FROM workspaces WHERE id = :id")
    suspend fun get(id: String): Workspace?

    @Query("SELECT * FROM workspaces WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): Workspace?

    // §4: "removing a custom persona requires assigning another valid persona first" — this
    // lets the persona deletion flow repoint any workspace using it in one statement instead
    // of the caller having to look each one up.
    @Query("UPDATE workspaces SET personaId = :newPersonaId WHERE personaId = :oldPersonaId")
    suspend fun relinkPersona(oldPersonaId: String, newPersonaId: String)

    // Fresh installs never run the migration that seeds the Default Workspace (Room just
    // creates the table from the @Entity) — this is the cold-start seed for that case,
    // IGNORE so it's a no-op once the row already exists (mirrors PersonaDao.insertAll).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefault(workspace: Workspace)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workspace: Workspace)

    @Update
    suspend fun update(workspace: Workspace)

    @Delete
    suspend fun delete(workspace: Workspace)
}
