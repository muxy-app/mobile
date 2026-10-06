package com.muxy.app.networking.server

import com.muxy.app.models.Connection
import uniffi.muxy_mobile.ConnectionEvent

fun interface RemoteServerConnector {
    suspend fun connect(
        connection: Connection,
        events: (ConnectionEvent) -> Unit,
    ): RemoteServerConnection
}

interface RemoteServerConnection : ServerConnection {
    suspend fun serverId(): String
}

class RemoteServerException(
    val failure: ServerFailure,
) : Exception()
