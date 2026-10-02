package com.muxy.app.networking.ssh

import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.models.Connection
import com.muxy.app.persistence.secrets.SecretStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

fun interface SshConnectionTesting {
    suspend fun test(connection: Connection)
}

class SshConnectionTester(
    private val clients: SshClientFactory,
    private val secrets: SecretStore,
    private val trust: SshHostKeyTrust,
) : SshConnectionTesting {
    override suspend fun test(connection: Connection) {
        val credentials = secrets.sshCredentials(connection)
        val config = connection.sshConfig ?: throw SshException(SshError.MISSING_CREDENTIALS)
        val client = clients.create()
        try {
            client.connect(connection.host, connection.port, trust.verifier(connection.id))
            client.authenticate(config, credentials)
            client.openShell(TerminalGridSize(80, 24))
        } finally {
            withContext(NonCancellable) { client.close() }
        }
    }
}
