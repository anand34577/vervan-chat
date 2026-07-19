package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.Chat
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao : BaseDao<Chat> {
    @Query("SELECT * FROM chats WHERE archived = 0 AND deletedAt IS NULL AND (draft != '' OR EXISTS (SELECT 1 FROM messages WHERE messages.chatId = chats.id)) ORDER BY pinned DESC, updatedAt DESC")
    fun observeChats(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE deletedAt IS NULL AND (draft != '' OR EXISTS (SELECT 1 FROM messages WHERE messages.chatId = chats.id)) ORDER BY pinned DESC, updatedAt DESC")
    fun observeListableChats(): Flow<List<Chat>>

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

    @Query("UPDATE chats SET archived = :archived, updatedAt = :updatedAt WHERE id = :chatId AND deletedAt IS NULL")
    suspend fun setArchived(chatId: String, archived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET archived = :archived, updatedAt = :updatedAt WHERE id IN (:chatIds) AND deletedAt IS NULL")
    suspend fun setArchived(chatIds: Set<String>, archived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :chatId AND deletedAt IS NULL")
    suspend fun setPinned(chatId: String, pinned: Boolean, updatedAt: Long = System.currentTimeMillis())
}
