package com.muxy.app.features.addconnection

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.core.validation.ValidatedSshInput
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.models.SshAuthMethod
import com.muxy.app.models.SshConfig
import com.muxy.app.networking.ssh.SshConnectionTesting
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.secrets.ConnectionSecret
import com.muxy.app.persistence.secrets.SecretStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

fun interface SshConnectionAdding {
    suspend fun add(input: ValidatedSshInput): Connection
}

class SshConnectionAdder(
    private val store: ConnectionStore,
    private val secrets: SecretStore,
    private val tester: SshConnectionTesting,
) : SshConnectionAdding {
    override suspend fun add(input: ValidatedSshInput): Connection {
        val connection =
            Connection(
                id = UUID.randomUUID(),
                name = input.name,
                host = input.host,
                port = input.port,
                kind = ConnectionKind.SSH,
                sshConfig = SshConfig(input.username, input.authMethod),
            )
        var saved = false
        try {
            saveCredentials(input, connection.id)
            tester.test(connection)
            withContext(NonCancellable) {
                store.upsert(connection)
                saved = true
            }
            return connection
        } finally {
            if (!saved) deleteCredentials(connection.id)
        }
    }

    private suspend fun saveCredentials(
        input: ValidatedSshInput,
        id: UUID,
    ) {
        attempt {
            val kind = if (input.authMethod == SshAuthMethod.PASSWORD) ConnectionSecret.SSH_PASSWORD else ConnectionSecret.SSH_PRIVATE_KEY
            secrets.write(kind.name(id), input.secret)
            input.passphrase?.takeIf { input.authMethod == SshAuthMethod.PRIVATE_KEY }?.let {
                secrets.write(ConnectionSecret.SSH_PASSPHRASE.name(id), it)
            }
        }.getOrElse {
            Log.ssh.error("Failed to store SSH credentials: ${it.javaClass.simpleName}")
            throw SshCredentialStorageException()
        }
    }

    private suspend fun deleteCredentials(id: UUID) {
        withContext(NonCancellable) {
            ConnectionSecret.entries.forEach { kind ->
                attempt { secrets.delete(kind.name(id)) }
                    .onFailure { Log.ssh.error("Failed to delete SSH credentials: ${it.javaClass.simpleName}") }
            }
        }
    }
}

class SshCredentialStorageException : Exception("Couldn't securely store the credentials.")
