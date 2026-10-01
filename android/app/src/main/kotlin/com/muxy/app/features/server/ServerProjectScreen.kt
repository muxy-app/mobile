package com.muxy.app.features.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.ThemedBorderedButton
import com.muxy.app.design.components.ThemedEmptyState
import com.muxy.app.design.components.blockingTouches
import com.muxy.app.features.projectdetail.ProjectTabsScreen
import com.muxy.app.features.terminal.TerminalScreen
import com.muxy.app.features.terminal.TerminalSettings

@Composable
fun ServerProjectScreen(
    viewModel: ServerProjectViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.terminalSettings.collectAsStateWithLifecycle()
    val isConnectionLost by viewModel.isConnectionLost.collectAsStateWithLifecycle()
    ProjectTabsScreen(
        state = state,
        onSelect = viewModel::select,
        onClose = viewModel::close,
        onCreate = viewModel::createTab,
        onBack = onBack,
    ) { item, isActive ->
        val terminal = viewModel.terminal(item) ?: return@ProjectTabsScreen
        ServerTerminalPage(terminal, settings, isActive, isConnectionLost)
    }
}

@Composable
private fun ServerTerminalPage(
    terminal: ServerTerminal,
    settings: TerminalSettings,
    isActive: Boolean,
    isDisconnected: Boolean,
) {
    val phase by terminal.phase.collectAsStateWithLifecycle()
    val hasScreen by terminal.hasScreen.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize()) {
        TerminalScreen(controller = terminal.controller, settings = settings, isActive = isActive)
        TerminalOverlay(phase, hasScreen, isDisconnected, terminal::retry)
    }
}

@Composable
private fun TerminalOverlay(
    phase: ServerTerminalPhase,
    hasScreen: Boolean,
    isDisconnected: Boolean,
    onRetry: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val modifier = Modifier.fillMaxSize().background(theme.groupedBackground).blockingTouches()
    if (isDisconnected) {
        ThemedEmptyState(
            title = "Disconnected",
            icon = R.drawable.ic_wifi_off,
            message = "Reconnect to continue using this terminal.",
            modifier = modifier,
        )
        return
    }
    when (phase) {
        ServerTerminalPhase.Waiting, ServerTerminalPhase.Creating, ServerTerminalPhase.Attaching -> {
            if (hasScreen) return
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.accent)
            }
        }

        is ServerTerminalPhase.Failed -> {
            ThemedEmptyState(
                title = "Terminal Unavailable",
                icon = R.drawable.ic_warning,
                message = phase.message,
                modifier = modifier,
            ) {
                ThemedBorderedButton(text = "Try Again", onClick = onRetry)
            }
        }

        ServerTerminalPhase.Live, ServerTerminalPhase.Ended -> {
            Unit
        }
    }
}
