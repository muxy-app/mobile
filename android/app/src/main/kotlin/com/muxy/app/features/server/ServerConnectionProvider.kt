package com.muxy.app.features.server

import com.muxy.app.networking.server.RemoteServerConnector
import com.muxy.app.networking.server.RemoteServerException
import com.muxy.app.networking.server.ServerConnection
import com.muxy.app.networking.server.ServerConnector
import com.muxy.app.networking.server.ServerFailure
import com.muxy.app.networking.ssh.SshError
import com.muxy.app.persistence.connections.ConnectionStore
import uniffi.muxy_mobile.ConnectionEvent
import uniffi.muxy_mobile.MobileException
import uniffi.muxy_mobile.ServerCredential
import java.util.UUID

interface ServerConnectionProvider {
    val serverName: String

    suspend fun connect(events: (ConnectionEvent) -> Unit): ServerConnection
}

class PairedServerConnectionProvider(
    private val connector: ServerConnector,
    private val credential: suspend () -> ServerCredential?,
) : ServerConnectionProvider {
    override var serverName: String = "the computer"
        private set

    override suspend fun connect(events: (ConnectionEvent) -> Unit): ServerConnection {
        val saved = credential() ?: throw MobileException.InvalidCredential()
        serverName = saved.serverName
        return connector.connect(saved, events)
    }
}

class RemoteServerConnectionProvider(
    private val connectionId: UUID,
    private val connections: ConnectionStore,
    private val connector: RemoteServerConnector,
) : ServerConnectionProvider {
    override var serverName: String = "the computer"
        private set

    override suspend fun connect(events: (ConnectionEvent) -> Unit): ServerConnection {
        val saved =
            connections.load().firstOrNull { it.id == connectionId && it.isRemoteServer }
                ?: throw RemoteServerException(ServerFailure.Ssh(SshError.MISSING_CREDENTIALS))
        serverName = saved.name
        return connector.connect(saved, events)
    }
}
