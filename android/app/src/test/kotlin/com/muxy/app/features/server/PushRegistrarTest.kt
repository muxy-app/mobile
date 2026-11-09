package com.muxy.app.features.server

import com.muxy.app.testing.FakePushContext
import com.muxy.app.testing.FakeServerConnection
import com.muxy.app.testing.FakeServerConnector
import com.muxy.app.testing.serverCredential
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.muxy_mobile.ConnectionEvent
import uniffi.muxy_mobile.PushRegistration
import uniffi.muxy_mobile.PushStatus
import kotlin.time.Duration.Companion.seconds

class PushRegistrarTest {
    private fun TestScope.connected(
        context: FakePushContext,
        connections: List<FakeServerConnection>,
    ): Pair<ServerController, FakeServerConnector> {
        val connector = FakeServerConnector(connections.map { Result.success(it) })
        val controller = ServerController("server-1", connector, { serverCredential() }, backgroundScope, pushContext = context)
        controller.setWantsConnection(true)
        runCurrent()
        return controller to connector
    }

    @Test
    fun connectingRegistersTheTokenAndRecordsTheReply() =
        runTest {
            val context = FakePushContext()
            val connection = FakeServerConnection()
            val (controller, _) = connected(context, listOf(connection))
            assertEquals(listOf("fcm-token"), connection.registeredTokens)
            assertEquals(PushStatus.ACTIVE, context.statuses["server-1"])
            assertTrue(controller.supportsPushNotifications.value)
        }

    @Test
    fun everyConnectRegistersAgain() =
        runTest {
            val first = FakeServerConnection()
            val second = FakeServerConnection()
            val (_, connector) = connected(FakePushContext(), listOf(first, second))
            connector.emit(ConnectionEvent.Disconnected)
            runCurrent()
            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(1, first.registeredTokens.size)
            assertEquals(1, second.registeredTokens.size)
        }

    @Test
    fun aPausedServerStaysRegisteredAndIsUnregisteredWhenTurnedOff() =
        runTest {
            val context = FakePushContext()
            val connection = FakeServerConnection().apply { pushRegistration = PushRegistration(PushStatus.DISABLED, null) }
            val (controller, _) = connected(context, listOf(connection))
            assertEquals(PushStatus.DISABLED, context.statuses["server-1"])
            assertTrue(context.isRegistered("server-1"))
            context.intent = PushIntent.UNWANTED
            controller.syncPushRegistration()
            runCurrent()
            assertEquals(1, connection.unregisterCount)
            assertEquals(listOf("server-1"), context.unregistered)
        }

    @Test
    fun aRefusedTokenAsksForAFreshToken() =
        runTest {
            val context = FakePushContext()
            val connection = FakeServerConnection().apply { pushRegistration = PushRegistration(PushStatus.REFUSED, ByteArray(32) { 2 }) }
            connected(context, listOf(connection))
            assertEquals(1, context.freshTokenRequests)
            assertEquals(PushStatus.REFUSED, context.statuses["server-1"])
        }

    @Test
    fun turningNotificationsOffUnregisters() =
        runTest {
            val context = FakePushContext()
            val connection = FakeServerConnection()
            val (controller, _) = connected(context, listOf(connection))
            context.intent = PushIntent.UNWANTED
            controller.syncPushRegistration()
            runCurrent()
            assertEquals(1, connection.unregisterCount)
            assertFalse(context.isRegistered("server-1"))
        }

    @Test
    fun turningOffWhileOfflineUnregistersOnTheNextConnect() =
        runTest {
            val context = FakePushContext(intent = PushIntent.UNWANTED).apply { registered += "server-1" }
            val connection = FakeServerConnection()
            connected(context, listOf(connection))
            assertEquals(1, connection.unregisterCount)
            assertTrue(connection.registeredTokens.isEmpty())
            assertEquals(listOf("server-1"), context.unregistered)
        }

    @Test
    fun anUndecidedPermissionNeitherRegistersNorUnregisters() =
        runTest {
            val context = FakePushContext(intent = PushIntent.UNDECIDED).apply { registered += "server-1" }
            val connection = FakeServerConnection()
            connected(context, listOf(connection))
            assertTrue(context.syncs > 0)
            assertTrue(connection.registeredTokens.isEmpty())
            assertEquals(0, connection.unregisterCount)
        }

    @Test
    fun serversWithoutPushAreNeverAsked() =
        runTest {
            val context = FakePushContext()
            val connection = FakeServerConnection().apply { pushSupported = false }
            val (controller, _) = connected(context, listOf(connection))
            assertTrue("server-1" in context.unsupported)
            assertTrue(connection.registeredTokens.isEmpty())
            assertFalse(controller.supportsPushNotifications.value)
        }

    @Test
    fun nothingIsSentUntilATokenArrives() =
        runTest {
            val context = FakePushContext(deviceToken = null)
            val connection = FakeServerConnection()
            val (controller, _) = connected(context, listOf(connection))
            assertTrue(context.syncs > 0)
            assertTrue(connection.registeredTokens.isEmpty())
            context.deviceToken = "fresh"
            controller.syncPushRegistration()
            runCurrent()
            assertEquals(listOf("fresh"), connection.registeredTokens)
        }
}
