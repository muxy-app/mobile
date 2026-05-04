package com.muxy.app.data

import android.util.Base64
import com.muxy.app.model.MuxyEvent
import com.muxy.app.model.MuxyEventKind
import com.muxy.app.model.ReleasePaneParams
import com.muxy.app.model.TakeOverPaneParams
import com.muxy.app.model.TerminalContentDTO
import com.muxy.app.model.TerminalInputParams
import com.muxy.app.model.TerminalResizeParams
import com.muxy.app.model.TerminalScrollParams
import com.muxy.app.model.decodeTerminalContent
import com.muxy.app.model.getTerminalContentRequest
import com.muxy.app.model.releasePaneRequest
import com.muxy.app.model.takeOverPaneRequest
import com.muxy.app.model.terminalInputRequest
import com.muxy.app.model.terminalResizeRequest
import com.muxy.app.model.terminalScrollRequest
import com.muxy.app.model.toKind
import com.muxy.app.net.Transport
import com.muxy.app.net.TransportEvent
import com.muxy.app.net.newRequestId
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class PaneSession(
    val paneID: String,
    initialCols: Int,
    initialRows: Int,
    private val client: Transport,
    private val scope: CoroutineScope,
) {

    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick.asStateFlow()

    private var cols: Int = initialCols.coerceAtLeast(2)
    private var rows: Int = initialRows.coerceAtLeast(2)

    private val output = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {

            sendBytes(data, offset, count)
        }
        override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit
        override fun onCopyTextToClipboard(text: String?) = Unit
        override fun onPasteTextFromClipboard() = Unit
        override fun onBell() = Unit
        override fun onColorsChanged() = Unit
    }

    val emulator: TerminalEmulator = TerminalEmulator(
        output,
        cols,
        rows,
         12,
         24,
         2000,
         null,
    )

    private var eventJob: Job? = null

    @Volatile
    var byteSink: ((ByteArray) -> Unit)? = null

    suspend fun takeOver(takeoverCols: Int = cols, takeoverRows: Int = rows) {
        val c = takeoverCols.coerceAtLeast(2)
        val r = takeoverRows.coerceAtLeast(2)
        cols = c
        rows = r
        runCatching {
            client.send(
                takeOverPaneRequest(newRequestId(), TakeOverPaneParams(paneID, c, r)),
                10.seconds,
            )
        }
    }

    fun start() {
        eventJob?.cancel()
        eventJob = scope.launch {
            client.events.collect { evt ->
                when (evt) {
                    is TransportEvent.EventReceived -> handle(evt.event)
                    else -> Unit
                }
            }
        }
    }

    fun stop() {
        eventJob?.cancel()
        eventJob = null
        scope.launch {
            runCatching { client.send(releasePaneRequest(newRequestId(), ReleasePaneParams(paneID)), 5.seconds) }
        }
    }

    fun resize(newCols: Int, newRows: Int) {
        val c = newCols.coerceAtLeast(2)
        val r = newRows.coerceAtLeast(2)
        if (c == cols && r == rows) return
        cols = c
        rows = r
        synchronized(emulator) {
            emulator.resize(c, r, 12, 24)
        }
        scope.launch {
            runCatching {
                client.send(
                    terminalResizeRequest(newRequestId(), TerminalResizeParams(paneID, c, r)),
                    5.seconds,
                )
            }
        }
        bumpTick()
    }

    fun scroll(deltaX: Double, deltaY: Double, precise: Boolean) {
        scope.launch {
            runCatching {
                client.send(
                    terminalScrollRequest(
                        newRequestId(),
                        TerminalScrollParams(paneID, deltaX, deltaY, precise),
                    ),
                    5.seconds,
                )
            }
        }
    }

    suspend fun getContent(): TerminalContentDTO? = runCatching {
        val resp = client.send(getTerminalContentRequest(newRequestId(), paneID), 10.seconds)
        decodeTerminalContent(resp.result)
    }.getOrNull()

    fun sendBytes(bytes: ByteArray) = sendBytes(bytes, 0, bytes.size)

    fun sendBytes(data: ByteArray, offset: Int, count: Int) {
        if (count <= 0) return
        val slice = if (offset == 0 && count == data.size) data else data.copyOfRange(offset, offset + count)
        val b64 = Base64.encodeToString(slice, Base64.NO_WRAP)
        client.sendFireAndForget(terminalInputRequest(newRequestId(), TerminalInputParams(paneID, b64)))
    }

    private fun handle(event: MuxyEvent) {
        val out = when (val kind = event.toKind()) {
            is MuxyEventKind.TerminalOutput -> kind.output
            is MuxyEventKind.TerminalSnapshot -> kind.output
            else -> return
        }
        if (out.paneID != paneID) return
        val bytes = runCatching { Base64.decode(out.bytes, Base64.DEFAULT) }.getOrNull() ?: return
        val sink = byteSink
        if (sink != null) {
            sink(bytes)
            return
        }
        synchronized(emulator) {
            emulator.append(bytes, bytes.size)
        }
        bumpTick()
    }

    private fun bumpTick() {
        _tick.value = _tick.value + 1
    }

    fun applyTheme(fg: Long, bg: Long, palette: List<Long>) {
        synchronized(emulator) {
            val colors = emulator.mColors.mCurrentColors
            colors[TextStyle.COLOR_INDEX_FOREGROUND] = (0xFF000000.toInt()) or (fg.toInt() and 0xFFFFFF)
            colors[TextStyle.COLOR_INDEX_BACKGROUND] = (0xFF000000.toInt()) or (bg.toInt() and 0xFFFFFF)
            colors[TextStyle.COLOR_INDEX_CURSOR] = (0xFF000000.toInt()) or (fg.toInt() and 0xFFFFFF)
            if (palette.size >= 16) {
                for (i in 0 until 16) {
                    colors[i] = (0xFF000000.toInt()) or (palette[i].toInt() and 0xFFFFFF)
                }
            }
        }
        bumpTick()
    }
}
