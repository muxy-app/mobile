package com.muxy.app.features.sshterminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.TabStrip
import com.muxy.app.design.components.ThemedBorderedButton
import com.muxy.app.design.components.ThemedEmptyState
import com.muxy.app.design.components.ThemedProminentButton
import com.muxy.app.design.components.TopBarAction
import com.muxy.app.design.components.blockingTouches
import com.muxy.app.features.projectdetail.TabPager
import com.muxy.app.features.terminal.TerminalScreen
import com.muxy.app.features.terminal.TerminalSettings
import com.muxy.app.networking.ssh.SshConnectionState

@Composable
fun SshTerminalScreen(
    viewModel: SshTerminalViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.terminalSettings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            MuxyTopAppBar(
                title = state.name,
                navigationIcon = { TopBarAction(R.drawable.ic_arrow_back, "Back", onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            if (state.loading) {
                ConnectingOverlay(Modifier.fillMaxSize())
                return@Column
            }
            val error = state.error
            if (error != null) {
                ThemedEmptyState(title = "Connection Failed", icon = R.drawable.ic_warning, message = error)
                return@Column
            }
            if (state.tabs.isEmpty()) {
                ThemedEmptyState(title = "No Tabs", icon = R.drawable.ic_terminal, message = "Create a tab to open a shell.") {
                    ThemedProminentButton(text = "New Tab", onClick = viewModel::createTab)
                }
                return@Column
            }
            TabStrip(state.tabs, state.selectedId, viewModel::select, viewModel::close, viewModel::createTab)
            TabPager(state.tabs, state.selectedId, viewModel::select) { item, active ->
                viewModel.terminal(item)?.let { SshTerminalPage(it, settings, active) }
            }
        }
    }
}

@Composable
private fun SshTerminalPage(
    tab: SshTerminalTab,
    settings: TerminalSettings,
    active: Boolean,
) {
    val state by tab.state.collectAsStateWithLifecycle()
    val overlay = Modifier.fillMaxSize().background(LocalAppTheme.current.groupedBackground).blockingTouches()
    Box(Modifier.fillMaxSize()) {
        TerminalScreen(tab.controller, settings, active && state == SshConnectionState.Connected)
        when (val current = state) {
            SshConnectionState.Idle, SshConnectionState.Connecting -> {
                ConnectingOverlay(overlay)
            }

            SshConnectionState.Connected, SshConnectionState.Exited -> {
                return@Box
            }

            SshConnectionState.Disconnected -> {
                ThemedEmptyState("Disconnected", R.drawable.ic_wifi_off, "The SSH session ended.", modifier = overlay) {
                    ThemedBorderedButton("Retry", tab::retry)
                }
            }

            is SshConnectionState.Failed -> {
                ThemedEmptyState("Connection Failed", R.drawable.ic_warning, current.error.message, modifier = overlay) {
                    ThemedBorderedButton("Retry", tab::retry)
                }
            }
        }
    }
}

@Composable
private fun ConnectingOverlay(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalAppTheme.current.accent)
    }
}
