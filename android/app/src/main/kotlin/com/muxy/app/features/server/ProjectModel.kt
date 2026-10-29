package com.muxy.app.features.server

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.networking.server.ServerConnection
import com.muxy.app.networking.server.ServerFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.muxy_mobile.Session
import uniffi.muxy_mobile.SessionStatus
import java.util.UUID

class ProjectModel(
    val projectId: String,
    private val server: ServerController,
    private val scope: CoroutineScope,
) {
    private val mutableTabs = MutableStateFlow<List<ServerTerminal>>(emptyList())
    private val mutableSelectedTabId = MutableStateFlow<UUID?>(null)
    private val mutableHasLoadedSessions = MutableStateFlow(false)
    private val mutableIsRefreshingSessions = MutableStateFlow(false)
    private val mutableSessionsLoadFailed = MutableStateFlow(false)
    private val endedSessionIds = mutableSetOf<ULong>()
    private val sessionsRefresh = CoalescedRefresh(scope)
    private var sessionsGeneration = 0
    private var isBrowsing = false

    val tabs: StateFlow<List<ServerTerminal>> = mutableTabs.asStateFlow()
    val selectedTabId: StateFlow<UUID?> = mutableSelectedTabId.asStateFlow()
    val hasLoadedSessions: StateFlow<Boolean> = mutableHasLoadedSessions.asStateFlow()
    val isRefreshingSessions: StateFlow<Boolean> = mutableIsRefreshingSessions.asStateFlow()
    val sessionsLoadFailed: StateFlow<Boolean> = mutableSessionsLoadFailed.asStateFlow()

    var isVisible = false
        private set

    val needsSessions: Boolean
        get() = isVisible || isBrowsing

    val selectedTab: ServerTerminal?
        get() = mutableTabs.value.firstOrNull { it.id == mutableSelectedTabId.value }

    fun setVisible(visible: Boolean) {
        if (isVisible == visible) return
        isVisible = visible
        server.updateVisibleTerminal()
        if (!needsSessions) cancelSessionRefresh()
        if (!visible) return
        server.refreshSessions(this)
    }

    fun setBrowsing(browsing: Boolean) {
        if (isBrowsing == browsing) return
        isBrowsing = browsing
        if (!needsSessions) cancelSessionRefresh()
    }

    fun createTab(): ServerTerminal {
        val tab =
            ServerTerminal(
                projectId,
                sessionId = null,
                server = server,
                scope = scope,
                directory = server.project(projectId)?.directory.orEmpty(),
            )
        mutableTabs.value += tab
        select(tab)
        return tab
    }

    fun select(tab: ServerTerminal) {
        if (tab.isRetired || mutableTabs.value.none { it === tab }) return
        if (mutableSelectedTabId.value == tab.id) return
        mutableSelectedTabId.value = tab.id
        server.updateVisibleTerminal()
    }

    fun close(tab: ServerTerminal) {
        remove(tab)
        val sessionId = tab.sessionId ?: return
        endedSessionIds += sessionId
        scope.launch {
            attempt { server.endSession(sessionId) }
                .onFailure { error ->
                    Log.connection.error("Ending a session failed: ${ServerFailure.from(error)}")
                    endedSessionIds -= sessionId
                    server.refreshSessions(this@ProjectModel)
                }
        }
    }

    fun sessionDidEnd(sessionId: ULong) {
        endedSessionIds += sessionId
        val tab = mutableTabs.value.firstOrNull { it.sessionId == sessionId } ?: return
        tab.markEnded()
        remove(tab)
    }

    fun didRegister(terminal: ServerTerminal) {
        val sessionId = terminal.sessionId ?: return
        mutableTabs.value
            .filter { it.id != terminal.id && it.sessionId == sessionId }
            .forEach(::remove)
    }

    fun refreshSessions(connection: ServerConnection) {
        if (!needsSessions) return
        sessionsRefresh.request { loadSessions(connection) }
    }

    fun connectionDidClose() {
        cancelSessionRefresh()
        mutableTabs.value.forEach(ServerTerminal::connectionDidClose)
    }

    fun removeAllTabs() {
        cancelSessionRefresh()
        mutableTabs.value.forEach(server::retire)
        mutableTabs.value = emptyList()
        mutableSelectedTabId.value = null
        server.updateVisibleTerminal()
    }

    private suspend fun loadSessions(connection: ServerConnection) {
        if (connection !== server.connection || !needsSessions) return
        val token = sessionsGeneration
        val knownBeforeRequest = mutableTabs.value.mapNotNull { it.sessionId }.toSet()
        mutableIsRefreshingSessions.value = true
        val result = attempt { connection.sessions(projectId) }
        if (token != sessionsGeneration || connection !== server.connection || !needsSessions) return
        mutableIsRefreshingSessions.value = false
        result
            .onSuccess { listed ->
                val live = listed.filter { it.status == SessionStatus.LIVE || it.status == SessionStatus.STARTING }
                mutableHasLoadedSessions.value = true
                mutableSessionsLoadFailed.value = false
                reconcile(live, knownBeforeRequest)
            }.onFailure {
                mutableSessionsLoadFailed.value = true
                Log.connection.error("Loading sessions failed: ${ServerFailure.from(it)}")
            }
    }

    private fun cancelSessionRefresh() {
        sessionsGeneration += 1
        sessionsRefresh.cancel()
        mutableIsRefreshingSessions.value = false
    }

    private fun reconcile(
        sessions: List<Session>,
        knownBeforeRequest: Set<ULong>,
    ) {
        val liveIds = sessions.map { it.id }.toSet()
        endedSessionIds.retainAll(liveIds)
        pruneEnded(knownBeforeRequest - liveIds)
        appendNew(sessions)
        if (selectedTab != null) return
        mutableTabs.value.firstOrNull()?.let(::select)
    }

    private fun pruneEnded(endedIds: Set<ULong>) {
        val ended = mutableTabs.value.filter { tab -> tab.sessionId?.let { it in endedIds } == true }
        ended.forEach { tab ->
            tab.markEnded()
            remove(tab)
        }
    }

    private fun appendNew(sessions: List<Session>) {
        val open = mutableTabs.value.mapNotNull { it.sessionId }.toSet()
        sessions
            .filter { it.id !in open && it.id !in endedSessionIds }
            .sortedBy { it.id }
            .forEach { session ->
                val tab = ServerTerminal(projectId, session.id, server, scope, session.directory)
                server.register(tab, session.id)
                mutableTabs.value += tab
            }
    }

    private fun remove(tab: ServerTerminal) {
        val current = mutableTabs.value
        val index = current.indexOfFirst { it.id == tab.id }
        if (index < 0) return
        val remaining = current.toMutableList().apply { removeAt(index) }
        mutableTabs.value = remaining
        server.retire(tab)
        if (mutableSelectedTabId.value == tab.id) {
            mutableSelectedTabId.value = (remaining.getOrNull(index) ?: remaining.lastOrNull())?.id
        }
        server.updateVisibleTerminal()
    }
}
