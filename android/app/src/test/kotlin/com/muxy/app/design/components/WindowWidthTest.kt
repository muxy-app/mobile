package com.muxy.app.design.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowWidthTest {
    @Test
    fun phonesAreCompact() {
        assertEquals(WindowWidth.COMPACT, WindowWidth.of(411.dp))
        assertEquals(WindowWidth.COMPACT, WindowWidth.of(599.dp))
        assertFalse(WindowWidth.COMPACT.isWide)
    }

    @Test
    fun portraitTabletsAreMedium() {
        assertEquals(WindowWidth.MEDIUM, WindowWidth.of(600.dp))
        assertEquals(WindowWidth.MEDIUM, WindowWidth.of(800.dp))
        assertTrue(WindowWidth.MEDIUM.isWide)
    }

    @Test
    fun unfoldedPhonesAndLandscapeTabletsAreExpanded() {
        assertEquals(WindowWidth.EXPANDED, WindowWidth.of(840.dp))
        assertEquals(WindowWidth.EXPANDED, WindowWidth.of(1280.dp))
        assertTrue(WindowWidth.EXPANDED.isWide)
    }
}
