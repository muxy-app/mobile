package com.muxy.app.features.terminalkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DPadDirectionTest {
    private val deadZone = 10f

    @Test
    fun movementInsideTheDeadZoneIsIgnored() {
        assertNull(DPadDirection.of(0f, 0f, deadZone))
        assertNull(DPadDirection.of(6f, 8f, deadZone))
    }

    @Test
    fun theDominantAxisPicksTheDirection() {
        assertEquals(DPadDirection.RIGHT, DPadDirection.of(20f, 5f, deadZone))
        assertEquals(DPadDirection.LEFT, DPadDirection.of(-20f, 19f, deadZone))
        assertEquals(DPadDirection.DOWN, DPadDirection.of(5f, 20f, deadZone))
        assertEquals(DPadDirection.UP, DPadDirection.of(-5f, -20f, deadZone))
    }

    @Test
    fun aDiagonalPrefersTheVerticalAxis() {
        assertEquals(DPadDirection.DOWN, DPadDirection.of(15f, 15f, deadZone))
    }

    @Test
    fun directionsSendArrowKeys() {
        assertEquals(
            listOf(TerminalKey.Up, TerminalKey.Down, TerminalKey.Left, TerminalKey.Right),
            DPadDirection.entries.map { it.key },
        )
    }
}
