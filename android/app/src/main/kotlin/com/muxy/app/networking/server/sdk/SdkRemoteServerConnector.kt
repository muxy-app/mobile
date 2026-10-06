package com.muxy.app.networking.server.sdk

import com.muxy.app.models.Connection
import com.muxy.app.networking.server.RemoteServerConnection
import com.muxy.app.networking.server.RemoteServerConnector
import com.muxy.app.networking.server.RemoteServerException
import com.muxy.app.networking.server.ServerFailure
import com.muxy.app.networking.ssh.SshError
import com.muxy.app.networking.ssh.SshException
import com.muxy.app.networking.ssh.SshExecClientFactory
import com.muxy.app.networking.ssh.SshHostKeyTrust
import com.muxy.app.networking.ssh.sshCredentials
import com.muxy.app.persistence.secrets.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import uniffi.muxy_mobile.ConnectionEvent
import uniffi.muxy_mobile.MobileException
import uniffi.muxy_mobile.bridgeCommand

class SdkRemoteServerConnector(
    private val clients: SshExecClientFactory,
    private val secrets: SecretStore,
    private val trust: SshHostKeyTrust,
) : RemoteServerConnector {
    override suspend fun connect(
        connection: Connection,
        events: (ConnectionEvent) -> Unit,
    ): RemoteServerConnection {
        val client = clients.create()
        var bridge: SshBridge? = null
        try {
            val config = connection.sshConfig ?: throw SshException(SshError.MISSING_CREDENTIALS)
            val credentials = secrets.sshCredentials(connection)
            client.connect(connection.host, connection.port, trust.verifier(connection.id))
            client.authenticate(config, credentials)
            val command = client.execute(bridgeCommand())
            val openedBridge = SshBridge(client, command)
            bridge = openedBridge
            val opened = openedBridge.connect("${config.username}@${connection.host}", ConnectionEventRelay(events))
            return SdkServerConnection(opened, SdkLanes(), openedBridge::close)
        } catch (error: Exception) {
            bridge?.close()
            withContext(NonCancellable) { client.close() }
            if (error is CancellationException) throw error
            val failure =
                when (error) {
                    is MobileException.Unreachable -> ServerFailure.Server(error.reason)
                    is MobileException -> ServerFailure.from(error)
                    else -> ServerFailure.Ssh(SshError.classify(error))
                }
            throw RemoteServerException(failure)
        }
    }
}
