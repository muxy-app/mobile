package com.muxy.app.networking.server.sdk

import com.muxy.app.networking.server.ScrollbackSnapshot
import com.muxy.app.networking.server.ServerTerminalChannel
import uniffi.muxy_mobile.Key
import uniffi.muxy_mobile.Line
import uniffi.muxy_mobile.Modifiers
import uniffi.muxy_mobile.MouseButton
import uniffi.muxy_mobile.Screen
import uniffi.muxy_mobile.ScrollDirection
import uniffi.muxy_mobile.Scrollback
import uniffi.muxy_mobile.Terminal

class SdkTerminalChannel(
    private val terminal: Terminal,
    private val lanes: SdkLanes,
) : ServerTerminalChannel {
    override val sessionId: ULong
        get() = terminal.sessionId()

    override fun screen(): Screen = terminal.screen()

    override fun send(text: String) {
        lanes.send { terminal.sendInput(text.toByteArray()) }
    }

    override fun send(
        key: Key,
        modifiers: Modifiers,
    ) {
        lanes.send { terminal.sendKey(key, modifiers) }
    }

    override fun paste(text: String) {
        lanes.send { terminal.paste(text) }
    }

    override fun click(
        button: MouseButton,
        row: Int,
        column: Int,
        modifiers: Modifiers,
    ) {
        lanes.send { terminal.click(button, row.toCells(), column.toCells(), modifiers) }
    }

    override fun scroll(
        direction: ScrollDirection,
        row: Int,
        column: Int,
    ) {
        lanes.send { terminal.scroll(direction, row.toCells(), column.toCells()) }
    }

    override suspend fun resize(
        columns: Int,
        rows: Int,
    ) {
        lanes.request { terminal.resize(columns.toCells(), rows.toCells()) }
    }

    override suspend fun scrollback(maxRows: Int): ScrollbackSnapshot {
        val scrollback = lanes.request { terminal.scrollback(maxRows.toCells()) }
        return SdkScrollbackSnapshot(scrollback, lanes)
    }

    override suspend fun detach() {
        lanes.request { terminal.detach() }
    }

    override fun close() {
        lanes.closeAfterInput(terminal)
    }
}

class SdkScrollbackSnapshot(
    private val scrollback: Scrollback,
    private val lanes: SdkLanes,
) : ScrollbackSnapshot {
    override val historyRows: Long
        get() = scrollback.historyRows().coerceAtMost(Long.MAX_VALUE.toULong()).toLong()

    override fun lines(): List<Line> = scrollback.lines()

    override suspend fun loadOlder(maxRows: Int): List<Line> = lanes.request { scrollback.loadOlder(maxRows.toCells()) }

    override fun close() {
        scrollback.close()
    }
}
