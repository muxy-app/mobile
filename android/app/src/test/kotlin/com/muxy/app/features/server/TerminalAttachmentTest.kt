package com.muxy.app.features.server

import com.muxy.app.features.terminal.TerminalController
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.features.terminalkit.TerminalKey
import com.muxy.app.features.terminalkit.TerminalKeyStroke
import com.muxy.app.testing.FakeServerConnection
import com.muxy.app.testing.FakeServerConnector
import com.muxy.app.testing.serverCredential
import com.muxy.app.testing.serverProject
import com.muxy.app.testing.session
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.muxy_mobile.ConnectionEvent
import uniffi.muxy_mobile.MobileException
import kotlin.time.Duration.Companion.seconds

class TerminalAttachmentTest {
    private val size = TerminalGridSize(columns = 50, rows = 20)

    private fun TestScope.connected(vararg connections: FakeServerConnection): Pair<ServerController, FakeServerConnector> {
        val connector = FakeServerConnector(connections.map { Result.success(it) })
        val controller = ServerController("server-1", connector, { serverCredential() }, backgroundScope)
        controller.setWantsConnection(true)
        runCurrent()
        return controller to connector
    }

    private fun TestScope.visibleTab(
        controller: ServerController,
        sessionId: ULong,
    ): ServerTerminal {
        controller.showProject("muxy")
        runCurrent()
        val model = controller.projectModel("muxy")
        val tab = model.tabs.value.first { it.sessionId == sessionId }
        model.select(tab)
        tab.controller.viewportDidChange(size)
        runCurrent()
        return tab
    }

