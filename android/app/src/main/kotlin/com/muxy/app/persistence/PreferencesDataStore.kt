package com.muxy.app.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.muxy.app.core.logging.Log
import kotlinx.coroutines.CoroutineScope
import java.io.File

fun preferencesDataStore(
    name: String,
    scope: CoroutineScope,
    file: () -> File,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler =
            ReplaceFileCorruptionHandler { error ->
                Log.persistence.error("$name were unreadable and have been reset", error)
                emptyPreferences()
            },
        scope = scope,
        produceFile = file,
    )
