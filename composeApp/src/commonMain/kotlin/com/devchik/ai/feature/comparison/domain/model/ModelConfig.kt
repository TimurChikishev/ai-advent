package com.devchik.ai.feature.comparison.domain.model

enum class ModelTier { Weak, Medium, Strong }

data class ModelConfig(
    val tier: ModelTier,
    val modelId: String,
    val displayName: String,
    val parameters: String,
    val providerName: String,
    val apiEndpoint: String,
    val apiKey: String,
    val maxTokens: Int,
    val temperature: Float,
)
