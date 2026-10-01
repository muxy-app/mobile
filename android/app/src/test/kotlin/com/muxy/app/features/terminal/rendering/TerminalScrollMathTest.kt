package com.muxy.app.features.terminal.rendering

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalScrollMathTest {
    private val visible = Size(100f, 100f)
    private val content = Size(400f, 300f)

    private fun rect(
        x: Float,
        y: Float,
        width: Float = 8f,
        height: Float = 16f,
    ) = Rect(x, y, x + width, y + height)

    @Test
    fun aVisibleRectDoesNotScroll() {
        assertEquals(Offset.Zero, TerminalScrollMath.offset(rect(10f, 10f), visible, content, Offset.Zero))
    }

    @Test
    fun aRectBelowScrollsJustEnough() {
        assertEquals(Offset(0f, 66f), TerminalScrollMath.offset(rect(0f, 150f), visible, content, Offset.Zero))
    }

    @Test
    fun aRectToTheRightScrollsSideways() {
        assertEquals(Offset(158f, 0f), TerminalScrollMath.offset(rect(250f, 0f), visible, content, Offset.Zero))
    }

    @Test
    fun aRectAboveScrollsBackUp() {
        assertEquals(Offset(0f, 20f), TerminalScrollMath.offset(rect(0f, 20f), visible, content, Offset(0f, 120f)))
    }

    @Test
    fun contentSmallerThanTheViewportNeverScrolls() {
        assertEquals(Offset.Zero, TerminalScrollMath.offset(rect(40f, 40f), visible, Size(50f, 50f), Offset.Zero))
    }

    @Test
    fun bottomOffsetKeepsTheHorizontalPosition() {
        assertEquals(Offset(30f, 200f), TerminalScrollMath.bottomOffset(visible, content, Offset(30f, 0f)))
    }

    @Test
    fun visibilityAllowsATolerance() {
        val partial = rect(0f, 95f, height = 10f)
        assertFalse(TerminalScrollMath.isVisible(partial, Offset.Zero, visible, Size.Zero))
        assertTrue(TerminalScrollMath.isVisible(partial, Offset.Zero, visible, Size(4f, 8f)))
    }

    @Test
    fun theLastRowCountsAsVisibleDespiteRounding() {
        val cellHeight = 47f / 3f
        val rows = 22f
        val tall = Size(300f, rows * cellHeight)
        val viewport = Size(300f, cellHeight * 10)
        val bottom = TerminalScrollMath.bottomOffset(viewport, tall, Offset.Zero)
        val lastRow = rect(0f, (rows - 1) * cellHeight, height = cellHeight)
        assertTrue(TerminalScrollMath.isVisible(lastRow, bottom, viewport, Size(4f, cellHeight / 2)))
    }

    @Test
    fun atBottomAllowsATolerance() {
        assertTrue(TerminalScrollMath.isAtBottom(Offset(0f, 195f), visible, content, 8f))
        assertFalse(TerminalScrollMath.isAtBottom(Offset(0f, 150f), visible, content, 8f))
    }
}
