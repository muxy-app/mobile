package com.muxy.app.features.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.features.projects.ProjectListScreen

@Composable
fun ServerProjectsScreen(
    viewModel: ServerProjectsViewModel,
    onSelect: (projectId: String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProjectListScreen(
        title = state.connectionName,
        connectionName = state.connectionName,
        items = state.items,
        hasProjects = state.hasProjects,
        workspaces = emptyList(),
        selectedWorkspaceId = null,
        status = state.status,
        onSelect = { onSelect(it.id) },
        onSelectWorkspace = {},
        onRetry = viewModel::retry,
        onPairAgain = {},
        onBack = onBack,
    )
}
