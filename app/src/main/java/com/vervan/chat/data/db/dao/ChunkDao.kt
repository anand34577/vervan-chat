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

    /** Reading-order counterpart to [getForKnowledgeBases] — used by
     * [com.vervan.chat.retrieval.RetrievalEngine.retrieveOverviewFallback] to sample a document
     * from beginning to end rather than by relevance score. */
    @Query("SELECT * FROM chunks WHERE knowledgeBaseId IN (:kbIds) ORDER BY documentId, chunkIndex LIMIT :limit")
    suspend fun getForKnowledgeBasesOrdered(kbIds: List<String>, limit: Int): List<Chunk>

    @Query("SELECT COUNT(DISTINCT documentId) FROM chunks WHERE knowledgeBaseId IN (:kbIds)")
    suspend fun countDocumentsForKnowledgeBases(kbIds: List<String>): Int

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

    // ORDER BY id (the primary key) used to sort here — id is a random UUID (see Chunk.id), so
    // that ordering was effectively random, not reading order. chunkIndex is what the import
    // pipeline actually assigns in extraction order (see DocumentImportManager.persistChunks'
    // raw.mapIndexed) — that's what a page/reading-order-sequential preview needs to sort by.
    @Query("SELECT * FROM chunks WHERE documentId = :documentId ORDER BY chunkIndex ASC")
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

    /** Moves a fully-built shadow index onto the stable document id during a re-index swap. */
    @Query("UPDATE chunks SET documentId = :targetDocumentId WHERE documentId = :stagedDocumentId")
    suspend fun moveChunksToDocument(stagedDocumentId: String, targetDocumentId: String)

    /** Rebuilds the manually-maintained FTS mirror after a shadow-index document-id swap. */
    @Query("INSERT INTO chunks_fts(chunkId, documentId, text) SELECT id, documentId, text FROM chunks WHERE documentId = :documentId")
    suspend fun rebuildFtsForDocument(documentId: String)
}
