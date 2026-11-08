package com.muxy.app.features.server

import com.muxy.app.features.push.PushTarget
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.testing.FakePushContext
import com.muxy.app.testing.FakeServerConnection
import com.muxy.app.testing.FakeServerConnector
import com.muxy.app.testing.serverCredential
import com.muxy.app.testing.session
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.muxy_mobile.ConnectionEvent
import uniffi.muxy_mobile.MobileException
import kotlin.time.Duration.Companion.seconds

class SessionOpenerTest {
    private val size = TerminalGridSize(columns = 50, rows = 20)
    private val target = PushTarget("server-1", 42u, "muxy", 7u)

    private fun TestScope.controller(
        context: FakePushContext,
        outcomes: List<Result<FakeServerConnection>>,
    ): Pair<ServerController, FakeServerConnector> {
        val connector = FakeServerConnector(outcomes)
        val controller = ServerController("server-1", connector, { serverCredential() }, backgroundScope, pushContext = context)
        controller.setWantsConnection(true)
        runCurrent()
        return controller to connector
    }

    private fun TestScope.open(
        controller: ServerController,
        target: PushTarget = this@SessionOpenerTest.target,
    ): SessionOpening? {
        controller.showSession(target)
        runCurrent()
        return controller.sessionFocus.value
            ?.takeIf { it.target == target }
            ?.opening
    }

    @Test
    fun anOpenTabForTheSessionIsSelected() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(41u), session(42u)))
            val (controller, _) = controller(FakePushContext(), listOf(Result.success(connection)))
            controller.showProject("muxy")
            runCurrent()
            val model = controller.projectModel("muxy")
            val existing = model.tabs.value.first { it.sessionId == 42uL }

            assertEquals(SessionOpening.Opened("muxy", existing.id), open(controller))
            assertEquals(existing.id, model.selectedTabId.value)
            assertEquals(2, model.tabs.value.size)
        }

    @Test
    fun theSessionIsAttachedWithoutStartingAShell() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(42u)))
            val (controller, _) = controller(FakePushContext(), listOf(Result.success(connection)))

            val opening = open(controller) as SessionOpening.Opened

            val tab = checkNotNull(controller.projectModel("muxy").selectedTab)
            assertEquals(tab.id, opening.tabId)
            tab.controller.viewportDidChange(size)
            runCurrent()
            assertEquals(listOf(42uL), connection.attached)
            assertTrue(connection.created.isEmpty())
            assertTrue(controller.projectModel("muxy").isVisible)
        }

    @Test
    fun aSessionThatMovedOpensInItsCurrentProject() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(42u, project = "home")))
            val (controller, _) = controller(FakePushContext(), listOf(Result.success(connection)))

            assertEquals("home", open(controller)?.projectId)
            assertEquals(42uL, controller.projectModel("home").selectedTab?.sessionId)
            assertTrue(controller.projectModel("home").isVisible)
            assertTrue(
                controller
                    .projectModel("muxy")
                    .tabs.value
                    .isEmpty(),
            )
        }

    @Test
    fun anEndedSessionOpensTheProjectAndIsAcknowledged() =
        runTest {
            val context = FakePushContext()
            val connection = FakeServerConnection()
            val (controller, _) = controller(context, listOf(Result.success(connection)))

            assertEquals(SessionOpening.Ended("muxy"), open(controller))
            assertEquals(listOf(7uL), connection.acknowledged)
            assertEquals(listOf(42uL), context.removedSessions)
        }

    @Test
    fun theEventIsAcknowledgedOnceThePaneIsLive() =
        runTest {
            val context = FakePushContext()
            val connection = FakeServerConnection(sessions = listOf(session(42u)))
            val (controller, _) = controller(context, listOf(Result.success(connection)))

            open(controller)
            assertTrue(connection.acknowledged.isEmpty())

            checkNotNull(controller.projectModel("muxy").selectedTab).controller.viewportDidChange(size)
            runCurrent()
            assertEquals(listOf(7uL), connection.acknowledged)
            assertEquals(listOf(42uL), context.removedSessions)
        }

    @Test
    fun aDroppedConnectionIsRetriedOnceReconnected() =
        runTest {
            val first = FakeServerConnection(sessions = listOf(session(42u))).apply { findFailures += MobileException.Disconnected() }
            val second = FakeServerConnection(sessions = listOf(session(42u)))
            val (controller, connector) = controller(FakePushContext(), listOf(Result.success(first), Result.success(second)))

            assertNull(open(controller))
            connector.emit(ConnectionEvent.Disconnected)
            advanceTimeBy(2.seconds)
            runCurrent()

            assertEquals(
                "muxy",
                controller.sessionFocus.value
                    ?.opening
                    ?.projectId,
            )
            assertEquals(2, connector.connectCount)
        }

    @Test
    fun openingWaitsForTheServerToConnect() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(42u)))
            val (controller, connector) =
                controller(FakePushContext(), listOf(Result.failure(MobileException.Unreachable("asleep")), Result.success(connection)))

            assertNull(open(controller))
            assertTrue(controller.projectModel("muxy").isVisible)
            advanceTimeBy(2.seconds)
            runCurrent()

            assertEquals(
                "muxy",
                controller.sessionFocus.value
                    ?.opening
                    ?.projectId,
            )
            assertEquals(2, connector.connectCount)
        }

    @Test
    fun leavingStopsTheOpen() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(42u)))
            val (controller, _) =
                controller(
                    FakePushContext(),
                    listOf(Result.failure(MobileException.Unreachable("asleep")), Result.success(connection)),
                )

            assertNull(open(controller))
            controller.showSession(null)
            advanceTimeBy(2.seconds)
            runCurrent()

            assertNull(controller.sessionFocus.value)
            assertTrue(connection.acknowledged.isEmpty())
        }
}
