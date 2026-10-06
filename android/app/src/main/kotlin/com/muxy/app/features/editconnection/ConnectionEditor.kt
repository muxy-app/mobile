package com.muxy.app.features.editconnection

import com.muxy.app.core.validation.ValidatedConnectionInput
import com.muxy.app.core.validation.ValidatedSshInput
import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.features.server.ServerDirectory
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.models.DiscoverySource
import com.muxy.app.models.SshAuthMethod
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.credentials.CredentialStore
import com.muxy.app.persistence.credentials.SecretCredentialStore
import com.muxy.app.persistence.secrets.ConnectionSecret
import com.muxy.app.persistence.secrets.SecretUpdating
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class ConnectionEditor(
    private val connections: ConnectionStore,
    private val credentials: CredentialStore,
    private val secrets: SecretUpdating,
    private val directory: ServerDirectory,
) {
    suspend fun save(
        previous: Connection,
        input: ValidatedConnectionInput,
        username: String,
        replacement: ValidatedSshInput?,
    ): Connection =
        withContext(NonCancellable) {
            require(previous.id != DemoConnection.id)
            val endpointChanged = previous.host != input.host || previous.port != input.port
            val manuallyEditedDevice = previous.kind == ConnectionKind.DEVICE && endpointChanged
            val updated =
                previous.copy(
                    name = input.name,
                    host = input.host,
                    port = input.port,
                    serviceName = if (manuallyEditedDevice) null else previous.serviceName,
                    discoverySource = if (manuallyEditedDevice) DiscoverySource.MANUAL else previous.discoverySource,
                    sshConfig =
                        previous.sshConfig?.copy(
                            username = username.trim(),
                            authMethod =
                                replacement?.authMethod ?: previous.sshConfig.authMethod,
                        ),
                )
            val changes = credentialChanges(previous, updated, endpointChanged, replacement)
            if (changes.isEmpty()) {
                connections.update(previous, updated)
                updated.serverRouteId?.let(directory::credentialDidChange)
                return@withContext updated
            }
            secrets.update(changes) { connections.update(previous, updated) }
            updated.serverRouteId?.let(directory::credentialDidChange)
            updated
        }

    private suspend fun credentialChanges(
        previous: Connection,
        updated: Connection,
        endpointChanged: Boolean,
        replacement: ValidatedSshInput?,
    ): Map<String, String?> {
        if (previous.kind == ConnectionKind.SERVER && !previous.isRemoteServer) {
            val serverId = requireNotNull(previous.serverId)
            val saved = checkNotNull(credentials.credential(serverId)) { "The saved server credential is unavailable" }
            val credential =
                saved.copy(
                    serverName = updated.name,
                    hosts = if (endpointChanged) listOf(updated.host) else saved.hosts,
                    port = if (endpointChanged) updated.port.toUShort() else saved.port,
                )
            return mapOf(SecretCredentialStore.name(serverId) to SecretCredentialStore.encode(credential))
        }
        if (!previous.usesSsh || replacement == null) return emptyMap()
        requireNotNull(previous.sshConfig)
        val password = replacement.authMethod == SshAuthMethod.PASSWORD
        return mapOf(
            ConnectionSecret.SSH_PASSWORD.name(previous.id) to replacement.secret.takeIf { password },
            ConnectionSecret.SSH_PRIVATE_KEY.name(previous.id) to replacement.secret.takeUnless { password },
            ConnectionSecret.SSH_PASSPHRASE.name(previous.id) to replacement.passphrase.takeUnless { password },
        )
    }
}
