package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 mirror of [Chunk.text], manually kept in sync by [com.vervan.chat.data.db.dao.ChunkDao]
 * (not Room's external-content/`contentEntity` FTS: that mechanism maps FTS `docid` onto the
 * content entity's underlying integer rowid, and mixing that with [Chunk]'s own String UUID
 * primary key is exactly the kind of "clever" wiring that's hard to verify without a device to
 * run it on — an explicit, manually-synced table is easier to reason about and just as fast).
 *
 * Lets [com.vervan.chat.retrieval.RetrievalEngine]'s keyword mode ask SQLite's tokenized,
 * indexed MATCH for the candidate chunk set instead of a per-chunk Kotlin-side substring scan
 * over the whole KB scope on every query — the brute-force ceiling [ChunkDao]'s own doc comment
 * already calls out, and it also fixes a real correctness bug in the old scan: a naive
 * `.contains()` matched a search term inside unrelated words (e.g. "cat" inside "category"),
 * where FTS respects real token/word boundaries.
 */
@Fts4(notIndexed = ["chunkId", "documentId"])
@Entity(tableName = "chunks_fts")
data class ChunkFts(
    val chunkId: String,
    val documentId: String,
    val text: String
)
