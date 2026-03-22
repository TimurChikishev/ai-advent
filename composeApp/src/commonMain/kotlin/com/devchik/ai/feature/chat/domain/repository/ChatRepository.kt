package com.devchik.ai.feature.chat.domain.repository

import com.devchik.ai.feature.chat.domain.model.ChatMessageItem
import com.devchik.ai.feature.chat.domain.model.ChatSession
import com.devchik.ai.feature.chat.domain.model.TokenUsage
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer contract for chat data operations.
 *
 * Implemented by [ChatRepositoryImpl] in the data layer.
 * All append* methods persist a single message incrementally (not batch).
 */
interface ChatRepository {
    fun getSessions(): Flow<List<ChatSession>>
    suspend fun createSession(title: String): String
    suspend fun deleteSession(id: String)
    /** Loads the full UI-visible history (all roles) for restoring ChatScreen state. */
    suspend fun loadHistory(sessionId: String): List<ChatMessageItem>
    suspend fun appendUserMessage(sessionId: String, content: String)
    suspend fun appendAssistantMessage(sessionId: String, content: String, tokenUsage: TokenUsage? = null)
    suspend fun appendSystemMessage(sessionId: String, content: String)
    suspend fun appendErrorMessage(sessionId: String, content: String)
    /** Returns context compression statistics for the given session. */
    suspend fun getContextStats(sessionId: String): ContextStats
}

data class ContextStats(
    val totalMessages: Int,
    val summarizedMessages: Int,
    val summaryCount: Int,
    val isCompressed: Boolean,
)
