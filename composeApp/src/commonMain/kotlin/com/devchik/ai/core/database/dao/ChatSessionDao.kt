package com.devchik.ai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.devchik.ai.core.database.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSession(id: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSessionTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSessionTimestamp(id: String, updatedAt: Long)

    /** Обновляет настройки стратегии контекста для конкретной сессии */
    @Query("UPDATE chat_sessions SET contextStrategy = :strategy, contextWindowSize = :windowSize WHERE id = :id")
    suspend fun updateSessionContextSettings(id: String, strategy: String?, windowSize: Int?)

    /** Все ветки (дочерние сессии) для данной родительской сессии */
    @Query("SELECT * FROM chat_sessions WHERE parentSessionId = :parentId ORDER BY createdAt DESC")
    suspend fun getBranches(parentId: String): List<ChatSessionEntity>
}
