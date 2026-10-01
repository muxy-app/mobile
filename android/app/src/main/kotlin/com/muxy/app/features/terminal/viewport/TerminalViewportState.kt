package com.muxy.app.features.terminal.viewport

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

class TerminalViewportState {
    var keyboardOffset: Float = 0f
        private set

    var viewportOffset: Float = 0f
        private set

    var needsCursorPlacement: Boolean = false
        private set

    private var followsCursor = false

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
        needsCursorPlacement = followsCursor
        if (followsCursor) return
        viewportOffset = if (wasAboveKeyboard) keyboardOffset else minOf(viewportOffset, keyboardOffset)
    }

    fun placeCursor(
        frame: Rect?,
        viewportHeight: Float,
    ) {
        if (!needsCursorPlacement || frame == null || viewportHeight <= 0f) return
        needsCursorPlacement = false
        viewportOffset = 0f
        if (frame.bottom <= 0f || frame.top >= viewportHeight) return
        if (frame.bottom <= viewportHeight - keyboardOffset) return
        viewportOffset = minOf(keyboardOffset, maxOf(0f, frame.top))
    }

    fun followCursor() {
        followsCursor = true
        needsCursorPlacement = keyboardOffset > 0f
    }

    fun stopFollowingCursor() {
        followsCursor = false
        needsCursorPlacement = false
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

    private companion object {
        const val HALF_PIXEL = 0.5f
    }
}
