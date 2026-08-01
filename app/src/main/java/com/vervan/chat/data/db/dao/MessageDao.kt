package com.vervan.chat.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vervan.chat.data.db.entities.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao : BaseDao<Message> {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun observeMessages(chatId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun getMessages(chatId: String): List<Message>

    @Query("SELECT * FROM messages WHERE state = 'STREAMING'")
    suspend fun getUnfinished(): List<Message>

    @Query("UPDATE messages SET state = 'CANCELLED' WHERE chatId = :chatId AND state = 'STREAMING'")
    suspend fun cancelStreamingForChat(chatId: String)

    @Query("UPDATE messages SET state = 'COMPLETE' WHERE chatId = :chatId AND state = 'STREAMING'")
    suspend fun completeStreamingForChat(chatId: String)

    @Query("UPDATE messages SET reaction = :reaction WHERE id = :messageId")
    suspend fun setReaction(messageId: String, reaction: String?)

    @Query("UPDATE messages SET feedbackReason = :reason WHERE id = :messageId")
    suspend fun setFeedbackReason(messageId: String, reason: String?)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteForChat(chatId: String)

    // Universal search — message-body search was missing entirely before;
    // chats only matched by title. Returns matching messages directly so the search screen can
    // show the matching snippet, not just "found in some chat".
    @Query("SELECT * FROM messages WHERE content LIKE '%' || :q || '%' ORDER BY createdAt DESC LIMIT 20")
    suspend fun search(q: String): List<Message>

    // Chat Screen — workspace chat-list card preview/metadata line.
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND role != 'SYSTEM' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForChat(chatId: String): Message?

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND role != 'SYSTEM'")
    suspend fun countForChat(chatId: String): Int

    // Chat-list last-message preview — one row per chat (the most recent non-SYSTEM message)
    // observed as a single Flow so the chat list refreshes instantly when any conversation
    // gets a new message, without each row spinning up its own per-chat query. Used by
    // ChatListViewModel.lastMessageByChat.
    @Query(
        "SELECT m.* FROM messages m INNER JOIN (" +
            "SELECT chatId, MAX(createdAt) AS maxCreated FROM messages WHERE role != 'SYSTEM' GROUP BY chatId" +
            ") latest ON m.chatId = latest.chatId AND m.createdAt = latest.maxCreated"
    )
    fun observeLatestPerChat(): Flow<List<Message>>

    @Query(
        "SELECT m.* FROM messages m INNER JOIN (" +
            "SELECT chatId, MAX(createdAt) AS maxCreated FROM messages " +
            "WHERE role != 'SYSTEM' AND chatId IN (:chatIds) GROUP BY chatId" +
            ") latest ON m.chatId = latest.chatId AND m.createdAt = latest.maxCreated " +
            "WHERE m.chatId IN (:chatIds)"
    )
    fun observeLatestForChats(chatIds: List<String>): Flow<List<Message>>

    // Smart collections — single round-trip replacements for the previous
    // chats.forEach { getMessages(c.id) } N+1 in SmartCollectionsViewModel, which loaded every
    // message of every chat into memory on each recompute. DISTINCT chatId rides the chatId index.
    @Query("SELECT DISTINCT chatId FROM messages WHERE state IN (:states)")
    suspend fun getChatIdsWithState(states: List<String>): List<String>

    // Knowledge graph — sourcesJson/memoryActivityJson are display-only provenance blobs (see
    // their doc comments on Message), not a real FK table, but a LIKE scan is enough to answer
    // "which chats cited this document / touched this memory" at personal-library scale.
    @Query("SELECT DISTINCT chatId FROM messages WHERE sourcesJson LIKE '%' || :documentId || '%'")
    suspend fun chatIdsReferencingDocument(documentId: String): List<String>

    @Query("SELECT DISTINCT chatId FROM messages WHERE memoryActivityJson LIKE '%' || :memoryId || '%'")
    suspend fun chatIdsReferencingMemory(memoryId: String): List<String>

    @Query(
        "SELECT DISTINCT chatId FROM messages WHERE imagePath IS NOT NULL " +
            "OR audioPath IS NOT NULL OR voiceRecordingPath IS NOT NULL"
    )
    suspend fun getChatIdsWithAttachments(): List<String>

    // Model Calculator's "recommended for this device" card — real measured throughput per
    // model instead of a synthetic benchmark pass, built from the generationMs/tokenCount every
    // assistant reply already records. Only rows with both fields present and a positive
    // duration count (guards the tokens-per-second division downstream against a zero/negative
    // denominator from a clock anomaly or a pre-migration row).
    @Query(
        "SELECT modelId, SUM(tokenCount) AS totalTokens, SUM(generationMs) AS totalMs, COUNT(*) AS samples " +
            "FROM messages " +
            "WHERE modelId IS NOT NULL AND tokenCount IS NOT NULL AND generationMs IS NOT NULL AND generationMs > 0 " +
            "GROUP BY modelId"
    )
    fun observeModelSpeedStats(): Flow<List<ModelSpeedStat>>
}

data class ModelSpeedStat(val modelId: String, val totalTokens: Long, val totalMs: Long, val samples: Int) {
    val tokensPerSecond: Float get() = if (totalMs <= 0) 0f else totalTokens * 1000f / totalMs
}
