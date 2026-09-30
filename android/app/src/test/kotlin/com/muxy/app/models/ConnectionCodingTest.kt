package com.muxy.app.models

import com.muxy.app.persistence.connections.ConnectionJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class ConnectionCodingTest {
    private val json = ConnectionJson

    @Test
    fun roundTrips() {
        val connection =
            Connection(UUID.randomUUID(), "Studio", "studio.local", 4865, pairingState = PairingState.PAIRED, serviceName = "Studio")
        assertEquals(connection, json.decodeFromString(Connection.serializer(), json.encodeToString(Connection.serializer(), connection)))
    }

    @Test
    fun roundTripsWithoutServiceName() {
        val connection = Connection(UUID.randomUUID(), "Studio", "studio.local", 4865)
        val decoded = json.decodeFromString(Connection.serializer(), json.encodeToString(Connection.serializer(), connection))
        assertNull(decoded.serviceName)
        assertEquals(connection, decoded)
    }

    @Test
    fun usesTheIosKeysAndValues() {
        val connection =
            Connection(
                id = UUID.fromString("0000000a-0000-4000-8000-00000000000b"),
                name = "Box",
                host = "box.local",
                port = 22,
                kind = ConnectionKind.SSH,
                pairingState = PairingState.NOT_PAIRED,
                discoverySource = DiscoverySource.BONJOUR,
                sshConfig = SshConfig("root", SshAuthMethod.PRIVATE_KEY),
                serverId = "server-1",
            )
        val encoded = Json.parseToJsonElement(json.encodeToString(Connection.serializer(), connection)).jsonObject
        assertEquals("0000000A-0000-4000-8000-00000000000B", encoded.getValue("id").jsonPrimitive.content)
        assertEquals("ssh", encoded.getValue("kind").jsonPrimitive.content)
        assertEquals("notPaired", encoded.getValue("pairingState").jsonPrimitive.content)
        assertEquals("bonjour", encoded.getValue("discoverySource").jsonPrimitive.content)
        assertEquals("server-1", encoded.getValue("serverID").jsonPrimitive.content)
        assertEquals(
            "privateKey",
            encoded
                .getValue("sshConfig")
                .jsonObject
                .getValue("authMethod")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun defaultsAMissingKindToDevice() {
        val decoded =
            json.decodeFromString(
                Connection.serializer(),
                """{ "id": "0000000A-0000-4000-8000-00000000000B", "name": "Studio", "host": "h", "port": 1, "pairingState": "paired", "discoverySource": "qr" }""",
            )
        assertEquals(ConnectionKind.DEVICE, decoded.kind)
        assertEquals(DiscoverySource.QR, decoded.discoverySource)
    }
}
