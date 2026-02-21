package com.devchik.ai.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

lateinit var androidAppContext: Context

actual fun createDataStore(): DataStore<Preferences> = createDataStore(
    producePath = { androidAppContext.filesDir.resolve(DATASTORE_FILE_NAME).absolutePath }
)
