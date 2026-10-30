package com.muxy.app.features.terminal.viewport

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

class TerminalViewportState {
    var keyboardOffset: Float = 0f
        private set

    var viewportOffset: Float = 0f
        private set

    private var followsCursor = false

    val needsCursorPlacement: Boolean
        get() = followsCursor && keyboardOffset > 0f

    fun updateKeyboardOffset(offset: Float) {
        val previousKeyboardOffset = keyboardOffset
        val wasAboveKeyboard = previousKeyboardOffset > 0f && abs(viewportOffset - previousKeyboardOffset) <= HALF_PIXEL
        keyboardOffset = maxOf(0f, offset)
        if (keyboardOffset <= 0f) {
            viewportOffset = 0f
            stopFollowingCursor()
            return
        }
        if (previousKeyboardOffset == 0f) followCursor()
        val raisedOffset = if (wasAboveKeyboard && !followsCursor) keyboardOffset else viewportOffset
        viewportOffset = minOf(raisedOffset, keyboardOffset)
    }

    fun placeCursor(
        frame: Rect?,
        viewportHeight: Float,
    ) {
        if (!needsCursorPlacement || frame == null || viewportHeight <= 0f) return
        viewportOffset = minOf(viewportOffset, keyboardOffset)
        if (frame.bottom <= 0f || frame.top >= viewportHeight) return
        viewportOffset = revealingOffset(frame, viewportOffset, viewportHeight - keyboardOffset, keyboardOffset)
    }

    fun followCursor() {
        followsCursor = true
    }

    fun stopFollowingCursor() {
        followsCursor = false
    }

    fun consume(delta: Float): Float {
        if (delta == 0f) return delta
        stopFollowingCursor()
        if (keyboardOffset <= 0f) return delta
        val nextOffset = (viewportOffset + delta).coerceIn(0f, keyboardOffset)
        val consumed = nextOffset - viewportOffset
        if (consumed == 0f) return delta
        viewportOffset = nextOffset
        return delta - consumed
    }

    companion object {
        private const val HALF_PIXEL = 0.5f

        fun revealingOffset(
            frame: Rect,
            current: Float,
            visibleHeight: Float,
            limit: Float,
        ): Float {
            val clearingKeyboard = maxOf(current, frame.bottom - visibleHeight)
            val showingTop = minOf(clearingKeyboard, frame.top)
            return showingTop.coerceIn(0f, limit)
        }
    }
}
