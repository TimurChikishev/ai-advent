package com.devchik.ai.feature.chat.data

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import com.devchik.ai.core.database.dao.ChatMessageDao
import com.devchik.ai.core.database.dao.ChatSessionDao
import com.devchik.ai.core.database.entity.ChatMessageEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class RoomChatHistoryProvider(
    private val chatMessageDao: ChatMessageDao,
    private val chatSessionDao: ChatSessionDao,
) : ChatHistoryProvider {

    private val mutex = Mutex()

    override suspend fun store(conversationId: String, messages: List<Message>) {
        // ChatMemory calls store() on strategy completion.
        // We handle persistence incrementally via appendMessage, so we skip
        // the bulk overwrite to avoid destroying custom system/error entries
        // and re-inserting tool_call/tool_result that break DeepSeek context.
    }

    override suspend fun load(conversationId: String): List<Message> {
        return mutex.withLock {
            chatMessageDao.getMessages(conversationId)
                .filter { it.role == ROLE_USER || it.role == ROLE_ASSISTANT }
                .mapNotNull { it.toMessage() }
        }
    }

    suspend fun appendMessage(conversationId: String, message: Message) {
        mutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            chatMessageDao.insertMessage(message.toEntity(conversationId, 0).copy(timestamp = now))
            chatSessionDao.updateSessionTimestamp(conversationId, now)
        }
    }

    suspend fun appendRawEntity(entity: ChatMessageEntity) {
        mutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            chatMessageDao.insertMessage(entity.copy(timestamp = now))
            chatSessionDao.updateSessionTimestamp(entity.sessionId, now)
        }
    }

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
        const val ROLE_ERROR = "error"
        const val ROLE_TOOL_CALL = "tool_call"
        const val ROLE_TOOL_RESULT = "tool_result"
    }
}

private fun Message.toEntity(sessionId: String, orderIndex: Long): ChatMessageEntity {
    val now = Clock.System.now().toEpochMilliseconds()
    return when (this) {
        is Message.User -> ChatMessageEntity(
            sessionId = sessionId,
            role = RoomChatHistoryProvider.ROLE_USER,
            content = content,
            timestamp = now + orderIndex,
        )
        is Message.Assistant -> ChatMessageEntity(
            sessionId = sessionId,
            role = RoomChatHistoryProvider.ROLE_ASSISTANT,
            content = content,
            timestamp = now + orderIndex,
        )
        is Message.System -> ChatMessageEntity(
            sessionId = sessionId,
            role = RoomChatHistoryProvider.ROLE_SYSTEM,
            content = content,
            timestamp = now + orderIndex,
        )
        is Message.Tool.Call -> ChatMessageEntity(
            sessionId = sessionId,
            role = RoomChatHistoryProvider.ROLE_TOOL_CALL,
            content = content,
            toolId = id,
            toolName = tool,
            timestamp = now + orderIndex,
        )
        is Message.Tool.Result -> ChatMessageEntity(
            sessionId = sessionId,
            role = RoomChatHistoryProvider.ROLE_TOOL_RESULT,
            content = content,
            toolId = id,
            toolName = tool,
            timestamp = now + orderIndex,
        )
        else -> ChatMessageEntity(
            sessionId = sessionId,
            role = RoomChatHistoryProvider.ROLE_SYSTEM,
            content = content,
            timestamp = now + orderIndex,
        )
    }
}

private fun ChatMessageEntity.toMessage(): Message? {
    val requestMeta = RequestMetaInfo.Empty
    val responseMeta = ResponseMetaInfo.Empty
    return when (role) {
        RoomChatHistoryProvider.ROLE_USER -> Message.User(content = content, metaInfo = requestMeta)
        RoomChatHistoryProvider.ROLE_ASSISTANT -> Message.Assistant(content = content, metaInfo = responseMeta)
        RoomChatHistoryProvider.ROLE_SYSTEM -> Message.System(content = content, metaInfo = requestMeta)
        RoomChatHistoryProvider.ROLE_TOOL_CALL -> Message.Tool.Call(
            id = toolId,
            tool = toolName ?: "",
            content = content,
            metaInfo = responseMeta,
        )
        RoomChatHistoryProvider.ROLE_TOOL_RESULT -> Message.Tool.Result(
            id = toolId,
            tool = toolName ?: "",
            content = content,
            metaInfo = requestMeta,
        )
        RoomChatHistoryProvider.ROLE_ERROR -> null
        else -> null
    }
}
