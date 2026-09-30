package com.muxy.app.persistence.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class InMemorySettingsStore(
    initial: AppSettings? = AppSettings(),
) : SettingsStore {
    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings?> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.update { transform(it ?: AppSettings()) }
    }

    fun load(settings: AppSettings) {
        state.value = settings
    }
}
