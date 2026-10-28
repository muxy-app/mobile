package com.muxy.app.features.tabs

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.features.server.CoalescedRefresh
import com.muxy.app.models.Connection
import com.muxy.app.models.DisplayPath
import com.muxy.app.models.Project
import com.muxy.app.models.Tab
import com.muxy.app.models.TabArea
import com.muxy.app.models.TabKind
import com.muxy.app.models.Workspace
import com.muxy.app.models.WorkspaceFlattening
import com.muxy.app.networking.muxy1.ConnectionError
import com.muxy.app.networking.muxy1.ConnectionManager
import com.muxy.app.networking.muxy1.ConnectionState
import com.muxy.app.networking.muxy1.ConnectionStatus
import com.muxy.app.networking.muxy1.createTerminalTab
import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.networking.muxy1.protocol.EventType
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProjectsResult
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.networking.muxy1.readWorkspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

internal class DeviceTabsCatalog(
    private val connection: Connection,
    private val manager: ConnectionManager,
    private val parentScope: CoroutineScope,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val refresh = CoalescedRefresh(scope)
    private val workspaceRequests = Semaphore(4)
    private val mutableState = MutableStateFlow(TabsDevice(connection))
    val state = mutableState.asStateFlow()

    private var active = false
    private var generation = 0
    private var session: Long? = null
    private var connectionJob: Job? = null
    private var status = TabsDeviceStatus.OFFLINE
    private var connectionMessage: String? = null
    private var projects = emptyList<Project>()
    private var projectsRevision = 0
    private var hasLoadedProjects = false
    private var projectsFailed = false
    private var loading = false
    private val workspaces = mutableMapOf<UUID, Workspace>()
    private val workspaceRevisions = mutableMapOf<UUID, Int>()
    private val loadedWorkspaces = mutableSetOf<UUID>()
    private val failedWorkspaces = mutableSetOf<UUID>()
    private val loadingWorkspaces = mutableSetOf<UUID>()

    init {
        scope.launch { manager.status.collect(::connectionChanged) }
        scope.launch { manager.events(connection.id).collect(::receive) }
    }

    fun setActive(active: Boolean) {
        if (this.active == active) return
        this.active = active
        invalidate()
        if (active) {
            connect(force = false)
            return
        }
        status = TabsDeviceStatus.OFFLINE
        connectionMessage = null
        publish()
        connectionJob = scope.launch { manager.disconnect() }
    }

    fun retry() {
        if (!active) return
        if (manager.status.value.connectedSession(connection.id) != null) {
            refresh.request { load(includeProjects = true) }
            return
        }
        invalidate()
        connect(force = true)
    }

    suspend fun createTab(projectId: String): UUID? {
        val project = projects.firstOrNull { it.id.uuidString == projectId } ?: return null
        val currentSession = session ?: return null
        val token = generation
        val tab = manager.createTerminalTab(connection.id, project.id) { isCurrent(currentSession, token) } ?: return null
        refresh.request { load(includeProjects = false) }
        return tab.id
    }

    fun close() {
        active = false
        invalidate()
        scope.cancel()
        parentScope.launch { manager.close() }
    }

    private fun connect(force: Boolean) {
        status = TabsDeviceStatus.CONNECTING
        connectionMessage = null
        publish()
        val token = generation
        connectionJob =
            scope.launch {
                attempt { if (force) manager.reconnect(connection) else manager.ensureConnected(connection) }
                    .onSuccess {
                        if (active && token == generation) connectionChanged(manager.status.value)
                    }.onFailure {
                        if (!active || token != generation) return@onFailure
                        status = TabsDeviceStatus.OFFLINE
                        connectionMessage = "Couldn't connect to this device."
                        publish()
                        Log.connection.error("Connecting a tab catalog failed", it)
                    }
            }
    }

    private fun invalidate() {
        generation += 1
        session = null
        connectionJob?.cancel()
        connectionJob = null
        refresh.cancel()
        loading = false
        loadingWorkspaces.clear()
    }

    private fun connectionChanged(connectionStatus: ConnectionStatus) {
        if (!active) return
        val state = connectionStatus.of(connection.id)
        connectionMessage = null
        status =
            when (state) {
                ConnectionState.Idle, ConnectionState.Connecting, ConnectionState.Authenticating -> TabsDeviceStatus.CONNECTING
                ConnectionState.Connected -> if (connection.id == DemoConnection.id) TabsDeviceStatus.DEMO else TabsDeviceStatus.ONLINE
                ConnectionState.Disconnected, is ConnectionState.Failed -> TabsDeviceStatus.OFFLINE
            }
        if (state is ConnectionState.Failed) {
            connectionMessage =
                when (state.error) {
                    ConnectionError.MISSING_TOKEN, ConnectionError.AUTHENTICATION_FAILED -> "Pair this device again from Projects."
                    ConnectionError.INVALID_ENDPOINT -> "Edit this device to check its address."
                    ConnectionError.CONNECTION_FAILED, ConnectionError.NOT_CONNECTED -> "Couldn't reach this device."
                }
        }
        val nextSession = connectionStatus.connectedSession(connection.id)
        if (nextSession == null) {
            session = null
            refresh.cancel()
            loading = false
            loadingWorkspaces.clear()
            publish()
            return
        }
        val needsLoad = session != nextSession
        session = nextSession
        publish()
        if (needsLoad) refresh.request { load(includeProjects = true) }
    }

    private suspend fun load(includeProjects: Boolean) {
        val currentSession = session ?: return
        val token = generation
        if (!isCurrent(currentSession, token)) return
        loading = true
        publish()
        try {
            if (includeProjects) loadProjects(currentSession, token)
            if (!isCurrent(currentSession, token)) return
            val listed = projects.toList()
            loadingWorkspaces.addAll(listed.map { it.id })
            publish()
            coroutineScope {
                listed.forEach { project ->
                    launch { workspaceRequests.withPermit { loadWorkspace(project, currentSession, token) } }
                }
            }
        } finally {
            if (isCurrent(currentSession, token)) {
                loading = false
                loadingWorkspaces.clear()
                publish()
            }
        }
    }

    private suspend fun loadProjects(
        currentSession: Long,
        token: Int,
    ) {
        val revision = projectsRevision
        attempt {
            val result = manager.request(connection.id, Method.LIST_PROJECTS)
            check(result.type == ResultType.PROJECTS) { "Unexpected result ${result.type}" }
            result.decode(ProjectsResult.serializer()).projects
        }.onSuccess {
            if (isCurrent(currentSession, token) && revision == projectsRevision) applyProjects(it)
        }.onFailure {
            if (!isCurrent(currentSession, token) || revision != projectsRevision) return@onFailure
            projectsFailed = true
            Log.client.error("Loading home projects failed", it)
        }
    }

    private suspend fun loadWorkspace(
        project: Project,
        currentSession: Long,
        token: Int,
    ) {
        if (!isCurrent(currentSession, token)) return
        val revision = workspaceRevisions[project.id] ?: 0
        val result = attempt { manager.readWorkspace(connection.id, project.id) }
        if (!isCurrent(currentSession, token) || projects.none { it.id == project.id }) return
        loadingWorkspaces -= project.id
        if (revision != (workspaceRevisions[project.id] ?: 0)) return publish()
        result
            .onSuccess { workspace ->
                if (workspace == null) {
                    workspaces.remove(project.id)
                    loadedWorkspaces += project.id
                    failedWorkspaces -= project.id
                    return@onSuccess
                }
                applyWorkspace(workspace)
            }.onFailure { error ->
                failedWorkspaces += project.id
                Log.client.error("Loading home tabs failed", error)
            }
        publish()
    }

    private fun receive(event: EventEnvelope) {
        val currentSession = session ?: return
        if (!isCurrent(currentSession, generation)) return
        val data = event.data ?: return
        when (event.event) {
            EventName.PROJECTS_CHANGED -> {
                if (data.type != EventType.PROJECTS) return
                runCatching { data.decode(ProjectsResult.serializer()).projects }
                    .onSuccess {
                        applyProjects(it)
                        refresh.request { load(includeProjects = false) }
                    }.onFailure { Log.client.error("Decoding home projects failed", it) }
            }

            EventName.WORKSPACE_CHANGED -> {
                if (data.type != EventType.WORKSPACE) return
                runCatching { data.decode(Workspace.serializer()) }
                    .onSuccess(::applyWorkspace)
                    .onFailure { Log.client.error("Decoding home tabs failed", it) }
            }
        }
    }

    private fun applyProjects(projects: List<Project>) {
        projectsRevision += 1
        this.projects = projects.sortedBy(Project::sortOrder)
        hasLoadedProjects = true
        projectsFailed = false
        val ids = projects.map { it.id }.toSet()
        workspaces.keys.filter { it !in ids }.forEach { workspaceRevisions[it] = (workspaceRevisions[it] ?: 0) + 1 }
        workspaces.keys.retainAll(ids)
        loadedWorkspaces.retainAll(ids)
        failedWorkspaces.retainAll(ids)
        publish()
    }

    private fun applyWorkspace(workspace: Workspace) {
        workspaceRevisions[workspace.projectId] = (workspaceRevisions[workspace.projectId] ?: 0) + 1
        workspaces[workspace.projectId] = workspace
        loadedWorkspaces += workspace.projectId
        failedWorkspaces -= workspace.projectId
        publish()
    }

    private fun isCurrent(
        currentSession: Long,
        token: Int,
    ): Boolean = active && token == generation && manager.status.value.connectedSession(connection.id) == currentSession

    private fun publish() {
        val message =
            connectionMessage ?: when {
                projectsFailed -> "Couldn't refresh projects. Try again."
                failedWorkspaces.isNotEmpty() -> "Couldn't refresh all tabs. Try again."
                else -> null
            }
        mutableState.value =
            TabsDevice(
                connection = connection,
                status = status,
                hasLoadedProjects = hasLoadedProjects,
                isLoading = loading,
                message = message,
                projects =
                    projects.map { project ->
                        val areas = workspaces[project.id]?.let(WorkspaceFlattening::tabAreas).orEmpty()
                        TabsProject(
                            id = project.id.uuidString,
                            name = project.name,
                            color = project.iconColor,
                            path = project.path,
                            tabs = areas.flatMap { area -> area.tabs.map { tab -> deviceTab(tab, area) } },
                            hasLoadedTabs = project.id in loadedWorkspaces,
                            isLoading = project.id in loadingWorkspaces,
                            loadFailed = project.id in failedWorkspaces,
                        )
                    },
            )
    }
}

private fun deviceTab(
    tab: Tab,
    area: TabArea,
): TabsTab =
    TabsTab(
        id = tab.id,
        title = tab.title.ifBlank { "Terminal" },
        kind = tab.kind,
        detail = if (tab.kind == TabKind.Vcs) "Source control" else DisplayPath.abbreviated(area.projectPath),
    )
