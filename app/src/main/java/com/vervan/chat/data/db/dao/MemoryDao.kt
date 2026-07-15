package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vervan.chat.data.db.entities.Memory
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Memory>>

    @Query(
        "SELECT * FROM memories WHERE deletedAt IS NULL AND enabled = 1 AND (scope = 'GLOBAL' " +
            "OR (scope = 'PERSONA' AND scopeRefId = :personaId) " +
            "OR (scope = 'PROJECT' AND scopeRefId = :projectId))"
    )
    suspend fun getApplicable(personaId: String?, projectId: String?): List<Memory>

    @Query("SELECT * FROM memories WHERE key = :key AND scope = :scope AND (scopeRefId IS :scopeRefId) AND deletedAt IS NULL LIMIT 1")
    suspend fun findByKey(key: String, scope: com.vervan.chat.data.db.entities.MemoryScope, scopeRefId: String?): Memory?

    // Recycle bin coverage (Phase 6, spec §34).
    @Query("SELECT * FROM memories WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<Memory>>

    @Query("DELETE FROM memories WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long)

    // Universal search (Phase 6, spec §29) — memories weren't searchable before.
    @Query("SELECT * FROM memories WHERE deletedAt IS NULL AND text LIKE '%' || :q || '%' ORDER BY createdAt DESC LIMIT 20")
    suspend fun search(q: String): List<Memory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: Memory)

    @Update
    suspend fun update(memory: Memory)

    @Delete
    suspend fun delete(memory: Memory)
}
