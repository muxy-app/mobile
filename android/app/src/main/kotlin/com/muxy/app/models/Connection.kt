@file:UseSerializers(UuidSerializer::class)

package com.muxy.app.models

import com.muxy.app.core.Endpoint
import com.muxy.app.core.serialization.UuidSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.UUID

@Serializable
enum class ConnectionKind {
    @SerialName("device")
    DEVICE,

    @SerialName("server")
    SERVER,

    @SerialName("ssh")
    SSH,
}

@Serializable
enum class PairingState {
    @SerialName("notPaired")
    NOT_PAIRED,

    @SerialName("paired")
    PAIRED,
}

@Serializable
enum class DiscoverySource {
    @SerialName("manual")
    MANUAL,

    @SerialName("qr")
    QR,

    @SerialName("bonjour")
    BONJOUR,
}

@Serializable
data class Connection(
    val id: UUID,
    val name: String,
    val host: String,
    val port: Int,
    val kind: ConnectionKind = ConnectionKind.DEVICE,
    val pairingState: PairingState = PairingState.NOT_PAIRED,
    val serviceName: String? = null,
    val discoverySource: DiscoverySource = DiscoverySource.MANUAL,
    val sshConfig: SshConfig? = null,
    @SerialName("serverID")
    val serverId: String? = null,
) {
    val endpoint: Endpoint
        get() = Endpoint(host, port)
}
