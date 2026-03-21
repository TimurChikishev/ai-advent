package com.devchik.ai.di.core

import com.devchik.ai.core.datastore.DATASTORE_FILE_NAME
import com.devchik.ai.core.datastore.createDataStore
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual val coreStoragePlatformModule: Module = module {
    single {
        createDataStore(
            producePath = {
                val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
                    directory = NSDocumentDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = false,
                    error = null,
                )
                requireNotNull(documentDirectory).path + "/$DATASTORE_FILE_NAME"
            }
        )
    }
}