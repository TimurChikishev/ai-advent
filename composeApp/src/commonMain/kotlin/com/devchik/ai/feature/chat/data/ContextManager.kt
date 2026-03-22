package com.devchik.ai.feature.chat.data

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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Manages conversation context compression for the Koog agent chat.
 *
 * Strategy:
 * - The last [RECENT_WINDOW] user+assistant messages are kept verbatim.
 * - Older messages are compressed into summaries in batches of [SUMMARY_BATCH_SIZE].
 * - Summaries are persisted in [ChatSummaryEntity] so they survive app restarts
 *   and don't need to be regenerated.
 * - When loading context for the LLM, the result is:
 *   `[System: combined summaries] + [recent N messages]`.
 *
 * Summarization uses the DeepSeek API directly (non-streaming) to produce
 * a concise summary of conversation segments.
 */
class ContextManager(
    private val chatMessageDao: ChatMessageDao,
    private val chatSummaryDao: ChatSummaryDao,
    private val httpClient: HttpClient,
) {
    private val mutex = Mutex()

    /**
     * Builds the optimized context for a conversation.
     * Returns Koog [Message] list: optional summary system message + recent messages.
     */
    suspend fun buildContext(conversationId: String): List<Message> = mutex.withLock {
        val allMessages = chatMessageDao.getMessages(conversationId)
            .filter { it.role == RoomChatHistoryProvider.ROLE_USER || it.role == RoomChatHistoryProvider.ROLE_ASSISTANT }
            .filter { it.content.isNotBlank() }

        if (allMessages.size <= RECENT_WINDOW) {
            return@withLock allMessages.mapNotNull { it.toKoogMessage() }
        }

        val recentMessages = allMessages.takeLast(RECENT_WINDOW)
        val olderMessages = allMessages.dropLast(RECENT_WINDOW)

        val existingSummaries = chatSummaryDao.getSummaries(conversationId)
        val lastSummaryCutoff = existingSummaries.maxOfOrNull { it.coveredUntilTimestamp } ?: 0L

        val unsummarized = olderMessages.filter { it.timestamp > lastSummaryCutoff }

        if (unsummarized.size >= SUMMARY_BATCH_SIZE) {
            val batches = unsummarized.chunked(SUMMARY_BATCH_SIZE)
            for (batch in batches) {
                if (batch.size < SUMMARY_BATCH_SIZE) break
                val summaryText = generateSummary(batch)
                if (summaryText != null) {
                    chatSummaryDao.insertSummary(
                        ChatSummaryEntity(
                            sessionId = conversationId,
                            summary = summaryText,
                            coveredUntilTimestamp = batch.last().timestamp,
                            messageCount = batch.size,
                            createdAt = Clock.System.now().toEpochMilliseconds(),
                        )
                    )
                }
            }
        }

        val allSummaries = chatSummaryDao.getSummaries(conversationId)
        val combinedSummary = buildCombinedSummary(allSummaries)

        val result = mutableListOf<Message>()
        if (combinedSummary.isNotBlank()) {
            result.add(
                Message.System(
                    content = "$SUMMARY_PREFIX\n$combinedSummary",
                    metaInfo = RequestMetaInfo.Empty,
                )
            )
        }

        val latestSummaryCutoff = allSummaries.maxOfOrNull { it.coveredUntilTimestamp } ?: 0L
        val unsummarizedRemaining = olderMessages.filter { it.timestamp > latestSummaryCutoff }
        for (msg in unsummarizedRemaining) {
            msg.toKoogMessage()?.let { result.add(it) }
        }

        for (msg in recentMessages) {
            msg.toKoogMessage()?.let { result.add(it) }
        }

        result
    }

    /**
     * Returns summary stats for UI display.
     */
    suspend fun getSummaryStats(conversationId: String): SummaryStats {
        val summaries = chatSummaryDao.getSummaries(conversationId)
        val totalMessages = chatMessageDao.getMessages(conversationId)
            .count { it.role == RoomChatHistoryProvider.ROLE_USER || it.role == RoomChatHistoryProvider.ROLE_ASSISTANT }
        val summarizedCount = summaries.sumOf { it.messageCount }
        return SummaryStats(
            totalMessages = totalMessages,
            summarizedMessages = summarizedCount,
            summaryCount = summaries.size,
            isCompressed = summaries.isNotEmpty(),
        )
    }

    private suspend fun generateSummary(messages: List<ChatMessageEntity>): String? {
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
            temperature = 0.3f,
        )

        return try {
            val response = httpClient.post("$BASE_URL/chat/completions") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${BuildKonfig.DEEPSEEK_API_KEY}")
                setBody(request)
            }.body<DeepSeekResponse>()

            response.choices.firstOrNull()?.message?.content
        } catch (e: Exception) {
            null
        }
    }

    private fun buildCombinedSummary(summaries: List<ChatSummaryEntity>): String {
        if (summaries.isEmpty()) return ""
        if (summaries.size == 1) return summaries.first().summary
        return summaries.joinToString("\n\n") { summary ->
            "[${summary.messageCount} messages] ${summary.summary}"
        }
    }

    data class SummaryStats(
        val totalMessages: Int,
        val summarizedMessages: Int,
        val summaryCount: Int,
        val isCompressed: Boolean,
    )

    companion object {
        const val RECENT_WINDOW = 10
        const val SUMMARY_BATCH_SIZE = 10

        private const val BASE_URL = "https://api.deepseek.com"
        private const val MODEL = "deepseek-chat"
        private const val MAX_SUMMARY_TOKENS = 500

        const val SUMMARY_PREFIX = "[Previous conversation summary]"

        private const val SUMMARIZATION_SYSTEM_PROMPT =
            "You are a conversation summarizer. Produce a concise summary of the given conversation segment. " +
            "Preserve key facts, decisions, code snippets references, and user preferences. " +
            "Keep the summary in the same language(s) as the original conversation. " +
            "Be factual and brief — aim for 3-5 sentences."
    }
}

private fun ChatMessageEntity.toKoogMessage(): Message? {
    return when (role) {
        RoomChatHistoryProvider.ROLE_USER -> Message.User(
            content = content,
            metaInfo = RequestMetaInfo.Empty,
        )
        RoomChatHistoryProvider.ROLE_ASSISTANT -> Message.Assistant(
            content = content,
            metaInfo = ai.koog.prompt.message.ResponseMetaInfo.Empty,
        )
        else -> null
    }
}
