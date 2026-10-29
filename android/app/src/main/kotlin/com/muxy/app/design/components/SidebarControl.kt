package com.muxy.app.design.components

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.muxy.app.R

@Stable
class SidebarState(
    isVisible: Boolean,
) {
    var isVisible by mutableStateOf(isVisible)
        private set

    var activeTabId by mutableStateOf<Any?>(null)
        private set

    fun toggle() {
        isVisible = !isVisible
    }

    fun select(tabId: Any) {
        activeTabId = tabId
    }

    fun clear(tabId: Any) {
        if (activeTabId == tabId) activeTabId = null
    }

    companion object {
        val Saver: Saver<SidebarState, Boolean> = Saver(save = { it.isVisible }, restore = ::SidebarState)
    }
}

@Composable
fun rememberSidebarState(): SidebarState = rememberSaveable(saver = SidebarState.Saver) { SidebarState(isVisible = true) }

data class SidebarControl(
    val state: SidebarState,
    val isPaneRoot: Boolean,
)

val LocalSidebarControl = staticCompositionLocalOf<SidebarControl?> { null }

val LocalActiveTabId = compositionLocalOf<Any?> { null }

@Composable
fun PaneNavigationIcon(onBack: () -> Unit) {
    val sidebar = LocalSidebarControl.current
    Row {
        if (sidebar?.isPaneRoot != true) TopBarAction(R.drawable.ic_arrow_back, "Back", onBack)
        if (sidebar != null) SidebarToggle(sidebar.state)
    }
}

@Composable
fun SidebarToggle(state: SidebarState) {
    TopBarAction(R.drawable.ic_sidebar, if (state.isVisible) "Hide sidebar" else "Show sidebar", state::toggle)
}
