package com.muxy.app.networking.muxy1

import com.muxy.app.core.serialization.parseUuid
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.features.demo.DemoShell
import com.muxy.app.features.projectdetail.terminal.ConnectionTerminalChannel
import com.muxy.app.features.projectdetail.terminal.TerminalOwnership
import com.muxy.app.features.projectdetail.terminal.TerminalSession
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.models.Workspace
import com.muxy.app.models.WorkspaceFlattening
import com.muxy.app.networking.muxy1.protocol.GetWorkspaceParams
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProjectsResult
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.ReleasePaneParams
import com.muxy.app.persistence.secrets.SecretTokenStore
import com.muxy.app.testing.Frames
import com.muxy.app.testing.InMemorySecretStore
import com.muxy.app.testing.TransportRecorder
import com.muxy.app.testing.connectionManager
import com.muxy.app.testing.credential
import com.muxy.app.testing.device
import com.muxy.app.testing.tokenStoreWith
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ConnectionManagerTerminalTest {
    private val clientId = UUID.randomUUID()
    private val recorder =
        TransportRecorder { _, frame ->
            when (Frames.method(frame)) {
                "authenticateDevice" -> listOf(Frames.pairing(Frames.id(frame), clientId = clientId.uuidString))
                "takeOverPane" -> listOf(Frames.result(Frames.id(frame), "ok"))
                else -> emptyList()
            }
        }

    @Test
    fun theIdentityCarriesTheClientAndDeviceIds() =
        runTest {
            val studio = device()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            assertNull(manager.identity(studio.id))
            manager.ensureConnected(studio)
            val identity = checkNotNull(manager.identity(studio.id))
            assertEquals(clientId, identity.clientId)
            assertEquals(parseUuid(credential(studio).deviceId), identity.deviceId)
            assertTrue(identity.matches(clientId))
            manager.disconnect()
            assertNull(manager.identity(studio.id))
        }

    @Test
    fun notificationsAreSentWithoutWaitingForAReply() =
        runTest {
            val studio = device()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            manager.ensureConnected(studio)
            manager.notify(studio.id, Method.RELEASE_PANE, ProtocolJson.encodeToJsonElement(ReleasePaneParams("pane")))
            val frame = checkNotNull(recorder.latest).sentFrames.last()
            assertEquals("releasePane", Frames.method(frame))
            assertEquals(
                "pane",
                Frames
                    .params(frame)
                    .getValue("paneID")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun notifyingADisconnectedMacFails() =
        runTest {
            val manager = connectionManager(recorder, tokenStoreWith())
            val error = runCatching { manager.notify(device().id, Method.TERMINAL_INPUT) }.exceptionOrNull()
            assertEquals(ConnectionError.NOT_CONNECTED, (error as ConnectionException).error)
        }

    @Test
    fun aReleaseAndANewTakeoverReachTheMacInOrder() =
        runTest(UnconfinedTestDispatcher()) {
            val studio = device()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            manager.ensureConnected(studio)
            advanceUntilIdle()
            val session =
                TerminalSession(UUID.randomUUID(), ConnectionTerminalChannel(studio.id, manager), backgroundScope, backgroundScope, {})
            session.source.resize(TerminalGridSize(80, 24))
            session.activate(manager.status.value.connectedSession(studio.id))
            session.deactivate()
            session.activate(manager.status.value.connectedSession(studio.id))
            val methods = checkNotNull(recorder.latest).sentFrames.map(Frames::method).filter { it in setOf("takeOverPane", "releasePane") }
            assertEquals(listOf("takeOverPane", "releasePane", "takeOverPane"), methods)
        }

    @Test
    fun theDemoTerminalTakesOverAndAnswersTypedCommands() =
        runTest(UnconfinedTestDispatcher()) {
            val tokens = SecretTokenStore(InMemorySecretStore()).apply { setCredential(DemoConnection.credential, DemoConnection.id) }
            val manager = connectionManager(TransportRecorder(), tokens)
            manager.ensureConnected(DemoConnection.connection)
            advanceUntilIdle()
            val project =
                manager
                    .request(DemoConnection.id, Method.LIST_PROJECTS)
                    .decode(ProjectsResult.serializer())
                    .projects
                    .first()
            val workspace =
                manager
                    .request(
                        DemoConnection.id,
                        Method.GET_WORKSPACE,
                        GetWorkspaceParams(project.id.uuidString),
                    ).decode(Workspace.serializer())
            val paneId =
                checkNotNull(
                    WorkspaceFlattening
                        .tabAreas(workspace)
                        .first()
                        .tabs
                        .first()
                        .paneId,
                )
            val session =
                TerminalSession(paneId, ConnectionTerminalChannel(DemoConnection.id, manager), backgroundScope, backgroundScope, {})
            session.source.resize(TerminalGridSize(80, 24))
            session.activate(manager.status.value.connectedSession(DemoConnection.id))
            assertEquals(TerminalOwnership.Owned, session.ownership.value)
            session.controller.sendText("ls")
            session.controller.sendText("\n")
            yield()
            val screen =
                checkNotNull(session.source.frame()).lines.joinToString("\n") { line -> line.spans.joinToString("") { it.text }.trimEnd() }
            assertTrue(screen.contains("Demo Mode - this terminal is simulated."))
            assertTrue(screen.contains(DemoShell.PROMPT.trimEnd() + " ls"))
            assertTrue(screen.contains("[Demo Mode] Commands are not executed in demo mode."))
        }
}
