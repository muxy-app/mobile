package com.muxy.app.features.projectdetail.terminal

import com.muxy.app.networking.muxy1.ConnectionIdentity
import com.muxy.app.networking.muxy1.ConnectionManager
import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.RawTagged
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import java.util.UUID

interface TerminalChannel {
    val events: Flow<EventEnvelope>

    fun identity(): ConnectionIdentity?

    suspend fun request(
        method: Method,
        params: JsonElement?,
    ): RawTagged

    suspend fun notify(
        method: Method,
        params: JsonElement?,
    )
}

class ConnectionTerminalChannel(
    private val connectionId: UUID,
    private val manager: ConnectionManager,
) : TerminalChannel {
    override val events: Flow<EventEnvelope> = manager.events(connectionId)

    override fun identity(): ConnectionIdentity? = manager.identity(connectionId)

    override suspend fun request(
        method: Method,
        params: JsonElement?,
    ): RawTagged = manager.request(connectionId, method, params)

    override suspend fun notify(
        method: Method,
        params: JsonElement?,
    ) {
        manager.notify(connectionId, method, params)
    }
}
