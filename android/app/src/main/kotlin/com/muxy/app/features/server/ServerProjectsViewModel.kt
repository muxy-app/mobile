package com.muxy.app.features.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.features.projects.ProjectListItem
import com.muxy.app.features.projects.ProjectListStatus
import com.muxy.app.persistence.connections.ConnectionStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

data class ServerProjectsUiState(
    val connectionName: String,
    val items: List<ProjectListItem>,
    val status: ProjectListStatus,
) {
    val hasProjects: Boolean
        get() = items.isNotEmpty()
}

class ServerProjectsViewModel(
    connectionId: UUID,
    private val server: ServerController,
    connectionStore: ConnectionStore,
) : ViewModel() {
    private val connectionName =
        connectionStore.connections.map { connections -> connections?.firstOrNull { it.id == connectionId }?.name.orEmpty() }

    val uiState: StateFlow<ServerProjectsUiState> =
        combine(connectionName, server.phase, server.catalog, ::buildUiState)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                buildUiState(
                    connectionStore.connections.value
                        ?.firstOrNull { it.id == connectionId }
                        ?.name
                        .orEmpty(),
                    server.phase.value,
                    server.catalog.value,
                ),
            )

    fun retry() {
        if (server.phase.value != ServerPhase.Connected) {
            server.retryNow()
            return
        }
        server.reloadProjects()
    }

    private fun buildUiState(
        connectionName: String,
        phase: ServerPhase,
        catalog: ProjectCatalog,
    ) = ServerProjectsUiState(
        connectionName = connectionName,
        items = ProjectTree.rows(catalog.projects).map(ServerProjectListing::item),
        status = ServerProjectListing.status(phase, catalog, connectionName),
    )
}
