package com.muxy.app.demo

import com.muxy.app.model.MuxyEvent
import com.muxy.app.model.MuxyMessage
import com.muxy.app.model.MuxyResponse
import com.muxy.app.net.Transport
import com.muxy.app.net.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration

class DemoTransport(val backend: DemoBackend = DemoBackend()) : Transport {

    @Volatile
    private var connected: Boolean = false

    override val isConnected: Boolean get() = connected
    override val requiresAuth: Boolean = false

    private val _events = MutableSharedFlow<TransportEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<TransportEvent> = _events.asSharedFlow()

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun connect(host: String, port: Int) {
        connected = true
        if (!scope.isActiveSafe()) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
        _events.tryEmit(TransportEvent.Open)
    }

    override fun disconnect() {
        connected = false
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        _events.tryEmit(TransportEvent.Closed(reason = "demo disconnect", code = 1000))
    }

    override suspend fun send(request: MuxyMessage.Request, timeout: Duration): MuxyResponse {
        check(connected) { "DemoTransport not connected" }
        val delayMs = backend.simulatedDelay(request.payload.method).inWholeMilliseconds
        if (delayMs > 0) delay(delayMs)
        return backend.handle(request.payload, ::emitEvent)
    }

    override fun sendFireAndForget(request: MuxyMessage.Request): Boolean {
        if (!connected) return false
        if (request.payload.method != "terminalInput") return true
        scope.launch {
            backend.handleTerminalInput(request.payload, ::emitEvent)
        }
        return true
    }

    private suspend fun emitEvent(event: MuxyEvent) {
        _events.emit(TransportEvent.EventReceived(event))
    }

    private fun CoroutineScope.isActiveSafe(): Boolean =
        coroutineContext[kotlinx.coroutines.Job]?.isActive == true
}
