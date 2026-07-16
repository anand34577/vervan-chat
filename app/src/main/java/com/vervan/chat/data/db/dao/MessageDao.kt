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

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteForChat(chatId: String)

    // Universal search (Phase 6, spec §29) — message-body search was missing entirely before;
    // chats only matched by title. Returns matching messages directly so the search screen can
    // show the matching snippet, not just "found in some chat".
    @Query("SELECT * FROM messages WHERE content LIKE '%' || :q || '%' ORDER BY createdAt DESC LIMIT 20")
    suspend fun search(q: String): List<Message>

    // Chat Screen spec §14 — workspace chat-list card preview/metadata line.
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND role != 'SYSTEM' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForChat(chatId: String): Message?

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND role != 'SYSTEM'")
    suspend fun countForChat(chatId: String): Int
}
