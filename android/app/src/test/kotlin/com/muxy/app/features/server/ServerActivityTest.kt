package com.muxy.app.features.server

import com.muxy.app.models.AgentProvider
import com.muxy.app.models.TabActivity
import com.muxy.app.testing.FakeServerConnection
import com.muxy.app.testing.FakeServerConnector
import com.muxy.app.testing.serverCredential
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.muxy_mobile.Activity
import uniffi.muxy_mobile.ActivityEvent
import uniffi.muxy_mobile.ActivityKind
import uniffi.muxy_mobile.Agent
import uniffi.muxy_mobile.AgentState
import uniffi.muxy_mobile.ConnectionEvent

class ServerActivityTest {
    private fun TestScope.connected(connection: FakeServerConnection): Pair<ServerController, FakeServerConnector> {
        val connector = FakeServerConnector(listOf(Result.success(connection)))
        val controller = ServerController("server-1", connector, { serverCredential() }, backgroundScope)
        controller.setWantsConnection(true)
        runCurrent()
        return controller to connector
    }

    @Test
    fun activityLoadsPerSessionAndAcknowledgesItsEvents() =
        runTest {
            val connection = FakeServerConnection()
            connection.activity =
                Activity(
                    agents = listOf(Agent(sessionId = 1u, projectId = "muxy", provider = "Claude Code", state = AgentState.BLOCKED)),
                    events =
                        listOf(
                            ActivityEvent(
                                id = 7u,
                                sessionId = 1u,
                                projectId = "muxy",
                                provider = "Claude Code",
                                timestamp = 100u,
                                kind = ActivityKind.ATTENTION,
                            ),
                        ),
                )
            val (controller, _) = connected(connection)

            val waiting = controller.activity.value[1u]
            assertEquals(TabActivity.State.WAITING, waiting?.state)
            assertEquals(AgentProvider.CLAUDE, waiting?.agentProvider)
            assertNull(controller.activity.value[2u])

            controller.acknowledgeActivity(1u)
            runCurrent()

            assertEquals(listOf(7uL), connection.acknowledged)
            assertEquals(TabActivity.State.IDLE, controller.activity.value[1u]?.state)
        }

    @Test
    fun activityChangesAreReloaded() =
        runTest {
            val connection = FakeServerConnection()
            val (controller, connector) = connected(connection)

            connection.activity =
                Activity(
                    agents = listOf(Agent(sessionId = 3u, projectId = "muxy", provider = "Codex", state = AgentState.WORKING)),
                    events = emptyList(),
                )
            connector.emit(ConnectionEvent.ActivityChanged)
            runCurrent()

            assertEquals(TabActivity.State.WORKING, controller.activity.value[3u]?.state)
            assertEquals("Codex", controller.activity.value[3u]?.providerName)
        }
}
