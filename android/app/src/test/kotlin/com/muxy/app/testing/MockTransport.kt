package com.muxy.app.testing

import com.muxy.app.networking.muxy1.transport.Transport
import com.muxy.app.networking.muxy1.transport.TransportException
import com.muxy.app.networking.muxy1.transport.TransportFailure
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.concurrent.CopyOnWriteArrayList

class MockTransport(
    private val connectFailure: TransportException? = null,
    private val autoReply: (String) -> List<String> = { emptyList() },
) : Transport {
    private val inbound = Channel<String>(Channel.UNLIMITED)

    val sentFrames: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var didConnect = false
        private set

    @Volatile
    var didClose = false
        private set

    override suspend fun connect() {
        connectFailure?.let { throw it }
        didConnect = true
    }

    override suspend fun send(text: String) {
        if (didClose) throw TransportException(TransportFailure.CLOSED)
        sentFrames += text
        autoReply(text).forEach { inbound.trySend(it) }
    }

    override suspend fun receive(): String =
        try {
            inbound.receive()
        } catch (error: ClosedReceiveChannelException) {
            throw TransportException(TransportFailure.CLOSED, error)
        }

    override suspend fun close() {
        didClose = true
        inbound.close()
    }

    fun enqueue(text: String) {
        inbound.trySend(text)
    }

    fun failReaders() {
        inbound.close()
    }
}

class TransportRecorder(
    private val connectFailure: TransportException? = null,
    private val reply: (url: String, frame: String) -> List<String> = { _, frame -> Frames.authenticated(frame) },
) {
    val transports: MutableList<MockTransport> = CopyOnWriteArrayList()
    val urls: MutableList<String> = CopyOnWriteArrayList()

    val latest: MockTransport?
        get() = transports.lastOrNull()

    fun make(url: String): MockTransport {
        urls += url
        return MockTransport(connectFailure) { frame -> reply(url, frame) }.also { transports += it }
    }
}
