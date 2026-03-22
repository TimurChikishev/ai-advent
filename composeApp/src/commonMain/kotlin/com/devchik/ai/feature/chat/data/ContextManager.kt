package com.devchik.ai.feature.chat.data

import ai.koog.prompt.message.Message
import com.devchik.ai.core.database.dao.ChatSessionDao
import com.devchik.ai.feature.chat.data.strategy.ContextBuildStats
import com.devchik.ai.feature.chat.data.strategy.ContextBuildStrategy
import com.devchik.ai.feature.chat.data.strategy.BranchingStrategy
import com.devchik.ai.feature.chat.data.strategy.SlidingWindowStrategy
import com.devchik.ai.feature.chat.data.strategy.StickyFactsStrategy
import com.devchik.ai.feature.chat.data.strategy.SummaryStrategy
import com.devchik.ai.feature.chat.domain.model.ContextStrategy
import com.devchik.ai.feature.settings.domain.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Диспетчер стратегий управления контекстом.
 *
 * Приоритет настроек: per-session (из ChatSessionEntity) > глобальные (из SettingsRepository).
 * Если у сессии заданы собственные contextStrategy/contextWindowSize — используются они,
 * иначе берутся из глобальных AISettings.
 *
 * Потокобезопасность: [mutex] защищает buildContext от параллельных вызовов,
 * которые могут привести к дублированию summary в SummaryStrategy.
 *
 * @param settingsRepository Источник глобальных настроек (fallback).
 * @param chatSessionDao DAO для чтения per-session настроек.
 * @param slidingWindowStrategy Реализация стратегии скользящего окна.
 * @param summaryStrategy Реализация стратегии суммаризации.
 * @param stickyFactsStrategy Реализация стратегии липких фактов (key-value memory).
 * @param branchingStrategy Реализация стратегии ветвления.
 */
class ContextManager(
    private val settingsRepository: SettingsRepository,
    private val chatSessionDao: ChatSessionDao,
    private val slidingWindowStrategy: SlidingWindowStrategy,
    private val summaryStrategy: SummaryStrategy,
    private val stickyFactsStrategy: StickyFactsStrategy,
    private val branchingStrategy: BranchingStrategy,
) {
    private val mutex = Mutex()

    /**
     * Определяет актуальную стратегию и размер окна для сессии,
     * с учётом per-session overrides и глобальных настроек.
     */
    private suspend fun resolveSettings(conversationId: String): Pair<ContextBuildStrategy, Int> {
        val globalSettings = settingsRepository.settings.first()
        val session = chatSessionDao.getSession(conversationId)

        val strategyEnum = session?.contextStrategy
            ?.let { name -> ContextStrategy.entries.find { it.name == name } }
            ?: globalSettings.contextStrategy

        val windowSize = session?.contextWindowSize
            ?: globalSettings.contextWindowSize

        return resolveStrategy(strategyEnum) to windowSize
    }

    /**
     * Строит оптимизированный контекст для LLM запроса.
     * Приоритет: per-session настройки > глобальные AISettings.
     */
    suspend fun buildContext(conversationId: String): List<Message> = mutex.withLock {
        val (strategy, windowSize) = resolveSettings(conversationId)
        strategy.buildContext(conversationId, windowSize)
    }

    /**
     * Возвращает статистику текущей стратегии для отображения в UI чата.
     * Приоритет: per-session настройки > глобальные AISettings.
     */
    suspend fun getStats(conversationId: String): ContextBuildStats {
        val (strategy, _) = resolveSettings(conversationId)
        return strategy.getStats(conversationId)
    }

    /** Маппинг enum [ContextStrategy] → конкретную реализацию [ContextBuildStrategy]. */
    fun resolveStrategy(contextStrategy: ContextStrategy): ContextBuildStrategy {
        return when (contextStrategy) {
            ContextStrategy.SLIDING_WINDOW -> slidingWindowStrategy
            ContextStrategy.SUMMARY -> summaryStrategy
            ContextStrategy.STICKY_FACTS -> stickyFactsStrategy
            ContextStrategy.BRANCHING -> branchingStrategy
        }
    }
}
