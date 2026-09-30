package com.muxy.app.features.demo

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.secrets.TokenStore
import com.muxy.app.persistence.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

fun CoroutineScope.syncDemoMode(
    settings: Flow<AppSettings?>,
    store: ConnectionStore,
    tokens: TokenStore,
): Job =
    launch {
        settings
            .filterNotNull()
            .map { it.demoMode }
            .distinctUntilChanged()
            .collect { enabled ->
                attempt { DemoConnection.apply(enabled, store, tokens) }
                    .onFailure { Log.persistence.error("Applying demo mode failed", it) }
            }
    }
