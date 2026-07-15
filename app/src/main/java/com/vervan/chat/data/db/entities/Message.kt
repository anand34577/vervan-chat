package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class MessageRole { USER, ASSISTANT, SYSTEM }

// B2: PENDING was defined but never assigned to any message (all messages default to COMPLETE
// or are explicitly created as STREAMING) — removed rather than kept as dead state.
enum class MessageState { STREAMING, COMPLETE, CANCELLED, INTERRUPTED, FAILED, AWAITING_CONFIRMATION }

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val chatId: String,
    // Null for the first message in a chat. A message with more than one child is a
    // branch point — see com.vervan.chat.data.branch.BranchUtil.
    val parentId: String? = null,
    val role: MessageRole,
    val content: String,
    val state: MessageState = MessageState.COMPLETE,
    val imagePath: String? = null,
    // Mono-WAV voice message attachment, sent as raw audio bytes to the model directly
    // (LlmEngine.generate's Content.AudioBytes) when the loaded model supports audio input —
    // separate from the composer's "Dictate to text" mic, which runs Android's own offline
    // SpeechRecognizer and sends the transcript as plain text instead.
    val audioPath: String? = null,
    // JSON array of {chunkId, documentName, sectionPath, excerpt, score} — org.json,
    // not a real table, because it's display-only provenance for this one message.
    val sourcesJson: String? = null,
    // {"tool": name, "params": {...}} — set while state == AWAITING_CONFIRMATION, i.e. a
    // reversible-write tool call the model proposed that the user hasn't approved yet.
    val toolCallJson: String? = null,
    // {"tool": name, "success": bool, "summary": text} — set once a tool call (read-only
    // or approved write) has actually run, for display under the message.
    val toolResultJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
