package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** Suggestion-inbox states. */
enum class MemorySuggestionStatus { PENDING, ACCEPTED, REJECTED }

/**
 * A proposed memory awaiting user review (suggestion-inbox capture mode). The app
 * may detect something worth remembering (e.g. the user said "remember that …" in chat, or a
 * tool captured a fact) and enqueue it here *inactive* — it never enters the prompt until the
 * user explicitly accepts it, at which point it becomes a real [Memory].
 */
@Entity(tableName = "memory_suggestions")
data class MemorySuggestion(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val key: String? = null,
    val scope: MemoryScope = MemoryScope.GLOBAL,
    val scopeRefId: String? = null,
    val sourceChatId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val status: MemorySuggestionStatus = MemorySuggestionStatus.PENDING
)