    @Test
    fun onlyTheVisibleTerminalIsAttached() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(1u), session(2u)))
            val (controller) = connected(connection)
            controller.showProject("muxy")
            runCurrent()
            val model = controller.projectModel("muxy")
            val (first, second) = model.tabs.value
            second.controller.viewportDidChange(size)
            model.select(second)
            first.controller.viewportDidChange(size)
            runCurrent()
            assertEquals(ServerTerminalPhase.Live, second.phase.value)
            assertEquals(listOf(2uL), connection.attached)

            model.select(first)
            runCurrent()
            assertEquals(ServerTerminalPhase.Live, first.phase.value)
            assertEquals(listOf(2uL, 1uL), connection.attached)
            assertEquals(1, connection.channel(2u)?.detachCount)
            assertTrue(connection.channel(2u)!!.isClosed)
        }

    @Test
    fun aHiddenProjectDetachesItsTerminal() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(1u)))
            val (controller) = connected(connection)
            visibleTab(controller, 1u)
            controller.showProject(null)
            runCurrent()
            assertEquals(1, connection.channel(1u)?.detachCount)
        }

    @Test
    fun aNewTerminalIsCreatedAtTheViewportSize() =
        runTest {
            val connection = FakeServerConnection()
            val (controller) = connected(connection)
            val model = controller.projectModel("muxy")
            model.createTab()
            val tab = model.selectedTab!!
            tab.controller.viewportDidChange(size)
            controller.showProject("muxy")
            runCurrent()
            assertEquals(ServerTerminalPhase.Live, tab.phase.value)
            assertEquals(listOf(50 to 20), connection.created)
            assertEquals(900uL, tab.sessionId)
        }

    @Test
    fun tinyViewportsNeverCreateASession() =
        runTest {
            val connection = FakeServerConnection()
            val (controller) = connected(connection)
            val model = controller.projectModel("muxy")
            model.createTab()
            model.selectedTab!!.controller.viewportDidChange(TerminalGridSize(columns = 19, rows = 4))
            controller.showProject("muxy")
            runCurrent()
            assertTrue(connection.created.isEmpty())
        }

    @Test
    fun anEndedSessionClosesItsTab() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(1u)))
            val (controller, connector) = connected(connection)
            visibleTab(controller, 1u)
            connector.emit(ConnectionEvent.SessionEnded(1u))
            runCurrent()
            assertTrue(
                controller
                    .projectModel("muxy")
                    .tabs.value
                    .isEmpty(),
            )
        }

    @Test
    fun closingEndsTheSessionOnTheComputer() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(1u)))
            val (controller) = connected(connection)
            val tab = visibleTab(controller, 1u)
            controller.projectModel("muxy").close(tab)
            runCurrent()
            assertEquals(listOf(1uL), connection.ended)
            assertTrue(
                controller
                    .projectModel("muxy")
                    .tabs.value
                    .isEmpty(),
            )
        }

    @Test
    fun aTabWhoseSessionCouldNotBeEndedComesBack() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(1u)))
            val (controller) = connected(connection)
            val tab = visibleTab(controller, 1u)
            connection.failsEnding = true
            controller.projectModel("muxy").close(tab)
            runCurrent()
            assertEquals(
                listOf(1uL),
                controller
                    .projectModel("muxy")
                    .tabs.value
                    .map { it.sessionId },
            )
        }

    @Test
    fun aReconnectAttachesTheVisibleTerminalAgain() =
        runTest {
            val first = FakeServerConnection(sessions = listOf(session(1u)))
            val second = FakeServerConnection(sessions = listOf(session(1u)))
            val (controller, connector) = connected(first, second)
            val tab = visibleTab(controller, 1u)
            connector.emit(ConnectionEvent.Disconnected)
            runCurrent()
            assertTrue(first.channel(1u)!!.isClosed)
            advanceTimeBy(2.seconds)
            assertEquals(listOf(1uL), second.attached)
            assertEquals(ServerTerminalPhase.Live, tab.phase.value)
        }

    @Test
    fun aProjectRemovedOnTheComputerClosesItsTabs() =
        runTest {
            val first = FakeServerConnection(sessions = listOf(session(1u)))
            val second = FakeServerConnection(projects = listOf(serverProject("home", "Home", isHome = true)))
            val (controller, connector) = connected(first, second)
            val model = controller.projectModel("muxy")
            visibleTab(controller, 1u)
            connector.emit(ConnectionEvent.Disconnected)
            advanceTimeBy(2.seconds)
            assertTrue(model.tabs.value.isEmpty())
            assertEquals(null, controller.project("muxy"))
        }

    @Test
    fun anOpenScreenOfARemovedProjectKeepsTheControllersModel() =
        runTest {
            val first = FakeServerConnection(sessions = listOf(session(1u)))
            val second = FakeServerConnection(projects = listOf(serverProject("home", "Home", isHome = true)))
            val (controller, connector) = connected(first, second)
            val model = controller.projectModel("muxy")
            visibleTab(controller, 1u)
            connector.emit(ConnectionEvent.Disconnected)
            advanceTimeBy(2.seconds)
            assertSame(model, controller.projectModel("muxy"))
            model.createTab()
            model.selectedTab!!.controller.viewportDidChange(size)
            runCurrent()
            assertEquals(listOf(50 to 20), second.created)
        }

    @Test
    fun anAttachInterruptedByADisconnectIsRetriedAfterReconnect() =
        runTest {
            val first =
                FakeServerConnection(
                    sessions = listOf(session(1u)),
                    attachFailures =
                        mutableMapOf(1uL to MobileException.Disconnected()),
                )
            val second = FakeServerConnection(sessions = listOf(session(1u)))
            val (controller, connector) = connected(first, second)
            val tab = visibleTab(controller, 1u)
            assertEquals(ServerTerminalPhase.Waiting, tab.phase.value)
            assertTrue(first.attached.isEmpty())
            connector.emit(ConnectionEvent.Disconnected)
            advanceTimeBy(2.seconds)
            assertEquals(listOf(1uL), second.attached)
            assertEquals(ServerTerminalPhase.Live, tab.phase.value)
        }

    @Test
    fun aFailedAttachShowsWhyAndCanBeRetried() =
        runTest {
            val connection =
                FakeServerConnection(sessions = listOf(session(1u)), attachFailures = mutableMapOf(1uL to MobileException.Timeout()))
            val (controller) = connected(connection)
            val tab = visibleTab(controller, 1u)
            assertEquals(ServerTerminalPhase.Failed("Muxy isn't responding."), tab.phase.value)
            tab.retry()
            runCurrent()
            assertEquals(ServerTerminalPhase.Live, tab.phase.value)
        }

    @Test
    fun aSessionThatNoLongerExistsIsRemovedAfterAFailedAttach() =
        runTest {
            val connection =
                FakeServerConnection(
                    sessions = listOf(session(5u)),
                    attachFailures = mutableMapOf(5uL to MobileException.Server("session 5 does not exist")),
                )
            val (controller) = connected(connection)
            controller.showProject("muxy")
            runCurrent()
            val model = controller.projectModel("muxy")
            connection.endSession(5u)
            model.tabs.value
                .first()
                .controller
                .viewportDidChange(size)
            runCurrent()
            assertTrue(model.tabs.value.isEmpty())
        }

    @Test
    fun inputIsTranslatedForTheTerminal() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(1u)))
            val (controller) = connected(connection)
            val terminal = visibleTab(controller, 1u).controller
            terminal.sendText("ls")
            terminal.sendText("\n")
            terminal.sendText("\t")
            terminal.sendText("a\r\nb\nc")
            terminal.toggleModifier()
            terminal.sendText("c")
            terminal.send(TerminalKeyStroke(TerminalKey.Up))
            assertEquals(
                listOf("text:ls", "key:Enter", "key:Tab", "text:a\rb\rc", "key:ctrl+character(c)", "key:Up"),
                connection.channel(1u)?.sent,
            )
            assertFalse(terminal.sticky.value.armed)
        }

    @Test
    fun pastesOverTheLimitAreRefused() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(1u)))
            val (controller) = connected(connection)
            val terminal = visibleTab(controller, 1u).controller
            terminal.paste("x".repeat(TerminalController.MAXIMUM_PASTE_BYTES + 1))
            terminal.paste("echo ok")
            assertEquals(listOf("echo ok"), connection.channel(1u)?.pastes)
            assertEquals(TerminalController.PASTE_TOO_LARGE, terminal.notice.value)
        }

    @Test
    fun theTitleFollowsTheProgramThenTheFolder() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(1u)))
            val (controller, connector) = connected(connection)
            controller.showProject("muxy")
            runCurrent()
            val tab =
                controller
                    .projectModel("muxy")
                    .tabs.value
                    .single()
            assertEquals("Terminal 1", tab.title.value)
            tab.controller.viewportDidChange(size)
            runCurrent()
            assertEquals("shell 1", tab.title.value)
            val channel = connection.channel(1u)!!
            channel.currentScreen = channel.currentScreen.copy(title = " ", directory = "/Users/demo/api/")
            connector.emit(ConnectionEvent.MetadataChanged(1u))
            runCurrent()
            assertEquals("api", tab.title.value)
        }
}
