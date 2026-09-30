package com.muxy.app.testing

import com.muxy.app.core.device.PhoneName
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.models.Connection
import com.muxy.app.models.DiscoverySource
import com.muxy.app.models.PairingState
import com.muxy.app.networking.muxy1.ConnectionManager
import com.muxy.app.persistence.secrets.DeviceCredential
import com.muxy.app.persistence.secrets.SecretTokenStore
import com.muxy.app.persistence.secrets.TokenStore
import com.muxy.app.services.pairing.LivePairingService
import com.muxy.app.services.pairing.PairingService
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import java.util.UUID

const val PHONE_NAME = "Saeed's Pixel"

fun device(
    name: String = "Studio",
    host: String = "studio.local",
    port: Int = 4865,
    id: UUID = UUID.randomUUID(),
    serviceName: String? = null,
): Connection =
    Connection(
        id = id,
        name = name,
        host = host,
        port = port,
        pairingState = PairingState.PAIRED,
        serviceName = serviceName,
        discoverySource = DiscoverySource.MANUAL,
    )

fun credential(connection: Connection): DeviceCredential = DeviceCredential(connection.id.uuidString, "token-${connection.name}")

suspend fun tokenStoreWith(vararg connections: Connection): TokenStore =
    SecretTokenStore(InMemorySecretStore()).apply {
        connections.forEach { setCredential(credential(it), it.id) }
    }

fun TestScope.connectionManager(
    recorder: TransportRecorder,
    tokens: TokenStore,
    pairingService: PairingService = LivePairingService(),
): ConnectionManager =
    ConnectionManager(
        makeTransport = recorder::make,
        tokenStore = tokens,
        phoneName = PhoneName { PHONE_NAME },
        pairingService = pairingService,
        dispatcher = StandardTestDispatcher(testScheduler),
    )
