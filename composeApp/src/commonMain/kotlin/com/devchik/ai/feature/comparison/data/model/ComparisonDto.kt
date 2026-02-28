package com.devchik.ai.feature.comparison.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ComparisonRequest(
    val model: String,
    val messages: List<ComparisonMessageDto>,
    val stream: Boolean = false,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Float? = null,
)

@Serializable
data class ComparisonMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class ComparisonStreamChunk(
    val choices: List<ComparisonStreamChoice> = emptyList(),
    val usage: ComparisonUsage? = null,
)

@Serializable
data class ComparisonStreamChoice(
    val delta: ComparisonDelta = ComparisonDelta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ComparisonDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
data class ComparisonUsage(
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)
