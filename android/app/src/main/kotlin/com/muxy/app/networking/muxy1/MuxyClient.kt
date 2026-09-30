package com.muxy.app.networking.muxy1

import com.muxy.app.core.logging.Log
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.IncomingFrame
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.RawTagged
import com.muxy.app.networking.muxy1.protocol.RequestEnvelope
import com.muxy.app.networking.muxy1.protocol.ResponseEnvelope
import com.muxy.app.networking.muxy1.transport.Transport
import com.muxy.app.networking.muxy1.transport.TransportException
import com.muxy.app.networking.muxy1.transport.TransportFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MuxyClient(
    private val transport: Transport,
    parentScope: CoroutineScope,
    private val requestTimeout: Duration = DEFAULT_REQUEST_TIMEOUT,
    private val idProvider: () -> String = { UUID.randomUUID().uuidString },
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + Job(parentScope.coroutineContext[Job]))
    private val pending = ConcurrentHashMap<String, CompletableDeferred<RawTagged>>()
    private val eventChannel = Channel<EventEnvelope>(Channel.UNLIMITED)
    private val isStarted = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(false)

    val events: Flow<EventEnvelope> = eventChannel.receiveAsFlow()

    suspend fun connect() {
        transport.connect()
    }

    fun start() {
        if (!isStarted.compareAndSet(false, true)) return
        scope.launch { runReadLoop() }
    }

    suspend fun request(
        method: Method,
        params: JsonElement? = null,
        timeout: Duration = requestTimeout,
    ): RawTagged {
        if (isStopped.get()) throw TransportException(TransportFailure.CLOSED)
        val id = idProvider()
        val response = CompletableDeferred<RawTagged>()
        pending[id] = response
        try {
            if (isStopped.get()) throw TransportException(TransportFailure.CLOSED)
            transport.send(RequestEnvelope.encode(id, method, params))
            return withTimeoutOrNull(timeout) { response.await() } ?: throw TransportException(TransportFailure.TIMED_OUT)
        } finally {
            pending.remove(id)
        }
    }

    suspend fun notify(
        method: Method,
        params: JsonElement? = null,
    ) {
        if (isStopped.get()) throw TransportException(TransportFailure.CLOSED)
        transport.send(RequestEnvelope.encode(idProvider(), method, params))
    }

    suspend fun stop() {
        if (!isStopped.compareAndSet(false, true)) return
        scope.cancel()
        eventChannel.close()
        withContext(NonCancellable) { transport.close() }
        failPending(TransportException(TransportFailure.CLOSED))
    }

    private suspend fun runReadLoop() {
        while (true) {
            val text =
                try {
                    transport.receive()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    handleReadFailure(error)
                    return
                }
            handle(text)
        }
    }

    private fun handle(text: String) {
        val frame =
            try {
                IncomingFrame.parse(text)
            } catch (error: SerializationException) {
                Log.client.error("Failed to decode frame", error)
                return
            } catch (error: IllegalArgumentException) {
                Log.client.error("Failed to decode frame", error)
                return
            }
        when (frame) {
            is IncomingFrame.Response -> handle(frame.envelope)
            is IncomingFrame.Event -> eventChannel.trySend(frame.envelope)
        }
    }

    private fun handle(response: ResponseEnvelope) {
        val deferred = pending.remove(response.id) ?: return
        val error = response.error
        val result = response.result
        when {
            error != null -> deferred.completeExceptionally(ProtocolException(error))
            result != null -> deferred.complete(result)
            else -> deferred.completeExceptionally(TransportException(TransportFailure.UNSUPPORTED_FRAME))
        }
    }

    private fun handleReadFailure(error: Exception) {
        Log.client.error("Read loop ended", error)
        eventChannel.close()
        failPending(error as? TransportException ?: TransportException(TransportFailure.CLOSED, error))
    }

    private fun failPending(error: Exception) {
        val waiting = pending.values.toList()
        pending.clear()
        waiting.forEach { it.completeExceptionally(error) }
    }

    private companion object {
        val DEFAULT_REQUEST_TIMEOUT = 30.seconds
    }
}
