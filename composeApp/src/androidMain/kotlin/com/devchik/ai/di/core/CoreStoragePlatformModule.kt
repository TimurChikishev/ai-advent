package com.devchik.ai.di.core

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
}