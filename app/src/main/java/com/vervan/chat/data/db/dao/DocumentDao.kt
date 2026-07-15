package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vervan.chat.data.db.entities.Document
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE knowledgeBaseId = :kbId AND deletedAt IS NULL ORDER BY importedAt DESC")
    fun observeForKb(kbId: String): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE knowledgeBaseId = :kbId")
    suspend fun getForKb(kbId: String): List<Document>

    @Query("SELECT * FROM documents WHERE status NOT IN ('READY', 'FAILED', 'UNSUPPORTED') AND deletedAt IS NULL ORDER BY importedAt DESC")
    fun observeIndexing(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: String): Document?

    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE deletedAt IS NULL AND displayName LIKE '%' || :q || '%' LIMIT 20")
    suspend fun search(q: String): List<Document>

    // Document versioning (Phase 3, spec §20) — detects a same-named re-import so the import
    // pipeline can offer replace/keep-both instead of silently creating a look-alike duplicate.
    @Query("SELECT * FROM documents WHERE knowledgeBaseId = :kbId AND displayName = :name AND deletedAt IS NULL LIMIT 1")
    suspend fun findActiveByNameInKb(kbId: String, name: String): Document?

    @Query("DELETE FROM documents WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    @Query("SELECT COUNT(*) FROM documents WHERE workspaceId = :workspaceId AND deletedAt IS NULL")
    fun observeCountForWorkspace(workspaceId: String): Flow<Int>

    // Workspace deletion cascade (§13/§16) — returned so each can go through
    // DocumentImportManager.delete() (removes the copied file + embedded chunks), not a bare
    // SQL delete which would orphan them.
    @Query("SELECT * FROM documents WHERE workspaceId = :workspaceId")
    suspend fun getForWorkspace(workspaceId: String): List<Document>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: Document)

    @Update
    suspend fun update(document: Document)

    @Delete
    suspend fun delete(document: Document)
}
