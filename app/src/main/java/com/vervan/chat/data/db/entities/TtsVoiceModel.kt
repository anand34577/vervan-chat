package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** A downloaded TTS voice/model file for the realtime voice pipeline (Piper or Kokoro —
 * Supertonic manages its own storage via the SDK's `autoDownload`, so it never gets a row
 * here). One row per (engine, language) voice file actually on disk. */
@Entity(tableName = "tts_voice_models")
data class TtsVoiceModel(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val engine: String, // "PIPER" or "KOKORO"
    val language: String, // "hi", "en", or "multi" (Kokoro covers many languages in one file)
    val filePath: String,
    val fileSizeBytes: Long,
    val sha256: String = "",
    val downloadedAt: Long = System.currentTimeMillis(),
    val isReady: Boolean = true
)
