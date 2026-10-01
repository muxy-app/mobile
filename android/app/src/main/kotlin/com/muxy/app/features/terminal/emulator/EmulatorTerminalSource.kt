package com.muxy.app.features.terminal.emulator

import com.muxy.app.features.terminal.TerminalCellPosition
import com.muxy.app.features.terminal.TerminalFrame
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.features.terminal.TerminalScrollDirection
import com.muxy.app.features.terminal.TerminalScrollback
import com.muxy.app.features.terminal.TerminalSource
import com.muxy.app.features.terminal.TerminalSourceListener
import com.muxy.app.features.terminalkit.TerminalKey
import com.muxy.app.features.terminalkit.TerminalKeyStroke
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSessionClient
import java.io.ByteArrayOutputStream

class EmulatorTerminalSource(
    private val sink: (ByteArray) -> Unit,
    private val clipboard: (String) -> Unit,
    private val scrollForwarder: (Double) -> Boolean = { false },
    private val onResize: (TerminalGridSize) -> Unit = {},
) : TerminalSource {
    override var listener: TerminalSourceListener? = null

    var grid: TerminalGridSize? = null
        private set

    private val output = Output()
    private var emulator: TerminalEmulator? = null
    private var batch: ByteArrayOutputStream? = null

    override fun frame(): TerminalFrame? = emulator?.let(EmulatorFrameMapper::frame)

    fun feed(bytes: ByteArray) {
        val current = emulator ?: return
        batched { current.append(bytes, bytes.size) }
        listener?.screenDidChange()
    }

    fun restart(bytes: ByteArray) {
        val size = grid ?: return
        emulator = makeEmulator(size)
        feed(bytes)
    }

    override fun sendText(text: String) {
        write(text.replace("\r\n", "\r").replace('\n', '\r').toByteArray())
    }

    override fun send(stroke: TerminalKeyStroke) {
        val current = emulator
        val bytes =
            EmulatorKeyEncoder.bytes(
                stroke = stroke,
                applicationCursor = current?.isCursorKeysApplicationMode == true,
                applicationKeypad = current?.isKeypadApplicationMode == true,
            ) ?: return
        write(bytes)
    }

    override fun paste(text: String) {
        val current = emulator ?: return
        batched { current.paste(text) }
    }

    override fun resize(size: TerminalGridSize) {
        grid = size
        val current = emulator
        if (current == null) {
            emulator = makeEmulator(size)
        } else {
            current.resize(size.columns, size.rows, 0, 0)
        }
        listener?.screenDidChange()
        onResize(size)
    }

    override fun scroll(
        direction: TerminalScrollDirection,
        cell: TerminalCellPosition,
    ) {
        val current = emulator ?: return
        if (!current.isMouseTrackingActive) {
            send(TerminalKeyStroke(if (direction == TerminalScrollDirection.UP) TerminalKey.Up else TerminalKey.Down))
            return
        }
        val button =
            if (direction == TerminalScrollDirection.UP) TerminalEmulator.MOUSE_WHEELUP_BUTTON else TerminalEmulator.MOUSE_WHEELDOWN_BUTTON
        batched { current.sendMouseEvent(button, cell.column + 1, cell.row + 1, true) }
    }

    override fun click(cell: TerminalCellPosition) {
        val current = emulator ?: return
        batched {
            current.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, cell.column + 1, cell.row + 1, true)
            current.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, cell.column + 1, cell.row + 1, false)
        }
    }

    override fun forwardScroll(deltaY: Double): Boolean = scrollForwarder(deltaY)

    override suspend fun scrollback(maxRows: Int): TerminalScrollback? = emulator?.let { EmulatorScrollback.capture(it, maxRows) }

    private fun write(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val pending = batch
        if (pending != null) {
            pending.write(bytes)
            return
        }
        sink(bytes)
    }

    private inline fun batched(block: () -> Unit) {
        if (batch != null) {
            block()
            return
        }
        val pending = ByteArrayOutputStream()
        batch = pending
        try {
            block()
        } finally {
            batch = null
        }
        if (pending.size() > 0) sink(pending.toByteArray())
    }

    private fun makeEmulator(size: TerminalGridSize): TerminalEmulator =
        TerminalEmulator(output, size.columns, size.rows, 0, 0, TRANSCRIPT_ROWS + size.rows, QuietClient)

    private inner class Output : TerminalOutput() {
        override fun write(
            data: ByteArray,
            offset: Int,
            count: Int,
        ) {
            this@EmulatorTerminalSource.write(data.copyOfRange(offset, offset + count))
        }

        override fun titleChanged(
            oldTitle: String?,
            newTitle: String?,
        ) = Unit

        override fun onCopyTextToClipboard(text: String?) {
            text?.let(clipboard)
        }

        override fun onPasteTextFromClipboard() = Unit

        override fun onBell() = Unit

        override fun onColorsChanged() = Unit
    }

    private object QuietClient : TerminalSessionClient {
        override fun onTerminalCursorStateChange(state: Boolean) = Unit

        override fun getTerminalCursorStyle(): Int? = null

        override fun logError(
            tag: String?,
            message: String?,
        ) = Unit

        override fun logWarn(
            tag: String?,
            message: String?,
        ) = Unit

        override fun logInfo(
            tag: String?,
            message: String?,
        ) = Unit

        override fun logDebug(
            tag: String?,
            message: String?,
        ) = Unit

        override fun logVerbose(
            tag: String?,
            message: String?,
        ) = Unit

        override fun logStackTraceWithMessage(
            tag: String?,
            message: String?,
            e: Exception?,
        ) = Unit

        override fun logStackTrace(
            tag: String?,
            e: Exception?,
        ) = Unit
    }

    companion object {
        const val TRANSCRIPT_ROWS = 500
    }
}
