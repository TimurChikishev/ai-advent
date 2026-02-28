package com.devchik.ai.feature.comparison.data

import com.devchik.ai.BuildKonfig
import com.devchik.ai.feature.comparison.data.model.ComparisonMessageDto
import com.devchik.ai.feature.comparison.data.model.ComparisonRequest
import com.devchik.ai.feature.comparison.data.model.ComparisonStreamChunk
import com.devchik.ai.feature.comparison.domain.ComparisonRepository
import com.devchik.ai.feature.comparison.domain.model.ModelConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json

class ComparisonRepositoryImpl(
    private val httpClient: HttpClient,
) : ComparisonRepository {

    override fun query(config: ModelConfig, prompt: String): Flow<String> = channelFlow {
        httpClient.preparePost("${config.apiEndpoint}/chat/completions") {
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
                socketTimeoutMillis = Long.MAX_VALUE
            }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            setBody(
                ComparisonRequest(
                    model = config.modelId,
                    messages = listOf(ComparisonMessageDto(role = "user", content = prompt)),
                    stream = true,
                    maxTokens = config.maxTokens,
                    temperature = config.temperature,
                )
            )
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw Exception("[${response.status.value}] $errorBody")
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                if (data.isBlank()) continue
                val chunk = runCatching { json.decodeFromString<ComparisonStreamChunk>(data) }.getOrNull()
                chunk?.choices?.firstOrNull()?.delta?.content?.let { send(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun analyze(prompt: String, results: List<Pair<String, String>>): Flow<String> = channelFlow {
        val analysisPrompt = buildAnalysisPrompt(prompt, results)

        httpClient.preparePost("$DEEPSEEK_BASE_URL/chat/completions") {
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
                socketTimeoutMillis = Long.MAX_VALUE
            }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${BuildKonfig.DEEPSEEK_API_KEY}")
            setBody(
                ComparisonRequest(
                    model = "deepseek-chat",
                    messages = listOf(ComparisonMessageDto(role = "user", content = analysisPrompt)),
                    stream = true,
                    maxTokens = 2048,
                    temperature = 0.7f,
                )
            )
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw Exception("[${response.status.value}] $errorBody")
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                if (data.isBlank()) continue
                val chunk = runCatching { json.decodeFromString<ComparisonStreamChunk>(data) }.getOrNull()
                chunk?.choices?.firstOrNull()?.delta?.content?.let { send(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getTokenCount(config: ModelConfig, prompt: String, response: String): Int {
        val promptWords = prompt.split("\\s+".toRegex()).count { it.isNotBlank() }
        val responseWords = response.split("\\s+".toRegex()).count { it.isNotBlank() }
        return ((promptWords + responseWords) * 1.3).toInt()
    }

    private fun buildAnalysisPrompt(prompt: String, results: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.appendLine("Тебе даны несколько ответов на один и тот же вопрос от разных источников.")
        sb.appendLine("Твоя задача — провести объективный анализ, не зная, кто их написал.")
        sb.appendLine()
        sb.appendLine("**Вопрос:** $prompt")
        sb.appendLine()
        results.forEachIndexed { index, (_, response) ->
            val label = "Ответ ${index + 1}"
            sb.appendLine("---")
            sb.appendLine("### $label")
            sb.appendLine(response.take(800))
            sb.appendLine()
        }
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("Проанализируй ответы выше и для каждого из них оцени:")
        sb.appendLine("1. **Точность и фактическая корректность**")
        sb.appendLine("2. **Полнота и глубина раскрытия темы**")
        sb.appendLine("3. **Структура и читаемость**")
        sb.appendLine("4. **Сильные и слабые стороны**")
        sb.appendLine()
        sb.appendLine("В конце дай итоговую оценку: какой ответ лучше и почему.")

        return sb.toString()
    }

    companion object {
        private const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
