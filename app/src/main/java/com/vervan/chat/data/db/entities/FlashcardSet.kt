package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Flashcard-set metadata (spec §55) — name + description + last-studied timestamp, separate
 * from the individual [StudyCard] rows (which only carry `setName`). Lets the Library list
 * decks with descriptions and "studied N ago" without scanning every card.
 */
@Entity(tableName = "flashcard_sets")
data class FlashcardSet(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastStudiedAt: Long? = null
)
