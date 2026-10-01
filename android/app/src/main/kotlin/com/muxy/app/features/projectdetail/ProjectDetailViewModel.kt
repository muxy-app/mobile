package com.muxy.app.features.projectdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.design.AppTheme
import com.muxy.app.design.ThemeCatalog
import com.muxy.app.features.projectdetail.terminal.ConnectionTerminalChannel
import com.muxy.app.features.projectdetail.terminal.TerminalSession
import com.muxy.app.features.projectdetail.terminal.TerminalSessionStore
import com.muxy.app.features.projectdetail.terminal.clientTerminalTheme
import com.muxy.app.features.terminal.TerminalClipboard
import com.muxy.app.features.terminal.TerminalSettings
import com.muxy.app.models.Connection
import com.muxy.app.models.Tab
import com.muxy.app.models.TabArea
import com.muxy.app.models.TabKind
import com.muxy.app.models.Workspace
import com.muxy.app.models.WorkspaceFlattening
import com.muxy.app.networking.muxy1.ConnectionManager
import com.muxy.app.networking.muxy1.ConnectionState
import com.muxy.app.networking.muxy1.protocol.CloseTabParams
import com.muxy.app.networking.muxy1.protocol.CreateTabParams
import com.muxy.app.networking.muxy1.protocol.ErrorCode
import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.networking.muxy1.protocol.EventType
import com.muxy.app.networking.muxy1.protocol.GetWorkspaceParams
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.networking.muxy1.protocol.SelectProjectParams
import com.muxy.app.networking.muxy1.protocol.SelectTabParams
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class ProjectTabsStatus {
    LOADING,
    READY,
    DISCONNECTED,
}

data class ProjectDetailUiState(
    val projectName: String,
    val connectionName: String,
    val tabs: List<Tab>,
    val selectedTabId: UUID?,
    val status: ProjectTabsStatus,
)

