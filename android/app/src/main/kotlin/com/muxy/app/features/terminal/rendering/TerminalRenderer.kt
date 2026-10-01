package com.muxy.app.features.terminal.rendering

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathEffect
import androidx.compose.ui.geometry.Rect
import androidx.core.graphics.withClip
import com.muxy.app.features.terminal.CursorShape
import com.muxy.app.features.terminal.TerminalCursor
import com.muxy.app.features.terminal.TerminalLine
import com.muxy.app.features.terminal.TerminalSpan
import com.muxy.app.features.terminal.TerminalStyle
import com.muxy.app.features.terminal.UnderlineStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class TerminalRenderer(
    val metrics: TerminalMetrics,
    private val paints: TerminalPaints,
    val resolver: TerminalColorResolver,
) {
    private val fill = Paint()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val curl = Path()
    private val dotted: PathEffect = DashPathEffect(floatArrayOf(metrics.lineThickness, metrics.lineThickness * 2), 0f)
    private val dashed: PathEffect = DashPathEffect(floatArrayOf(metrics.lineThickness * 4, metrics.lineThickness * 2), 0f)

    val backgroundColor: Int = opaque(resolver.theme.background)

    fun rows(
        top: Float,
        bottom: Float,
        rowCount: Int,
    ): IntRange {
        if (rowCount <= 0 || metrics.cellHeight <= 0f) return IntRange.EMPTY
        val first = max(0, floor(top / metrics.cellHeight).toInt())
        val last = min(rowCount, ceil(bottom / metrics.cellHeight).toInt())
        return if (first < last) first until last else IntRange.EMPTY
    }

    fun drawBackgrounds(
        canvas: Canvas,
        line: TerminalLine,
        row: Int,
    ) {
        forEachSpan(line, row) { span, rect ->
            val style = resolver.resolve(span.style)
            if (style.background == resolver.theme.background) return@forEachSpan
            fill.color = opaque(style.background)
            canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fill)
        }
    }

    fun drawSelection(
        canvas: Canvas,
        columns: IntRange,
        row: Int,
    ) {
        val rect = metrics.cellRect(row, columns.first, columns.last - columns.first + 1)
        fill.color = (SELECTION_ALPHA shl 24) or resolver.theme.selectionBackground
        canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fill)
    }

    fun drawText(
        canvas: Canvas,
        line: TerminalLine,
        row: Int,
    ) {
        forEachSpan(line, row) { span, rect ->
            val style = resolver.resolve(span.style)
            if (!style.drawsText) return@forEachSpan
            if (!isBlank(span.text)) draw(canvas, span, rect, opaque(style.foreground))
            drawDecorations(canvas, span.style, rect, opaque(style.decoration))
        }
    }

    fun drawCursor(
        canvas: Canvas,
        cursor: TerminalCursor,
        line: TerminalLine?,
    ) {
        val cell = line?.let { TerminalLineCells.cell(cursor.column, it) }
        val rect = metrics.cellRect(cursor.row, cursor.column, cell?.width ?: 1)
        val cursorColor = opaque(resolver.theme.cursor)
        fill.color = cursorColor
        when (cursor.shape) {
            CursorShape.BLOCK -> {
                canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fill)
                if (cell == null || isBlank(cell.text)) return
                drawCluster(canvas, cell.text, rect, cell.style, opaque(resolver.theme.cursorText))
            }

            CursorShape.BAR -> {
                val width = max(CURSOR_BAR_DP * metrics.density, metrics.cellWidth * CURSOR_BAR_SHARE)
                canvas.drawRect(rect.left, rect.top, rect.left + width, rect.bottom, fill)
            }

            CursorShape.UNDERLINE -> {
                canvas.drawRect(rect.left, rect.bottom - CURSOR_UNDERLINE_DP * metrics.density, rect.right, rect.bottom, fill)
            }

            CursorShape.HOLLOW -> {
                val inset = metrics.density / 2
                stroke.color = cursorColor
                stroke.strokeWidth = metrics.density
                stroke.pathEffect = null
                canvas.drawRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, stroke)
            }
        }
    }

    fun drawMarkedText(
        canvas: Canvas,
        text: String,
        cursor: TerminalCursor,
    ) {
        val paint = paints.cluster(TerminalStyle.PLAIN, opaque(resolver.theme.foreground))
        val cell = metrics.cellRect(cursor.row, cursor.column)
        val width = max(metrics.cellWidth, paint.measureText(text))
        fill.color = backgroundColor
        canvas.drawRect(cell.left, cell.top, cell.left + width, cell.bottom, fill)
        canvas.drawText(text, cell.left, cell.top + metrics.baseline, paint)
        strokeLine(canvas, cell.top + metrics.baseline + metrics.underlineOffset, cell.left, cell.left + width, paint.color, null)
    }

    private inline fun forEachSpan(
        line: TerminalLine,
        row: Int,
        body: (TerminalSpan, Rect) -> Unit,
    ) {
        var column = 0
        for (span in line.spans) {
            val width = span.width
            val rect = metrics.cellRect(row, column, width)
            column += width
            if (width > 0) body(span, rect)
        }
    }

    private fun draw(
        canvas: Canvas,
        span: TerminalSpan,
        rect: Rect,
        color: Int,
    ) {
        when (span) {
            is TerminalSpan.Run -> drawRun(canvas, span.text, rect, span.style, color)
            is TerminalSpan.Cluster -> drawCluster(canvas, span.text, rect, span.style, color)
        }
    }

    private fun drawRun(
        canvas: Canvas,
        text: String,
        rect: Rect,
        style: TerminalStyle,
        color: Int,
    ) {
        val paint = paints.run(style, color)
        if (isAscii(text) || abs(paint.measureText(text) - rect.width) < MISMATCH_TOLERANCE) {
            canvas.drawText(text, rect.left, rect.top + metrics.baseline, paint)
            return
        }
        text.forEachIndexed { index, character ->
            val left = rect.left + index * metrics.cellWidth
            drawCluster(canvas, character.toString(), Rect(left, rect.top, left + metrics.cellWidth, rect.bottom), style, color)
        }
    }

    private fun drawCluster(
        canvas: Canvas,
        text: String,
        rect: Rect,
        style: TerminalStyle,
        color: Int,
    ) {
        val paint = paints.cluster(style, color)
        val width = paint.measureText(text)
        val scale = if (width > rect.width && width > 0f) rect.width / width else 1f
        canvas.withClip(rect.left, rect.top, rect.right, rect.bottom) {
            translate(rect.left + (rect.width - width * scale) / 2, rect.top + metrics.baseline)
            scale(scale, scale)
            drawText(text, 0f, 0f, paint)
        }
    }

    private fun drawDecorations(
        canvas: Canvas,
        style: TerminalStyle,
        rect: Rect,
        color: Int,
    ) {
        val underlineY = rect.top + metrics.baseline + metrics.underlineOffset
        val thickness = metrics.lineThickness
        when (style.underline) {
            UnderlineStyle.NONE -> {
                Unit
            }

            UnderlineStyle.SINGLE -> {
                strokeLine(canvas, underlineY, rect.left, rect.right, color, null)
            }

            UnderlineStyle.DOUBLE -> {
                strokeLine(canvas, underlineY - thickness, rect.left, rect.right, color, null)
                strokeLine(canvas, underlineY + thickness, rect.left, rect.right, color, null)
            }

            UnderlineStyle.CURLY -> {
                strokeCurl(canvas, underlineY, rect.left, rect.right, color)
            }

            UnderlineStyle.DOTTED -> {
                strokeLine(canvas, underlineY, rect.left, rect.right, color, dotted)
            }

            UnderlineStyle.DASHED -> {
                strokeLine(canvas, underlineY, rect.left, rect.right, color, dashed)
            }
        }
        if (style.strikethrough) {
            strokeLine(canvas, rect.top + metrics.baseline - metrics.strikeOffset, rect.left, rect.right, color, null)
        }
        if (style.overline) {
            strokeLine(canvas, rect.top + thickness / 2, rect.left, rect.right, color, null)
        }
    }

    private fun strokeLine(
        canvas: Canvas,
        y: Float,
        start: Float,
        end: Float,
        color: Int,
        effect: PathEffect?,
    ) {
        stroke.color = color
        stroke.strokeWidth = metrics.lineThickness
        stroke.pathEffect = effect
        canvas.drawLine(start, y, end, y, stroke)
    }

    private fun strokeCurl(
        canvas: Canvas,
        y: Float,
        start: Float,
        end: Float,
        color: Int,
    ) {
        val amplitude = max(metrics.lineThickness, CURL_AMPLITUDE_DP * metrics.density)
        val wavelength = max(metrics.cellWidth / 2, CURL_WAVELENGTH_DP * metrics.density)
        curl.rewind()
        curl.moveTo(start, y)
        var x = start
        var direction = -1f
        while (x < end) {
            val next = min(x + wavelength, end)
            curl.quadTo((x + next) / 2, y + amplitude * direction, next, y)
            direction = -direction
            x = next
        }
        stroke.color = color
        stroke.strokeWidth = metrics.lineThickness
        stroke.pathEffect = null
        canvas.drawPath(curl, stroke)
    }

    private fun isBlank(text: String): Boolean = text.all { it == ' ' }

    private fun isAscii(text: String): Boolean = text.all { it.code < ASCII_LIMIT }

    companion object {
        private const val SELECTION_ALPHA = 0x59
        private const val OPAQUE = 0xFF shl 24
        private const val ASCII_LIMIT = 0x80
        private const val MISMATCH_TOLERANCE = 0.5f
        private const val CURSOR_BAR_DP = 2f
        private const val CURSOR_BAR_SHARE = 0.15f
        private const val CURSOR_UNDERLINE_DP = 2f
        private const val CURL_AMPLITUDE_DP = 1.5f
        private const val CURL_WAVELENGTH_DP = 3f

        fun opaque(rgb: Int): Int = OPAQUE or rgb
    }
}
