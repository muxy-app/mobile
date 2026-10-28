package com.muxy.app.features.tabs

import androidx.lifecycle.ViewModel
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed interface TabsHomeAction {
    data class OpenTab(
        val selection: TabsSelection,
    ) : TabsHomeAction

    data object Unlock : TabsHomeAction
}

class TabsHomeViewModel(
    private val catalog: TabsCatalog,
) : ViewModel() {
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
