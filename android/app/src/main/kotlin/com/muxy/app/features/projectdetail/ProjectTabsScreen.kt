package com.muxy.app.features.projectdetail

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.TabStrip
import com.muxy.app.design.components.TabStripItem
import com.muxy.app.design.components.ThemedEmptyState
import com.muxy.app.design.components.ThemedProminentButton
import com.muxy.app.design.components.TopBarAction
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

data class ProjectTabsUiState(
    val projectName: String,
    val connectionName: String,
    val tabs: List<TabStripItem>,
    val selectedTabId: Any?,
    val status: ProjectTabsStatus,
)

@Composable
fun ProjectTabsScreen(
    state: ProjectTabsUiState,
    onSelect: (TabStripItem) -> Unit,
    onClose: (TabStripItem) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    page: @Composable (tab: TabStripItem, isActive: Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            MuxyTopAppBar(
                title = state.projectName,
                navigationIcon = { TopBarAction(R.drawable.ic_arrow_back, "Back", onBack) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            if (state.tabs.isEmpty()) {
                EmptyTabs(state.status, state.connectionName, onCreate)
                return@Column
            }
            TabStrip(
                tabs = state.tabs,
                selectedTabId = state.selectedTabId,
                onSelect = onSelect,
                onClose = onClose,
                onCreate = onCreate,
            )
            TabPager(state.tabs, state.selectedTabId, onSelect, page)
        }
    }
}

@Composable
private fun TabPager(
    tabs: List<TabStripItem>,
    selectedTabId: Any?,
    onSelect: (TabStripItem) -> Unit,
    page: @Composable (tab: TabStripItem, isActive: Boolean) -> Unit,
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }
    val pagerState = rememberPagerState(initialPage = selectedIndex.coerceAtLeast(0)) { tabs.size }
    val currentTabs = rememberUpdatedState(tabs)
    val currentOnSelect = rememberUpdatedState(onSelect)
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && pagerState.currentPage != selectedIndex) pagerState.animateScrollToPage(selectedIndex)
    }
    LaunchedEffect(pagerState) { reportSwipes(pagerState) { page -> currentTabs.value.getOrNull(page)?.let(currentOnSelect.value) } }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { tabs[it].id },
    ) { index ->
        page(tabs[index], pagerState.settledPage == index)
    }
}

private suspend fun reportSwipes(
    pagerState: PagerState,
    onSwipe: (Int) -> Unit,
) {
    coroutineScope {
        var isDragged = false
        launch {
            pagerState.interactionSource.interactions
                .filterIsInstance<DragInteraction.Start>()
                .collect { isDragged = true }
        }
        snapshotFlow { pagerState.isScrollInProgress }
            .filter { !it }
            .collect {
                if (!isDragged) return@collect
                isDragged = false
                onSwipe(pagerState.currentPage)
            }
    }
}

@Composable
private fun EmptyTabs(
    status: ProjectTabsStatus,
    connectionName: String,
    onCreate: () -> Unit,
) {
    when (status) {
        ProjectTabsStatus.LOADING -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalAppTheme.current.accent)
            }
        }

        ProjectTabsStatus.READY -> {
            ThemedEmptyState(
                title = "No Tabs",
                icon = R.drawable.ic_web_asset,
                message = "Create a tab to get started.",
            ) {
                ThemedProminentButton(text = "New Tab", onClick = onCreate)
            }
        }

        ProjectTabsStatus.DISCONNECTED -> {
            ThemedEmptyState(
                title = "Not Connected",
                icon = R.drawable.ic_wifi_off,
                message = "Reconnect to $connectionName to see this project.",
            )
        }
    }
}
