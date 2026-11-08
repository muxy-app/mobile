package com.muxy.app.features.push

import com.muxy.app.persistence.secrets.SecretStore

interface PushKeyStore {
    suspend fun key(serverId: String): PushKey?

    suspend fun save(key: PushKey)

    suspend fun delete(serverId: String)
}

class SecretPushKeyStore(
    private val secrets: SecretStore,
) : PushKeyStore {
    override suspend fun key(serverId: String): PushKey? = secrets.read(name(serverId))?.let { PushKey.decode(serverId, it) }

    override suspend fun save(key: PushKey) {
        secrets.write(name(key.serverId), key.encoded())
    }

    override suspend fun delete(serverId: String) {
        secrets.delete(name(serverId))
    }

    private fun name(serverId: String): String = "server.$serverId.pushKey"
}
