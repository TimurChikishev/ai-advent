package com.devchik.ai.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.devchik.ai.core.database.dao.ChatMessageDao
import com.devchik.ai.core.database.dao.ChatSessionDao
import com.devchik.ai.core.database.dao.ChatSummaryDao
import com.devchik.ai.core.database.dao.StickyFactDao
import com.devchik.ai.core.database.entity.ChatMessageEntity
import com.devchik.ai.core.database.entity.ChatSessionEntity
import com.devchik.ai.core.database.entity.ChatSummaryEntity
import com.devchik.ai.core.database.entity.StickyFactEntity

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        ChatSummaryEntity::class,
        StickyFactEntity::class,
    ],
    version = 6,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatSummaryDao(): ChatSummaryDao
    abstract fun stickyFactDao(): StickyFactDao

    companion object {
        const val DATABASE_NAME = "ai_chat.db"
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
