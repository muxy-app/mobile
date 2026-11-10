package com.muxy.app.features.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.features.push.PushCenter
import com.muxy.app.features.push.PushServerStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

class TabsHomeViewModel(
    private val catalog: TabsCatalog,
    private val push: PushCenter,
    supportsPushNotifications: Flow<Boolean>,
) : ViewModel() {
    val showsNotificationPrompt: StateFlow<Boolean> =
        combine(push.showsPrompt, supportsPushNotifications) { prompt, supported -> prompt && supported }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val notifyingServers: StateFlow<Set<String>> =
        push.state
            .map { state ->
                state
                    ?.statuses
                    .orEmpty()
                    .filterValues { it != PushServerStatus.UNSUPPORTED }
                    .keys
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun enableNotifications() {
        push.requestPermission()
    }

    fun dismissNotificationPrompt() {
        push.dismissPrompt()
    }

    val devices = catalog.devices
    private val mutableCreating = MutableStateFlow<TabsProjectKey?>(null)
    private val mutableError = MutableStateFlow<String?>(null)
    val creating = mutableCreating.asStateFlow()
    val error = mutableError.asStateFlow()

    fun select(
        key: TabsProjectKey,
        tabId: UUID,
    ): TabsSelection? {
        if (mutableCreating.value != null) return null
        return catalog.select(key, tabId)
    }

    fun closeTab(
        key: TabsProjectKey,
        tabId: UUID,
    ) {
        if (mutableCreating.value != null) return
        catalog.closeTab(key, tabId)
    }

    suspend fun createTab(key: TabsProjectKey): TabsSelection? {
        if (mutableCreating.value != null) return null
        mutableCreating.value = key
        mutableError.value = null
        try {
            return attempt { catalog.createTab(key) }
                .onFailure {
                    Log.client.error("Creating a tab from home failed", it)
                    mutableError.value =
                        "Couldn't confirm the new tab. Refresh this device before trying again; it may already have been created."
                }.getOrNull()
        } finally {
            mutableCreating.value = null
        }
    }

    fun retry(connectionId: UUID) {
        catalog.retry(connectionId)
    }

    fun dismissError() {
        mutableError.value = null
    }
}
