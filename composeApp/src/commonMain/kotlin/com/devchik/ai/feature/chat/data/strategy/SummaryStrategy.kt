package com.devchik.ai.feature.chat.data.strategy

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import com.devchik.ai.BuildKonfig
import com.devchik.ai.core.database.dao.ChatMessageDao
import com.devchik.ai.core.database.dao.ChatSummaryDao
import com.devchik.ai.core.database.entity.ChatMessageEntity
import com.devchik.ai.core.database.entity.ChatSummaryEntity
import com.devchik.ai.feature.ai.data.model.DeepSeekMessageDto
import com.devchik.ai.feature.ai.data.model.DeepSeekRequest
import com.devchik.ai.feature.ai.data.model.DeepSeekResponse
import com.devchik.ai.feature.chat.data.RoomChatHistoryProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlin.time.Clock

/**
 * Стратегия суммаризации — сжимает старые сообщения в краткие резюме через LLM.
 *
 * Алгоритм работы:
 * 1. Загружает все user+assistant сообщения из Room.
 * 2. Если сообщений <= [windowSize] — возвращает все "как есть" (суммаризация не нужна).
 * 3. Иначе разделяет: recent (последние N) и older (всё до них).
 * 4. Проверяет, какие из older уже покрыты существующими summary (по coveredUntilTimestamp).
 * 5. Если накопилось >= [SUMMARY_BATCH_SIZE] несуммаризированных — вызывает DeepSeek API
 *    для генерации summary для каждого полного батча.
 * 6. Сохраняет summary в Room ([ChatSummaryEntity]) — при следующем вызове не пересоздаётся.
 * 7. Собирает результат: [System: все summary] + [несуммаризированный хвост older] + [recent N].
 *
 * @param chatMessageDao DAO для чтения сообщений.
 * @param chatSummaryDao DAO для чтения/записи summary.
 * @param httpClient Ktor HTTP клиент для вызовов DeepSeek API (суммаризация).
 */
