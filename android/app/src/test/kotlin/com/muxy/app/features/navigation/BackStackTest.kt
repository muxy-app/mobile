package com.muxy.app.features.navigation

import androidx.navigation3.runtime.NavBackStack
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class BackStackTest {
    @Test
    fun openingARouteThatIsNotInTheStackAppendsIt() {
        assertEquals(
            listOf(AppRoute.Connections, AppRoute.Settings),
            listOf<AppRoute>(AppRoute.Connections).opening(AppRoute.Settings),
        )
    }

    @Test
    fun openingARouteInTheStackCutsBackToIt() {
        assertEquals(listOf("connections", "projects"), listOf("connections", "projects", "detail").opening("projects"))
    }

    @Test
    fun openingCutsBackToTheLastOccurrence() {
        assertEquals(listOf("a", "b", "a"), listOf("a", "b", "a", "c").opening("a"))
    }

    @Test
    fun openingTheTopRouteKeepsTheStack() {
        assertEquals(listOf("a", "b"), listOf("a", "b").opening("b"))
    }

    @Test
    fun openAppliesOpeningToTheBackStack() {
        val backStack = NavBackStack<AppRoute>(AppRoute.Connections)
        backStack.open(AppRoute.Settings)
        assertEquals(listOf(AppRoute.Connections, AppRoute.Settings), backStack.toList())
        backStack.open(AppRoute.Settings)
        assertEquals(listOf(AppRoute.Connections, AppRoute.Settings), backStack.toList())
        backStack.open(AppRoute.Connections)
        assertEquals(listOf<AppRoute>(AppRoute.Connections), backStack.toList())
    }

    @Test
    fun closeRemovesTheRouteOnlyWhileItIsOnTop() {
        val backStack = NavBackStack(AppRoute.Connections, AppRoute.Settings)
        backStack.close(AppRoute.Settings)
        assertEquals(listOf<AppRoute>(AppRoute.Connections), backStack.toList())
        backStack.close(AppRoute.Settings)
        assertEquals(listOf<AppRoute>(AppRoute.Connections), backStack.toList())
    }

    @Test
    fun closeIgnoresARouteBelowTheTop() {
        val backStack = NavBackStack(AppRoute.Connections, AppRoute.Settings)
        backStack.close(AppRoute.Connections)
        assertEquals(listOf(AppRoute.Connections, AppRoute.Settings), backStack.toList())
    }

    @Test
    fun openingByIdentityReplacesTheMatchingRoute() {
        assertEquals(listOf("a", "B"), listOf("a", "b", "c").opening("B") { it.lowercase() })
    }

    @Test
    fun replaceWithKeepsMatchingRoutesAndSwapsTheRest() {
        val backStack = NavBackStack(AppRoute.Connections, AppRoute.Settings, AppRoute.Paywall)
        backStack.replaceWith(listOf(AppRoute.Connections, AppRoute.AddConnection))
        assertEquals(listOf(AppRoute.Connections, AppRoute.AddConnection), backStack.toList())
        backStack.replaceWith(listOf(AppRoute.Connections, AppRoute.AddConnection, AppRoute.Settings))
        assertEquals(listOf(AppRoute.Connections, AppRoute.AddConnection, AppRoute.Settings), backStack.toList())
    }

    @Test
    fun destinationIgnoresTheRequestedTab() {
        val connection = UUID.randomUUID()
        val project = UUID.randomUUID()
        assertEquals(
            AppRoute.ProjectDetail(connection, project, "Muxy"),
            AppRoute.ProjectDetail(connection, project, "Muxy", UUID.randomUUID()).destination,
        )
        assertEquals(
            AppRoute.ServerProject(connection, "server", "muxy"),
            AppRoute.ServerProject(connection, "server", "muxy", UUID.randomUUID()).destination,
        )
        assertEquals(AppRoute.Projects(connection), AppRoute.Projects(connection).destination)
    }

    @Test
    fun openingTheSameProjectWithAnotherTabReplacesItInsteadOfStackingIt() {
        val connection = UUID.randomUUID()
        val first = AppRoute.ServerProject(connection, "server", "muxy", UUID.randomUUID())
        val second = first.copy(initialTabId = UUID.randomUUID())
        val backStack = NavBackStack(AppRoute.Connections, AppRoute.ServerProjects(connection, "server"), first)
        backStack.open(second)
        assertEquals(listOf(AppRoute.Connections, AppRoute.ServerProjects(connection, "server"), second), backStack.toList())
    }

    @Test
    fun showInPaneReplacesEverythingAboveHome() {
        val connection = UUID.randomUUID()
        val pane = AppRoute.SshTerminal(connection)
        val detail = AppRoute.ProjectDetail(connection, UUID.randomUUID(), "Muxy")
        val backStack = NavBackStack(AppRoute.Connections, AppRoute.Projects(connection), detail)
        backStack.showInPane(pane)
        assertEquals(listOf(AppRoute.Connections, pane), backStack.toList())
    }
}
