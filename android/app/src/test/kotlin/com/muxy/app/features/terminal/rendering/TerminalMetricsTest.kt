package com.muxy.app.features.terminal.rendering

import androidx.compose.ui.geometry.Rect
import com.muxy.app.features.terminal.TerminalGridSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalMetricsTest {
    private val metrics = TerminalMetrics(21f, 45f, 35f, 3f, 2f, 10f, 2.625f)

    @Test
    fun gridSizeCountsWholeCells() {
        assertEquals(TerminalGridSize(40, 20), metrics.gridSize(21f * 40.6f, 45f * 20.9f))
    }

    @Test
    fun tooSmallAViewportHasNoGrid() {
        assertNull(metrics.gridSize(21f * 9, 500f))
        assertNull(metrics.gridSize(500f, 45f * 2))
    }

    @Test
    fun cellRectsSpanTheirWidth() {
        assertEquals(Rect(63f, 90f, 105f, 135f), metrics.cellRect(row = 2, column = 3, width = 2))
    }

    @Test
    fun anEmptyCellHasNoGrid() {
        assertNull(TerminalMetrics(0f, 45f, 35f, 3f, 2f, 10f, 1f).gridSize(500f, 500f))
    }

    @Test
    fun usableGridsNeedTwentyByFour() {
        assertEquals(false, TerminalGridSize(19, 30).isUsable())
        assertEquals(false, TerminalGridSize(80, 3).isUsable())
        assertEquals(true, TerminalGridSize(20, 4).isUsable())
    }
}
