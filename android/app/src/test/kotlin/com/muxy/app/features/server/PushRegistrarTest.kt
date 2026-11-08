package com.muxy.app.features.server

import com.muxy.app.testing.FakePushContext
import com.muxy.app.testing.FakeServerConnection
import com.muxy.app.testing.FakeServerConnector
import com.muxy.app.testing.serverCredential
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.muxy_mobile.ConnectionEvent
import uniffi.muxy_mobile.PushRegistration
import uniffi.muxy_mobile.PushStatus
import kotlin.time.Duration.Companion.seconds

class PushRegistrarTest {
    private fun TestScope.connected(
        context: FakePushContext,
        vararg connections: FakeServerConnection,
    ): Pair<ServerController, FakeServerConnector> {
        val connector = FakeServerConnector(connections.map { Result.success(it) })
        val controller = ServerController("server-1", connector, { serverCredential() }, backgroundScope, pushContext = context)
        controller.setWantsConnection(true)
        runCurrent()
        return controller to connector
    }

    @Test
    fun connectingRegistersTheTokenAndKeepsTheKey() =
        runTest {
            val context = FakePushContext()
            val connection = FakeServerConnection()
            val (controller, _) = connected(context, connection)
            assertEquals(listOf("fcm-token"), connection.registeredTokens)
            assertArrayEquals(ByteArray(32) { 7 }, context.keys["server-1"])
            assertEquals(PushStatus.ACTIVE, context.statuses["server-1"])
            assertTrue(controller.supportsPushNotifications.value)
        }

    @Test
    fun everyConnectRegistersAgain() =
        runTest {
            val first = FakeServerConnection()
            val second = FakeServerConnection()
            val (_, connector) = connected(FakePushContext(), first, second)
            connector.emit(ConnectionEvent.Disconnected)
            runCurrent()
            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(1, first.registeredTokens.size)
            assertEquals(1, second.registeredTokens.size)
        }

    @Test
    fun aDisabledServerDropsTheKey() =
        runTest {
            val context = FakePushContext().apply { keys["server-1"] = ByteArray(32) { 1 } }
            val connection = FakeServerConnection().apply { pushRegistration = PushRegistration(PushStatus.DISABLED, null) }
            connected(context, connection)
            assertEquals(PushStatus.DISABLED, context.statuses["server-1"])
            assertNull(context.keys["server-1"])
        }

    @Test
    fun aRefusedTokenKeepsTheKeyAndAsksForAFreshToken() =
        runTest {
            val context = FakePushContext()
            val connection = FakeServerConnection().apply { pushRegistration = PushRegistration(PushStatus.REFUSED, ByteArray(32) { 2 }) }
            connected(context, connection)
            assertEquals(1, context.freshTokenRequests)
            assertArrayEquals(ByteArray(32) { 2 }, context.keys["server-1"])
        }

    @Test
    fun turningNotificationsOffUnregisters() =
        runTest {
            val context = FakePushContext()
            val connection = FakeServerConnection()
            val (controller, _) = connected(context, connection)
            context.wants = false
            controller.syncPushRegistration()
            runCurrent()
            assertEquals(1, connection.unregisterCount)
            assertNull(context.keys["server-1"])
        }

    @Test
    fun turningOffWhileOfflineUnregistersOnTheNextConnect() =
        runTest {
            val context = FakePushContext(wants = false).apply { keys["server-1"] = ByteArray(32) { 1 } }
            val connection = FakeServerConnection()
            connected(context, connection)
            assertEquals(1, connection.unregisterCount)
            assertTrue(connection.registeredTokens.isEmpty())
            assertNull(context.keys["server-1"])
        }

    @Test
    fun serversWithoutPushAreNeverAsked() =
        runTest {
            val context = FakePushContext()
            val connection = FakeServerConnection().apply { pushSupported = false }
            val (controller, _) = connected(context, connection)
            assertTrue("server-1" in context.unsupported)
            assertTrue(connection.registeredTokens.isEmpty())
            assertFalse(controller.supportsPushNotifications.value)
        }

    @Test
    fun nothingIsSentUntilATokenArrives() =
        runTest {
            val context = FakePushContext(deviceToken = null)
            val connection = FakeServerConnection()
            val (controller, _) = connected(context, connection)
            assertTrue(connection.registeredTokens.isEmpty())
            context.deviceToken = "fresh"
            controller.syncPushRegistration()
            runCurrent()
            assertEquals(listOf("fresh"), connection.registeredTokens)
        }
}
