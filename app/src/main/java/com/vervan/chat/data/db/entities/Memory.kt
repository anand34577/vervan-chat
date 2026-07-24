package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class MemoryScope { GLOBAL, PERSONA, PROJECT }

/**
 * manual capture only — the user writes or confirms every memory (via the
 * Memory screen or a "Remember this" action on a message). No inference-from-conversation
 * pipeline: that needs conflict resolution, confidence scoring, and a suggestion inbox
 * that's a lot of speculative machinery for a first cut. This still
 * satisfies "no memory is added silently" — everything here was something the
 * user explicitly chose to save.
 */
@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val scope: MemoryScope = MemoryScope.GLOBAL,
    // personaId or projectId depending on scope; null for GLOBAL
    val scopeRefId: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    // optional dedup key (e.g. "favorite_color") — saving a memory with a key
    // that already exists in the same scope replaces it instead of adding a contradictory
    // duplicate (the "canonical key" conflict rule). Freeform memories with no
    // natural key just leave this null and stack normally.
    val key: String? = null,
    // Derived local search data. The model id prevents a same-dimension embedding from an old
    // model being mistaken for a current one; both fields are safely rebuilt on demand.
    val embedding: ByteArray? = null,
    val embeddingModelId: String? = null,
    // Recycle bin coverage.
    val deletedAt: Long? = null
)
