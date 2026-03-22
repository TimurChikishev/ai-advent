package com.devchik.ai.feature.chat.data.strategy

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import com.devchik.ai.BuildKonfig
import com.devchik.ai.core.database.dao.ChatMessageDao
import com.devchik.ai.core.database.dao.StickyFactDao
import com.devchik.ai.core.database.entity.StickyFactEntity
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
 * Стратегия "липких фактов" (Sticky Facts / Key-Value Memory).
 *
 * Принцип работы:
 * 1. Из всей истории диалога LLM извлекает ключевые факты в формате key=value
 *    (цель, ограничения, предпочтения, решения, договорённости, технический стек и т.д.).
 * 2. Факты хранятся в Room (таблица sticky_facts) и обновляются после каждого
 *    нового сообщения пользователя.
 * 3. В контекст LLM отправляется: [System: facts block] + [последние N сообщений].
 *
 * Отличие от Summary:
 * - Summary сжимает историю в свободный текст — хорошо для narrative continuity.
 * - Sticky Facts структурируют информацию в key-value — хорошо для точечных фактов,
 *   которые LLM должен "помнить" на протяжении всего диалога.
 *
 * Обновление фактов:
 * - При каждом buildContext проверяется, появились ли новые сообщения после lastUpdatedAt.
 * - Если да — LLM получает текущие факты + новые сообщения и возвращает обновлённый набор.
 * - LLM может добавлять новые ключи, обновлять значения и удалять устаревшие (через DELETE:key).
 *
 * @param chatMessageDao DAO для чтения сообщений.
 * @param stickyFactDao DAO для чтения/записи фактов.
 * @param httpClient Ktor HTTP клиент для вызовов DeepSeek API (извлечение фактов).
 */
