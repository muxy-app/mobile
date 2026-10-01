package com.muxy.app.features.addconnection

import com.muxy.app.models.ConnectionKind
import com.muxy.app.models.DiscoverySource
import com.muxy.app.models.PairingState
import com.muxy.app.testing.InMemoryConnectionStore
import com.muxy.app.testing.InMemoryCredentialStore
import com.muxy.app.testing.PAIRING_LINK
import com.muxy.app.testing.StubPairingService
import com.muxy.app.testing.serverCredential
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.muxy_mobile.MobileException

class ServerPairingModelTest {
    private val target = PairingTarget(PAIRING_LINK, "192.168.1.20:7419", DiscoverySource.QR)

    private fun model(
        pairing: StubPairingService = StubPairingService(),
        credentials: InMemoryCredentialStore = InMemoryCredentialStore(),
        connections: InMemoryConnectionStore = InMemoryConnectionStore(),
    ) = ServerPairingModel(pairing, credentials, connections, deviceName = "Pixel")

    @Test
    fun aValidLinkAsksToConfirmTheFirstAddress() {
        val model = model()
        model.receive(" $PAIRING_LINK\n", DiscoverySource.QR)
        assertEquals(ServerPairingStep.Confirm(target), model.step)
        assertNull(model.failure)
        assertTrue(model.canPair)
    }

    @Test
    fun aForeignCodeShowsTheSpecMessage() {
        val model = model(StubPairingService(accepts = { false }))
        model.receive("https://example.com", DiscoverySource.MANUAL)
        assertEquals(ServerPairingStep.Entry, model.step)
        assertEquals("This isn't a Muxy pairing code.", model.failure)
    }

    @Test
    fun aBlankDeviceNameCannotPair() {
        val model = model()
        model.receive(PAIRING_LINK, DiscoverySource.QR)
        model.deviceName = "   "
        assertFalse(model.canPair)
    }

    @Test
    fun pairingSavesTheCredentialAndAConnection() =
        runTest {
            val credentials = InMemoryCredentialStore()
            val connections = InMemoryConnectionStore()
            val pairing = StubPairingService()
            val model = model(pairing, credentials, connections)
            model.receive(PAIRING_LINK, DiscoverySource.QR)
            model.deviceName = " Work Phone "
            val connection = model.pair()!!
            assertEquals(listOf("Work Phone"), pairing.pairedNames)
            assertEquals(serverCredential().serverId, connection.serverId)
            assertEquals(ConnectionKind.SERVER, connection.kind)
            assertEquals(PairingState.PAIRED, connection.pairingState)
            assertEquals("Studio", connection.name)
            assertEquals("192.168.1.20", connection.host)
            assertEquals(7419, connection.port)
            assertEquals(DiscoverySource.QR, connection.discoverySource)
            assertEquals(listOf(connection), connections.load())
            assertEquals(
                serverCredential().token.toList(),
                credentials.items
                    .getValue("server-1")
                    .token
                    .toList(),
            )
        }

    @Test
    fun pairingTheSameComputerAgainReplacesItsConnection() =
        runTest {
            val connections = InMemoryConnectionStore()
            val first = model(connections = connections).apply { receive(PAIRING_LINK, DiscoverySource.QR) }.pair()!!
            val renamed = StubPairingService(paired = Result.success(serverCredential(name = "Studio Pro")))
            val second = model(renamed, connections = connections).apply { receive(PAIRING_LINK, DiscoverySource.MANUAL) }.pair()!!
            assertEquals(first.id, second.id)
            assertEquals(listOf("Studio Pro"), connections.load().map { it.name })
        }

    @Test
    fun anExpiredCodeAsksForANewOne() =
        runTest {
            val model = model(StubPairingService(paired = Result.failure(MobileException.Unauthorized())))
            model.receive(PAIRING_LINK, DiscoverySource.QR)
            assertNull(model.pair())
            assertTrue(model.failure!!.contains("Show a new code on your computer."))
            assertEquals(ServerPairingStep.Confirm(target), model.step)
        }

    @Test
    fun aFailedSaveDoesNotReportSuccess() =
        runTest {
            val connections = InMemoryConnectionStore()
            val model = model(credentials = InMemoryCredentialStore(failsSaving = true), connections = connections)
            model.receive(PAIRING_LINK, DiscoverySource.QR)
            assertNull(model.pair())
            assertEquals(ServerPairingModel.SAVE_FAILED, model.failure)
            assertTrue(connections.load().isEmpty())
        }

    @Test
    fun pairingWithoutAConfirmedLinkDoesNothing() =
        runTest {
            assertNull(model().pair())
        }
}
