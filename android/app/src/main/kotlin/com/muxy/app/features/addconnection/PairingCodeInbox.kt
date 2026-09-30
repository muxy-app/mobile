package com.muxy.app.features.addconnection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

class PairingCodeInbox {
    private val pending = MutableStateFlow<String?>(null)

    val code: StateFlow<String?> = pending.asStateFlow()

    fun deliver(code: String) {
        pending.value = code
    }

    fun take(): String? = pending.getAndUpdate { null }
}
