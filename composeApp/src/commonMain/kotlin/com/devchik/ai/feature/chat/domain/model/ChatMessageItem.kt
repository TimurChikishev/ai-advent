package com.devchik.ai.feature.chat.domain.model

sealed class ChatMessageItem {
    abstract val content: String

    data class User(override val content: String) : ChatMessageItem()
    data class Assistant(override val content: String) : ChatMessageItem()
    data class System(override val content: String) : ChatMessageItem()
    data class Error(override val content: String) : ChatMessageItem()
}
