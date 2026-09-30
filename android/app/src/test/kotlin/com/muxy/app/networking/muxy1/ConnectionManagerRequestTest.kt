package com.muxy.app.networking.muxy1

import app.cash.turbine.test
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.models.Project
import com.muxy.app.networking.muxy1.protocol.CreateTabParams
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProjectsResult
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.persistence.secrets.SecretTokenStore
import com.muxy.app.testing.Frames
import com.muxy.app.testing.InMemorySecretStore
import com.muxy.app.testing.TransportRecorder
import com.muxy.app.testing.connectionManager
import com.muxy.app.testing.device
import com.muxy.app.testing.tokenStoreWith
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionManagerRequestTest {
    private val recorder =
        TransportRecorder { _, frame ->
            when (Frames.method(frame)) {
                "authenticateDevice" -> {
                    listOf(Frames.pairing(Frames.id(frame)))
                }

                "listProjects" -> {
                    listOf(
                        Frames.result(
                            Frames.id(frame),
                            ResultType.PROJECTS,
                            """{ "projects": [ ${Frames.project("11111111-1111-1111-1111-111111111111", "muxy")} ] }""",
                        ),
                    )
                }

                else -> {
                    emptyList()
                }
            }
        }

    @Test
    fun forwardsARequestAndDecodesTheResult() =
        runTest {
            val studio = device()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            manager.ensureConnected(studio)
            val result = manager.request(studio.id, Method.LIST_PROJECTS)
            assertEquals(ResultType.PROJECTS, result.type)
            assertEquals(listOf("muxy"), result.decode(ProjectsResult.serializer()).projects.map(Project::name))
        }

    @Test
    fun requestThrowsWhenNotConnected() =
        runTest {
            val manager = connectionManager(recorder, tokenStoreWith())
            val error = runCatching { manager.request(device().id, Method.LIST_PROJECTS) }.exceptionOrNull()
            assertEquals(ConnectionError.NOT_CONNECTED, (error as ConnectionException).error)
        }

    @Test
    fun requestForAnotherMacThrowsNotConnected() =
        runTest {
            val studio = device()
            val laptop = device(name = "Laptop", host = "laptop.local")
            val manager = connectionManager(recorder, tokenStoreWith(studio, laptop))
            manager.ensureConnected(laptop)
            val error = runCatching { manager.request(studio.id, Method.LIST_PROJECTS) }.exceptionOrNull()
            assertEquals(ConnectionError.NOT_CONNECTED, (error as ConnectionException).error)
            assertEquals(1, recorder.latest!!.sentFrames.size)
        }

    @Test
    fun theDemoConnectionIsServedWithoutASocket() =
        runTest {
            val tokens = SecretTokenStore(InMemorySecretStore()).apply { setCredential(DemoConnection.credential, DemoConnection.id) }
            val demoRecorder = TransportRecorder()
            val manager = connectionManager(demoRecorder, tokens)
            manager.ensureConnected(DemoConnection.connection)
            assertEquals(ConnectionState.Connected, manager.status.value.of(DemoConnection.id))
            val projects = manager.request(DemoConnection.id, Method.LIST_PROJECTS).decode(ProjectsResult.serializer()).projects
            assertEquals(listOf("Muxy", "Web App"), projects.map(Project::name))
            assertTrue(demoRecorder.transports.isEmpty())
        }

    @Test
    fun demoRequestsEmitTheirEvents() =
        runTest {
            val tokens = SecretTokenStore(InMemorySecretStore()).apply { setCredential(DemoConnection.credential, DemoConnection.id) }
            val manager = connectionManager(TransportRecorder(), tokens)
            manager.ensureConnected(DemoConnection.connection)
            val project =
                manager
                    .request(DemoConnection.id, Method.LIST_PROJECTS)
                    .decode(ProjectsResult.serializer())
                    .projects
                    .first()
            manager.events(DemoConnection.id).test {
                manager.request(DemoConnection.id, Method.CREATE_TAB, CreateTabParams(project.id.uuidString, null, "terminal"))
                assertEquals(EventName.WORKSPACE_CHANGED, awaitItem().event)
            }
        }
}
