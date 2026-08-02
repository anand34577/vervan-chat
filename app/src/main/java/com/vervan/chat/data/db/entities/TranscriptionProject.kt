package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** One imported/recorded audio (or video-extracted-audio) file transcribed via a whisper.cpp
 * model — the Transcription screen's persistence unit. Doubles as both "current transcript"
 * autosave target and the screen's history list; there's no separate history table. */
@Entity(tableName = "transcription_projects", indices = [Index("createdAt")])
data class TranscriptionProject(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val audioPath: String,
    val durationMs: Long,
    val transcript: String = "",
    val engine: String = "WHISPER_CPP",
    val modelVariant: String,
    val status: String = "PENDING", // PENDING, TRANSCRIBING, DONE, FAILED, CANCELLED
    val errorMessage: String? = null,
    // JSON array of {"start":ms,"end":ms,"text":"..."} from whisper.cpp's per-segment
    // timestamps (see WhisperCppSttEngine.transcribeWithTimestamps) — a fixed side list for
    // "tap to jump to that part of the recording", independent of free-text edits to
    // [transcript] itself. Null for a project transcribed before this existed, or one that's
    // never been transcribed.
    val segmentsJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
