package com.muxy.app.features.projectdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.design.components.TabStripItem
import com.muxy.app.features.projectdetail.terminal.TerminalTabPage

@Composable
fun ProjectDetailScreen(
    viewModel: ProjectDetailViewModel,
    onBack: () -> Unit,
    onTool: (ProjectTool) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val terminalSettings by viewModel.terminalSettings.collectAsStateWithLifecycle()
    val tabs = state.tabs.associateBy { it.id }
    val tabsState =
        ProjectTabsUiState(
            projectName = state.projectName,
            connectionName = state.connectionName,
            tabs = state.tabs.map { TabStripItem(it.id, it.title, it.kind.icon()) },
            selectedTabId = state.selectedTabId,
            status = state.status,
        )
    ProjectTabsScreen(
        state = tabsState,
        onSelect = { item -> tabs[item.id]?.let(viewModel::select) },
        onClose = { item -> tabs[item.id]?.let(viewModel::closeTab) },
        onCreate = viewModel::createTab,
        onBack = onBack,
        onTool = onTool,
    ) { item, isActive ->
        val tab = tabs[item.id] ?: return@ProjectTabsScreen
        val session = viewModel.terminalSession(tab)
        if (session == null) {
            UnsupportedTabPage(tab.title)
        } else {
            TerminalTabPage(session, terminalSettings, isActive)
        }
    }
}
