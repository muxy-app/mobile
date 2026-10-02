package com.muxy.app.features.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.features.addconnection.AddConnectionInbox
import com.muxy.app.features.addconnection.AddConnectionRequest
import com.muxy.app.persistence.settings.AppSettings
import com.muxy.app.persistence.settings.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RootViewModel(
    private val settingsStore: SettingsStore,
    private val addConnectionRequests: AddConnectionInbox,
    private val startupReady: StateFlow<Boolean>,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsStore.settings

    fun completeOnboarding() {
        viewModelScope.launch { settingsStore.update { it.copy(hasCompletedOnboarding = true) } }
    }

    fun openPairingLink(link: String) {
        if (!DeepLink.isPairingLink(link)) return
        viewModelScope.launch {
            startupReady.first { it }
            completeOnboarding()
            addConnectionRequests.deliver(AddConnectionRequest.PairingCode(link))
        }
    }
}
