package com.devchik.ai.feature.chat.data.strategy

import ai.koog.prompt.message.Message
import com.devchik.ai.core.database.dao.ChatMessageDao
import com.devchik.ai.core.database.dao.ChatSessionDao
import com.devchik.ai.feature.chat.data.RoomChatHistoryProvider

/**
 * Стратегия ветвления (Branching) — каждая ветка является отдельной сессией
 * с копией истории до точки checkpoint.
 *
 * Для построения контекста работает как sliding window: берёт последние N сообщений
 * из текущей сессии-ветки. Поскольку ветка уже содержит полную копию истории
 * до checkpoint + свои новые сообщения, дополнительная логика не нужна.
 *
 * Основная ценность Branching — не в контексте для LLM, а в UI-функциональности:
 * возможность создать checkpoint, разветвить диалог и переключаться между ветками.
 *
 * @param chatMessageDao DAO для чтения сообщений.
 * @param chatSessionDao DAO для чтения информации о сессии (parent link).
 */
class BranchingStrategy(
    private val chatMessageDao: ChatMessageDao,
    private val chatSessionDao: ChatSessionDao,
) : ContextBuildStrategy {

    override suspend fun buildContext(conversationId: String, windowSize: Int): List<Message> {
        return chatMessageDao.getMessages(conversationId)
            .filter { it.role == RoomChatHistoryProvider.ROLE_USER || it.role == RoomChatHistoryProvider.ROLE_ASSISTANT }
            .filter { it.content.isNotBlank() }
            .takeLast(windowSize)
            .mapNotNull { it.toKoogMessage() }
    }

    override suspend fun getStats(conversationId: String): ContextBuildStats {
        val session = chatSessionDao.getSession(conversationId)
        val total = chatMessageDao.getMessages(conversationId)
            .count { it.role == RoomChatHistoryProvider.ROLE_USER || it.role == RoomChatHistoryProvider.ROLE_ASSISTANT }

        val parentId = session?.parentSessionId
        val parentTitle = parentId?.let { chatSessionDao.getSession(it)?.title }
        val branches = chatSessionDao.getBranches(conversationId)

        return ContextBuildStats(
            totalMessages = total,
            contextMessages = total,
            strategyLabel = "Branching",
            details = buildString {
                if (parentTitle != null) {
                    append("Branch from: $parentTitle")
                } else if (branches.isNotEmpty()) {
                    append("${branches.size} branch(es)")
                } else {
                    append("No branches yet")
                }
                append(", $total msgs")
            },
        )
    }
}
