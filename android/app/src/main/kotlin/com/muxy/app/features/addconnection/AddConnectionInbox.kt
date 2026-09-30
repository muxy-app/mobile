package com.muxy.app.features.addconnection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import java.util.UUID

sealed interface AddConnectionRequest {
    data class PairingCode(
        val code: String,
    ) : AddConnectionRequest

    data class Repair(
        val connectionId: UUID,
    ) : AddConnectionRequest
}

class AddConnectionInbox {
    private val pending = MutableStateFlow<AddConnectionRequest?>(null)

    val request: StateFlow<AddConnectionRequest?> = pending.asStateFlow()

    fun deliver(request: AddConnectionRequest) {
        pending.value = request
    }

    fun take(): AddConnectionRequest? = pending.getAndUpdate { null }
}
