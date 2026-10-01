package com.muxy.app.networking.muxy1

import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.RawTagged
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID

interface ProjectChannel {
    val connected: Flow<Boolean>
    val events: Flow<EventEnvelope>

    suspend fun request(
        method: Method,
        params: JsonElement?,
    ): RawTagged
}

class ConnectionProjectChannel(
    private val connectionId: UUID,
    private val manager: ConnectionManager,
) : ProjectChannel {
    override val connected = manager.status.map { it.of(connectionId) == ConnectionState.Connected }.distinctUntilChanged()
    override val events = manager.events(connectionId)

    override suspend fun request(
        method: Method,
        params: JsonElement?,
    ): RawTagged {
        currentCoroutineContext().ensureActive()
        val result = manager.request(connectionId, method, params)
        currentCoroutineContext().ensureActive()
        return result
    }
}

suspend inline fun <reified P> ProjectChannel.request(
    method: Method,
    params: P,
): RawTagged = request(method, ProtocolJson.encodeToJsonElement(params))
