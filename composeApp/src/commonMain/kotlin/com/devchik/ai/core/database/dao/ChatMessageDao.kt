package com.devchik.ai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.devchik.ai.core.database.entity.ChatMessageEntity

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessages(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: String)

    /** Сообщения до указанного timestamp включительно — для копирования при создании ветки */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND timestamp <= :maxTimestamp ORDER BY timestamp ASC")
    suspend fun getMessagesUpTo(sessionId: String, maxTimestamp: Long): List<ChatMessageEntity>
}
