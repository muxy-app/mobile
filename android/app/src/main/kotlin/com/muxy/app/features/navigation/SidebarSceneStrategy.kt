package com.muxy.app.features.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.LocalActiveTabId
import com.muxy.app.design.components.LocalSidebarControl
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.SidebarControl
import com.muxy.app.design.components.SidebarState
import com.muxy.app.design.components.SidebarToggle
import com.muxy.app.design.components.ThemedEmptyState
import com.muxy.app.design.components.WindowWidth

class SidebarSceneStrategy(
    private val windowWidth: WindowWidth,
    private val sidebar: SidebarState,
) : SceneStrategy<AppRoute> {
    override fun SceneStrategyScope<AppRoute>.calculateScene(entries: List<NavEntry<AppRoute>>): Scene<AppRoute>? {
        if (!windowWidth.isWide) return null
        val home = entries.firstOrNull()?.takeIf { SIDEBAR_KEY in it.metadata } ?: return null
        val pane = entries.drop(1).lastOrNull()
        if (pane != null && PANE_KEY !in pane.metadata) return null
        return SidebarScene(
            home = home,
            pane = pane,
            previousEntries = entries.dropLast(1),
            sidebarWidth = if (windowWidth == WindowWidth.EXPANDED) 340.dp else 300.dp,
            control = SidebarControl(sidebar, isPaneRoot = entries.size <= 2),
        )
    }

    companion object {
        private const val SIDEBAR_KEY = "muxy.sidebar"
        private const val PANE_KEY = "muxy.pane"

        fun sidebar(): Map<String, Any> = mapOf(SIDEBAR_KEY to true)

        fun pane(): Map<String, Any> = mapOf(PANE_KEY to true)
    }
}

private data class SidebarScene(
    private val home: NavEntry<AppRoute>,
    private val pane: NavEntry<AppRoute>?,
    override val previousEntries: List<NavEntry<AppRoute>>,
    private val sidebarWidth: Dp,
    private val control: SidebarControl,
) : Scene<AppRoute> {
    override val key: Any = SidebarScene::class

    override val entries: List<NavEntry<AppRoute>> = listOfNotNull(home, pane)

    override val content: @Composable () -> Unit = {
        SidebarLayout(home, pane, sidebarWidth, control)
    }
}

@Composable
private fun SidebarLayout(
    home: NavEntry<AppRoute>,
    pane: NavEntry<AppRoute>?,
    sidebarWidth: Dp,
    control: SidebarControl,
) {
    val theme = LocalAppTheme.current
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(theme.groupedBackground)
                .onPreviewKeyEvent { event ->
                    if (!event.isSidebarShortcut) return@onPreviewKeyEvent false
                    control.state.toggle()
                    true
                },
    ) {
        AnimatedVisibility(
            visible = control.state.isVisible,
            enter = expandHorizontally(expandFrom = Alignment.Start),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start),
        ) {
            Row {
                Box(modifier = Modifier.width(sidebarWidth).fillMaxHeight()) {
                    CompositionLocalProvider(LocalActiveTabId provides control.state.activeTabId) { home.Content() }
                }
                VerticalDivider(color = theme.separator)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            CompositionLocalProvider(LocalSidebarControl provides control) {
                if (pane == null) {
                    PanePlaceholder(control.state)
                } else {
                    key(pane.contentKey) { pane.Content() }
                }
            }
        }
    }
}

@Composable
private fun PanePlaceholder(sidebar: SidebarState) {
    Scaffold(
        topBar = { MuxyTopAppBar(title = "", navigationIcon = { SidebarToggle(sidebar) }) },
        containerColor = LocalAppTheme.current.groupedBackground,
    ) { padding ->
        ThemedEmptyState(
            title = "No Tab Open",
            icon = R.drawable.ic_web_asset,
            message = "Choose a tab from the sidebar, or create a new one.",
            modifier = Modifier.padding(padding).fillMaxSize(),
        )
    }
}

private val KeyEvent.isSidebarShortcut: Boolean
    get() = type == KeyEventType.KeyDown && isCtrlPressed && isShiftPressed && key == Key.S
