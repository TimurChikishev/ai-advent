package com.devchik.ai.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Room entity для хранения "липких фактов" (key-value) из диалога.
 *
 * Используется стратегией [StickyFactsStrategy]: после каждого сообщения пользователя
 * LLM анализирует диалог и извлекает/обновляет ключевые факты — цели, ограничения,
 * предпочтения, решения, договорённости.
 *
 * Составной PK: (sessionId + factKey) — один ключ на сессию уникален,
 * при обновлении факта запись перезаписывается (REPLACE).
 *
 * Связь с сессией: FK на [ChatSessionEntity] с CASCADE delete.
 */
@Entity(
    tableName = "sticky_facts",
    primaryKeys = ["sessionId", "factKey"],
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class StickyFactEntity(
    /** ID сессии чата, к которой относится факт */
    val sessionId: String,
    /** Ключ факта — короткий идентификатор категории (например "goal", "constraints", "tech_stack") */
    val factKey: String,
    /** Значение факта — текстовое описание на естественном языке */
    val factValue: String,
    /** Timestamp последнего обновления этого факта */
    val updatedAt: Long,
)
