package com.muxy.app.persistence.credentials

import com.muxy.app.core.logging.Log
import com.muxy.app.core.serialization.Base64ByteArraySerializer
import com.muxy.app.persistence.secrets.SecretStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import uniffi.muxy_mobile.ServerCredential

interface CredentialStore {
    suspend fun credential(serverId: String): ServerCredential?

    suspend fun save(credential: ServerCredential)

    suspend fun delete(serverId: String)
}

class SecretCredentialStore(
    private val secrets: SecretStore,
) : CredentialStore {
    override suspend fun credential(serverId: String): ServerCredential? {
        val encoded = secrets.read(name(serverId)) ?: return null
        return try {
            Json.decodeFromString(StoredCredential.serializer(), encoded).credential
        } catch (error: SerializationException) {
            unreadable(error)
        } catch (error: IllegalArgumentException) {
            unreadable(error)
        }
    }

    override suspend fun save(credential: ServerCredential) {
        secrets.write(name(credential.serverId), Json.encodeToString(StoredCredential.serializer(), StoredCredential.from(credential)))
    }

    override suspend fun delete(serverId: String) {
        secrets.delete(name(serverId))
    }

    private fun unreadable(error: Exception): ServerCredential? {
        Log.persistence.error("Skipping an unreadable paired computer: ${error.javaClass.simpleName}")
        return null
    }

    private fun name(serverId: String): String = "server.$serverId.credential"
}

@Serializable
class StoredCredential(
    val serverId: String,
    val serverName: String,
    val hosts: List<String>,
    val port: Int,
    @Serializable(with = Base64ByteArraySerializer::class)
    val fingerprint: ByteArray,
    val deviceId: String,
    @Serializable(with = Base64ByteArraySerializer::class)
    val token: ByteArray,
) {
    init {
        require(port in 0..UShort.MAX_VALUE.toInt()) { "Invalid port" }
    }

    val credential: ServerCredential
        get() =
            ServerCredential(
                serverId = serverId,
                serverName = serverName,
                hosts = hosts,
                port = port.toUShort(),
                fingerprint = fingerprint,
                deviceId = deviceId,
                token = token,
            )

    companion object {
        fun from(credential: ServerCredential): StoredCredential =
            StoredCredential(
                serverId = credential.serverId,
                serverName = credential.serverName,
                hosts = credential.hosts,
                port = credential.port.toInt(),
                fingerprint = credential.fingerprint,
                deviceId = credential.deviceId,
                token = credential.token,
            )
    }
}
