package com.muxy.app.features.navigation

import androidx.navigation3.runtime.NavBackStack
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
