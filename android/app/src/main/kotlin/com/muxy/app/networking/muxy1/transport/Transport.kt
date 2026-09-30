package com.muxy.app.networking.muxy1.transport

import java.io.IOException

interface Transport {
    suspend fun connect()

    suspend fun send(text: String)

    suspend fun receive(): String

    suspend fun close()
}

enum class TransportFailure {
    INVALID_URL,
    NOT_CONNECTED,
    CLOSED,
    TIMED_OUT,
    UNSUPPORTED_FRAME,
}

class TransportException(
    val failure: TransportFailure,
    cause: Throwable? = null,
) : IOException("Transport ${failure.name.lowercase()}", cause)
