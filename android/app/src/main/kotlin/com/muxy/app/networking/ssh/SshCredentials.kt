package com.muxy.app.networking.ssh

import com.muxy.app.models.Connection
import com.muxy.app.models.SshAuthMethod
import com.muxy.app.persistence.secrets.ConnectionSecret
import com.muxy.app.persistence.secrets.SecretStore

class SshCredentials(
    val secret: String,
    val passphrase: String?,
)

suspend fun SecretStore.sshCredentials(connection: Connection): SshCredentials {
    val config = connection.sshConfig ?: throw SshException(SshError.MISSING_CREDENTIALS)
    val kind = if (config.authMethod == SshAuthMethod.PASSWORD) ConnectionSecret.SSH_PASSWORD else ConnectionSecret.SSH_PRIVATE_KEY
    val secret = read(kind.name(connection.id)) ?: throw SshException(SshError.MISSING_CREDENTIALS)
    return SshCredentials(secret, read(ConnectionSecret.SSH_PASSPHRASE.name(connection.id)))
}
