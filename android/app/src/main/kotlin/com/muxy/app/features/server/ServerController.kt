package com.muxy.app.features.server

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.networking.server.ReconnectSchedule
import com.muxy.app.networking.server.ServerConnection
import com.muxy.app.networking.server.ServerConnector
import com.muxy.app.networking.server.ServerFailure
import com.muxy.app.networking.server.ServerProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uniffi.muxy_mobile.ConnectionEvent
import uniffi.muxy_mobile.MobileException
import uniffi.muxy_mobile.ServerCredential
import kotlin.time.Duration

class ServerController(
    val serverId: String,
    private val connector: ServerConnector,
    private val credentialProvider: suspend () -> ServerCredential?,
    private val scope: CoroutineScope,
    private val schedule: ReconnectSchedule = ReconnectSchedule(),
) {
    private val mutablePhase = MutableStateFlow<ServerPhase>(ServerPhase.Idle)
    private val mutableCatalog = MutableStateFlow(ProjectCatalog())

    val phase: StateFlow<ServerPhase> = mutablePhase.asStateFlow()
    val catalog: StateFlow<ProjectCatalog> = mutableCatalog.asStateFlow()

    var serverName: String = DEFAULT_SERVER_NAME
        private set

    var connection: ServerConnection? = null
        private set

    private val attachment = AttachmentCoordinator(this, scope)
    private val projectModels = mutableMapOf<String, ProjectModel>()
    private val terminalsBySession = mutableMapOf<ULong, ServerTerminal>()
    private val projectsRefresh = CoalescedRefresh(scope)
    private val sessionsRefresh = CoalescedRefresh(scope)
    private var visibleProjectId: String? = null
    private var generation = 0
    private var wantsConnection = false
    private var isConnecting = false
    private var restartPending = false
    private var reconnectJob: Job? = null

    fun project(projectId: String): ServerProject? = mutableCatalog.value.projects.firstOrNull { it.id == projectId }

    fun projectModel(projectId: String): ProjectModel {
        projectModels[projectId]?.let { return it }
        val model = ProjectModel(projectId, this, scope)
        projectModels[projectId] = model
        if (projectId == visibleProjectId) model.setVisible(true)
        return model
    }

    fun showProject(projectId: String?) {
        if (visibleProjectId == projectId) return
        visibleProjectId = projectId
        projectModels.values.filter { it.projectId != projectId }.forEach { it.setVisible(false) }
        projectId?.let { projectModel(it).setVisible(true) }
    }

    fun setWantsConnection(wants: Boolean) {
        if (wantsConnection == wants) return
        wantsConnection = wants
        if (wants) {
            connect()
            return
        }
        closeConnection()
        resetPhaseUnlessFailed()
    }

    fun retryNow() {
        if (mutablePhase.value is ServerPhase.Failed) mutablePhase.value = ServerPhase.Idle
        schedule.reset()
        reconnectJob?.cancel()
        reconnectJob = null
        connect()
    }

    fun credentialDidChange() {
        closeConnection()
        mutablePhase.value = ServerPhase.Idle
        schedule.reset()
        connect()
    }

    suspend fun endSession(sessionId: ULong) {
        val current = connection ?: throw MobileException.Disconnected()
        current.endSession(sessionId)
        sessionDidEnd(sessionId)
    }

    fun reloadProjects() {
        if (connection == null) return
        projectsRefresh.request(::loadProjects)
    }

    fun register(
        terminal: ServerTerminal,
        sessionId: ULong,
    ) {
        terminal.assign(sessionId)
        terminalsBySession[sessionId] = terminal
        projectModels[terminal.projectId]?.didRegister(terminal)
    }

    fun retire(terminal: ServerTerminal) {
        terminal.markRetired()
        val sessionId = terminal.sessionId
        if (sessionId != null && terminalsBySession[sessionId] === terminal) terminalsBySession.remove(sessionId)
        attachment.retire(terminal)
    }

    fun updateVisibleTerminal() {
        attachment.show(projectModels.values.firstOrNull { it.isVisible }?.selectedTab)
    }

    fun viewportDidChange(terminal: ServerTerminal) {
        attachment.viewportDidChange(terminal)
    }

    fun attachDidFail(terminal: ServerTerminal) {
        projectModels[terminal.projectId]?.let(::refreshSessions)
    }

    fun refreshSessions(model: ProjectModel) {
        val current = connection ?: return
        scope.launch { model.refreshSessions(current) }
    }

    private fun connect() {
        if (!wantsConnection || connection != null || isConnecting) return
        if (mutablePhase.value is ServerPhase.Failed) return
        reconnectJob?.cancel()
        reconnectJob = null
        generation += 1
        val token = generation
        isConnecting = true
        if (mutablePhase.value == ServerPhase.Idle) mutablePhase.value = ServerPhase.Connecting
        scope.launch { connect(token) }
    }

    private suspend fun connect(token: Int) {
        val credential = credentialProvider()
        if (token != generation) return
        if (credential == null) {
            isConnecting = false
            mutablePhase.value = ServerPhase.Failed(ServerFailure.InvalidCredential)
            return
        }
        serverName = credential.serverName
        attempt { connector.connect(credential) { event -> scope.launch { handle(event, token) } } }
            .onSuccess { didConnect(it, token) }
            .onFailure { didFailToConnect(ServerFailure.from(it), token) }
    }

    private fun didConnect(
        opened: ServerConnection,
        token: Int,
    ) {
        if (token != generation) {
            opened.disconnect()
            return
        }
        isConnecting = false
        connection = opened
        mutablePhase.value = ServerPhase.Connected
        schedule.reset()
        restartPending = false
        Log.connection.info("Connected to Muxy ${opened.serverVersion}")
        projectsRefresh.request(::loadProjects)
        sessionsRefresh.request(::loadSessions)
        attachment.connectionDidOpen()
    }

    private fun didFailToConnect(
        failure: ServerFailure,
        token: Int,
    ) {
        if (token != generation) return
        isConnecting = false
        Log.connection.error("Connecting failed: $failure")
        if (failure.isFatal) {
            mutablePhase.value = ServerPhase.Failed(failure)
            return
        }
        val delay = schedule.nextDelay()
        scheduleReconnect(delay, ReconnectReason.Lost(failure, schedule.attempt))
    }

    private fun handle(
        event: ConnectionEvent,
        token: Int,
    ) {
        if (token != generation) return
        when (event) {
            is ConnectionEvent.ScreenChanged -> terminalsBySession[event.sessionId]?.screenDidChange()
            is ConnectionEvent.MetadataChanged -> terminalsBySession[event.sessionId]?.metadataDidChange()
            is ConnectionEvent.SessionEnded -> sessionDidEnd(event.sessionId)
            ConnectionEvent.CatalogChanged -> reloadProjects()
            ConnectionEvent.SessionsChanged -> reloadSessions()
            ConnectionEvent.ServerRestarting -> serverIsRestarting()
            ConnectionEvent.Disconnected -> connectionDidClose()
            ConnectionEvent.ActivityChanged, is ConnectionEvent.GitChanged, is ConnectionEvent.FilesChanged -> Unit
        }
    }

    private fun serverIsRestarting() {
        restartPending = true
        mutablePhase.value = ServerPhase.Reconnecting(ReconnectReason.ServerRestarting)
    }

    private fun connectionDidClose() {
        val restarting = restartPending
        closeConnection()
        if (!wantsConnection) {
            resetPhaseUnlessFailed()
            return
        }
        if (restarting) {
            scheduleReconnect(schedule.afterRestart, ReconnectReason.ServerRestarting)
            return
        }
        val delay = schedule.nextDelay()
        scheduleReconnect(delay, ReconnectReason.Lost(ServerFailure.Disconnected, schedule.attempt))
    }

    private fun closeConnection() {
        reconnectJob?.cancel()
        reconnectJob = null
        generation += 1
        isConnecting = false
        restartPending = false
        val closing = connection
        connection = null
        attachment.connectionDidClose()
        projectModels.values.flatMap { it.tabs.value }.forEach(ServerTerminal::connectionDidClose)
        projectsRefresh.cancel()
        sessionsRefresh.cancel()
        closing?.disconnect()
    }

    private fun resetPhaseUnlessFailed() {
        if (mutablePhase.value is ServerPhase.Failed) return
        mutablePhase.value = ServerPhase.Idle
        schedule.reset()
    }

    private fun scheduleReconnect(
        delay: Duration,
        reason: ReconnectReason,
    ) {
        mutablePhase.value = ServerPhase.Reconnecting(reason)
        reconnectJob?.cancel()
        reconnectJob =
            scope.launch {
                delay(delay)
                connect()
            }
    }

    private fun sessionDidEnd(sessionId: ULong) {
        terminalsBySession.remove(sessionId)?.let { projectModels[it.projectId]?.sessionDidEnd(sessionId) }
        reloadSessions()
    }

    private fun reloadSessions() {
        if (connection == null) return
        sessionsRefresh.request(::loadSessions)
    }

    private suspend fun loadProjects() {
        val current = connection ?: return
        attempt { current.projects() }
            .onSuccess { projects ->
                mutableCatalog.value = ProjectCatalog(projects, hasLoaded = true, loadFailed = false)
                dropRemovedProjects()
            }.onFailure { error ->
                mutableCatalog.update { it.copy(loadFailed = true) }
                Log.connection.error("Loading projects failed: ${ServerFailure.from(error)}")
            }
    }

    private suspend fun loadSessions() {
        val current = connection ?: return
        projectModels.values
            .filter { it.isVisible }
            .forEach { it.refreshSessions(current) }
    }

    private fun dropRemovedProjects() {
        val existing =
            mutableCatalog.value.projects
                .map { it.id }
                .toSet()
        val removed = projectModels.filterKeys { it !in existing }
        removed.forEach { (projectId, model) ->
            model.removeAllTabs()
            projectModels.remove(projectId)
        }
    }

    private companion object {
        const val DEFAULT_SERVER_NAME = "the computer"
    }
}
