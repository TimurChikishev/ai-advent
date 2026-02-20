package com.devchik.ai.feature.ai.domain.model

data class AIMessage(
    val role: Role,
    val content: String,
) {
    enum class Role { User, Assistant }
}
