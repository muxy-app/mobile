package com.muxy.app.networking.muxy1.transport

import com.muxy.app.core.logging.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class WebSocketTransport(
    private val url: String,
    private val client: OkHttpClient,
    private val handshakeTimeout: Duration = HANDSHAKE_TIMEOUT,
) : Transport {
    private val incoming = Channel<String>(Channel.UNLIMITED)
    private val opened = CompletableDeferred<Unit>()

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var isClosed = false

    override suspend fun connect() {
        val request =
            runCatching { Request.Builder().url(url).build() }
                .getOrElse { throw TransportException(TransportFailure.INVALID_URL, it) }
        Log.transport.debug("WebSocket connecting")
        val webSocket = client.newWebSocket(request, Listener())
        socket = webSocket
        val didOpen =
            try {
                withTimeoutOrNull(handshakeTimeout) { opened.await() }
            } catch (error: IOException) {
                throw TransportException(TransportFailure.CLOSED, error)
            }
        if (didOpen != null) return
        Log.transport.error("WebSocket handshake timed out")
        isClosed = true
        webSocket.cancel()
        throw TransportException(TransportFailure.TIMED_OUT)
    }

    override suspend fun send(text: String) {
        val webSocket = socket ?: throw TransportException(TransportFailure.NOT_CONNECTED)
        if (!webSocket.send(text)) throw TransportException(TransportFailure.CLOSED)
    }

    override suspend fun receive(): String =
        try {
            incoming.receive()
        } catch (error: ClosedReceiveChannelException) {
            throw TransportException(TransportFailure.CLOSED, error)
        }

    override suspend fun close() {
        isClosed = true
        socket?.close(GOING_AWAY, null)
        socket = null
        incoming.close()
        Log.transport.debug("WebSocket closed")
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(
            webSocket: WebSocket,
            response: Response,
        ) {
            if (isClosed || !opened.complete(Unit)) webSocket.cancel()
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            incoming.trySend(text)
        }

        override fun onMessage(
            webSocket: WebSocket,
            bytes: ByteString,
        ) {
            incoming.trySend(bytes.utf8())
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            Log.transport.debug("WebSocket closed by peer with code $code")
            opened.completeExceptionally(TransportException(TransportFailure.CLOSED))
            incoming.close()
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            Log.transport.error("WebSocket failed", t)
            opened.completeExceptionally(t as? IOException ?: IOException(t))
            incoming.close()
        }
    }

    private companion object {
        val HANDSHAKE_TIMEOUT = 8.seconds
        const val NORMAL_CLOSURE = 1000
        const val GOING_AWAY = 1001
    }
}
