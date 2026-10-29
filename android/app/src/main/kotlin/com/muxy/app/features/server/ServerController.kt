package com.muxy.app.features.server

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.models.TabActivity
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
    private val provider: ServerConnectionProvider,
    private val scope: CoroutineScope,
    private val schedule: ReconnectSchedule = ReconnectSchedule(),
) {
    constructor(
        serverId: String,
        connector: ServerConnector,
        credentialProvider: suspend () -> ServerCredential?,
        scope: CoroutineScope,
        schedule: ReconnectSchedule = ReconnectSchedule(),
    ) : this(serverId, PairedServerConnectionProvider(connector, credentialProvider), scope, schedule)

    private val mutablePhase = MutableStateFlow<ServerPhase>(ServerPhase.Idle)
    private val mutableCatalog = MutableStateFlow(ProjectCatalog())
    private val mutableActivity = MutableStateFlow<Map<ULong, TabActivity>>(emptyMap())

    val phase: StateFlow<ServerPhase> = mutablePhase.asStateFlow()
    val catalog: StateFlow<ProjectCatalog> = mutableCatalog.asStateFlow()
    val activity: StateFlow<Map<ULong, TabActivity>> = mutableActivity.asStateFlow()

    val serverName: String
        get() = provider.serverName

    var connection: ServerConnection? = null
        private set

    val fileWatches = FileWatches(scope)

    private val attachment = AttachmentCoordinator(this, scope)
    private val projectModels = mutableMapOf<String, ProjectModel>()
    private val terminalsBySession = mutableMapOf<ULong, ServerTerminal>()
    private val projectsRefresh = CoalescedRefresh(scope)
    private val sessionsRefresh = CoalescedRefresh(scope)
    private val activityRefresh = CoalescedRefresh(scope)
    private var pendingEventIds = emptyMap<ULong, List<ULong>>()
    private var visibleProjectId: String? = null
    private var isBrowsing = false
    private var generation = 0
    private var wantsConnection = false
    private var isConnecting = false
    private var restartPending = false
    private var reconnectJob: Job? = null
    private var connectJob: Job? = null

    fun project(projectId: String): ServerProject? = mutableCatalog.value.projects.firstOrNull { it.id == projectId }

    fun projectModel(projectId: String): ProjectModel {
        projectModels[projectId]?.let { return it }
        val model = ProjectModel(projectId, this, scope)
        projectModels[projectId] = model
        model.setBrowsing(isBrowsing && project(projectId) != null)
        if (projectId == visibleProjectId) model.setVisible(true)
        return model
    }

    fun setBrowsing(browsing: Boolean) {
        if (isBrowsing == browsing) return
        isBrowsing = browsing
        updateBrowsingProjects()
        if (browsing) reloadSessions()
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
        val visible = projectModels.values.firstOrNull { it.isVisible }?.selectedTab
        attachment.show(visible)
        visible?.sessionId?.let(::acknowledgeActivity)
    }

    fun acknowledgeActivity(sessionId: ULong) {
        val current = connection ?: return
        val eventIds = pendingEventIds[sessionId].orEmpty()
        if (eventIds.isEmpty()) return
        pendingEventIds = pendingEventIds - sessionId
        mutableActivity.update { activity ->
            val seen = activity[sessionId] ?: return@update activity
            val state = if (seen.state == TabActivity.State.WORKING) TabActivity.State.WORKING else TabActivity.State.IDLE
            activity + (sessionId to seen.copy(state = state, since = null))
        }
        scope.launch {
            attempt { current.acknowledgeActivity(eventIds) }
                .onFailure { error ->
                    Log.connection.error("Acknowledging activity failed: ${ServerFailure.from(error)}")
                    reloadActivity()
                }
        }
    }

    fun viewportDidChange(terminal: ServerTerminal) {
        attachment.viewportDidChange(terminal)
    }

    fun attachDidFail(terminal: ServerTerminal) {
        projectModels[terminal.projectId]?.let(::refreshSessions)
    }

    fun refreshSessions(model: ProjectModel) {
        val current = connection ?: return
        model.refreshSessions(current)
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
        connectJob = scope.launch { connect(token) }
    }

    private suspend fun connect(token: Int) {
        attempt { provider.connect { event -> scope.launch { handle(event, token) } } }
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
        fileWatches.connectionChanged(opened)
        mutablePhase.value = ServerPhase.Connected
        schedule.reset()
        restartPending = false
        Log.connection.info("Connected to Muxy ${opened.serverVersion}")
        projectsRefresh.request(::loadProjects)
        sessionsRefresh.request(::loadSessions)
        reloadActivity()
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
            is ConnectionEvent.FilesChanged -> fileWatches.deliver(event.projectId, event.paths)
            ConnectionEvent.ActivityChanged -> reloadActivity()
            is ConnectionEvent.GitChanged -> Unit
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
        connectJob?.cancel()
        connectJob = null
        generation += 1
        isConnecting = false
        restartPending = false
        val closing = connection
        connection = null
        fileWatches.connectionChanged(null)
        attachment.connectionDidClose()
        projectModels.values.forEach(ProjectModel::connectionDidClose)
        projectsRefresh.cancel()
        sessionsRefresh.cancel()
        activityRefresh.cancel()
        pendingEventIds = emptyMap()
        mutableActivity.value = emptyMap()
        mutableCatalog.update { it.copy(isRefreshing = false) }
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

    private fun reloadActivity() {
        if (connection == null) return
        activityRefresh.request(::loadActivity)
    }

    private suspend fun loadActivity() {
        val current = connection ?: return
        val token = generation
        attempt { current.activity() }
            .onSuccess { activity ->
                if (current !== connection || token != generation) return@onSuccess
                pendingEventIds = activity.events.groupBy({ it.sessionId }, { it.id })
                mutableActivity.value = TabActivitySdk.bySession(activity)
                projectModels.values
                    .firstOrNull { it.isVisible }
                    ?.selectedTab
                    ?.sessionId
                    ?.let(::acknowledgeActivity)
            }.onFailure { error ->
                if (current !== connection || token != generation) return@onFailure
                Log.connection.error("Loading activity failed: ${ServerFailure.from(error)}")
            }
    }

    private suspend fun loadProjects() {
        val current = connection ?: return
        val token = generation
        mutableCatalog.update { it.copy(isRefreshing = true) }
        attempt { current.projects() }
            .onSuccess { projects ->
                if (current !== connection || token != generation) return@onSuccess
                mutableCatalog.value = ProjectCatalog(projects, hasLoaded = true, loadFailed = false)
                closeRemovedProjects()
                updateBrowsingProjects()
                if (isBrowsing) reloadSessions()
            }.onFailure { error ->
                if (current !== connection || token != generation) return@onFailure
                mutableCatalog.update { it.copy(loadFailed = true, isRefreshing = false) }
                Log.connection.error("Loading projects failed: ${ServerFailure.from(error)}")
            }
    }

    private suspend fun loadSessions() {
        val current = connection ?: return
        projectModels.values
            .filter { it.needsSessions }
            .forEach { it.refreshSessions(current) }
    }

    private fun updateBrowsingProjects() {
        val projectIds =
            mutableCatalog.value.projects
                .map { it.id }
                .toSet()
        if (isBrowsing) projectIds.forEach(::projectModel)
        projectModels.values.forEach { it.setBrowsing(isBrowsing && it.projectId in projectIds) }
    }

    private fun closeRemovedProjects() {
        val existing =
            mutableCatalog.value.projects
                .map { it.id }
                .toSet()
        projectModels.values
            .filter { it.projectId !in existing }
            .forEach(ProjectModel::removeAllTabs)
    }
}
