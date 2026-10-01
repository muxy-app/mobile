package com.muxy.app.features.navigation

import com.muxy.app.features.projectdetail.ProjectTool
import com.muxy.app.features.projectdetail.ToolProject
import com.muxy.app.features.server.ServerFocus
import com.muxy.app.networking.muxy1.ConnectionFocus
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ProjectToolsRouteTest {
    private val connection = UUID.randomUUID()
    private val project = UUID.randomUUID()

    @Test
    fun everyToolIsAModalThatKeepsItsDeviceConnected() {
        for (tool in ProjectTool.entries) {
            val route = AppRoute.ProjectTools(ToolProject.Device(connection, project, "Muxy"), tool)
            assertTrue(route.isModal)
            assertEquals(ConnectionFocus.Device(connection), listOf(AppRoute.Connections, route).connectionFocus())
        }
    }

    @Test
    fun serverToolsKeepTheUnderlyingProjectVisibleAndDoNotConnectAMac() {
        val project = AppRoute.ServerProject(connection, "server", "muxy")
        for (tool in ProjectTool.entries) {
            val route = AppRoute.ProjectTools(ToolProject.Server(connection, "server", "muxy"), tool)
            val stack = listOf(AppRoute.Connections, project, route)
            assertEquals(ServerFocus("server", "muxy"), stack.serverFocus())
            assertEquals(ConnectionFocus.None, stack.connectionFocus())
        }
    }

    @Test
    fun toolRoutesRoundTripWithoutSerializingLiveModels() {
        val routes: List<AppRoute> =
            listOf(
                AppRoute.ProjectTools(ToolProject.Device(connection, project, "Muxy"), ProjectTool.FILES),
                AppRoute.ProjectTools(ToolProject.Server(connection, "server", "muxy"), ProjectTool.WORKTREES),
            )
        assertEquals(routes, ProtocolJson.decodeFromString<List<AppRoute>>(ProtocolJson.encodeToString(routes)))
    }
}
