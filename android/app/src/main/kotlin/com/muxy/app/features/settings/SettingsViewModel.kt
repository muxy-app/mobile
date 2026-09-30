package com.muxy.app.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.persistence.settings.AppSettings
import com.muxy.app.persistence.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsStore.settings
            .filterNotNull()
            .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.settings.value ?: AppSettings())

    fun selectTheme(name: String) {
        viewModelScope.launch { settingsStore.update { it.copy(themeName = name) } }
    }
}
