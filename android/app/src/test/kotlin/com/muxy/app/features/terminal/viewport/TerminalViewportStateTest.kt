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

    @Test
    fun aPartlyCoveredCursorShiftsOnlyEnoughToClearTheKeyboard() {
        val state = raised()
        state.placeCursor(cursorAt(320f), 600f)
        assertEquals(40f, state.viewportOffset)
    }

    @Test
    fun aVisibleCursorKeepsTheCurrentOffset() {
        val state = raised()
        state.placeCursor(cursorAt(320f), 600f)
        state.placeCursor(cursorAt(200f), 600f)
        assertEquals(40f, state.viewportOffset)
    }

    @Test
    fun aCursorAboveTheVisibleAreaScrollsDownJustEnough() {
        val state = raised()
        state.placeCursor(bottomCursor, 600f)
        state.placeCursor(cursorAt(100f), 600f)
        assertEquals(100f, state.viewportOffset)
    }

    @Test
    fun steppingTheCursorPastTheKeyboardMovesOneRowAtATime() {
        val state = raised()
        val offsets =
            (14..17).map { row ->
                state.placeCursor(cursorAt(row * 20f), 600f)
                state.viewportOffset
            }
        assertEquals(listOf(0f, 20f, 40f, 60f), offsets)
        state.placeCursor(cursorAt(280f), 600f)
        assertEquals(60f, state.viewportOffset)
    }

    @Test
    fun followingKeepsTheCursorVisibleAcrossScreenUpdates() {
        val state = raised()
        state.placeCursor(cursorAt(320f), 600f)
        assertTrue(state.needsCursorPlacement)
        state.placeCursor(bottomCursor, 600f)
        assertEquals(300f, state.viewportOffset)
    }

    @Test
    fun aShrinkingKeyboardClampsTheOffsetWhileFollowing() {
        val state = raised()
        state.placeCursor(bottomCursor, 600f)
        state.updateKeyboardOffset(100f)
        assertEquals(100f, state.viewportOffset)
    }

    @Test
    fun openingTheKeyboardPreservesBottomBelowTheCursor() {
        listOf(8f, 20f, 60f).forEach { gap ->
            val state = TerminalViewportState()
            state.updateKeyboardOffset(300f, isAtBottom = true)
            state.placeCursor(cursorAt(580f - gap), 600f)
            assertEquals(300f, state.viewportOffset)
        }
    }

    @Test
    fun followingAtBottomTracksKeyboardHeightChanges() {
        val state = TerminalViewportState()
        listOf(100f, 240f, 300f, 310f, 320f, 350f, 280f).forEach { height ->
            state.updateKeyboardOffset(height, isAtBottom = true)
            state.placeCursor(cursorAt(560f), 600f)
            assertEquals(height, state.viewportOffset)
        }
    }

    @Test
    fun keyboardHeightChangesPreserveAManuallyDetachedViewportAtLiveBottom() {
        val state = TerminalViewportState()
        state.updateKeyboardOffset(300f, isAtBottom = true)
        state.placeCursor(bottomCursor, 600f)
        state.consume(-80f)
        state.updateKeyboardOffset(350f, isAtBottom = true)
        state.placeCursor(bottomCursor, 600f)
        assertEquals(220f, state.viewportOffset)
        assertFalse(state.needsCursorPlacement)
    }

    @Test
    fun reopeningTheKeyboardRestoresBottomAnchoring() {
        val state = TerminalViewportState()
        listOf(300f, 0f, 300f).forEach { height ->
            state.updateKeyboardOffset(height, isAtBottom = true)
            state.placeCursor(cursorAt(560f), 600f)
            assertEquals(height, state.viewportOffset)
        }
    }

    @Test
    fun aCursorMovingAboveTheRaisedViewportRemainsVisible() {
        val state = TerminalViewportState()
        state.updateKeyboardOffset(300f, isAtBottom = true)
        state.placeCursor(bottomCursor, 600f)
        state.placeCursor(cursorAt(100f), 600f)
        assertEquals(100f, state.viewportOffset)
    }

    @Test
    fun openingTheKeyboardAtBottomDoesNotMoveAnAlreadyVisibleCursor() {
        listOf(0f, 100f, 280f).forEach { y ->
            val state = TerminalViewportState()
            state.updateKeyboardOffset(300f, isAtBottom = true)
            state.placeCursor(cursorAt(y), 600f)
            assertEquals(0f, state.viewportOffset)
        }
    }

    @Test
    fun animatedKeyboardOpeningPreservesBottomOnceTheCursorIsCovered() {
        val state = TerminalViewportState()
        listOf(5f, 10f, 20f, 30f, 100f, 300f, 310f, 320f).forEach { height ->
            state.updateKeyboardOffset(height, isAtBottom = true)
            state.placeCursor(cursorAt(560f), 600f)
            assertEquals(if (height <= 20f) 0f else height, state.viewportOffset)
        }
    }

    @Test
    fun cursorMovementAfterKeyboardOpeningOnlyScrollsEnoughToStayVisible() {
        val state = TerminalViewportState()
        state.updateKeyboardOffset(300f, isAtBottom = true)
        state.placeCursor(cursorAt(100f), 600f)
        state.placeCursor(cursorAt(320f), 600f)
        assertEquals(40f, state.viewportOffset)
        state.placeCursor(cursorAt(340f), 600f)
        assertEquals(60f, state.viewportOffset)
    }
}
