package com.muxy.app.networking.muxy1

import com.muxy.app.networking.muxy1.transport.TransportException
import com.muxy.app.networking.muxy1.transport.TransportFailure
import com.muxy.app.services.pairing.PairingStatus
import com.muxy.app.testing.Frames
import com.muxy.app.testing.PHONE_NAME
import com.muxy.app.testing.TransportRecorder
import com.muxy.app.testing.connectionManager
import com.muxy.app.testing.credential
import com.muxy.app.testing.device
import com.muxy.app.testing.tokenStoreWith
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionManagerTest {
    @Test
    fun connectReachesConnectedState() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            manager.ensureConnected(studio)
            assertEquals(ConnectionState.Connected, manager.status.value.of(studio.id))
            assertEquals(listOf("ws://studio.local:4865"), recorder.urls)
        }

    @Test
    fun ensureConnectedIsNoOpWhenAlreadyConnected() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            manager.ensureConnected(studio)
            manager.ensureConnected(studio)
            assertEquals(ConnectionState.Connected, manager.status.value.of(studio.id))
            assertEquals(1, recorder.transports.size)
        }

    @Test
    fun ensureConnectedReconnectsAfterTransportReadFailure() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            manager.ensureConnected(studio)
            recorder.latest!!.failReaders()
            advanceUntilIdle()
            assertEquals(ConnectionState.Disconnected, manager.status.value.of(studio.id))
            manager.ensureConnected(studio)
            assertEquals(ConnectionState.Connected, manager.status.value.of(studio.id))
            assertEquals(2, recorder.transports.size)
        }

    @Test
    fun ensureConnectedSwitchesToADifferentDevice() =
        runTest {
            val studio = device()
            val laptop = device(name = "Laptop", host = "laptop.local")
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio, laptop))
            manager.ensureConnected(studio)
            manager.ensureConnected(laptop)
            assertEquals(ConnectionState.Connected, manager.status.value.of(laptop.id))
            assertEquals(ConnectionState.Idle, manager.status.value.of(studio.id))
            assertTrue(recorder.transports.first().didClose)
        }

    @Test
    fun reconnectAlwaysOpensANewSocket() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            manager.ensureConnected(studio)
            manager.reconnect(studio)
            assertEquals(2, recorder.transports.size)
            assertTrue(recorder.transports.first().didClose)
        }

    @Test
    fun disconnectMovesToDisconnected() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            manager.ensureConnected(studio)
            manager.disconnect()
            assertEquals(ConnectionState.Disconnected, manager.status.value.state)
            assertNull(manager.status.value.connectionId)
            assertTrue(recorder.latest!!.didClose)
        }

    @Test
    fun beginPairingReusesTheSocketAfterwards() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            val status = manager.beginPairing(studio, credential(studio)) {}
            assertTrue(status is PairingStatus.Paired)
            manager.ensureConnected(studio)
            assertEquals(ConnectionState.Connected, manager.status.value.of(studio.id))
            assertEquals(1, recorder.transports.size)
        }

    @Test
    fun anEmptyHostReportsAnInvalidEndpoint() =
        runTest {
            val bad = device(host = "")
            val manager = connectionManager(TransportRecorder(), tokenStoreWith(bad))
            manager.ensureConnected(bad)
            assertEquals(ConnectionState.Failed(ConnectionError.INVALID_ENDPOINT), manager.status.value.of(bad.id))
        }

    @Test
    fun aMissingCredentialReportsAMissingToken() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith())
            manager.ensureConnected(studio)
            assertEquals(ConnectionState.Failed(ConnectionError.MISSING_TOKEN), manager.status.value.of(studio.id))
            assertTrue(recorder.transports.isEmpty())
        }

    @Test
    fun anUnreachableMacReportsAConnectionFailure() =
        runTest {
            val studio = device()
            val manager =
                connectionManager(
                    TransportRecorder(connectFailure = TransportException(TransportFailure.TIMED_OUT)),
                    tokenStoreWith(studio),
                )
            manager.ensureConnected(studio)
            assertEquals(ConnectionState.Failed(ConnectionError.CONNECTION_FAILED), manager.status.value.of(studio.id))
        }

    @Test
    fun aRejectedCredentialReportsAnAuthenticationFailure() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder { _, frame -> listOf(Frames.error(Frames.id(frame), 401)) }
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            manager.ensureConnected(studio)
            assertEquals(ConnectionState.Failed(ConnectionError.AUTHENTICATION_FAILED), manager.status.value.of(studio.id))
            assertTrue(recorder.latest!!.didClose)
        }

    @Test
    fun authenticatesWithThePhoneNameAndTheStoredDeviceId() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            manager.ensureConnected(studio)
            val params = Frames.params(recorder.latest!!.sentFrames.single())
            assertEquals(PHONE_NAME, params.getValue("deviceName").jsonPrimitive.content)
            assertEquals(credential(studio).deviceId, params.getValue("deviceID").jsonPrimitive.content)
            assertEquals(credential(studio).token, params.getValue("token").jsonPrimitive.content)
        }

    @Test
    fun theSessionGrowsWithEachConnection() =
        runTest {
            val studio = device()
            val manager = connectionManager(TransportRecorder(), tokenStoreWith(studio))
            manager.ensureConnected(studio)
            val first = manager.status.value.connectedSession(studio.id)
            manager.reconnect(studio)
            val second = manager.status.value.connectedSession(studio.id)
            assertTrue(first != null && second != null && second > first)
        }

    @Test
    fun aConnectToTheSameMacJoinsTheOneInFlight() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            val first = launch { manager.ensureConnected(studio) }
            val second = launch { manager.reconnect(studio) }
            first.join()
            second.join()
            assertEquals(1, recorder.transports.size)
            assertEquals(ConnectionState.Connected, manager.status.value.of(studio.id))
        }

    @Test
    fun aConnectToAnotherMacCancelsTheOneInFlight() =
        runTest {
            val silent = device(name = "Silent", host = "silent.local")
            val laptop = device(name = "Laptop", host = "laptop.local")
            val recorder = TransportRecorder { url, frame -> if (url.contains("silent")) emptyList() else Frames.authenticated(frame) }
            val manager = connectionManager(recorder, tokenStoreWith(silent, laptop))
            launch { manager.ensureConnected(silent) }
            runCurrent()
            assertEquals(ConnectionState.Authenticating, manager.status.value.of(silent.id))
            manager.ensureConnected(laptop)
            assertEquals(ConnectionState.Connected, manager.status.value.of(laptop.id))
            assertTrue(recorder.transports.first().didClose)
        }

    @Test
    fun disconnectCancelsAConnectInFlight() =
        runTest {
            val silent = device(name = "Silent", host = "silent.local")
            val recorder = TransportRecorder { _, _ -> emptyList() }
            val manager = connectionManager(recorder, tokenStoreWith(silent))
            launch { manager.ensureConnected(silent) }
            runCurrent()
            manager.disconnect()
            assertEquals(ConnectionState.Disconnected, manager.status.value.state)
            assertTrue(recorder.latest!!.didClose)
        }

    @Test
    fun disconnectUnlessPairingKeepsAPairingThatWaitsForApproval() =
        runTest {
            val studio = device()
            val recorder =
                TransportRecorder { _, frame ->
                    if (Frames.method(frame) ==
                        "authenticateDevice"
                    ) {
                        listOf(Frames.error(Frames.id(frame), 401))
                    } else {
                        emptyList()
                    }
                }
            val manager = connectionManager(recorder, tokenStoreWith())
            val statuses = mutableListOf<PairingStatus>()
            launch { manager.beginPairing(studio, credential(studio)) { statuses += it } }
            runCurrent()
            assertEquals(PairingStatus.AwaitingApproval, statuses.last())
            manager.disconnectUnlessPairing()
            runCurrent()
            assertTrue(!recorder.latest!!.didClose)
        }
}
