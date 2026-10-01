package com.muxy.app.features.terminal.rendering

import android.graphics.Paint
import androidx.compose.ui.geometry.Rect
import com.muxy.app.features.terminal.TerminalGridSize
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class TerminalMetrics(
    val cellWidth: Float,
    val cellHeight: Float,
    val baseline: Float,
    val underlineOffset: Float,
    val lineThickness: Float,
    val strikeOffset: Float,
    val density: Float,
) {
    fun gridSize(
        width: Float,
        height: Float,
    ): TerminalGridSize? {
        if (cellWidth <= 0f || cellHeight <= 0f) return null
        val columns = (width / cellWidth).toInt()
        val rows = (height / cellHeight).toInt()
        if (columns < MINIMUM_COLUMNS || rows < MINIMUM_ROWS) return null
        return TerminalGridSize(columns, rows)
    }

    fun cellRect(
        row: Int,
        column: Int,
        width: Int = 1,
    ): Rect {
        val left = column * cellWidth
        val top = row * cellHeight
        return Rect(left, top, left + width * cellWidth, top + cellHeight)
    }

    companion object {
        const val MINIMUM_COLUMNS = 10
        const val MINIMUM_ROWS = 3

        fun measure(
            paint: Paint,
            density: Float,
        ): TerminalMetrics {
            val fontMetrics = paint.fontMetrics
            val ascent = -fontMetrics.ascent
            val descent = fontMetrics.descent
            val cellHeight = ceil(ascent + descent)
            return TerminalMetrics(
                cellWidth =
                    paint
                        .measureText("M")
                        .roundToInt()
                        .coerceAtLeast(1)
                        .toFloat(),
                cellHeight = cellHeight,
                baseline = (ascent + (cellHeight - ascent - descent) / 2).roundToInt().toFloat(),
                underlineOffset = max(1f, paint.underlinePosition),
                lineThickness = max(1f, paint.underlineThickness),
                strikeOffset = -paint.strikeThruPosition,
                density = density,
            )
        }
    }
}
