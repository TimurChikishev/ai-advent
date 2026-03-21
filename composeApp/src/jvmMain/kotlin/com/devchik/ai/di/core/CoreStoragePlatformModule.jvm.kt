package com.devchik.ai.di.core

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.devchik.ai.core.database.AppDatabase
import com.devchik.ai.core.datastore.DATASTORE_FILE_NAME
import com.devchik.ai.core.datastore.createDataStore
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

actual val coreStoragePlatformModule: Module = module {
    single {
        createDataStore(
            producePath = {
                File(System.getProperty("user.home"), ".ai/$DATASTORE_FILE_NAME").absolutePath
            }
        )
    }

    single {
        val dbFile = File(System.getProperty("user.home"), ".ai/${AppDatabase.DATABASE_NAME}")
        dbFile.parentFile?.mkdirs()
        Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()
    }
}
