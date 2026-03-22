package com.devchik.ai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.devchik.ai.core.database.entity.ChatSummaryEntity

/**
 * DAO для работы с таблицей chat_summaries.
 *
 * Используется [SummaryStrategy] для:
 * - Чтения существующих summary (чтобы не пересоздавать).
 * - Сохранения новых summary после суммаризации батча.
 * - Удаления summary при удалении сессии (CASCADE на FK).
 */
@Dao
interface ChatSummaryDao {

    /** Все summary для сессии, отсортированные по времени покрытия (от ранних к поздним) */
    @Query("SELECT * FROM chat_summaries WHERE sessionId = :sessionId ORDER BY coveredUntilTimestamp ASC")
    suspend fun getSummaries(sessionId: String): List<ChatSummaryEntity>

    /** Последнее (самое свежее) summary — для быстрого определения границы суммаризации */
    @Query("SELECT * FROM chat_summaries WHERE sessionId = :sessionId ORDER BY coveredUntilTimestamp DESC LIMIT 1")
    suspend fun getLatestSummary(sessionId: String): ChatSummaryEntity?

    /** Вставка нового summary. REPLACE при конфликте ID (autoGenerate, конфликт маловероятен). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: ChatSummaryEntity)

    /** Удаление всех summary сессии — используется при ручной очистке */
    @Query("DELETE FROM chat_summaries WHERE sessionId = :sessionId")
    suspend fun deleteSummariesBySession(sessionId: String)
}
