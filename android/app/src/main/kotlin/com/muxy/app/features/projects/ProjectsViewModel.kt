package com.muxy.app.features.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.models.Connection
import com.muxy.app.models.Project
import com.muxy.app.models.ProjectWorkspace
import com.muxy.app.networking.muxy1.ConnectionError
import com.muxy.app.networking.muxy1.ConnectionManager
import com.muxy.app.networking.muxy1.ConnectionState
import com.muxy.app.networking.muxy1.protocol.ErrorCode
import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.networking.muxy1.protocol.EventType
import com.muxy.app.networking.muxy1.protocol.GetProjectLogoParams
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProjectLogoResult
import com.muxy.app.networking.muxy1.protocol.ProjectsResult
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.workspaces.WorkspaceSelectionStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Base64
import java.util.UUID

data class ProjectsUiState(
    val connectionName: String,
    val items: List<ProjectListItem>,
    val hasProjects: Boolean,
    val workspaces: List<ProjectWorkspace>,
    val selectedWorkspaceId: UUID?,
    val status: ProjectListStatus,
)

class ProjectsViewModel(
    private val connectionId: UUID,
    connectionStore: ConnectionStore,
    private val manager: ConnectionManager,
    private val workspaceSelectionStore: WorkspaceSelectionStore,
) : ViewModel() {
    private val content = MutableStateFlow(Content())
    private val logoRequests = Channel<Unit>(Channel.CONFLATED)
    private var hasChosenWorkspace = false

    private val connection: StateFlow<Connection?> =
        connectionStore.connections
            .map { connections -> connections?.firstOrNull { it.id == connectionId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, connectionStore.connections.value?.firstOrNull { it.id == connectionId })

    private val connectionState: StateFlow<ConnectionState> =
        manager.status
            .map { it.of(connectionId) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, manager.status.value.of(connectionId))

    val uiState: StateFlow<ProjectsUiState> =
        combine(connection, content, connectionState, ::buildUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, buildUiState(connection.value, content.value, connectionState.value))

    init {
        viewModelScope.launch { restoreWorkspaceSelection() }
        viewModelScope.launch {
            manager.status
                .mapNotNull { it.connectedSession(connectionId) }
                .distinctUntilChanged()
                .collectLatest { loadProjects() }
        }
        viewModelScope.launch {
            manager
                .events(connectionId)
                .filter { it.event == EventName.PROJECTS_CHANGED }
                .collect(::applyProjectsEvent)
        }
        viewModelScope.launch {
            logoRequests.receiveAsFlow().collect { loadMissingLogos() }
        }
    }

    fun project(itemId: String): Project? = content.value.projects.firstOrNull { it.id.uuidString == itemId }

    fun selectWorkspace(workspaceId: UUID?) {
        hasChosenWorkspace = true
        if (content.value.selectedWorkspaceId == workspaceId) return
        content.update { it.copy(selectedWorkspaceId = workspaceId) }
        viewModelScope.launch { workspaceSelectionStore.save(workspaceId, connectionId) }
    }

    fun retry() {
        val connection = connection.value ?: return
        viewModelScope.launch { manager.reconnect(connection) }
    }

    private suspend fun restoreWorkspaceSelection() {
        val saved = workspaceSelectionStore.load(connectionId) ?: return
        if (hasChosenWorkspace) return
        content.update { it.copy(selectedWorkspaceId = saved) }
        pruneSelectedWorkspace()
    }

    private suspend fun loadProjects() {
        attempt { manager.request(connectionId, Method.LIST_PROJECTS) }
            .mapCatching { result ->
                check(result.type == ResultType.PROJECTS) { "Unexpected result ${result.type}" }
                result.decode(ProjectsResult.serializer()).projects
            }.onSuccess(::applyProjects)
            .onFailure { error ->
                Log.client.error("Failed to load projects", error)
                content.update { it.copy(load = LoadState.FAILED) }
            }
    }

    private fun applyProjectsEvent(event: EventEnvelope) {
        val data = event.data?.takeIf { it.type == EventType.PROJECTS } ?: return
        runCatching { data.decode(ProjectsResult.serializer()).projects }
            .onSuccess(::applyProjects)
            .onFailure { Log.client.error("Failed to decode projects event", it) }
    }

    private fun applyProjects(projects: List<Project>) {
        content.update { it.copy(projects = projects.sortedBy(Project::sortOrder), load = LoadState.LOADED) }
        pruneSelectedWorkspace()
        logoRequests.trySend(Unit)
    }

    private fun pruneSelectedWorkspace() {
        val current = content.value
        val selected = current.selectedWorkspaceId ?: return
        if (current.projects.isEmpty() || current.projects.any { it.workspaceId == selected }) return
        content.update { it.copy(selectedWorkspaceId = null) }
        viewModelScope.launch { workspaceSelectionStore.save(null, connectionId) }
    }

    private suspend fun loadMissingLogos() {
        content.value.projects
            .filter { it.logo != null && it.id !in content.value.logos }
            .forEach { loadLogo(it) }
    }

    private suspend fun loadLogo(project: Project) {
        attempt { manager.request(connectionId, Method.GET_PROJECT_LOGO, GetProjectLogoParams(project.id.uuidString)) }
            .mapCatching { result ->
                check(result.type == ResultType.PROJECT_LOGO) { "Unexpected result ${result.type}" }
                Base64.getDecoder().decode(result.decode(ProjectLogoResult.serializer()).pngData)
            }.onSuccess { png -> content.update { it.copy(logos = it.logos + (project.id to ProjectLogo(png))) } }
            .onFailure { error ->
                if ((error as? ProtocolException)?.code == ErrorCode.NOT_FOUND) return
                Log.client.error("Failed to load project logo", error)
            }
    }

    private fun buildUiState(
        connection: Connection?,
        content: Content,
        state: ConnectionState,
    ): ProjectsUiState {
        val workspaces = workspaces(content.projects)
        val selected = content.selectedWorkspaceId?.takeIf { id -> workspaces.any { it.id == id } }
        val visible = if (selected == null) content.projects else content.projects.filter { it.workspaceId == selected }
        return ProjectsUiState(
            connectionName = connection?.name.orEmpty(),
            items = visible.map { listItem(it, content.logos[it.id]) },
            hasProjects = content.projects.isNotEmpty(),
            workspaces = workspaces,
            selectedWorkspaceId = selected,
            status = status(state, content.load),
        )
    }

    private fun status(
        state: ConnectionState,
        load: LoadState,
    ): ProjectListStatus =
        when (state) {
            ConnectionState.Idle, ConnectionState.Connecting, ConnectionState.Authenticating -> ProjectListStatus.Loading
            ConnectionState.Connected -> loadedStatus(load)
            ConnectionState.Disconnected -> ProjectListStatus.Disconnected(message = null)
            is ConnectionState.Failed -> failedStatus(state.error)
        }

    private fun loadedStatus(load: LoadState): ProjectListStatus =
        when (load) {
            LoadState.NOT_LOADED -> ProjectListStatus.Loading
            LoadState.FAILED -> ProjectListStatus.LoadFailed
            LoadState.LOADED -> ProjectListStatus.Empty
        }

    private fun failedStatus(error: ConnectionError): ProjectListStatus =
        if (error == ConnectionError.MISSING_TOKEN) ProjectListStatus.NeedsPairing else ProjectListStatus.Disconnected(message = null)

    private fun workspaces(projects: List<Project>): List<ProjectWorkspace> =
        projects
            .mapNotNull { project ->
                val id = project.workspaceId ?: return@mapNotNull null
                val name = project.workspaceName ?: return@mapNotNull null
                ProjectWorkspace(id, name)
            }.distinctBy { it.id }

    private fun listItem(
        project: Project,
        logo: ProjectLogo?,
    ) = ProjectListItem(
        id = project.id.uuidString,
        name = project.name,
        path = project.path,
        symbol = project.icon,
        iconColor = project.iconColor,
        logo = logo,
    )

    private enum class LoadState {
        NOT_LOADED,
        LOADED,
        FAILED,
    }

    private data class Content(
        val projects: List<Project> = emptyList(),
        val logos: Map<UUID, ProjectLogo> = emptyMap(),
        val load: LoadState = LoadState.NOT_LOADED,
        val selectedWorkspaceId: UUID? = null,
    )
}
