@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.muxy.app.features.navigation

import com.muxy.app.features.addconnection.AddConnectionInbox
import com.muxy.app.features.addconnection.AddConnectionRequest
import com.muxy.app.features.projectdetail.ProjectTool
import com.muxy.app.features.projectdetail.ToolProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PairingNavigationTest {
    private val connectionId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val projects = AppRoute.Projects(connectionId)
    private val detail = AppRoute.ProjectDetail(connectionId, projectId, "Muxy")
    private val base = listOf(AppRoute.Connections, projects, detail)
    private val inbox = AddConnectionInbox()

    private fun tools(tool: ProjectTool = ProjectTool.FILES) =
        AppRoute.ProjectTools(ToolProject.Device(connectionId, projectId, "Muxy"), tool)

    @Test
    fun repairingTheSameMacWaitsUntilItsToolClosesThroughTheGuardedExit() =
        runTest {
            for (tool in ProjectTool.entries) {
                val stack = MutableStateFlow<List<AppRoute>>(base + tools(tool))
                val delivered = mutableListOf<AddConnectionRequest>()
                val collector =
                    backgroundScope.launch {
                        pendingPairingRequests(inbox.request, stack).collect {
                            delivered += it
                            stack.value = stack.value.opening(AppRoute.AddConnection)
                        }
                    }
                val request = AddConnectionRequest.Repair(connectionId)
                inbox.deliver(request)
                runCurrent()
                assertTrue(delivered.isEmpty())
                assertEquals(request, inbox.request.value)
                assertEquals(base + tools(tool), stack.value)
                stack.value = base
                runCurrent()
                assertEquals(listOf(request), delivered)
                assertEquals(base + AppRoute.AddConnection, stack.value)
                assertEquals(request, inbox.take())
                stack.value = stack.value.dropLast(1).opening(projects)
                runCurrent()
                assertEquals(listOf(AppRoute.Connections, projects), stack.value)
                assertEquals(1, delivered.size)
                collector.cancel()
            }
        }

    @Test
    fun aToolAnywhereInTheStackDefersPairingForBothBackends() =
        runTest {
            val routes =
                listOf(
                    tools(),
                    AppRoute.ProjectTools(ToolProject.Server(connectionId, "server", "project"), ProjectTool.GIT),
                )
            for (tool in routes) {
                val stack = MutableStateFlow<List<AppRoute>>(base + tool + AppRoute.Settings)
                val delivered = mutableListOf<AddConnectionRequest>()
                val collector = backgroundScope.launch { pendingPairingRequests(inbox.request, stack).collect { delivered += it } }
                inbox.deliver(AddConnectionRequest.PairingCode("pending-code"))
                runCurrent()
                assertTrue(delivered.isEmpty())
                stack.value = base + tool
                runCurrent()
                assertTrue(delivered.isEmpty())
                stack.value = base
                runCurrent()
                assertEquals(1, delivered.size)
                inbox.take()
                collector.cancel()
            }
        }

    @Test
    fun onlyTheLatestQueuedPairingRequestIsDeliveredAfterToolsClose() =
        runTest {
            val stack = MutableStateFlow<List<AppRoute>>(base + tools())
            val delivered = mutableListOf<AddConnectionRequest>()
            backgroundScope.launch { pendingPairingRequests(inbox.request, stack).collect { delivered += it } }
            inbox.deliver(AddConnectionRequest.PairingCode("first"))
            runCurrent()
            val latest = AddConnectionRequest.PairingCode("latest")
            inbox.deliver(latest)
            runCurrent()
            assertTrue(delivered.isEmpty())
            stack.value = base
            runCurrent()
            assertEquals(listOf(latest), delivered)
            assertEquals(latest, inbox.take())
        }

    @Test
    fun consumedPairingRequestsAreNotReplayedAfterNavigationOrCollectorRecreation() =
        runTest {
            val stack = MutableStateFlow<List<AppRoute>>(base)
            val delivered = mutableListOf<AddConnectionRequest>()
            val collector =
                backgroundScope.launch {
                    pendingPairingRequests(inbox.request, stack).collect {
                        delivered += it
                        inbox.take()
                    }
                }
            inbox.deliver(AddConnectionRequest.PairingCode("code"))
            runCurrent()
            assertEquals(1, delivered.size)
            assertNull(inbox.request.value)
            stack.value = base + AppRoute.AddConnection
            runCurrent()
            collector.cancel()
            backgroundScope.launch { pendingPairingRequests(inbox.request, stack).collect { delivered += it } }
            stack.value = base
            runCurrent()
            assertEquals(1, delivered.size)
        }
}
