package com.vervan.chat.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class MessageRole { USER, ASSISTANT, SYSTEM }

// B2: PENDING was defined but never assigned to any message (all messages default to COMPLETE
// or are explicitly created as STREAMING) — removed rather than kept as dead state.
enum class MessageState { STREAMING, COMPLETE, CANCELLED, INTERRUPTED, FAILED, AWAITING_CONFIRMATION }

// chatId is the single busiest lookup column in the whole schema — every message list load, the
// chat-list screen's own EXISTS-has-messages subquery, and delete-for-chat all filter on it. See
// Migration(36, 37).
@Entity(tableName = "messages", indices = [Index("chatId")])
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
    // Imported document attached to this turn. The document row owns the durable file path and
    // MIME type; storing only its id here avoids duplicating attachment metadata in messages.
    val documentId: String? = null,
    // Mono-WAV voice message attachment, sent as raw audio bytes to the model directly
    // (LlmEngine.generate's Content.AudioBytes) when the loaded model supports audio input —
    // separate from the composer's "Dictate to text" mic, which runs Android's own offline
    // SpeechRecognizer and sends the transcript as plain text instead.
    val audioPath: String? = null,
    // JSON array of {chunkId, documentName, sectionPath, excerpt, score} — org.json,
    // not a real table, because it's display-only provenance for this one message.
    val sourcesJson: String? = null,
    // Persisted audit of memory context used/saved around this response. Kept on the message so
    // scrolling back later still shows exactly what the model knew at that turn.
    val memoryActivityJson: String? = null,
    // {"tool": name, "params": {...}} — set while state == AWAITING_CONFIRMATION, i.e. a
    // reversible-write tool call the model proposed that the user hasn't approved yet.
    val toolCallJson: String? = null,
    // {"tool": name, "success": bool, "summary": text} — set once a tool call (read-only
    // or approved write) has actually run, for display under the message.
    val toolResultJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // Generation stats for an assistant reply — null for user/system messages and for any
    // assistant message that didn't come from a live generate() call (tool results, the
    // vision/audio "not supported" stand-ins above). tokenCount is the same chars/4 estimate
    // ChatContextStrip already uses for context %, not an exact tokenizer count.
    val generationMs: Long? = null,
    val tokenCount: Int? = null,
    // Snapshot provenance belongs to the response: chats can switch configuration later.
    val modelId: String? = null,
    val modelName: String? = null,
    val backend: String? = null,
    val profile: String? = null,
    val thinkingMode: String? = null,
    // One persisted personal reaction is enough for this single-user offline app.
    val reaction: String? = null,
    // Set when the user picks a reason after reacting 👎 (see ChatViewModel.setFeedbackReason) —
    // kept alongside the modelId/profile/backend snapshot above so a later diagnostics view can
    // answer "which model/preset keeps getting this reason" without joining anything else.
    val feedbackReason: String? = null
)
