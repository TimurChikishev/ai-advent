package com.devchik.ai.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val sessionId: String,
    val summary: String,
    /** Timestamp of the newest message included in this summary. */
    val coveredUntilTimestamp: Long,
    /** Number of original messages compressed into this summary. */
    val messageCount: Int,
    val createdAt: Long,
)
