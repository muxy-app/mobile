package com.muxy.app.features.tabs

import androidx.lifecycle.ViewModel
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

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
