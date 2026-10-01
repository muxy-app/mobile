package com.muxy.app.features.terminal.rendering

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

object TerminalScrollMath {
    fun offset(
        revealing: Rect,
        visibleSize: Size,
        contentSize: Size,
        current: Offset,
    ): Offset {
        val x =
            when {
                revealing.left < current.x -> revealing.left
                revealing.right > current.x + visibleSize.width -> revealing.right - visibleSize.width
                else -> current.x
            }
        val y =
            when {
                revealing.top < current.y -> revealing.top
                revealing.bottom > current.y + visibleSize.height -> revealing.bottom - visibleSize.height
                else -> current.y
            }
        return clamped(Offset(x, y), visibleSize, contentSize)
    }

    fun bottomOffset(
        visibleSize: Size,
        contentSize: Size,
        current: Offset,
    ): Offset = clamped(Offset(current.x, contentSize.height - visibleSize.height), visibleSize, contentSize)

    fun isAtBottom(
        offset: Offset,
        visibleSize: Size,
        contentSize: Size,
        tolerance: Float,
    ): Boolean = offset.y >= maxOf(0f, contentSize.height - visibleSize.height) - tolerance

    fun isVisible(
        rect: Rect,
        offset: Offset,
        visibleSize: Size,
        tolerance: Size,
    ): Boolean {
        val visible =
            Rect(
                left = offset.x - tolerance.width,
                top = offset.y - tolerance.height,
                right = offset.x + visibleSize.width + tolerance.width,
                bottom = offset.y + visibleSize.height + tolerance.height,
            )
        return rect.left >= visible.left && rect.top >= visible.top && rect.right <= visible.right && rect.bottom <= visible.bottom
    }

    fun clamped(
        point: Offset,
        visibleSize: Size,
        contentSize: Size,
    ): Offset {
        val maxX = maxOf(0f, contentSize.width - visibleSize.width)
        val maxY = maxOf(0f, contentSize.height - visibleSize.height)
        return Offset(point.x.coerceIn(0f, maxX), point.y.coerceIn(0f, maxY))
    }
}
