package com.devchik.ai.feature.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeepSeekRequest(
    val model: String,
    val messages: List<DeepSeekMessageDto>,
    val stream: Boolean = false,
)

@Serializable
data class DeepSeekMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class DeepSeekResponse(
    val choices: List<DeepSeekChoice>,
)

@Serializable
data class DeepSeekChoice(
    val index: Int,
    val message: DeepSeekMessageDto,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class DeepSeekStreamChunk(
    val choices: List<DeepSeekStreamChoice>,
)

@Serializable
data class DeepSeekStreamChoice(
    val delta: DeepSeekDelta,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class DeepSeekDelta(
    val role: String? = null,
    val content: String? = null,
)
