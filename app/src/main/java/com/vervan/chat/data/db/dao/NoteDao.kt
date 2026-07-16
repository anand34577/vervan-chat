package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao : BaseDao<Note> {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId AND deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeForFolder(folderId: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE projectId = :projectId AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeForProject(projectId: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): Note?

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND (title LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%') ORDER BY updatedAt DESC LIMIT 20")
    suspend fun search(q: String): List<Note>

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Query("UPDATE notes SET projectId = NULL WHERE projectId = :projectId")
    suspend fun clearProject(projectId: String)

    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: String)
}
