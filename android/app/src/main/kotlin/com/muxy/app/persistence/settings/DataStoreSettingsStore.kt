package com.muxy.app.persistence.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.muxy.app.core.logging.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.io.IOException

class DataStoreSettingsStore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : SettingsStore {
    override val settings: StateFlow<AppSettings?> =
        dataStore.data
            .catch { error ->
                if (error !is IOException) throw error
                Log.persistence.error("Reading settings failed", error)
                emit(emptyPreferences())
            }.map { it.toAppSettings() }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        try {
            dataStore.edit { preferences -> preferences.write(transform(preferences.toAppSettings())) }
        } catch (error: IOException) {
            Log.persistence.error("Saving settings failed", error)
        }
    }

    companion object {
        fun preferences(
            scope: CoroutineScope,
            file: () -> File,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler =
                    ReplaceFileCorruptionHandler { error ->
                        Log.persistence.error("Settings were unreadable and have been reset", error)
                        emptyPreferences()
                    },
                scope = scope,
                produceFile = file,
            )
    }
}

private object Keys {
    val hasCompletedOnboarding = booleanPreferencesKey("muxy.hasCompletedOnboarding")
    val themeName = stringPreferencesKey("muxy.settings.theme")
    val useNerdFont = booleanPreferencesKey("muxy.settings.useNerdFont")
    val autoFocusTerminal = booleanPreferencesKey("muxy.settings.autoFocusTerminal")
    val demoMode = booleanPreferencesKey("muxy.settings.demoMode")
}

private fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        hasCompletedOnboarding = this[Keys.hasCompletedOnboarding] ?: defaults.hasCompletedOnboarding,
        themeName = this[Keys.themeName] ?: defaults.themeName,
        useNerdFont = this[Keys.useNerdFont] ?: defaults.useNerdFont,
        autoFocusTerminal = this[Keys.autoFocusTerminal] ?: defaults.autoFocusTerminal,
        demoMode = this[Keys.demoMode] ?: defaults.demoMode,
    )
}

private fun MutablePreferences.write(settings: AppSettings) {
    this[Keys.hasCompletedOnboarding] = settings.hasCompletedOnboarding
    this[Keys.themeName] = settings.themeName
    this[Keys.useNerdFont] = settings.useNerdFont
    this[Keys.autoFocusTerminal] = settings.autoFocusTerminal
    this[Keys.demoMode] = settings.demoMode
}
