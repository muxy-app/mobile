package com.muxy.app.features.projectdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.features.projectdetail.terminal.TerminalTabPage

@Composable
fun ProjectDetailScreen(
    viewModel: ProjectDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val terminalSettings by viewModel.terminalSettings.collectAsStateWithLifecycle()
    ProjectTabsScreen(
        state = state,
        onSelect = viewModel::select,
        onClose = viewModel::closeTab,
        onCreate = viewModel::createTab,
        onBack = onBack,
    ) { tab, isActive ->
        val session = viewModel.terminalSession(tab)
        if (session == null) {
            UnsupportedTabPage(tab.title)
        } else {
            TerminalTabPage(session, terminalSettings, isActive)
        }
    }
}
