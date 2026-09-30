package com.muxy.app.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import com.muxy.app.features.navigation.RootViewModel
import com.muxy.app.features.settings.SettingsViewModel
import com.muxy.app.persistence.settings.DataStoreSettingsStore
import com.muxy.app.persistence.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(
    context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore: SettingsStore =
        DataStoreSettingsStore(
            dataStore = DataStoreSettingsStore.preferences(scope) { context.preferencesDataStoreFile(SETTINGS_FILE) },
            scope = scope,
        )

    fun makeRootViewModel(): RootViewModel = RootViewModel(settingsStore)

    fun makeSettingsViewModel(): SettingsViewModel = SettingsViewModel(settingsStore)

    private companion object {
        const val SETTINGS_FILE = "settings"
    }
}