class StickyFactsStrategy(
    private val chatMessageDao: ChatMessageDao,
    private val stickyFactDao: StickyFactDao,
    private val httpClient: HttpClient,
) : ContextBuildStrategy {

    /**
     * Собирает контекст: [System: facts] + [последние N сообщений].
     *
     * Перед сборкой проверяет, нужно ли обновить факты (есть ли новые user-сообщения).
     */
    override suspend fun buildContext(conversationId: String, windowSize: Int): List<Message> {
        val allMessages = chatMessageDao.getMessages(conversationId)
            .filter { it.role == RoomChatHistoryProvider.ROLE_USER || it.role == RoomChatHistoryProvider.ROLE_ASSISTANT }
            .filter { it.content.isNotBlank() }

        // Обновляем факты, если есть новые сообщения после последнего обновления
        maybeUpdateFacts(conversationId, allMessages)

        val facts = stickyFactDao.getFacts(conversationId)
        val recentMessages = allMessages.takeLast(windowSize)

        val result = mutableListOf<Message>()

        // 1. Блок фактов как System message
        if (facts.isNotEmpty()) {
            val factsBlock = formatFactsBlock(facts)
            result.add(
                Message.System(
                    content = "$FACTS_PREFIX\n$factsBlock",
                    metaInfo = RequestMetaInfo.Empty,
                )
            )
        }

        // 2. Последние N сообщений — всегда полностью
        for (msg in recentMessages) {
            msg.toKoogMessage()?.let { result.add(it) }
        }

        return result
    }

    override suspend fun getStats(conversationId: String): ContextBuildStats {
        val facts = stickyFactDao.getFacts(conversationId)
        val totalMessages = chatMessageDao.getMessages(conversationId)
            .count { it.role == RoomChatHistoryProvider.ROLE_USER || it.role == RoomChatHistoryProvider.ROLE_ASSISTANT }
        return ContextBuildStats(
            totalMessages = totalMessages,
            contextMessages = totalMessages,
            strategyLabel = "Sticky Facts",
            details = if (facts.isNotEmpty()) {
                "${facts.size} facts extracted"
            } else {
                "No facts yet"
            },
        )
    }

    /**
     * Проверяет, появились ли новые user-сообщения после последнего обновления фактов.
     * Если да — запускает извлечение/обновление фактов через LLM.
     *
     * Триггер: timestamp последнего user-сообщения > максимальный updatedAt среди фактов.
     * Для первого вызова (фактов ещё нет) — нужно хотя бы [MIN_MESSAGES_FOR_EXTRACTION] сообщений.
     */
    private suspend fun maybeUpdateFacts(
        conversationId: String,
        allMessages: List<com.devchik.ai.core.database.entity.ChatMessageEntity>,
    ) {
        val existingFacts = stickyFactDao.getFacts(conversationId)
        val lastFactUpdate = existingFacts.maxOfOrNull { it.updatedAt } ?: 0L

        // Находим user-сообщения новее последнего обновления фактов
        val newUserMessages = allMessages
            .filter { it.role == RoomChatHistoryProvider.ROLE_USER && it.timestamp > lastFactUpdate }

        if (newUserMessages.isEmpty()) return

        // Для первого извлечения ждём минимум сообщений, чтобы было что извлекать
        if (existingFacts.isEmpty() && allMessages.size < MIN_MESSAGES_FOR_EXTRACTION) return

        // Берём последние сообщения для контекста извлечения (не всю историю — экономим токены)
        val contextForExtraction = allMessages.takeLast(EXTRACTION_CONTEXT_SIZE)

        val updatedFacts = extractFacts(conversationId, existingFacts, contextForExtraction)
        if (updatedFacts != null) {
            applyFactUpdates(conversationId, existingFacts, updatedFacts)
        }
    }

    /**
     * Вызывает DeepSeek API для извлечения/обновления фактов из диалога.
     *
     * LLM получает:
     * - System prompt с инструкцией формата ответа (key=value, по одному на строку).
     * - Текущие факты (если есть) для обновления/удаления.
     * - Последние сообщения диалога.
     *
     * @return Список пар (key, value), где value="DELETE" означает удаление факта.
     *         null при ошибке.
     */
    private suspend fun extractFacts(
        conversationId: String,
        existingFacts: List<StickyFactEntity>,
        recentMessages: List<com.devchik.ai.core.database.entity.ChatMessageEntity>,
    ): List<Pair<String, String>>? {
        val currentFactsText = if (existingFacts.isNotEmpty()) {
            "Current facts:\n" + existingFacts.joinToString("\n") { "${it.factKey}=${it.factValue}" }
        } else {
            "No existing facts yet."
        }

        val conversationText = recentMessages.joinToString("\n") { entity ->
            val role = if (entity.role == RoomChatHistoryProvider.ROLE_USER) "User" else "Assistant"
            "$role: ${entity.content}"
        }

        val request = DeepSeekRequest(
            model = MODEL,
            messages = listOf(
                DeepSeekMessageDto(
                    role = "system",
                    content = EXTRACTION_SYSTEM_PROMPT,
                ),
                DeepSeekMessageDto(
                    role = "user",
                    content = "$currentFactsText\n\nRecent conversation:\n$conversationText",
                ),
            ),
            maxTokens = MAX_EXTRACTION_TOKENS,
            temperature = 0.2f,
        )

        return try {
            val response = httpClient.post("$BASE_URL/chat/completions") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${BuildKonfig.DEEPSEEK_API_KEY}")
                setBody(request)
            }.body<DeepSeekResponse>()

            val content = response.choices.firstOrNull()?.message?.content ?: return null
            parseFactsResponse(content)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Парсит ответ LLM в список пар (key, value).
     * Формат: одна строка = один факт в формате "key=value".
     * Специальное значение "DELETE" означает удаление факта.
     * Строки без "=" игнорируются.
     */
    private fun parseFactsResponse(response: String): List<Pair<String, String>> {
        return response.lines()
            .map { it.trim() }
            .filter { it.contains("=") }
            .mapNotNull { line ->
                val eqIndex = line.indexOf('=')
                if (eqIndex <= 0) return@mapNotNull null
                val key = line.substring(0, eqIndex).trim()
                    .lowercase()
                    .replace(" ", "_")
                    // Убираем markdown-артефакты, если LLM добавил
                    .removePrefix("-")
                    .removePrefix("*")
                    .trim()
                val value = line.substring(eqIndex + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) key to value else null
            }
    }

    /**
     * Применяет обновления фактов: upsert новых/изменённых, удаление помеченных DELETE.
     */
    private suspend fun applyFactUpdates(
        conversationId: String,
        existingFacts: List<StickyFactEntity>,
        updatedFacts: List<Pair<String, String>>,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val toUpsert = mutableListOf<StickyFactEntity>()
        val toDelete = mutableListOf<String>()

        for ((key, value) in updatedFacts) {
            if (value.equals("DELETE", ignoreCase = true)) {
                toDelete.add(key)
            } else {
                val existing = existingFacts.find { it.factKey == key }
                // Upsert только если значение реально изменилось или факт новый
                if (existing == null || existing.factValue != value) {
                    toUpsert.add(
                        StickyFactEntity(
                            sessionId = conversationId,
                            factKey = key,
                            factValue = value,
                            updatedAt = now,
                        )
                    )
                }
            }
        }

        if (toUpsert.isNotEmpty()) {
            stickyFactDao.upsertFacts(toUpsert)
        }
        for (key in toDelete) {
            stickyFactDao.deleteFact(conversationId, key)
        }
    }

    /**
     * Форматирует факты в читаемый блок для System message.
     * Формат: "- key: value" по одному на строку.
     */
    private fun formatFactsBlock(facts: List<StickyFactEntity>): String {
        return facts.joinToString("\n") { "- ${it.factKey}: ${it.factValue}" }
    }

    companion object {
        private const val BASE_URL = "https://api.deepseek.com"
        private const val MODEL = "deepseek-chat"
        private const val MAX_EXTRACTION_TOKENS = 600

        /** Минимум сообщений для первого извлечения фактов */
        private const val MIN_MESSAGES_FOR_EXTRACTION = 4
        /** Сколько последних сообщений отправлять LLM для извлечения фактов */
        private const val EXTRACTION_CONTEXT_SIZE = 20

        const val FACTS_PREFIX = "[Key facts from this conversation]"

        /**
         * System prompt для LLM-экстрактора фактов.
         *
         * Инструкции:
         * - Формат ответа: строго key=value, по одному на строку.
         * - Категории ключей: goal, constraints, preferences, decisions, agreements, tech_stack, context.
         * - DELETE:key — для удаления устаревших фактов.
         * - Язык фактов — тот же, что и в диалоге.
         */
        private const val EXTRACTION_SYSTEM_PROMPT =
            "You are a fact extractor. Analyze the conversation and extract/update key facts.\n\n" +
            "RULES:\n" +
            "1. Output ONLY in format: key=value (one per line, nothing else).\n" +
            "2. Use short snake_case keys from these categories: " +
            "goal, constraints, preferences, decisions, agreements, tech_stack, context, user_name, language, " +
            "current_task, important_details, deadlines, tools, architecture.\n" +
            "3. If a fact changed, output the key with new value (it will be updated).\n" +
            "4. If a fact is no longer relevant, output: key=DELETE\n" +
            "5. Keep values concise (1-2 sentences max).\n" +
            "6. Write values in the same language as the conversation.\n" +
            "7. Only extract genuinely important, reusable facts — skip trivial details.\n" +
            "8. If there are existing facts that are still accurate, include them unchanged.\n\n" +
            "Example output:\n" +
            "goal=Build a mobile chat app with AI integration\n" +
            "tech_stack=Kotlin Multiplatform, Compose, Room, Koin, DeepSeek API\n" +
            "preferences=User prefers Russian comments in code\n" +
            "current_task=Implementing sticky facts context strategy"
    }
}
