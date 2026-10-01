package com.muxy.app.features.terminal.emulator

import com.muxy.app.features.terminal.TerminalLine
import com.muxy.app.features.terminal.TerminalScrollback
import com.termux.terminal.TerminalEmulator

class EmulatorScrollback private constructor(
    private val history: List<TerminalLine>,
    screen: List<TerminalLine>,
    maxRows: Int,
) : TerminalScrollback {
    private var oldestLoaded = (history.size - maxRows.coerceAtLeast(0)).coerceAtLeast(0)
    private val initialLines = history.subList(oldestLoaded, history.size) + screen

    override val historyRows: Int = history.size

    override fun lines(): List<TerminalLine> = initialLines

    override suspend fun loadOlder(maxRows: Int): List<TerminalLine> {
        val start = (oldestLoaded - maxRows.coerceAtLeast(0)).coerceAtLeast(0)
        val older = history.subList(start, oldestLoaded).toList()
        oldestLoaded = start
        return older
    }

    override fun close() = Unit

    companion object {
        fun capture(
            emulator: TerminalEmulator,
            maxRows: Int,
        ): EmulatorScrollback {
            val screen = emulator.screen
            val columns = emulator.mColumns
            val reverse = emulator.isReverseVideo
            val history = (-screen.activeTranscriptRows until 0).map { EmulatorFrameMapper.line(screen, it, columns, reverse) }
            val visible = (0 until emulator.mRows).map { EmulatorFrameMapper.line(screen, it, columns, reverse) }
            return EmulatorScrollback(history, visible, maxRows)
        }
    }
}
