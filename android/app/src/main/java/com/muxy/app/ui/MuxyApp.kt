package com.muxy.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muxy.app.ui.connect.ConnectScreen
import com.muxy.app.ui.connect.ConnectionState
import com.muxy.app.ui.connect.ConnectionViewModel
import com.muxy.app.ui.projects.ProjectsScreen
import com.muxy.app.ui.settings.SettingsScreen
import com.muxy.app.ui.theme.MuxyTheme
import com.muxy.app.ui.workspace.WorkspaceScreen
import com.muxy.app.BuildConfig

@Composable
fun MuxyApp(viewModel: ConnectionViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val activeProjectID by viewModel.session.activeProjectID.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onForeground()
                Lifecycle.Event.ON_STOP -> viewModel.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val theme by viewModel.session.deviceTheme.collectAsState()
    val palette = MuxyTheme.from(theme)

    // Drive system bar icon contrast from the active background. Connection-flow
    // screens sit on the dark Compose theme; once connected, the Mac-driven
    // palette takes over and may be light or dark.
    val barBg = if (state is ConnectionState.Connected) palette.background else MaterialTheme.colorScheme.background
    val useLightIcons = barBg.luminance() < 0.5f
    val context = LocalContext.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !useLightIcons
        controller.isAppearanceLightNavigationBars = !useLightIcons
    }

    var showSettings by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val s = state) {
            ConnectionState.Disconnected -> ConnectScreen(viewModel, onOpenSettings = { showSettings = true })
            is ConnectionState.Connecting -> CenteredStatus(s.deviceName, "Connecting…")
            is ConnectionState.AwaitingApproval -> AwaitingApproval(s.deviceName) { viewModel.disconnect() }
            is ConnectionState.Connected -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.background)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    if (activeProjectID == null) {
                        ProjectsScreen(viewModel)
                    } else {
                        BackHandler { viewModel.session.clearActiveProject() }
                        WorkspaceScreen(viewModel)
                    }
                }
            }
            is ConnectionState.Error -> ErrorView(s, onRetry = { viewModel.reconnect() }, onDisconnect = { viewModel.disconnect() })
        }

        if (showSettings) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                BackHandler { showSettings = false }
                SettingsScreen(
                    store = viewModel.terminalPreferences,
                    appVersion = BuildConfig.VERSION_NAME,
                    appBuild = BuildConfig.VERSION_CODE.toString(),
                    onBack = { showSettings = false },
                )
            }
        }
    }
}

@Composable
private fun CenteredStatus(deviceName: String, message: String) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(message, modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onBackground)
            Text("Reaching $deviceName", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AwaitingApproval(deviceName: String, onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("Waiting for Approval", style = MaterialTheme.typography.titleMedium)
            Text("Approve this device on $deviceName.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 16.dp)) { Text("Cancel") }
        }
    }
}

@Composable
private fun ErrorView(state: ConnectionState.Error, onRetry: () -> Unit, onDisconnect: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connection Failed", style = MaterialTheme.typography.titleMedium)
            Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.technicalDetails, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRetry) { Text("Retry") }
            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        }
    }
}
