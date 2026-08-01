package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vervan.chat.data.db.entities.Chunk
import com.vervan.chat.data.db.entities.ChunkFts
import kotlinx.coroutines.flow.Flow

@Dao
interface ChunkDao {
    /** Inserts [chunks] and keeps [ChunkFts] (see its doc comment) in sync in the same
     * transaction — every caller goes through this, never [insertAllChunksOnly] directly, so the
     * FTS index can't silently drift out of sync with the real table. */
    @Transaction
    suspend fun insertAll(chunks: List<Chunk>) {
        insertAllChunksOnly(chunks)
        insertFts(chunks.map { ChunkFts(chunkId = it.id, documentId = it.documentId, text = it.text) })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllChunksOnly(chunks: List<Chunk>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(entries: List<ChunkFts>)

    // scoring (keyword + cosine) happens in Kotlin over this result set, not in
    // SQL — fine up to a few thousand chunks (a realistic personal KB). Past that, move to
    // FTS5 for keyword and an ANN index for vectors.
    // [limit] bounds the fetch itself (RetrievalEngine passes MAX_CHUNKS_PER_QUERY + 1) so an
    // oversized KB's memory spike happens on a bounded read, not on the full table scan that a
    // post-fetch cap alone would still incur.
    // ORDER BY id makes the cap deterministic — without it, which chunks land in the first
    // [limit] rows once a KB exceeds the cap is unspecified by SQLite (no index backs this
    // query's natural order, and it isn't guaranteed to be insertion order either).
    @Query("SELECT * FROM chunks WHERE knowledgeBaseId IN (:kbIds) ORDER BY id LIMIT :limit")
    suspend fun getForKnowledgeBases(kbIds: List<String>, limit: Int): List<Chunk>

    /** Candidate chunk ids matching an FTS4 MATCH expression, scoped to [kbIds] via a join back
     * to [Chunk] (chunks_fts itself carries no knowledgeBaseId — see [ChunkFts]). [RetrievalEngine]
     * builds [matchQuery] from the user's search terms; this replaces the old per-chunk Kotlin
     * substring scan with an actual index lookup. */
    @Query(
        "SELECT chunks_fts.chunkId FROM chunks_fts " +
            "JOIN chunks ON chunks.id = chunks_fts.chunkId " +
            "WHERE chunks_fts MATCH :matchQuery AND chunks.knowledgeBaseId IN (:kbIds) " +
            "ORDER BY chunks.id LIMIT :limit"
    )
    suspend fun matchFts(matchQuery: String, kbIds: List<String>, limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM chunks WHERE documentId = :documentId")
    fun observeCountForDocument(documentId: String): Flow<Int>

    // Privacy dashboard's indexing summary — a chunk with a null embedding is still
    // keyword-searchable (see the Chunk.embedding doc comment) but not semantically indexed yet.
    @Query("SELECT COUNT(*) FROM chunks")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM chunks WHERE embedding IS NOT NULL")
    fun observeEmbeddedCount(): Flow<Int>

    @Query("SELECT * FROM chunks WHERE documentId = :documentId ORDER BY id ASC")
    fun observeForDocument(documentId: String): Flow<List<Chunk>>

    @Query("SELECT * FROM chunks WHERE id = :chunkId")
    suspend fun getChunk(chunkId: String): Chunk?

    @Query("SELECT * FROM chunks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<Chunk>

    /** Unscoped counterpart to [matchFts] — global search has no kbIds to filter by, it's
     * searching every knowledge base at once. */
    @Query(
        "SELECT chunks_fts.chunkId FROM chunks_fts " +
            "WHERE chunks_fts MATCH :matchQuery ORDER BY chunks_fts.chunkId LIMIT :limit"
    )
    suspend fun matchFtsAll(matchQuery: String, limit: Int): List<String>

    /** Deletes [documentId]'s chunks and its FTS entries together — see [insertAll]. */
    @Transaction
    suspend fun deleteForDocument(documentId: String) {
        deleteFtsForDocument(documentId)
        deleteChunksOnlyForDocument(documentId)
    }

    @Query("DELETE FROM chunks_fts WHERE documentId = :documentId")
    suspend fun deleteFtsForDocument(documentId: String)

    @Query("DELETE FROM chunks WHERE documentId = :documentId")
    suspend fun deleteChunksOnlyForDocument(documentId: String)
}
