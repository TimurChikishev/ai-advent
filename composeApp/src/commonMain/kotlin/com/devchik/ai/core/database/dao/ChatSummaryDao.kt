package com.devchik.ai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.devchik.ai.core.database.entity.ChatSummaryEntity

@Dao
interface ChatSummaryDao {

    @Query("SELECT * FROM chat_summaries WHERE sessionId = :sessionId ORDER BY coveredUntilTimestamp ASC")
    suspend fun getSummaries(sessionId: String): List<ChatSummaryEntity>

    @Query("SELECT * FROM chat_summaries WHERE sessionId = :sessionId ORDER BY coveredUntilTimestamp DESC LIMIT 1")
    suspend fun getLatestSummary(sessionId: String): ChatSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: ChatSummaryEntity)

    @Query("DELETE FROM chat_summaries WHERE sessionId = :sessionId")
    suspend fun deleteSummariesBySession(sessionId: String)
}
