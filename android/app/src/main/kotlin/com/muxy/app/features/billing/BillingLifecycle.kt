package com.muxy.app.features.billing

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BillingLifecycle(
    private val billing: BillingRepository,
    private val settings: SettingsStore,
    private val connections: ConnectionStore,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            settings.settings.filterNotNull().first()
            connections.load()
            billing.refresh()
        }
    }
}
