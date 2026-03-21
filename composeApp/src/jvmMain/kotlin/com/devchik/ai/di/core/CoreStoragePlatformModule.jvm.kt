package com.devchik.ai.di.core

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
}