package com.muxy.app.features.projectdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.design.components.TabStripItem
import com.muxy.app.features.projectdetail.terminal.TerminalTabPage
import com.muxy.app.models.TabTileKind
import java.util.UUID

@Composable
fun ProjectDetailScreen(
    viewModel: ProjectDetailViewModel,
    requestedTabId: UUID?,
    onBack: () -> Unit,
    onTool: (ProjectTool) -> Unit,
) {
    LaunchedEffect(requestedTabId) { requestedTabId?.let(viewModel::select) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val terminalSettings by viewModel.terminalSettings.collectAsStateWithLifecycle()
    val tabs = state.tabs.associateBy { it.id }
    val tabsState =
        ProjectTabsUiState(
            projectName = state.projectName,
            connectionName = state.connectionName,
            tabs = state.tabs.map { TabStripItem(it.id, it.displayTitle, TabTileKind.of(it.kind)) },
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
            UnsupportedTabPage(tab.displayTitle)
        } else {
            TerminalTabPage(session, terminalSettings, isActive)
        }
    }
}
