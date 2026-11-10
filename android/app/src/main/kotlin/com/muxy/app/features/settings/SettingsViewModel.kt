package com.muxy.app.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.features.push.PushAuthorization
import com.muxy.app.features.push.PushCenter
import com.muxy.app.features.push.PushServerRow
import com.muxy.app.persistence.settings.AppSettings
import com.muxy.app.persistence.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val push: PushCenter,
) : ViewModel() {
    val notificationAuthorization: StateFlow<PushAuthorization> = push.authorization

    val notificationServers: StateFlow<List<PushServerRow>> =
        push.servers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun allowNotifications() {
        push.requestPermission()
    }

    fun setNotificationsEnabled(
        serverId: String,
        enabled: Boolean,
    ) {
        push.setEnabled(serverId, enabled)
    }

    val settings: StateFlow<AppSettings> =
        settingsStore.settings
            .filterNotNull()
            .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.settings.value ?: AppSettings())

    fun selectTheme(name: String) {
        viewModelScope.launch { settingsStore.update { it.copy(themeName = name) } }
    }

    fun setUseNerdFont(enabled: Boolean) {
        viewModelScope.launch { settingsStore.update { it.copy(useNerdFont = enabled) } }
    }

    fun setAutoFocusTerminal(enabled: Boolean) {
        viewModelScope.launch { settingsStore.update { it.copy(autoFocusTerminal = enabled) } }
    }

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.update { it.copy(demoMode = enabled) } }
    }
}
