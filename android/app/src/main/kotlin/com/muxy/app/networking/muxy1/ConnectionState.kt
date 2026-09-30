package com.muxy.app.networking.muxy1

import java.io.IOException
import java.util.UUID

enum class ConnectionError {
    CONNECTION_FAILED,
    AUTHENTICATION_FAILED,
    INVALID_ENDPOINT,
    MISSING_TOKEN,
    NOT_CONNECTED,
}

class ConnectionException(
    val error: ConnectionError,
) : IOException("Connection ${error.name.lowercase()}")

sealed interface ConnectionState {
    data object Idle : ConnectionState

    data object Connecting : ConnectionState

    data object Authenticating : ConnectionState

    data object Connected : ConnectionState

    data object Disconnected : ConnectionState

    data class Failed(
        val error: ConnectionError,
    ) : ConnectionState
}

data class ConnectionStatus(
    val connectionId: UUID?,
    val state: ConnectionState,
    val session: Long,
) {
    fun of(id: UUID): ConnectionState = if (connectionId == id) state else ConnectionState.Idle

    fun connectedSession(id: UUID): Long? = session.takeIf { connectionId == id && state == ConnectionState.Connected }

    companion object {
        val idle = ConnectionStatus(null, ConnectionState.Idle, 0)
    }
}
