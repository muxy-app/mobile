package com.muxy.app.features.server

import com.muxy.app.networking.server.ServerFailure
import com.muxy.app.testing.FakeServerConnection
import com.muxy.app.testing.FakeServerConnector
import com.muxy.app.testing.serverCredential
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.muxy_mobile.ConnectionEvent
import uniffi.muxy_mobile.MobileException
import uniffi.muxy_mobile.ServerCredential
import kotlin.time.Duration.Companion.seconds

class ServerControllerTest {
    private fun TestScope.controller(
        connector: FakeServerConnector,
        credential: () -> ServerCredential? = { serverCredential() },
    ) = ServerController("server-1", connector, { credential() }, backgroundScope)

    private fun connected(vararg connections: FakeServerConnection) = FakeServerConnector(connections.map { Result.success(it) })

    @Test
    fun connectsAndLoadsTheCatalog() =
        runTest {
            val controller = controller(connected(FakeServerConnection()))
            controller.setWantsConnection(true)
            runCurrent()
            assertEquals(ServerPhase.Connected, controller.phase.value)
            assertEquals(
                listOf("home", "muxy"),
                controller.catalog.value.projects
                    .map { it.id },
            )
            assertTrue(controller.catalog.value.hasLoaded)
            assertEquals("Studio", controller.serverName)
        }

    @Test
    fun nothingConnectsUntilItIsWanted() =
        runTest {
            val connector = connected(FakeServerConnection())
            val controller = controller(connector)
            runCurrent()
            assertEquals(ServerPhase.Idle, controller.phase.value)
            assertEquals(0, connector.connectCount)
        }

    @Test
    fun aMissingCredentialAsksToPairAgainWithoutConnecting() =
        runTest {
            val connector = connected(FakeServerConnection())
            val controller = controller(connector) { null }
            controller.setWantsConnection(true)
            runCurrent()
            assertEquals(ServerPhase.Failed(ServerFailure.InvalidCredential), controller.phase.value)
            assertEquals(0, connector.connectCount)
        }

    @Test
    fun failuresOnlyTheUserCanFixStopRetrying() =
        runTest {
            val connector =
                FakeServerConnector(listOf(Result.failure(MobileException.Unauthorized()), Result.success(FakeServerConnection())))
            val controller = controller(connector)
            controller.setWantsConnection(true)
            advanceTimeBy(60.seconds)
            assertEquals(ServerPhase.Failed(ServerFailure.Unauthorized), controller.phase.value)
            assertEquals(1, connector.connectCount)
        }

    @Test
    fun unreachableComputersAreRetriedWithBackoff() =
        runTest {
            val connector =
                FakeServerConnector(
                    listOf(
                        Result.failure(MobileException.Unreachable("asleep")),
                        Result.failure(MobileException.Unreachable("asleep")),
                        Result.success(FakeServerConnection()),
                    ),
                )
            val controller = controller(connector)
            controller.setWantsConnection(true)
            runCurrent()
            assertEquals(ServerPhase.Reconnecting(ReconnectReason.Lost(ServerFailure.Unreachable, attempt = 1)), controller.phase.value)
            advanceTimeBy(1.seconds + 1.seconds / 10)
            assertEquals(2, connector.connectCount)
            assertEquals(ServerPhase.Reconnecting(ReconnectReason.Lost(ServerFailure.Unreachable, attempt = 2)), controller.phase.value)
            advanceTimeBy(2.seconds + 1.seconds / 10)
            assertEquals(ServerPhase.Connected, controller.phase.value)
            assertEquals(3, connector.connectCount)
        }

    @Test
    fun anUnexpectedDisconnectReconnects() =
        runTest {
            val first = FakeServerConnection()
            val connector = connected(first, FakeServerConnection())
            val controller = controller(connector)
            controller.setWantsConnection(true)
            runCurrent()
            connector.emit(ConnectionEvent.Disconnected)
            runCurrent()
            assertTrue(first.isDisconnected)
            assertTrue(controller.phase.value.isConnectionLost)
            advanceTimeBy(1.seconds + 1.seconds / 10)
            assertEquals(ServerPhase.Connected, controller.phase.value)
            assertEquals(2, connector.connectCount)
        }

