package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** A downloaded TTS voice/model file for the realtime voice pipeline (Piper, Kokoro, or
 * Supertonic). One row per (engine, language) voice file actually on disk. */
@Entity(tableName = "tts_voice_models")
data class TtsVoiceModel(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val engine: String, // "PIPER", "KOKORO", or "SUPERTONIC"
    val language: String, // "hi", "en", or "multi" (Kokoro/Supertonic cover many languages in one file)
    val filePath: String,
    val fileSizeBytes: Long,
    val sha256: String = "",
    val downloadedAt: Long = System.currentTimeMillis(),
    val isReady: Boolean = true
)
