package com.muxy.app.testing

import com.muxy.app.features.projectdetail.terminal.TerminalChannel
import com.muxy.app.networking.muxy1.ConnectionIdentity
import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.networking.muxy1.protocol.EventType
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.PaneOwner
import com.muxy.app.networking.muxy1.protocol.PaneOwnershipEvent
import com.muxy.app.networking.muxy1.protocol.RawTagged
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.networking.muxy1.protocol.TerminalBytesEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonElement
import java.util.UUID

class MockTerminalChannel(
    var identity: ConnectionIdentity? = null,
) : TerminalChannel {
    data class Call(
        val method: Method,
        val params: JsonElement?,
    )

    val requests = mutableListOf<Call>()
    val notifications = mutableListOf<Call>()
    val requestErrors = mutableMapOf<Method, Exception>()

    private val flow = MutableSharedFlow<EventEnvelope>(extraBufferCapacity = 64)

    override val events: Flow<EventEnvelope> = flow

    override fun identity(): ConnectionIdentity? = identity

    override suspend fun request(
        method: Method,
        params: JsonElement?,
    ): RawTagged {
        requests += Call(method, params)
        requestErrors[method]?.let { throw it }
        return RawTagged(ResultType.OK)
    }

    override suspend fun notify(
        method: Method,
        params: JsonElement?,
    ) {
        notifications += Call(method, params)
    }

    fun requests(method: Method): List<Call> = requests.filter { it.method == method }

    fun notifications(method: Method): List<Call> = notifications.filter { it.method == method }

    suspend fun emitOwnership(
        paneId: UUID,
        owner: PaneOwner,
    ) {
        flow.emit(
            EventEnvelope(EventName.PANE_OWNERSHIP_CHANGED, RawTagged.of(EventType.PANE_OWNERSHIP, PaneOwnershipEvent(paneId, owner))),
        )
    }

    suspend fun emitOutput(
        paneId: UUID,
        text: String,
        snapshot: Boolean = false,
    ) {
        val name = if (snapshot) EventName.TERMINAL_SNAPSHOT else EventName.TERMINAL_OUTPUT
        val type = if (snapshot) EventType.TERMINAL_SNAPSHOT else EventType.TERMINAL_OUTPUT
        flow.emit(EventEnvelope(name, RawTagged.of(type, TerminalBytesEvent(paneId, text.toByteArray()))))
    }
}
