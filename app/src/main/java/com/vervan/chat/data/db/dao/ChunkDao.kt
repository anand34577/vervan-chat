package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vervan.chat.data.db.entities.Chunk
import kotlinx.coroutines.flow.Flow

@Dao
interface ChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<Chunk>)

    // scoring (keyword + cosine) happens in Kotlin over this result set, not in
    // SQL — fine up to a few thousand chunks (a realistic personal KB). Past that, move to
    // FTS5 for keyword and an ANN index for vectors.
    // [limit] bounds the fetch itself (RetrievalEngine passes MAX_CHUNKS_PER_QUERY + 1) so an
    // oversized KB's memory spike happens on a bounded read, not on the full table scan that a
    // post-fetch cap alone would still incur.
    @Query("SELECT * FROM chunks WHERE knowledgeBaseId IN (:kbIds) LIMIT :limit")
    suspend fun getForKnowledgeBases(kbIds: List<String>, limit: Int): List<Chunk>

    @Query("SELECT COUNT(*) FROM chunks WHERE documentId = :documentId")
    fun observeCountForDocument(documentId: String): Flow<Int>

    @Query("SELECT * FROM chunks WHERE documentId = :documentId ORDER BY id ASC")
    fun observeForDocument(documentId: String): Flow<List<Chunk>>

    @Query("SELECT * FROM chunks WHERE id = :chunkId")
    suspend fun getChunk(chunkId: String): Chunk?

    @Query("DELETE FROM chunks WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String)
}
