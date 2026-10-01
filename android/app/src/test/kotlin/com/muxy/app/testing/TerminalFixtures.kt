package com.muxy.app.testing

import com.muxy.app.features.terminal.CursorShape
import com.muxy.app.features.terminal.TerminalCellPosition
import com.muxy.app.features.terminal.TerminalCursor
import com.muxy.app.features.terminal.TerminalDisplay
import com.muxy.app.features.terminal.TerminalFrame
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.features.terminal.TerminalLine
import com.muxy.app.features.terminal.TerminalModes
import com.muxy.app.features.terminal.TerminalScrollDirection
import com.muxy.app.features.terminal.TerminalScrollback
import com.muxy.app.features.terminal.TerminalSource
import com.muxy.app.features.terminal.TerminalSourceListener
import com.muxy.app.features.terminal.TerminalSpan
import com.muxy.app.features.terminal.TerminalStyle
import com.muxy.app.features.terminalkit.TerminalKeyStroke

fun line(text: String): TerminalLine = TerminalLine(listOf(TerminalSpan.Run(text, TerminalStyle.PLAIN)))

fun lines(vararg texts: String): List<TerminalLine> = texts.map(::line)

fun frame(
    rows: Int = 2,
    columns: Int = 20,
): TerminalFrame =
    TerminalFrame(
        columns = columns,
        rows = rows,
        lines = List(rows) { line(" ".repeat(columns)) },
        cursor = TerminalCursor(0, 0, visible = true, shape = CursorShape.BLOCK),
        modes = TerminalModes(),
        title = "",
        historyRows = 0,
    )

class StubScrollback(
    private val initial: List<TerminalLine>,
    override val historyRows: Int,
    olderPages: List<Result<List<TerminalLine>>>,
) : TerminalScrollback {
    private val pages = ArrayDeque(olderPages)

    var loadCount = 0
        private set

    override fun lines(): List<TerminalLine> = initial

    override suspend fun loadOlder(maxRows: Int): List<TerminalLine> {
        loadCount += 1
        return pages.removeFirst().getOrThrow()
    }

    override fun close() = Unit
}

class FakeTerminalSource(
    var currentFrame: TerminalFrame? = frame(),
    var scrollback: TerminalScrollback? = null,
) : TerminalSource {
    override var listener: TerminalSourceListener? = null

    val texts = mutableListOf<String>()
    val strokes = mutableListOf<TerminalKeyStroke>()
    val pastes = mutableListOf<String>()
    val sizes = mutableListOf<TerminalGridSize>()
    val scrolls = mutableListOf<Pair<TerminalScrollDirection, TerminalCellPosition>>()
    var scrollbackRequests = 0
        private set

    override fun frame(): TerminalFrame? = currentFrame

    override fun sendText(text: String) {
        texts += text
    }

    override fun send(stroke: TerminalKeyStroke) {
        strokes += stroke
    }

    override fun paste(text: String) {
        pastes += text
    }

    override fun resize(size: TerminalGridSize) {
        sizes += size
    }

    override fun scroll(
        direction: TerminalScrollDirection,
        cell: TerminalCellPosition,
    ) {
        scrolls += direction to cell
    }

    override fun click(cell: TerminalCellPosition) = Unit

    override fun forwardScroll(deltaY: Double): Boolean = false

    override suspend fun scrollback(maxRows: Int): TerminalScrollback? {
        scrollbackRequests += 1
        return scrollback
    }
}

class RecordingDisplay : TerminalDisplay {
    val events = mutableListOf<String>()

    override fun screenNeedsRefresh() {
        events += REFRESH
    }

    override fun prepareForLiveOutput() {
        events += PREPARE
    }

    companion object {
        const val REFRESH = "refresh"
        const val PREPARE = "prepare"
    }
}
