package com.devchik.ai.feature.ai.data

import com.devchik.ai.BuildKonfig
import com.devchik.ai.feature.ai.data.model.DeepSeekRequest
import com.devchik.ai.feature.ai.data.model.DeepSeekStreamChunk
import com.devchik.ai.feature.ai.domain.AIRepository
import com.devchik.ai.feature.ai.domain.model.AIMessage
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json

class AIRepositoryImpl(
    private val httpClient: HttpClient,
) : AIRepository {

    override fun sendMessage(messages: List<AIMessage>): Flow<String> = channelFlow {
        httpClient.preparePost("$BASE_URL/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${BuildKonfig.DEEPSEEK_API_KEY}")
            setBody(
                DeepSeekRequest(
                    model = MODEL,
                    messages = messages.map { it.toDto() },
                    stream = true,
                )
            )
        }.execute { response ->
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                if (data.isBlank()) continue
                val chunk = runCatching { json.decodeFromString<DeepSeekStreamChunk>(data) }.getOrNull()
                chunk?.choices?.firstOrNull()?.delta?.content?.let { send(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun AIMessage.toDto() = com.devchik.ai.feature.ai.data.model.DeepSeekMessageDto(
        role = when (role) {
            AIMessage.Role.User -> "user"
            AIMessage.Role.Assistant -> "assistant"
        },
        content = content,
    )

    companion object {
        private const val BASE_URL = "https://api.deepseek.com"
        private const val MODEL = "deepseek-chat"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
