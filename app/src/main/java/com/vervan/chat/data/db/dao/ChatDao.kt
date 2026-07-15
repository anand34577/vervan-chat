package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vervan.chat.data.db.entities.Chat
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE archived = 0 AND deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeChats(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeAllChats(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE folderId = :folderId AND deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeForFolder(folderId: String): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE folderId IS NULL AND projectId IS NULL AND archived = 0 AND deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeUnfiled(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE id = :id")
    fun observeChat(id: String): Flow<Chat?>

    @Query("SELECT * FROM chats WHERE projectId = :projectId AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeForProject(projectId: String): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE workspaceId = :workspaceId AND deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeForWorkspace(workspaceId: String): Flow<List<Chat>>

    @Query("SELECT COUNT(*) FROM chats WHERE workspaceId = :workspaceId AND deletedAt IS NULL AND archived = 0")
    fun observeActiveCountForWorkspace(workspaceId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM chats WHERE workspaceId = :workspaceId AND deletedAt IS NULL AND archived = 1")
    fun observeArchivedCountForWorkspace(workspaceId: String): Flow<Int>

    // Workspace deletion cascade (§13) needs the actual rows first so their messages/tool-audit
    // records can be cleaned up too, before the bulk DELETE below.
    @Query("SELECT * FROM chats WHERE workspaceId = :workspaceId")
    suspend fun getForWorkspace(workspaceId: String): List<Chat>

    @Query("DELETE FROM chats WHERE workspaceId = :workspaceId")
    suspend fun deleteForWorkspace(workspaceId: String)

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getChat(id: String): Chat?

    @Query("SELECT * FROM chats WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE deletedAt IS NULL AND title LIKE '%' || :q || '%' ORDER BY updatedAt DESC LIMIT 20")
    suspend fun search(q: String): List<Chat>

    @Query("DELETE FROM chats WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Query("UPDATE chats SET modelId = NULL WHERE modelId = :modelId")
    suspend fun clearModel(modelId: String)

    @Query("UPDATE chats SET personaId = NULL WHERE personaId = :personaId")
    suspend fun clearPersona(personaId: String)

    @Query("UPDATE chats SET projectId = NULL WHERE projectId = :projectId")
    suspend fun clearProject(projectId: String)

    @Query("UPDATE chats SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chat: Chat)

    @Update
    suspend fun update(chat: Chat)

    @Delete
    suspend fun delete(chat: Chat)
}
