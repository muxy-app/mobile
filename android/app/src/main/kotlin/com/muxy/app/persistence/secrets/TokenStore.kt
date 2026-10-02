package com.muxy.app.persistence.secrets

import com.muxy.app.core.logging.Log
import com.muxy.app.core.serialization.uuidString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class DeviceCredential(
    val deviceId: String,
    val token: String,
)

enum class ConnectionSecret(
    val suffix: String,
) {
    TOKEN("token"),
    SSH_PASSWORD("sshPassword"),
    SSH_PRIVATE_KEY("sshPrivateKey"),
    SSH_PASSPHRASE("sshPassphrase"),
    SSH_HOST_KEY("sshHostKey"),
    ;

    fun name(connectionId: UUID): String {
        if (this == TOKEN) return "connection.${connectionId.uuidString}.$suffix"
        return "${connectionId.uuidString}.$suffix"
    }
}

interface TokenStore {
    suspend fun credential(connectionId: UUID): DeviceCredential?

    suspend fun setCredential(
        credential: DeviceCredential,
        connectionId: UUID,
    )

    suspend fun deleteSecrets(connectionId: UUID)
}

class SecretTokenStore(
    private val secrets: SecretStore,
) : TokenStore {
    override suspend fun credential(connectionId: UUID): DeviceCredential? {
        val encoded = secrets.read(name(ConnectionSecret.TOKEN, connectionId)) ?: return null
        return try {
            Json.decodeFromString(DeviceCredential.serializer(), encoded)
        } catch (error: IllegalArgumentException) {
            Log.persistence.error("A stored credential is malformed: ${error.javaClass.simpleName}")
            null
        }
    }

    override suspend fun setCredential(
        credential: DeviceCredential,
        connectionId: UUID,
    ) {
        secrets.write(name(ConnectionSecret.TOKEN, connectionId), Json.encodeToString(DeviceCredential.serializer(), credential))
    }

    override suspend fun deleteSecrets(connectionId: UUID) {
        ConnectionSecret.entries.forEach { secrets.delete(name(it, connectionId)) }
    }

    private fun name(
        secret: ConnectionSecret,
        connectionId: UUID,
    ): String = secret.name(connectionId)
}
