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

/**
 * Adapter between Koog's [ChatHistoryProvider] and the Room database.
 *
 * Two distinct responsibilities:
 * 1. **Koog integration** — implements [ChatHistoryProvider.load]/[store] for ChatMemory feature.
 * 2. **Incremental persistence** — [appendMessage]/[appendRawEntity] used by [ChatRepositoryImpl]
 *    to save messages one-by-one as they arrive (user input, assistant response, tool events, errors).
 *
 * Key design decisions:
 * - [store] is intentionally a **no-op**. Koog's ChatMemory calls store() after each strategy
 *   completion with the full message list (including tool_call/tool_result). Allowing this would:
 *   (a) overwrite our custom system/error messages not known to Koog,
 *   (b) persist tool_call/tool_result that break DeepSeek API on reload (tool messages
 *       require matching pairs; partial saves cause API errors).
 * - [load] returns **only user + assistant** messages to Koog. System, error, and tool messages
 *   are excluded because: system/error are UI-only; tool_call/tool_result without matching
 *   pairs cause DeepSeek API validation errors.
 * - [appendRawEntity] exists for cases where Koog Message types don't cover our needs
 *   (e.g., error messages with ROLE_ERROR, or assistant messages with token usage fields).
 *
 * Thread safety: all DB writes go through [mutex] to prevent race conditions from concurrent
 * callbacks (streaming, tool events, and agent completion can fire close together).
 */
class RoomChatHistoryProvider(
    private val chatMessageDao: ChatMessageDao,
    private val chatSessionDao: ChatSessionDao,
) : ChatHistoryProvider {

    private val mutex = Mutex()

    override suspend fun store(conversationId: String, messages: List<Message>) {
        // No-op: see class KDoc for rationale.
    }

    /**
     * Loads conversation context for Koog's ChatMemory.
     * Only user and assistant messages — everything else is filtered out
     * to keep the LLM prompt clean and avoid DeepSeek API errors.
     */
    override suspend fun load(conversationId: String): List<Message> {
        return mutex.withLock {
            chatMessageDao.getMessages(conversationId)
                .filter { it.role == ROLE_USER || it.role == ROLE_ASSISTANT }
                .mapNotNull { it.toMessage() }
        }
    }

    /** Converts a Koog [Message] to entity and inserts. Used for user/system messages. */
    suspend fun appendMessage(conversationId: String, message: Message) {
        mutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            chatMessageDao.insertMessage(message.toEntity(conversationId, 0).copy(timestamp = now))
            chatSessionDao.updateSessionTimestamp(conversationId, now)
        }
    }

    /**
     * Inserts a pre-built [ChatMessageEntity] directly.
     * Used for: assistant messages (with token usage fields), error messages (ROLE_ERROR
     * has no Koog Message equivalent).
     */
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

/** Maps a Koog [Message] to a Room entity. Used by [RoomChatHistoryProvider.appendMessage]. */
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

/**
 * Maps a Room entity back to a Koog [Message].
 * Returns null for ROLE_ERROR (no Koog equivalent) and unknown roles.
 */
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
