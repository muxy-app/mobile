package com.muxy.app.features.navigation

import com.muxy.app.networking.muxy1.ConnectionFocus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class ConnectionFocusTest {
    private val studio = UUID.randomUUID()
    private val laptop = UUID.randomUUID()

    @Test
    fun theListFocusesNoMac() {
        assertEquals(ConnectionFocus.None, listOf(AppRoute.Connections).connectionFocus())
        assertEquals(ConnectionFocus.None, emptyList<AppRoute>().connectionFocus())
    }

    @Test
    fun aMacScreenOnTopFocusesThatMac() {
        assertEquals(ConnectionFocus.Device(studio), listOf(AppRoute.Connections, AppRoute.Projects(studio)).connectionFocus())
        assertEquals(
            ConnectionFocus.Device(studio),
            listOf(
                AppRoute.Connections,
                AppRoute.Projects(studio),
                AppRoute.ProjectDetail(studio, UUID.randomUUID(), "Muxy"),
            ).connectionFocus(),
        )
    }

    @Test
    fun theTopMostMacWins() {
        assertEquals(
            ConnectionFocus.Device(laptop),
            listOf(AppRoute.Connections, AppRoute.Projects(studio), AppRoute.Projects(laptop)).connectionFocus(),
        )
    }

    @Test
    fun modalsHoldTheCurrentConnection() {
        assertEquals(
            ConnectionFocus.Hold,
            listOf(AppRoute.Connections, AppRoute.Projects(studio), AppRoute.AddConnection).connectionFocus(),
        )
        assertEquals(ConnectionFocus.Hold, listOf(AppRoute.Connections, AppRoute.Settings).connectionFocus())
    }
}
