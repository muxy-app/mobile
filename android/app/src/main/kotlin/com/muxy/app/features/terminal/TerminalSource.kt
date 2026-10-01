package com.muxy.app.features.terminal

import com.muxy.app.features.terminalkit.TerminalKeyStroke

interface TerminalSourceListener {
    fun screenDidChange()

    fun modesDidChange(modes: TerminalModes)
}

enum class TerminalScrollDirection {
    UP,
    DOWN,
}

interface TerminalScrollback : AutoCloseable {
    val historyRows: Int

    fun lines(): List<TerminalLine>

    suspend fun loadOlder(maxRows: Int): List<TerminalLine>
}

interface TerminalSource {
    var listener: TerminalSourceListener?

    fun frame(): TerminalFrame?

    fun sendText(text: String)

    fun send(stroke: TerminalKeyStroke)

    fun paste(text: String)

    fun resize(size: TerminalGridSize)

    fun scroll(
        direction: TerminalScrollDirection,
        cell: TerminalCellPosition,
    )

    fun click(cell: TerminalCellPosition)

    fun forwardScroll(deltaY: Double): Boolean

    suspend fun scrollback(maxRows: Int): TerminalScrollback?
}
