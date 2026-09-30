package com.muxy.app.features.projectdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProjectDetailScreen(
    viewModel: ProjectDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProjectTabsScreen(
        state = state,
        onSelect = viewModel::select,
        onClose = viewModel::closeTab,
        onCreate = viewModel::createTab,
        onBack = onBack,
    )
}
