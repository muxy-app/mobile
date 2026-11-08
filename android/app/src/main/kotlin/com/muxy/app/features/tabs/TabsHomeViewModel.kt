package com.muxy.app.features.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.features.push.PushCenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

sealed interface TabsHomeAction {
    data class OpenTab(
        val selection: TabsSelection,
    ) : TabsHomeAction

    data object Unlock : TabsHomeAction
}

class TabsHomeViewModel(
    private val catalog: TabsCatalog,
    private val push: PushCenter,
    supportsPushNotifications: Flow<Boolean>,
) : ViewModel() {
    val showsNotificationPrompt: StateFlow<Boolean> =
        combine(push.showsPrompt, supportsPushNotifications) { prompt, supported -> prompt && supported }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
    ): TabsHomeAction? {
        if (mutableCreating.value != null) return null
        if (catalog.requiresUnlock(key.connectionId)) return TabsHomeAction.Unlock
        return catalog.select(key, tabId)?.let(TabsHomeAction::OpenTab)
    }

    fun closeTab(
        key: TabsProjectKey,
        tabId: UUID,
    ): TabsHomeAction? {
        if (mutableCreating.value != null) return null
        if (catalog.requiresUnlock(key.connectionId)) return TabsHomeAction.Unlock
        catalog.closeTab(key, tabId)
        return null
    }

    suspend fun createTab(key: TabsProjectKey): TabsHomeAction? {
        if (mutableCreating.value != null) return null
        if (catalog.requiresUnlock(key.connectionId)) return TabsHomeAction.Unlock
        mutableCreating.value = key
        mutableError.value = null
        try {
            val selection =
                attempt { catalog.createTab(key) }
                    .onFailure {
                        Log.client.error("Creating a tab from home failed", it)
                        mutableError.value =
                            "Couldn't confirm the new tab. Refresh this device before trying again; it may already have been created."
                    }.getOrNull()
            if (catalog.requiresUnlock(key.connectionId)) return TabsHomeAction.Unlock
            return selection?.let(TabsHomeAction::OpenTab)
        } finally {
            mutableCreating.value = null
        }
    }

    fun retry(connectionId: UUID): TabsHomeAction? {
        if (catalog.requiresUnlock(connectionId)) return TabsHomeAction.Unlock
        catalog.retry(connectionId)
        return null
    }

    fun dismissError() {
        mutableError.value = null
    }
}
