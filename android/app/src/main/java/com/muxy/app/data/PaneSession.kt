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
import com.muxy.app.net.MuxyClient
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

/**
 * One PaneSession per terminal pane the user opens. Owns a Termux
 * TerminalEmulator that consumes server-sent PTY bytes and surfaces a stable
 * "tick" counter so Compose can recompose on every output frame.
 *
 * Threading: TerminalEmulator is single-threaded; we synchronize() on the
 * emulator instance before append/sendKey calls to keep render reads consistent
 * with mutations from the network thread.
 */
class PaneSession(
    val paneID: String,
    initialCols: Int,
    initialRows: Int,
    private val client: MuxyClient,
    private val scope: CoroutineScope,
) {
    /** Increments every time the emulator buffer changes; observed by Compose to redraw. */
    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick.asStateFlow()

    private var cols: Int = initialCols.coerceAtLeast(2)
    private var rows: Int = initialRows.coerceAtLeast(2)

    private val output = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            // Bytes the emulator wants to send back to the server (e.g. ENQ, mouse, paste reply).
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
        /* cellWidthPixels */ 12,
        /* cellHeightPixels */ 24,
        /* transcriptRows */ 2000,
        /* client */ null,
    )

    private var eventJob: Job? = null

    /**
     * When set, incoming PTY bytes are forwarded here instead of being appended
     * to this PaneSession's own [emulator]. The new termux-backed TerminalView
     * owns its own [com.termux.terminal.TerminalSession] and emulator, so we
     * avoid double-rendering by routing bytes there.
     */
    @Volatile
    var byteSink: ((ByteArray) -> Unit)? = null

    /**
     * Send `takeOverPane` with the given size. Mirrors iOS's
     * `attemptAutoTakeOver` and `takeOverCurrentPane` — both call sites pass
     * the surface's measured cols/rows so the Mac sizes the PTY correctly on
     * the very first frame instead of starting at a stale 80x24 and reflowing.
     *
     * Also updates this PaneSession's cached cols/rows so subsequent resize()
     * calls compare against the right baseline.
     */
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

    /**
     * Start consuming terminalOutput / terminalSnapshot events. Does NOT send
     * `takeOverPane` — the caller (TerminalView) drives takeover once the
     * surface has measured its real cols/rows, mirroring iOS's
     * `attemptAutoTakeOver` flow gated on `reportedCols`/`reportedRows`.
     */
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

    /** Release the pane back to the Mac and stop. */
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

    /** Forward a scroll gesture to the server (mirrors Swift's terminalScroll). */
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

    /** Refetch the full PTY-bytes snapshot (e.g. after reconnect, to restore scrollback). */
    suspend fun getContent(): TerminalContentDTO? = runCatching {
        val resp = client.send(getTerminalContentRequest(newRequestId(), paneID), 10.seconds)
        decodeTerminalContent(resp.result)
    }.getOrNull()

    /** Forward keystrokes (already encoded into PTY bytes) to the server. */
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

    /**
     * Push the Mac-broadcast theme (default fg/bg + 16-color palette) into the
     * Termux emulator so SGR color codes render with the user's theme.
     */
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
