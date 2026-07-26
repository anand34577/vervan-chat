package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** One completed Text-to-Speech generation — the TTS screen's history list. Unlike
 * [TranscriptionProject], this is written only once generation succeeds (nothing here is
 * expensive enough to redo that losing an in-progress one is a real cost). */
@Entity(tableName = "tts_projects", indices = [Index("createdAt")])
data class TtsProject(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val sourceText: String,
    val engine: String,
    val voiceVariant: String,
    val language: String,
    val audioPath: String,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)