    @Test
    fun projectsStayVisibleWhileReconnecting() =
        runTest {
            val connector = connected(FakeServerConnection())
            val controller = controller(connector)
            controller.setWantsConnection(true)
            runCurrent()
            connector.emit(ConnectionEvent.Disconnected)
            runCurrent()
            assertEquals(
                listOf("home", "muxy"),
                controller.catalog.value.projects
                    .map { it.id },
            )
        }

    @Test
    fun aServerRestartShowsReconnectingThenReconnectsSoon() =
        runTest {
            val connector = connected(FakeServerConnection(), FakeServerConnection())
            val controller = controller(connector)
            controller.setWantsConnection(true)
            runCurrent()
            connector.emit(ConnectionEvent.ServerRestarting)
            runCurrent()
            assertEquals(ServerPhase.Reconnecting(ReconnectReason.ServerRestarting), controller.phase.value)
            connector.emit(ConnectionEvent.Disconnected)
            runCurrent()
            assertEquals(ServerPhase.Reconnecting(ReconnectReason.ServerRestarting), controller.phase.value)
            advanceTimeBy(1.seconds)
            assertEquals(1, connector.connectCount)
            advanceTimeBy(1.seconds)
            assertEquals(ServerPhase.Connected, controller.phase.value)
            assertEquals(2, connector.connectCount)
        }

    @Test
    fun eventsFromAnOldConnectionAreIgnored() =
        runTest {
            val connector = connected(FakeServerConnection(), FakeServerConnection())
            val controller = controller(connector)
            controller.setWantsConnection(true)
            runCurrent()
            controller.setWantsConnection(false)
            controller.setWantsConnection(true)
            runCurrent()
            connector.emit(ConnectionEvent.Disconnected, fromAttempt = 1)
            advanceTimeBy(30.seconds)
            assertEquals(ServerPhase.Connected, controller.phase.value)
            assertEquals(2, connector.connectCount)
        }

    @Test
    fun leavingDisconnectsWithoutRetrying() =
        runTest {
            val connection = FakeServerConnection()
            val connector = connected(connection)
            val controller = controller(connector)
            controller.setWantsConnection(true)
            runCurrent()
            controller.setWantsConnection(false)
            advanceTimeBy(30.seconds)
            assertEquals(ServerPhase.Idle, controller.phase.value)
            assertTrue(connection.isDisconnected)
            assertEquals(1, connector.connectCount)
        }

    @Test
    fun retryingClearsAFatalFailure() =
        runTest {
            val connector =
                FakeServerConnector(listOf(Result.failure(MobileException.IdentityMismatch()), Result.success(FakeServerConnection())))
            val controller = controller(connector)
            controller.setWantsConnection(true)
            runCurrent()
            assertEquals(ServerPhase.Failed(ServerFailure.IdentityMismatch), controller.phase.value)
            controller.retryNow()
            runCurrent()
            assertEquals(ServerPhase.Connected, controller.phase.value)
        }

    @Test
    fun pairingAgainReconnectsWithTheNewCredential() =
        runTest {
            val first = FakeServerConnection()
            val connector = connected(first, FakeServerConnection())
            var credential = serverCredential(name = "Studio")
            val controller = controller(connector) { credential }
            controller.setWantsConnection(true)
            runCurrent()
            credential = serverCredential(name = "Studio Pro")
            controller.credentialDidChange()
            runCurrent()
            assertTrue(first.isDisconnected)
            assertEquals(2, connector.connectCount)
            assertEquals("Studio Pro", connector.credentials.last().serverName)
            assertEquals("Studio Pro", controller.serverName)
        }

    @Test
    fun aCatalogChangeReloadsTheProjects() =
        runTest {
            val connection = FakeServerConnection()
            val connector = connected(connection)
            val controller = controller(connector)
            controller.setWantsConnection(true)
            runCurrent()
            connection.projects = connection.projects.take(1)
            connector.emit(ConnectionEvent.CatalogChanged)
            runCurrent()
            assertEquals(
                listOf("home"),
                controller.catalog.value.projects
                    .map { it.id },
            )
        }
}
