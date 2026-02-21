package com.devchik.ai.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath

internal const val DATASTORE_FILE_NAME = "ai_settings.preferences_pb"

// Shared implementation used by all platforms
fun createDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { producePath().toPath() }
    )

expect fun createDataStore(): DataStore<Preferences>
