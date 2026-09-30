package com.muxy.app.persistence.settings

import kotlinx.coroutines.flow.StateFlow

interface SettingsStore {
    val settings: StateFlow<AppSettings?>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
