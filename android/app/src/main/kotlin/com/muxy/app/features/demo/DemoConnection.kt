package com.muxy.app.features.demo

import com.muxy.app.core.serialization.uuidString
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.models.DiscoverySource
import com.muxy.app.models.PairingState
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.secrets.DeviceCredential
import com.muxy.app.persistence.secrets.TokenStore
import java.util.UUID

object DemoConnection {
    val id: UUID = UUID.fromString("00000000-0000-4000-8000-000000000001")
    const val NAME = "Demo Desktop"
    const val TOKEN = "demo-token"

    val connection: Connection =
        Connection(
            id = id,
            name = NAME,
            host = "demo.local",
            port = 4865,
            kind = ConnectionKind.DEVICE,
            pairingState = PairingState.PAIRED,
            serviceName = NAME,
            discoverySource = DiscoverySource.MANUAL,
        )

    val credential: DeviceCredential = DeviceCredential(deviceId = id.uuidString, token = TOKEN)

    suspend fun apply(
        enabled: Boolean,
        store: ConnectionStore,
        tokens: TokenStore,
    ) {
        if (enabled) {
            store.upsert(connection)
            tokens.setCredential(credential, id)
            return
        }
        if (store.load().none { it.id == id }) return
        store.delete(id)
        tokens.deleteSecrets(id)
    }
}
