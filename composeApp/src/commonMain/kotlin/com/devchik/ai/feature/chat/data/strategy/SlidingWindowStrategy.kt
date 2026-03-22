package com.devchik.ai.feature.chat.data.strategy

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import com.devchik.ai.core.database.dao.ChatMessageDao
import com.devchik.ai.core.database.entity.ChatMessageEntity
import com.devchik.ai.feature.chat.data.RoomChatHistoryProvider

/**
 * Стратегия "скользящего окна" — простейший подход к управлению контекстом.
 *
 * Принцип работы:
 * 1. Загружает все user+assistant сообщения из Room для данной сессии.
 * 2. Берёт только последние [windowSize] сообщений.
 * 3. Всё остальное полностью отбрасывается — LLM не получает никакой информации о ранней части диалога.
 *
 * Плюсы: нулевые дополнительные расходы токенов, максимальная простота.
 * Минусы: полная потеря раннего контекста, LLM не помнит что было обсуждено ранее.
 *
 * @param chatMessageDao DAO для чтения сообщений из Room.
 */
class SlidingWindowStrategy(
    private val chatMessageDao: ChatMessageDao,
) : ContextBuildStrategy {

    /**
     * Берёт последние [windowSize] сообщений (user + assistant) из БД.
     * Пустые сообщения и сообщения других ролей (system, error, tool) отфильтровываются.
     */
    override suspend fun buildContext(conversationId: String, windowSize: Int): List<Message> {
        return chatMessageDao.getMessages(conversationId)
            // Оставляем только user и assistant — system/error/tool не нужны для LLM контекста
            .filter { it.role == RoomChatHistoryProvider.ROLE_USER || it.role == RoomChatHistoryProvider.ROLE_ASSISTANT }
            // Пустые сообщения — артефакты от tool_call/tool_result строк
            .filter { it.content.isNotBlank() }
            // Берём только последние N — ядро стратегии скользящего окна
            .takeLast(windowSize)
            // Конвертируем Room entity → Koog Message
            .mapNotNull { it.toKoogMessage() }
    }

    /**
     * Возвращает статистику: общее количество сообщений.
     * Для sliding window нет компрессии, поэтому contextMessages = totalMessages.
     */
    override suspend fun getStats(conversationId: String): ContextBuildStats {
        val total = chatMessageDao.getMessages(conversationId)
            .count { it.role == RoomChatHistoryProvider.ROLE_USER || it.role == RoomChatHistoryProvider.ROLE_ASSISTANT }
        return ContextBuildStats(
            totalMessages = total,
            contextMessages = total,
            strategyLabel = "Sliding Window",
            details = "Last N messages kept, older discarded",
        )
    }
}

/**
 * Конвертирует Room [ChatMessageEntity] в Koog [Message].
 *
 * Поддерживает только user и assistant роли — остальные возвращают null,
 * т.к. system/error/tool сообщения не нужны в LLM контексте
 * (system/error — UI-only; tool — вызывают ошибки DeepSeek API без парных записей).
 *
 * Функция internal, т.к. используется несколькими стратегиями в этом пакете.
 */
internal fun ChatMessageEntity.toKoogMessage(): Message? {
    return when (role) {
        RoomChatHistoryProvider.ROLE_USER -> Message.User(
            content = content,
            metaInfo = RequestMetaInfo.Empty,
        )
        RoomChatHistoryProvider.ROLE_ASSISTANT -> Message.Assistant(
            content = content,
            metaInfo = ResponseMetaInfo.Empty,
        )
        else -> null
    }
}
