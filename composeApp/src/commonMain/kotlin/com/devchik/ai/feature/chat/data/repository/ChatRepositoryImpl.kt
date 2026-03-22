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
import com.devchik.ai.feature.chat.domain.model.TokenUsage
import com.devchik.ai.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Data-layer implementation of [ChatRepository].
 *
 * Bridges the domain layer with Room database and [RoomChatHistoryProvider].
 * - Read operations (getSessions, loadHistory) go directly to Room DAOs.
 * - Write operations (append*) delegate to [chatHistoryProvider] which handles
 *   mutex-synchronized DB inserts and session timestamp updates.
 * - [loadHistory] maps all persisted message types (user, assistant, system, error)
 *   to domain [ChatMessageItem]s, including token usage for assistant messages.
 *   Blank-content entities are filtered out (artifacts from tool_call/tool_result rows).
 */
class ChatRepositoryImpl(
    private val chatSessionDao: ChatSessionDao,
    private val chatMessageDao: ChatMessageDao,
    private val chatHistoryProvider: RoomChatHistoryProvider,
) : ChatRepository {

    /** Emits session list reactively. Preview text is taken from the last user/assistant message. */
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

    /**
     * Loads full UI-visible chat history for a session.
     * Unlike [RoomChatHistoryProvider.load] (which returns only user+assistant for LLM context),
     * this includes system and error messages for complete UI restoration.
     */
    override suspend fun loadHistory(sessionId: String): List<ChatMessageItem> {
        return chatMessageDao.getMessages(sessionId).mapNotNull { entity ->
            if (entity.content.isBlank()) return@mapNotNull null
            when (entity.role) {
                RoomChatHistoryProvider.ROLE_USER -> ChatMessageItem.User(entity.content)
                RoomChatHistoryProvider.ROLE_ASSISTANT -> {
                    val tokenUsage = if (entity.totalTokens != null && entity.totalTokens > 0) {
                        TokenUsage(
                            inputTokens = entity.inputTokens ?: 0,
                            outputTokens = entity.outputTokens ?: 0,
                            totalTokens = entity.totalTokens,
                        )
                    } else null
                    ChatMessageItem.Assistant(entity.content, tokenUsage)
                }
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

    /**
     * Uses [appendRawEntity] instead of [appendMessage] because we need to persist
     * token usage fields (inputTokens, outputTokens, totalTokens) which don't exist
     * in Koog's [Message.Assistant].
     */
    override suspend fun appendAssistantMessage(sessionId: String, content: String, tokenUsage: TokenUsage?) {
        chatHistoryProvider.appendRawEntity(
            ChatMessageEntity(
                sessionId = sessionId,
                role = RoomChatHistoryProvider.ROLE_ASSISTANT,
                content = content,
                timestamp = 0,
                inputTokens = tokenUsage?.inputTokens,
                outputTokens = tokenUsage?.outputTokens,
                totalTokens = tokenUsage?.totalTokens,
            )
        )
    }

    override suspend fun appendSystemMessage(sessionId: String, content: String) {
        chatHistoryProvider.appendMessage(
            sessionId,
            Message.System(content = content, metaInfo = RequestMetaInfo.Empty),
        )
    }

    /** Uses [appendRawEntity] because ROLE_ERROR has no Koog Message equivalent. */
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
