package com.devchik.ai.feature.chat.data.strategy

import ai.koog.prompt.message.Message

/**
 * Интерфейс стратегии построения контекста для LLM.
 *
 * Реализации определяют, как собирать список сообщений из истории диалога
 * перед отправкой в LLM. Каждая стратегия по-своему решает задачу
 * баланса между полнотой контекста и ограничениями на количество токенов.
 *
 * Используется через [ContextManager], который выбирает активную стратегию
 * на основе пользовательских настроек.
 */
interface ContextBuildStrategy {

    /**
     * Собирает оптимизированный контекст для отправки в LLM.
     *
     * @param conversationId ID сессии чата для загрузки истории из БД.
     * @param windowSize Количество последних сообщений, которые нужно сохранить "как есть".
     * @return Список Koog [Message] — готовый контекст для LLM.
     */
    suspend fun buildContext(conversationId: String, windowSize: Int): List<Message>

    /**
     * Возвращает статистику по текущему состоянию контекста для отображения в UI.
     *
     * @param conversationId ID сессии чата.
     * @return [ContextBuildStats] со статистикой стратегии.
     */
    suspend fun getStats(conversationId: String): ContextBuildStats
}

/**
 * Статистика работы стратегии контекста — отображается в TopAppBar чата.
 *
 * @property totalMessages Общее количество user+assistant сообщений в сессии.
 * @property contextMessages Количество сообщений, реально попадающих в контекст LLM.
 * @property strategyLabel Название стратегии для отображения (например "Summary", "Sliding Window").
 * @property details Человекочитаемое описание текущего состояния (например "20/30 msgs compressed").
 */
data class ContextBuildStats(
    val totalMessages: Int,
    val contextMessages: Int,
    val strategyLabel: String,
    val details: String,
)