class SummaryStrategy(
    private val chatMessageDao: ChatMessageDao,
    private val chatSummaryDao: ChatSummaryDao,
    private val httpClient: HttpClient,
) : ContextBuildStrategy {

    /**
     * Собирает контекст с суммаризацией старых сообщений.
     *
     * Возвращает: [System message с summary] + [unsummarized older msgs] + [recent N msgs].
     * Если сообщений мало — возвращает все без суммаризации.
     */
    override suspend fun buildContext(conversationId: String, windowSize: Int): List<Message> {
        // Загружаем все user+assistant сообщения, отфильтровывая пустые
        val allMessages = chatMessageDao.getMessages(conversationId)
            .filter { it.role == RoomChatHistoryProvider.ROLE_USER || it.role == RoomChatHistoryProvider.ROLE_ASSISTANT }
            .filter { it.content.isNotBlank() }

        // Если сообщений мало — суммаризация не нужна, возвращаем всё как есть
        if (allMessages.size <= windowSize) {
            return allMessages.mapNotNull { it.toKoogMessage() }
        }

        // Разделяем: последние N сохраняем полностью, остальное — кандидаты на суммаризацию
        val recentMessages = allMessages.takeLast(windowSize)
        val olderMessages = allMessages.dropLast(windowSize)

        // Ищем уже существующие summary, чтобы не суммаризировать повторно
        val existingSummaries = chatSummaryDao.getSummaries(conversationId)
        // coveredUntilTimestamp — timestamp последнего сообщения, вошедшего в какой-либо summary
        val lastSummaryCutoff = existingSummaries.maxOfOrNull { it.coveredUntilTimestamp } ?: 0L

        // Фильтруем только те старые сообщения, которые ещё НЕ были суммаризированы
        val unsummarized = olderMessages.filter { it.timestamp > lastSummaryCutoff }

        // Если набралось достаточно несуммаризированных — генерируем summary по батчам
        if (unsummarized.size >= SUMMARY_BATCH_SIZE) {
            val batches = unsummarized.chunked(SUMMARY_BATCH_SIZE)
            for (batch in batches) {
                // Неполный батч (хвост) — не суммаризируем, оставляем для следующего раза
                if (batch.size < SUMMARY_BATCH_SIZE) break
                // Вызываем DeepSeek API для генерации краткого summary
                val summaryText = generateSummary(batch)
                if (summaryText != null) {
                    // Сохраняем summary в Room для повторного использования
                    chatSummaryDao.insertSummary(
                        ChatSummaryEntity(
                            sessionId = conversationId,
                            summary = summaryText,
                            // Фиксируем границу: до какого timestamp покрыто этим summary
                            coveredUntilTimestamp = batch.last().timestamp,
                            messageCount = batch.size,
                            createdAt = Clock.System.now().toEpochMilliseconds(),
                        )
                    )
                }
            }
        }

        // Перечитываем все summary (включая только что созданные)
        val allSummaries = chatSummaryDao.getSummaries(conversationId)
        // Объединяем все summary в один текстовый блок
        val combinedSummary = buildCombinedSummary(allSummaries)

        // Собираем итоговый контекст
        val result = mutableListOf<Message>()

        // 1. Summary как System message (если есть что вставить)
        if (combinedSummary.isNotBlank()) {
            result.add(
                Message.System(
                    content = "$SUMMARY_PREFIX\n$combinedSummary",
                    metaInfo = RequestMetaInfo.Empty,
                )
            )
        }

        // 2. Несуммаризированный хвост older messages (между последним summary и recent window)
        val latestSummaryCutoff = allSummaries.maxOfOrNull { it.coveredUntilTimestamp } ?: 0L
        val unsummarizedRemaining = olderMessages.filter { it.timestamp > latestSummaryCutoff }
        for (msg in unsummarizedRemaining) {
            msg.toKoogMessage()?.let { result.add(it) }
        }

        // 3. Последние N сообщений — всегда полностью
        for (msg in recentMessages) {
            msg.toKoogMessage()?.let { result.add(it) }
        }

        return result
    }

    /**
     * Статистика суммаризации: сколько сообщений сжато и сколько summary создано.
     */
    override suspend fun getStats(conversationId: String): ContextBuildStats {
        val summaries = chatSummaryDao.getSummaries(conversationId)
        val totalMessages = chatMessageDao.getMessages(conversationId)
            .count { it.role == RoomChatHistoryProvider.ROLE_USER || it.role == RoomChatHistoryProvider.ROLE_ASSISTANT }
        val summarizedCount = summaries.sumOf { it.messageCount }
        return ContextBuildStats(
            totalMessages = totalMessages,
            contextMessages = totalMessages - summarizedCount,
            strategyLabel = "Summary",
            details = if (summaries.isNotEmpty()) {
                "$summarizedCount/$totalMessages msgs compressed (${summaries.size} summaries)"
            } else {
                "No compression yet"
            },
        )
    }

    /**
     * Вызывает DeepSeek API (non-streaming) для генерации summary батча сообщений.
     *
     * Формат запроса: system prompt с инструкцией суммаризатора + user message с текстом диалога.
     * Температура 0.3 — для максимальной фактологичности.
     *
     * @return Текст summary или null при ошибке (сетевая, API, парсинг).
     */
    private suspend fun generateSummary(messages: List<ChatMessageEntity>): String? {
        // Форматируем сообщения в читаемый текст: "User: ..." / "Assistant: ..."
        val conversationText = messages.joinToString("\n") { entity ->
            val role = if (entity.role == RoomChatHistoryProvider.ROLE_USER) "User" else "Assistant"
            "$role: ${entity.content}"
        }

        val request = DeepSeekRequest(
            model = MODEL,
            messages = listOf(
                DeepSeekMessageDto(
                    role = "system",
                    content = SUMMARIZATION_SYSTEM_PROMPT,
                ),
                DeepSeekMessageDto(
                    role = "user",
                    content = "Summarize this conversation segment:\n\n$conversationText",
                ),
            ),
            maxTokens = MAX_SUMMARY_TOKENS,
            temperature = 0.3f, // Низкая температура для фактологичности
        )

        return try {
            val response = httpClient.post("$BASE_URL/chat/completions") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${BuildKonfig.DEEPSEEK_API_KEY}")
                setBody(request)
            }.body<DeepSeekResponse>()

            response.choices.firstOrNull()?.message?.content
        } catch (e: Exception) {
            // Ошибка суммаризации не должна ломать чат — просто пропускаем этот батч
            null
        }
    }

    /**
     * Объединяет несколько summary в один текстовый блок.
     *
     * Если summary один — возвращает его текст напрямую.
     * Если несколько — каждый предваряется меткой "[N messages]" для контекста.
     */
    private fun buildCombinedSummary(summaries: List<ChatSummaryEntity>): String {
        if (summaries.isEmpty()) return ""
        if (summaries.size == 1) return summaries.first().summary
        return summaries.joinToString("\n\n") { summary ->
            "[${summary.messageCount} messages] ${summary.summary}"
        }
    }

    companion object {
        /** Минимальное количество несуммаризированных сообщений для запуска суммаризации */
        const val SUMMARY_BATCH_SIZE = 10

        private const val BASE_URL = "https://api.deepseek.com"
        private const val MODEL = "deepseek-chat"
        /** Максимум токенов на один summary — ограничивает длину резюме */
        private const val MAX_SUMMARY_TOKENS = 500

        /** Префикс system message с summary — помогает LLM понять контекст */
        const val SUMMARY_PREFIX = "[Previous conversation summary]"

        /** Системный промпт для LLM-суммаризатора: краткость, факты, сохранение языка */
        private const val SUMMARIZATION_SYSTEM_PROMPT =
            "You are a conversation summarizer. Produce a concise summary of the given conversation segment. " +
            "Preserve key facts, decisions, code snippets references, and user preferences. " +
            "Keep the summary in the same language(s) as the original conversation. " +
            "Be factual and brief — aim for 3-5 sentences."
    }
}
