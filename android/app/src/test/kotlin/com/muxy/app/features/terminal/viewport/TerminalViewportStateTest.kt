package com.muxy.app.features.terminal.viewport

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalViewportStateTest {
    private val bottomCursor = cursorAt(580f)

    private fun cursorAt(y: Float) = Rect(0f, y, 10f, y + 20f)

    private fun raised(keyboard: Float = 300f) = TerminalViewportState().apply { updateKeyboardOffset(keyboard) }

    @Test
    fun aCursorAboveTheKeyboardDoesNotMoveTheViewport() {
        listOf(0f, 100f, 280f).forEach { y ->
            val state = raised()
            state.placeCursor(cursorAt(y), 600f)
            assertEquals(0f, state.viewportOffset)
            assertFalse(state.needsCursorPlacement)
        }
    }

    @Test
    fun aCoveredCursorShiftsTheViewportByTheKeyboardOverlap() {
        val state = raised()
        state.placeCursor(bottomCursor, 600f)
        assertEquals(300f, state.viewportOffset)
    }

    @Test
    fun cursorPlacementNeverPushesTheCursorAboveTheViewport() {
        val state = raised(590f)
        state.placeCursor(cursorAt(5f), 600f)
        assertEquals(5f, state.viewportOffset)
    }

    @Test
    fun anOffscreenCursorDoesNotMoveTheViewport() {
        listOf(-20f, 600f).forEach { y ->
            val state = raised()
            state.placeCursor(cursorAt(y), 600f)
            assertEquals(0f, state.viewportOffset)
        }
    }

    @Test
    fun placementWaitsUntilTheCursorIsAvailable() {
        val state = raised()
        state.placeCursor(null, 600f)
        assertTrue(state.needsCursorPlacement)
        state.placeCursor(bottomCursor, 600f)
        assertEquals(300f, state.viewportOffset)
        assertFalse(state.needsCursorPlacement)
    }

    @Test
    fun hidingTheKeyboardResetsTheViewport() {
        val state = raised()
        state.placeCursor(bottomCursor, 600f)
        state.updateKeyboardOffset(0f)
        assertEquals(0f, state.viewportOffset)
        assertFalse(state.needsCursorPlacement)
    }

    @Test
    fun scrollingMovesTheViewportBeforeForwardingTheRemainder() {
        val state = raised()
        assertEquals(0f, state.consume(200f))
        assertEquals(200f, state.viewportOffset)
        assertEquals(50f, state.consume(150f))
        assertEquals(300f, state.viewportOffset)
        assertEquals(-50f, state.consume(-350f))
        assertEquals(0f, state.viewportOffset)
    }

    @Test
    fun manualScrollingPreventsAutomaticCursorPlacement() {
        val state = raised()
        state.placeCursor(bottomCursor, 600f)
        state.consume(-200f)
        state.placeCursor(bottomCursor, 600f)
        assertEquals(100f, state.viewportOffset)
        assertFalse(state.needsCursorPlacement)
    }

    @Test
    fun followingAgainRestoresCursorPlacementAfterManualScrolling() {
        val state = raised()
        state.placeCursor(bottomCursor, 600f)
        state.consume(-200f)
        state.followCursor()
        state.placeCursor(bottomCursor, 600f)
        assertEquals(300f, state.viewportOffset)
    }

    @Test
    fun reopeningTheKeyboardPlacesTheCursorAgain() {
        val state = raised()
        state.consume(100f)
        state.updateKeyboardOffset(0f)
        state.updateKeyboardOffset(300f)
        assertTrue(state.needsCursorPlacement)
        state.placeCursor(bottomCursor, 600f)
        assertEquals(300f, state.viewportOffset)
    }

    @Test
    fun keyboardHeightChangesPreserveAManualOffset() {
        val state = raised()
        state.consume(100f)
        state.updateKeyboardOffset(350f)
        assertEquals(100f, state.viewportOffset)
        assertFalse(state.needsCursorPlacement)
        state.updateKeyboardOffset(50f)
        assertEquals(50f, state.viewportOffset)
    }

    @Test
    fun aFullyRaisedViewportTracksKeyboardHeightChanges() {
        val state = raised()
        state.consume(300f)
        state.updateKeyboardOffset(350f)
        assertEquals(350f, state.viewportOffset)
    }

    @Test
    fun scrollingWithoutAKeyboardIsForwardedUnchanged() {
        val state = TerminalViewportState()
        assertEquals(100f, state.consume(100f))
        assertEquals(-100f, state.consume(-100f))
        assertEquals(0f, state.viewportOffset)
    }
}
