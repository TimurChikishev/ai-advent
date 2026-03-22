package com.devchik.ai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.devchik.ai.core.database.entity.StickyFactEntity

/**
 * DAO для работы с таблицей sticky_facts.
 *
 * Используется [StickyFactsStrategy] для:
 * - Чтения текущих фактов сессии (для подстановки в LLM контекст).
 * - Upsert фактов после извлечения LLM (REPLACE при конфликте по PK).
 * - Удаления отдельных фактов (если LLM решит, что факт устарел).
 */
@Dao
interface StickyFactDao {

    /** Все факты для сессии, отсортированные по ключу для стабильного порядка */
    @Query("SELECT * FROM sticky_facts WHERE sessionId = :sessionId ORDER BY factKey ASC")
    suspend fun getFacts(sessionId: String): List<StickyFactEntity>

    /** Upsert одного факта: вставка или обновление при совпадении (sessionId, factKey) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFact(fact: StickyFactEntity)

    /** Upsert списка фактов за один вызов */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFacts(facts: List<StickyFactEntity>)

    /** Удаление конкретного факта по ключу */
    @Query("DELETE FROM sticky_facts WHERE sessionId = :sessionId AND factKey = :factKey")
    suspend fun deleteFact(sessionId: String, factKey: String)

    /** Удаление всех фактов сессии — при ручной очистке */
    @Query("DELETE FROM sticky_facts WHERE sessionId = :sessionId")
    suspend fun deleteFactsBySession(sessionId: String)
}
