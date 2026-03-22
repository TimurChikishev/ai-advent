package com.devchik.ai.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity для хранения summary (сжатий) истории диалога.
 *
 * Используется стратегией [SummaryStrategy] для персистенции результатов суммаризации.
 * Каждая запись представляет одно summary, покрывающее батч из [messageCount] сообщений.
 *
 * Summary хранятся отдельно от сообщений, чтобы:
 * - Не пересоздавать при каждом запросе (LLM вызов дорогой).
 * - Сохранять при перезапуске приложения.
 * - Позволять поэтапное сжатие (несколько summary для одной сессии).
 *
 * Связь с сессией: FK на [ChatSessionEntity] с CASCADE delete —
 * при удалении сессии все её summary удаляются автоматически.
 */
@Entity(
    tableName = "chat_summaries",
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
data class ChatSummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** ID сессии чата, к которой относится summary */
    val sessionId: String,
    /** Текст summary — plain text на естественном языке, сгенерированный LLM */
    val summary: String,
    /**
     * Timestamp последнего сообщения, включённого в это summary.
     * Используется как "граница" — сообщения с timestamp <= этого значения
     * считаются уже суммаризированными и не обрабатываются повторно.
     */
    val coveredUntilTimestamp: Long,
    /** Количество оригинальных сообщений, сжатых в это summary */
    val messageCount: Int,
    /** Время создания самого summary */
    val createdAt: Long,
)
