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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class DataStoreSettingsStore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : SettingsStore {
    private val state = MutableStateFlow<AppSettings?>(null)

    override val settings: StateFlow<AppSettings?> = state.asStateFlow()

    init {
        scope.launch {
            dataStore.data
                .retryWhen { error, attempt ->
                    if (error !is IOException) return@retryWhen false
                    Log.persistence.error("Reading settings failed", error)
                    state.compareAndSet(null, AppSettings())
                    delay(readRetryDelayMillis(attempt))
                    true
                }.collect { state.value = it.toAppSettings() }
        }
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        try {
            dataStore.edit { preferences -> preferences.write(transform(preferences.toAppSettings())) }
        } catch (error: IOException) {
            Log.persistence.error("Saving settings failed", error)
        }
    }

    companion object {
        private const val READ_RETRY_BASE_MILLIS = 500L
        private const val READ_RETRY_MAX_MILLIS = 30_000L
        private const val READ_RETRY_MAX_DOUBLINGS = 6L

        private fun readRetryDelayMillis(attempt: Long): Long =
            minOf(READ_RETRY_BASE_MILLIS shl minOf(attempt, READ_RETRY_MAX_DOUBLINGS).toInt(), READ_RETRY_MAX_MILLIS)

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
