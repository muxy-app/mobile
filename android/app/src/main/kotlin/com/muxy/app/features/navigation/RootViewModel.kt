package com.muxy.app.features.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.persistence.settings.AppSettings
import com.muxy.app.persistence.settings.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RootViewModel(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsStore.settings

    fun completeOnboarding() {
        viewModelScope.launch { settingsStore.update { it.copy(hasCompletedOnboarding = true) } }
    }
}
