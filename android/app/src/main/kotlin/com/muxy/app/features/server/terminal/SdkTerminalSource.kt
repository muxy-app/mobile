package com.muxy.app.features.server.terminal

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.features.terminal.TerminalCellPosition
import com.muxy.app.features.terminal.TerminalFrame
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.features.terminal.TerminalLine
import com.muxy.app.features.terminal.TerminalScrollDirection
import com.muxy.app.features.terminal.TerminalScrollback
import com.muxy.app.features.terminal.TerminalSource
import com.muxy.app.features.terminal.TerminalSourceListener
import com.muxy.app.features.terminalkit.TerminalKey
import com.muxy.app.features.terminalkit.TerminalKeyStroke
import com.muxy.app.networking.server.ScrollbackSnapshot
import com.muxy.app.networking.server.ServerFailure
import com.muxy.app.networking.server.ServerTerminalChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.muxy_mobile.MouseButton
import uniffi.muxy_mobile.Screen
import kotlin.time.Duration.Companion.milliseconds

class SdkTerminalSource(
    private val scope: CoroutineScope,
    private val onViewportChange: () -> Unit,
) : TerminalSource {
    override var listener: TerminalSourceListener? = null

    var viewportSize: TerminalGridSize? = null
        private set

    var screen: Screen? = null
        private set

    private var channel: ServerTerminalChannel? = null
    private var cachedFrame: TerminalFrame? = null
    private var requestedSize: TerminalGridSize? = null
    private var resizeJob: Job? = null

    fun attach(channel: ServerTerminalChannel) {
        this.channel = channel
        readScreen(channel)
        listener?.screenDidChange()
        fitToViewport()
    }

    fun release(): ServerTerminalChannel? {
        val released = channel
        channel = null
        cancelFit()
        return released
    }

    fun screenDidChange() {
        listener?.screenDidChange()
    }

    fun metadataDidChange() {
        val current = channel ?: return
        val latest = readScreen(current)
        listener?.modesDidChange(SdkFrameMapper.modes(latest))
        listener?.screenDidChange()
    }

    override fun frame(): TerminalFrame? {
        val current = channel ?: return cachedFrame
        readScreen(current)
        return cachedFrame
    }

    override fun sendText(text: String) {
        val current = channel ?: return
        when (text) {
            "\n", "\r" -> current.send(SdkKeys.key(TerminalKeyStroke(TerminalKey.Enter)), SdkKeys.none)
            "\t" -> current.send(SdkKeys.key(TerminalKeyStroke(TerminalKey.Tab)), SdkKeys.none)
            else -> current.send(text.replace("\r\n", "\r").replace('\n', '\r'))
        }
    }

    override fun send(stroke: TerminalKeyStroke) {
        channel?.send(SdkKeys.key(stroke), SdkKeys.modifiers(stroke.modifiers))
    }

    override fun paste(text: String) {
        channel?.paste(text)
    }

    override fun resize(size: TerminalGridSize) {
        if (viewportSize == size) return
        viewportSize = size
        onViewportChange()
        scheduleFit()
    }

    override fun scroll(
        direction: TerminalScrollDirection,
        cell: TerminalCellPosition,
    ) {
        channel?.scroll(SdkKeys.direction(direction), cell.row, cell.column)
    }

    override fun click(cell: TerminalCellPosition) {
        channel?.click(MouseButton.LEFT, cell.row, cell.column, SdkKeys.none)
    }

    override fun forwardScroll(deltaY: Double): Boolean = false

    override suspend fun scrollback(maxRows: Int): TerminalScrollback? {
        val current = channel ?: return null
        return SdkScrollback(current.scrollback(maxRows))
    }

    private fun readScreen(channel: ServerTerminalChannel): Screen {
        val latest = channel.screen()
        screen = latest
        cachedFrame = SdkFrameMapper.frame(latest)
        return latest
    }

    private fun scheduleFit() {
        if (channel == null) return
        resizeJob?.cancel()
        resizeJob =
            scope.launch {
                delay(RESIZE_DEBOUNCE)
                fitToViewport()
            }
    }

    private fun fitToViewport() {
        val current = channel ?: return
        val size = viewportSize ?: return
        if (size == requestedSize) return
        requestedSize = size
        val shown = screen
        if (shown != null && shown.columns.toInt() == size.columns && shown.rows.toInt() == size.rows) return
        scope.launch {
            attempt { current.resize(size.columns, size.rows) }
                .onFailure { error ->
                    Log.terminal.error("Resize failed: ${ServerFailure.from(error)}")
                    if (channel === current) requestedSize = null
                }
        }
    }

    private fun cancelFit() {
        resizeJob?.cancel()
        resizeJob = null
        requestedSize = null
    }

    private class SdkScrollback(
        private val snapshot: ScrollbackSnapshot,
    ) : TerminalScrollback {
        override val historyRows: Int = snapshot.historyRows.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        override fun lines(): List<TerminalLine> = snapshot.lines().map(SdkFrameMapper::line)

        override suspend fun loadOlder(maxRows: Int): List<TerminalLine> = snapshot.loadOlder(maxRows).map(SdkFrameMapper::line)

        override fun close() {
            snapshot.close()
        }
    }

    private companion object {
        val RESIZE_DEBOUNCE = 120.milliseconds
    }
}
