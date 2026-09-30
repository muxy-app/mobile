package com.muxy.app.features.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.models.Project

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onSelect: (Project) -> Unit,
    onPairAgain: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProjectListScreen(
        title = state.connectionName,
        connectionName = state.connectionName,
        items = state.items,
        hasProjects = state.hasProjects,
        workspaces = state.workspaces,
        selectedWorkspaceId = state.selectedWorkspaceId,
        status = state.status,
        onSelect = { item -> viewModel.project(item.id)?.let(onSelect) },
        onSelectWorkspace = viewModel::selectWorkspace,
        onRetry = viewModel::retry,
        onPairAgain = onPairAgain,
        onBack = onBack,
    )
}
