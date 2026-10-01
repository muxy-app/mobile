package com.muxy.app.features.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.R
import com.muxy.app.design.components.TabStripItem
import com.muxy.app.features.projectdetail.ProjectTabsUiState
import com.muxy.app.features.terminal.TerminalSettings
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.settings.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

class ServerProjectViewModel(
    connectionId: UUID,
    private val projectId: String,
    private val server: ServerController,
    connectionStore: ConnectionStore,
    settingsStore: SettingsStore,
) : ViewModel() {
    private val model = server.projectModel(projectId)

    private val connectionName: Flow<String> =
        connectionStore.connections.map { connections -> connections?.firstOrNull { it.id == connectionId }?.name.orEmpty() }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tabItems: Flow<List<TabStripItem>> =
        model.tabs.flatMapLatest { tabs ->
            if (tabs.isEmpty()) return@flatMapLatest flowOf(emptyList())
            combine(tabs.map { tab -> tab.title.map { TabStripItem(tab.id, it, R.drawable.ic_terminal) } }) { it.toList() }
        }

    private val content: Flow<Content> =
        combine(model.selectedTabId, model.hasLoadedSessions, server.catalog, ::Content)

    val uiState: StateFlow<ProjectTabsUiState> =
        combine(connectionName, tabItems, content, server.phase, ::buildUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialState())

    val isConnectionLost: StateFlow<Boolean> =
        server.phase
            .map { it.isConnectionLost }
            .stateIn(viewModelScope, SharingStarted.Eagerly, server.phase.value.isConnectionLost)

    val terminalSettings: StateFlow<TerminalSettings> =
        settingsStore.settings
            .filterNotNull()
            .map(TerminalSettings::from)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                settingsStore.settings.value?.let(TerminalSettings::from) ?: TerminalSettings(),
            )

    fun terminal(item: TabStripItem): ServerTerminal? = model.tabs.value.firstOrNull { it.id == item.id }

    fun select(item: TabStripItem) {
        terminal(item)?.let(model::select)
    }

    fun close(item: TabStripItem) {
        terminal(item)?.let(model::close)
    }

    fun createTab() {
        model.createTab()
    }

    private fun initialState(): ProjectTabsUiState =
        buildUiState(
            connectionName = "",
            tabs = model.tabs.value.map { TabStripItem(it.id, it.title.value, R.drawable.ic_terminal) },
            content = Content(model.selectedTabId.value, model.hasLoadedSessions.value, server.catalog.value),
            phase = server.phase.value,
        )

    private fun buildUiState(
        connectionName: String,
        tabs: List<TabStripItem>,
        content: Content,
        phase: ServerPhase,
    ): ProjectTabsUiState {
        val project = content.catalog.projects.firstOrNull { it.id == projectId }
        val isRemoved = content.catalog.hasLoaded && project == null
        return ProjectTabsUiState(
            projectName = project?.name ?: DEFAULT_PROJECT_NAME,
            connectionName = connectionName,
            tabs = tabs,
            selectedTabId = content.selectedTabId,
            status = ServerProjectListing.tabsStatus(phase, content.hasLoadedSessions || isRemoved),
        )
    }

    private data class Content(
        val selectedTabId: UUID?,
        val hasLoadedSessions: Boolean,
        val catalog: ProjectCatalog,
    )

    private companion object {
        const val DEFAULT_PROJECT_NAME = "Project"
    }
}
