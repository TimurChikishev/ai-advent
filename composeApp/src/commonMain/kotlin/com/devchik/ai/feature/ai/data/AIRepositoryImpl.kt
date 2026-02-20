package com.devchik.ai.feature.ai.data

import com.devchik.ai.feature.ai.domain.AIRepository
import com.devchik.ai.feature.ai.domain.model.AIMessage
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AIRepositoryImpl(
    private val httpClient: HttpClient,
) : AIRepository {

    override suspend fun sendMessage(prompt: String): Result<AIMessage> = runCatching {
        val responseText = httpClient.post("https://api.example.com/ai/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt": "$prompt"}""")
        }.bodyAsText()

        AIMessage(
            role = AIMessage.Role.Assistant,
            content = responseText,
        )
    }
}
