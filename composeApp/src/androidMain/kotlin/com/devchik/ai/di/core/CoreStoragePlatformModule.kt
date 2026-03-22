package com.devchik.ai.di.core

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.devchik.ai.core.database.AppDatabase
import com.devchik.ai.core.datastore.DATASTORE_FILE_NAME
import com.devchik.ai.core.datastore.createDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val coreStoragePlatformModule = module {
    single {
        createDataStore(
            producePath = { androidContext().filesDir.resolve(DATASTORE_FILE_NAME).absolutePath }
        )
    }

    single {
        Room.databaseBuilder<AppDatabase>(
            context = androidContext(),
            name = androidContext().getDatabasePath(AppDatabase.DATABASE_NAME).absolutePath,
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .setDriver(BundledSQLiteDriver())
            .build()
    }
}
