package com.devchik.ai.feature.chat.domain.repository

import com.devchik.ai.feature.chat.domain.model.ChatMessageItem
import com.devchik.ai.feature.chat.domain.model.ChatSession
import com.devchik.ai.feature.chat.domain.model.TokenUsage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getSessions(): Flow<List<ChatSession>>
    suspend fun createSession(title: String): String
    suspend fun deleteSession(id: String)
    suspend fun loadHistory(sessionId: String): List<ChatMessageItem>
    suspend fun appendUserMessage(sessionId: String, content: String)
    suspend fun appendAssistantMessage(sessionId: String, content: String, tokenUsage: TokenUsage? = null)
    suspend fun appendSystemMessage(sessionId: String, content: String)
    suspend fun appendErrorMessage(sessionId: String, content: String)
}
