package com.muxy.app.features.projectdetail.terminal

import com.muxy.app.features.terminal.TerminalClipboard
import com.muxy.app.models.Tab
import com.muxy.app.models.TabKind
import com.muxy.app.networking.muxy1.ConnectionState
import com.muxy.app.networking.muxy1.protocol.ClientTerminalTheme
import kotlinx.coroutines.CoroutineScope
import java.util.UUID

class TerminalSessionStore(
    private val channel: TerminalChannel,
    private val scope: CoroutineScope,
    private val outbound: CoroutineScope,
    private val clipboard: TerminalClipboard,
) {
    private val sessions = mutableMapOf<UUID, TerminalSession>()
    private var activePaneId: UUID? = null
    private var connection: Long? = null
    private var connectionState: ConnectionState = ConnectionState.Idle
    private var clientTheme: ClientTerminalTheme? = null

    fun session(tab: Tab): TerminalSession? = paneId(tab)?.let(::session)

    fun selectionChanged(
        selectedTabId: UUID?,
        tabs: List<Tab>,
    ) {
        val paneId = tabs.firstOrNull { it.id == selectedTabId }?.let(::paneId)
        if (paneId == activePaneId) return
        activePaneId?.let { sessions[it]?.deactivate() }
        activePaneId = paneId
        paneId?.let { session(it).activate(connectionState, connection) }
    }

    fun tabsChanged(tabs: List<Tab>) {
        val livePanes = tabs.mapNotNull(::paneId).toSet()
        val closed = sessions.keys - livePanes
        closed.forEach { paneId ->
            sessions.remove(paneId)?.deactivate()
            if (activePaneId == paneId) activePaneId = null
        }
    }

    fun connectionChanged(
        state: ConnectionState,
        session: Long?,
    ) {
        connection = session
        connectionState = state
        activePaneId?.let { sessions[it] }?.connectionChanged(state, session)
    }

    fun useClientTheme(theme: ClientTerminalTheme) {
        clientTheme = theme
        sessions.values.forEach { it.useClientTheme(theme) }
    }

    fun teardown() {
        sessions.values.forEach(TerminalSession::deactivate)
        sessions.clear()
        activePaneId = null
    }

    private fun session(paneId: UUID): TerminalSession =
        sessions.getOrPut(paneId) {
            TerminalSession(paneId, channel, scope, outbound, clipboard).also { created -> clientTheme?.let(created::useClientTheme) }
        }

    private fun paneId(tab: Tab): UUID? = tab.paneId.takeIf { tab.kind == TabKind.Terminal }
}
