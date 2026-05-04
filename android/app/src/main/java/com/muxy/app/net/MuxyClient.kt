package com.muxy.app.net

import android.util.Log
import com.muxy.app.model.MuxyMessage
import com.muxy.app.model.MuxyResponse
import com.muxy.app.model.decodeMessage
import com.muxy.app.model.encodeMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

private const val TAG = "MuxyClient"

sealed class TransportEvent {
    data object Open : TransportEvent()
    data class Closed(val reason: String?, val code: Int?) : TransportEvent()
    data class Failure(val error: Throwable) : TransportEvent()
    data class EventReceived(val event: com.muxy.app.model.MuxyEvent) : TransportEvent()
}

class MuxyClient : Transport {
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<MuxyResponse>>()

    override val isConnected: Boolean get() = socket != null
    override val requiresAuth: Boolean = true

    private val _events = MutableSharedFlow<TransportEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<TransportEvent> = _events.asSharedFlow()

    override fun connect(host: String, port: Int) {
        disconnect()
        val url = "ws://$host:$port"
        Log.d(TAG, "Opening WebSocket to $url")
        val request = Request.Builder().url(url).build()
        socket = http.newWebSocket(request, listener)
    }

    override fun disconnect() {
        socket?.close(1000, "client disconnect")
        socket = null
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    override suspend fun send(request: MuxyMessage.Request, timeout: Duration): MuxyResponse {
        val socket = this.socket ?: error("WebSocket not connected")
        val deferred = CompletableDeferred<MuxyResponse>()
        pending[request.payload.id] = deferred
        val text = encodeMessage(request)
        if (!socket.send(text)) {
            pending.remove(request.payload.id)
            error("WebSocket send failed (queue full or closed)")
        }
        return try {
            withTimeout(timeout) { deferred.await() }
        } catch (t: TimeoutCancellationException) {
            pending.remove(request.payload.id)
            throw t
        }
    }

    override fun sendFireAndForget(request: MuxyMessage.Request): Boolean {
        val socket = this.socket ?: return false
        return socket.send(encodeMessage(request))
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket open")
            _events.tryEmit(TransportEvent.Open)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleText(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleText(bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed code=$code reason=$reason")
            if (socket === webSocket) socket = null
            _events.tryEmit(TransportEvent.Closed(reason, code))
            failAllPending(IllegalStateException("WebSocket closed: $code $reason"))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "WebSocket failure", t)
            if (socket === webSocket) socket = null
            _events.tryEmit(TransportEvent.Failure(t))
            failAllPending(t)
        }
    }

    private fun handleText(text: String) {
        val message = runCatching { decodeMessage(text) }.getOrElse {
            Log.w(TAG, "Failed to decode message: ${it.message}")
            return
        }
        when (message) {
            is MuxyMessage.Response -> {
                pending.remove(message.payload.id)?.complete(message.payload)
            }
            is MuxyMessage.Event -> {
                _events.tryEmit(TransportEvent.EventReceived(message.payload))
            }
            is MuxyMessage.Request -> Unit
        }
    }

    private fun failAllPending(error: Throwable) {
        val snapshot = pending.toMap()
        pending.clear()
        snapshot.values.forEach { it.completeExceptionally(error) }
    }
}

fun newRequestId(): String = UUID.randomUUID().toString()
