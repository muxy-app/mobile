package com.muxy.app.features.navigation

import com.muxy.app.features.server.ServerFocus
import com.muxy.app.networking.muxy1.ConnectionFocus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class ServerFocusTest {
    private val studio = UUID.randomUUID()
    private val laptop = UUID.randomUUID()
    private val studioProjects = AppRoute.ServerProjects(studio, "server-1")
    private val studioProject = AppRoute.ServerProject(studio, "server-1", "muxy")

    @Test
    fun noServerRouteFocusesNoServer() {
        assertEquals(ServerFocus(), listOf(AppRoute.Connections).serverFocus())
        assertEquals(ServerFocus(), listOf(AppRoute.Connections, AppRoute.Projects(studio)).serverFocus())
    }

    @Test
    fun theProjectsListFocusesTheServerWithoutAProject() {
        assertEquals(ServerFocus("server-1"), listOf(AppRoute.Connections, studioProjects).serverFocus())
    }

    @Test
    fun aProjectOnTopIsVisible() {
        assertEquals(ServerFocus("server-1", "muxy"), listOf(AppRoute.Connections, studioProjects, studioProject).serverFocus())
    }

    @Test
    fun aModalOverAProjectKeepsItVisible() {
        assertEquals(
            ServerFocus("server-1", "muxy"),
            listOf(AppRoute.Connections, studioProjects, studioProject, AppRoute.Settings).serverFocus(),
        )
        assertEquals(
            ServerFocus("server-1", "muxy"),
            listOf(AppRoute.Connections, studioProjects, studioProject, AppRoute.AddConnection).serverFocus(),
        )
    }

    @Test
    fun theTopmostServerIsTheActiveOne() {
        val routes = listOf(AppRoute.Connections, studioProjects, studioProject, AppRoute.ServerProjects(laptop, "server-2"))
        assertEquals(ServerFocus("server-2"), routes.serverFocus())
    }

    @Test
    fun serverScreensReleaseAnyMac() {
        assertEquals(ConnectionFocus.None, listOf(AppRoute.Connections, studioProjects).connectionFocus())
        assertEquals(ConnectionFocus.None, listOf(AppRoute.Connections, studioProjects, studioProject).connectionFocus())
    }
}
