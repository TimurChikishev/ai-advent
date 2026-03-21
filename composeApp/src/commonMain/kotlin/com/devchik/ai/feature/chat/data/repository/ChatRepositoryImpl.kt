package com.devchik.ai.feature.chat.data.repository

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import com.devchik.ai.core.database.dao.ChatMessageDao
import com.devchik.ai.core.database.dao.ChatSessionDao
import com.devchik.ai.core.database.entity.ChatMessageEntity
import com.devchik.ai.core.database.entity.ChatSessionEntity
import com.devchik.ai.feature.chat.data.RoomChatHistoryProvider
import com.devchik.ai.feature.chat.domain.model.ChatMessageItem
import com.devchik.ai.feature.chat.domain.model.ChatSession
import com.devchik.ai.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ChatRepositoryImpl(
    private val chatSessionDao: ChatSessionDao,
    private val chatMessageDao: ChatMessageDao,
    private val chatHistoryProvider: RoomChatHistoryProvider,
) : ChatRepository {

    override fun getSessions(): Flow<List<ChatSession>> {
        return chatSessionDao.getAllSessions().map { entities ->
            entities.map { entity ->
                val messages = chatMessageDao.getMessages(entity.id)
                val lastUserOrAssistant = messages
                    .lastOrNull { it.role == RoomChatHistoryProvider.ROLE_USER || it.role == RoomChatHistoryProvider.ROLE_ASSISTANT }
                ChatSession(
                    id = entity.id,
                    title = entity.title,
                    lastMessage = lastUserOrAssistant?.content?.take(100).orEmpty(),
                    updatedAt = entity.updatedAt,
                )
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createSession(title: String): String {
        val sessionId = Uuid.random().toString()
        val now = Clock.System.now().toEpochMilliseconds()
        chatSessionDao.insertSession(
            ChatSessionEntity(
                id = sessionId,
                title = title,
                createdAt = now,
                updatedAt = now,
            )
        )
        return sessionId
    }

    override suspend fun deleteSession(id: String) {
        chatSessionDao.deleteSession(id)
    }

    override suspend fun loadHistory(sessionId: String): List<ChatMessageItem> {
        return chatMessageDao.getMessages(sessionId).mapNotNull { entity ->
            if (entity.content.isBlank()) return@mapNotNull null
            when (entity.role) {
                RoomChatHistoryProvider.ROLE_USER -> ChatMessageItem.User(entity.content)
                RoomChatHistoryProvider.ROLE_ASSISTANT -> ChatMessageItem.Assistant(entity.content)
                RoomChatHistoryProvider.ROLE_SYSTEM -> ChatMessageItem.System(entity.content)
                RoomChatHistoryProvider.ROLE_ERROR -> ChatMessageItem.Error(entity.content)
                else -> null
            }
        }
    }

    override suspend fun appendUserMessage(sessionId: String, content: String) {
        chatHistoryProvider.appendMessage(
            sessionId,
            Message.User(content = content, metaInfo = RequestMetaInfo.Empty),
        )
    }

    override suspend fun appendAssistantMessage(sessionId: String, content: String) {
        chatHistoryProvider.appendMessage(
            sessionId,
            Message.Assistant(content = content, metaInfo = ResponseMetaInfo.Empty),
        )
    }

    override suspend fun appendSystemMessage(sessionId: String, content: String) {
        chatHistoryProvider.appendMessage(
            sessionId,
            Message.System(content = content, metaInfo = RequestMetaInfo.Empty),
        )
    }

    override suspend fun appendErrorMessage(sessionId: String, content: String) {
        chatHistoryProvider.appendRawEntity(
            ChatMessageEntity(
                sessionId = sessionId,
                role = RoomChatHistoryProvider.ROLE_ERROR,
                content = content,
                timestamp = 0,
            )
        )
    }
}