class ProjectDetailViewModel(
    private val connectionId: UUID,
    private val projectId: UUID,
    private val projectName: String,
    connectionStore: ConnectionStore,
    private val manager: ConnectionManager,
    settingsStore: SettingsStore,
    outbound: CoroutineScope,
    clipboard: TerminalClipboard,
) : ViewModel() {
    private val content = MutableStateFlow(Content())

    private val terminals = TerminalSessionStore(ConnectionTerminalChannel(connectionId, manager), viewModelScope, outbound, clipboard)

    val terminalSettings: StateFlow<TerminalSettings> =
        settingsStore.settings
            .filterNotNull()
            .map(TerminalSettings::from)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                settingsStore.settings.value?.let(TerminalSettings::from) ?: TerminalSettings(),
            )

    private val connection: StateFlow<Connection?> =
        connectionStore.connections
            .map { connections -> connections?.firstOrNull { it.id == connectionId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, connectionStore.connections.value?.firstOrNull { it.id == connectionId })

    private val connectionState: StateFlow<ConnectionState> =
        manager.status
            .map { it.of(connectionId) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, manager.status.value.of(connectionId))

    val uiState: StateFlow<ProjectDetailUiState> =
        combine(connection, content, connectionState, ::buildUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, buildUiState(connection.value, content.value, connectionState.value))

    init {
        viewModelScope.launch {
            manager.status
                .map { it.of(connectionId) to it.connectedSession(connectionId) }
                .distinctUntilChanged()
                .collect { (state, session) -> terminals.connectionChanged(state, session) }
        }
        viewModelScope.launch {
            content
                .map { allTabs(it) to it.selectedTabId }
                .distinctUntilChanged()
                .collect { (tabs, selected) ->
                    terminals.tabsChanged(tabs)
                    terminals.selectionChanged(selected, tabs)
                }
        }
        viewModelScope.launch {
            settingsStore.settings
                .filterNotNull()
                .map { AppTheme.from(ThemeCatalog.named(it.themeName)).terminalPalette.clientTerminalTheme() }
                .distinctUntilChanged()
                .collect(terminals::useClientTheme)
        }
        viewModelScope.launch {
            manager.status
                .mapNotNull { it.connectedSession(connectionId) }
                .distinctUntilChanged()
                .collectLatest { loadWorkspace() }
        }
        viewModelScope.launch {
            manager
                .events(connectionId)
                .filter { it.event == EventName.WORKSPACE_CHANGED }
                .collect(::applyWorkspaceEvent)
        }
    }

    fun terminalSession(tab: Tab): TerminalSession? = terminals.session(tab)

    override fun onCleared() {
        terminals.teardown()
    }

    fun select(tab: Tab) {
        if (content.value.selectedTabId == tab.id) return
        content.update { it.copy(selectedTabId = tab.id) }
        val areaId = areaContaining(tab.id)?.id ?: return
        send(Method.SELECT_TAB, SelectTabParams(projectId.uuidString, areaId.uuidString, tab.id.uuidString), "select tab")
    }

    fun createTab() {
        val params = CreateTabParams(projectId.uuidString, focusedArea()?.id?.uuidString, TabKind.Terminal.rawValue)
        viewModelScope.launch {
            attempt { manager.request(connectionId, Method.CREATE_TAB, params) }
                .mapCatching { result ->
                    check(result.type == ResultType.TAB) { "Unexpected result ${result.type}" }
                    result.decode(Tab.serializer())
                }.onSuccess { tab -> content.update { it.copy(selectedTabId = tab.id) } }
                .onFailure { Log.client.error("Failed to create tab", it) }
        }
    }

    fun closeTab(tab: Tab) {
        val areaId = areaContaining(tab.id)?.id ?: return
        removeLocally(tab.id)
        send(Method.CLOSE_TAB, CloseTabParams(projectId.uuidString, areaId.uuidString, tab.id.uuidString), "close tab")
    }

    private suspend fun loadWorkspace() {
        attempt { manager.request(connectionId, Method.SELECT_PROJECT, SelectProjectParams(projectId.uuidString)) }
            .onFailure { Log.client.error("Failed to select project", it) }
        attempt { manager.request(connectionId, Method.GET_WORKSPACE, GetWorkspaceParams(projectId.uuidString)) }
            .mapCatching { result ->
                check(result.type == ResultType.WORKSPACE) { "Unexpected result ${result.type}" }
                result.decode(Workspace.serializer())
            }.onSuccess(::apply)
            .onFailure { error ->
                if ((error as? ProtocolException)?.code == ErrorCode.NOT_FOUND) {
                    content.update { it.copy(workspace = null, hasLoaded = true) }
                    return
                }
                Log.client.error("Failed to load workspace", error)
            }
    }

    private fun applyWorkspaceEvent(event: EventEnvelope) {
        val data = event.data?.takeIf { it.type == EventType.WORKSPACE } ?: return
        runCatching { data.decode(Workspace.serializer()) }
            .onSuccess(::apply)
            .onFailure { Log.client.error("Failed to decode workspace event", it) }
    }

    private fun apply(workspace: Workspace) {
        if (workspace.projectId != projectId) return
        content.update { current ->
            val tabs = tabs(workspace)
            val keepsSelection = current.selectedTabId != null && tabs.any { it.id == current.selectedTabId }
            val selection =
                if (keepsSelection) {
                    current.selectedTabId
                } else {
                    WorkspaceFlattening.focusedTabArea(workspace)?.activeTabId
                        ?: tabs.firstOrNull()?.id
                }
            current.copy(workspace = workspace, hasLoaded = true, selectedTabId = selection)
        }
    }

    private fun removeLocally(tabId: UUID) {
        content.update { current ->
            val workspace = current.workspace ?: return@update current
            val owning = WorkspaceFlattening.areaContaining(tabId, workspace) ?: return@update current
            val remaining = owning.tabs.filterNot { it.id == tabId }
            val nextActive = if (owning.activeTabId == tabId) remaining.firstOrNull()?.id else owning.activeTabId
            val trimmed = owning.copy(tabs = remaining, activeTabId = nextActive)
            val updated = workspace.copy(root = WorkspaceFlattening.mapAreas(workspace.root) { if (it.id == owning.id) trimmed else it })
            val wasSelected = current.selectedTabId == tabId
            val fallback = remaining.firstOrNull()?.id ?: tabs(updated).firstOrNull()?.id
            current.copy(workspace = updated, selectedTabId = if (wasSelected) fallback else current.selectedTabId)
        }
    }

    private inline fun <reified P> send(
        method: Method,
        params: P,
        action: String,
    ) {
        viewModelScope.launch {
            attempt { manager.request(connectionId, method, params) }
                .onFailure { Log.client.error("Failed to $action", it) }
        }
    }

    private fun focusedArea(): TabArea? = content.value.workspace?.let(WorkspaceFlattening::focusedTabArea)

    private fun areaContaining(tabId: UUID): TabArea? = content.value.workspace?.let { WorkspaceFlattening.areaContaining(tabId, it) }

    private fun tabs(workspace: Workspace): List<Tab> = WorkspaceFlattening.tabAreas(workspace).flatMap(TabArea::tabs)

    private fun allTabs(content: Content): List<Tab> = content.workspace?.let(::tabs).orEmpty()

    private fun buildUiState(
        connection: Connection?,
        content: Content,
        state: ConnectionState,
    ) = ProjectDetailUiState(
        projectName = projectName,
        connectionName = connection?.name.orEmpty(),
        tabs = allTabs(content),
        selectedTabId = content.selectedTabId,
        status = status(state, content.hasLoaded),
    )

    private fun status(
        state: ConnectionState,
        hasLoaded: Boolean,
    ): ProjectTabsStatus =
        when (state) {
            ConnectionState.Idle, ConnectionState.Connecting, ConnectionState.Authenticating -> ProjectTabsStatus.LOADING
            ConnectionState.Connected -> if (hasLoaded) ProjectTabsStatus.READY else ProjectTabsStatus.LOADING
            ConnectionState.Disconnected, is ConnectionState.Failed -> ProjectTabsStatus.DISCONNECTED
        }

    private data class Content(
        val workspace: Workspace? = null,
        val hasLoaded: Boolean = false,
        val selectedTabId: UUID? = null,
    )
}
