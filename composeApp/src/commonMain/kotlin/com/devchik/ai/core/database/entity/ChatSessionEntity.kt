package com.devchik.ai.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity для сессии чата.
 *
 * Каждая сессия хранит свои настройки стратегии управления контекстом,
 * что позволяет использовать разные стратегии в разных диалогах.
 *
 * Для branching: сессия может быть веткой другой сессии.
 * [parentSessionId] указывает на родителя, [branchPointMessageId] — на сообщение-checkpoint.
 */
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Стратегия контекста для этой сессии (ContextStrategy.name). null = берётся из глобальных настроек. */
    val contextStrategy: String? = null,
    /** Размер окна контекста для этой сессии. null = берётся из глобальных настроек. */
    val contextWindowSize: Int? = null,
    /** ID родительской сессии, от которой создана ветка. null = корневая сессия. */
    val parentSessionId: String? = null,
    /** ID сообщения в родительской сессии, от которого сделан branch. */
    val branchPointMessageId: Long? = null,
)
