package com.muxy.app.features.server.terminal

import com.muxy.app.features.terminal.CursorShape
import com.muxy.app.features.terminal.TerminalColor
import com.muxy.app.features.terminal.TerminalCursor
import com.muxy.app.features.terminal.TerminalFrame
import com.muxy.app.features.terminal.TerminalLine
import com.muxy.app.features.terminal.TerminalModes
import com.muxy.app.features.terminal.TerminalSpan
import com.muxy.app.features.terminal.TerminalStyle
import com.muxy.app.features.terminal.UnderlineStyle
import com.termux.terminal.WcWidth
import uniffi.muxy_mobile.Line
import uniffi.muxy_mobile.Screen
import uniffi.muxy_mobile.Span
import uniffi.muxy_mobile.Style
import uniffi.muxy_mobile.Underline
import java.text.BreakIterator
import uniffi.muxy_mobile.CursorShape as SdkCursorShape
import uniffi.muxy_mobile.TerminalColor as SdkTerminalColor

object SdkFrameMapper {
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    fun frame(screen: Screen): TerminalFrame =
        TerminalFrame(
            columns = screen.columns.toInt(),
            rows = screen.rows.toInt(),
            lines = screen.lines.map(::line),
            cursor =
                TerminalCursor(
                    row = screen.cursor.row.toInt(),
                    column = screen.cursor.column.toInt(),
                    visible = screen.cursor.visible,
                    shape = shape(screen.cursor.shape),
                ),
            modes = modes(screen),
            title = screen.title,
            historyRows = screen.historyRows.coerceAtMost(Int.MAX_VALUE.toULong()).toInt(),
        )

    fun modes(screen: Screen): TerminalModes = TerminalModes(mouseTracking = screen.mouseTracking, alternateScroll = screen.alternateScroll)

    fun line(line: Line): TerminalLine {
        val spans = ArrayList<TerminalSpan>(line.spans.size)
        line.spans.forEach { appendSpan(it, spans) }
        return TerminalLine(spans)
    }

    private fun appendSpan(
        span: Span,
        into: MutableList<TerminalSpan>,
    ) {
        val width = span.width.toInt()
        if (width <= 0 || span.text.isEmpty()) return
        val style = style(span.style)
        if (span.text.length == width && span.text.all(::isSingleCell)) {
            into += TerminalSpan.Run(span.text, style)
            return
        }
        val clusters = clusters(span.text)
        if (clusters.sumOf { it.second } != width) {
            into += TerminalSpan.Cluster(span.text, width, style)
            return
        }
        appendClusters(clusters, style, into)
    }

    private fun appendClusters(
        clusters: List<Pair<String, Int>>,
        style: TerminalStyle,
        into: MutableList<TerminalSpan>,
    ) {
        val run = StringBuilder()
        for ((text, cells) in clusters) {
            if (cells == 1 && text.length == 1) {
                run.append(text)
                continue
            }
            if (run.isNotEmpty()) into += TerminalSpan.Run(run.toString(), style)
            run.setLength(0)
            into += TerminalSpan.Cluster(text, cells, style)
        }
        if (run.isNotEmpty()) into += TerminalSpan.Run(run.toString(), style)
    }

    private fun clusters(text: String): List<Pair<String, Int>> {
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
        val result = mutableListOf<Pair<String, Int>>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val cluster = text.substring(start, end)
            result += cluster to WcWidth.width(cluster.codePointAt(0)).coerceIn(1, 2)
            start = end
            end = iterator.next()
        }
        return result
    }

    private fun isSingleCell(character: Char): Boolean = !character.isSurrogate() && WcWidth.width(character.code) == 1

    private fun style(style: Style): TerminalStyle =
        TerminalStyle(
            foreground = color(style.foreground),
            background = color(style.background),
            bold = style.bold,
            italic = style.italic,
            faint = style.faint,
            underline = underline(style.underline),
            underlineColor = color(style.underlineColor),
            strikethrough = style.strikethrough,
            overline = style.overline,
            inverse = style.inverse,
            invisible = style.invisible,
        )

    private fun color(color: SdkTerminalColor): TerminalColor =
        when (color) {
            SdkTerminalColor.Default -> {
                TerminalColor.Default
            }

            is SdkTerminalColor.Indexed -> {
                TerminalColor.Indexed(color.index.toInt())
            }

            is SdkTerminalColor.Rgb -> {
                TerminalColor.Rgb((color.red.toInt() shl RED_SHIFT) or (color.green.toInt() shl GREEN_SHIFT) or color.blue.toInt())
            }
        }

    private fun underline(underline: Underline): UnderlineStyle =
        when (underline) {
            Underline.NONE -> UnderlineStyle.NONE
            Underline.SINGLE -> UnderlineStyle.SINGLE
            Underline.DOUBLE -> UnderlineStyle.DOUBLE
            Underline.CURLY -> UnderlineStyle.CURLY
            Underline.DOTTED -> UnderlineStyle.DOTTED
            Underline.DASHED -> UnderlineStyle.DASHED
        }

    private fun shape(shape: SdkCursorShape): CursorShape =
        when (shape) {
            SdkCursorShape.BLOCK -> CursorShape.BLOCK
            SdkCursorShape.BAR -> CursorShape.BAR
            SdkCursorShape.UNDERLINE -> CursorShape.UNDERLINE
            SdkCursorShape.HOLLOW -> CursorShape.HOLLOW
        }
}
