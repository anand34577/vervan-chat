package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A flashcard generated from pasted text or a note (spec §24 study workspace).
 * review tracking is one counter + a last-result flag, not a real spaced-repetition
 * scheduler (intervals, ease factor) — good enough to show progress, add SM-2 if this needs
 * to actually schedule reviews over time.
 */
@Entity(tableName = "study_cards")
data class StudyCard(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val setName: String,
    val question: String,
    val answer: String,
    val timesReviewed: Int = 0,
    val timesCorrect: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
