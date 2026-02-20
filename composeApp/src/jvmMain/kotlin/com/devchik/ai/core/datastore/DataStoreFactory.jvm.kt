package com.devchik.ai.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

actual fun createDataStore(): DataStore<Preferences> = createDataStore(
    producePath = {
        File(System.getProperty("user.home"), ".ai/$DATASTORE_FILE_NAME").absolutePath
    }
)
