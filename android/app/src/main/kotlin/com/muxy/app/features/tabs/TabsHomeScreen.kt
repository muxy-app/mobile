package com.muxy.app.features.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.ThemedAlertDialog
import com.muxy.app.design.components.ThemedDialogAction
import com.muxy.app.design.components.ThemedProminentButton
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import kotlinx.coroutines.launch

@Composable
fun TabsHomeScreen(
    viewModel: TabsHomeViewModel,
    onSelect: (TabsSelection) -> Unit,
    onOpenConnection: (Connection) -> Unit,
    onAddDevice: () -> Unit,
    onSettings: () -> Unit,
    onEdit: (Connection) -> Unit,
    onDelete: (Connection) -> Unit,
    onUnlock: () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showNewTab by rememberSaveable { mutableStateOf(false) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var collapsedProjects by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var revealedDevices by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val theme = LocalAppTheme.current
    val idle = creating == null
    val handle: (TabsHomeAction?) -> Unit = handle@{ action ->
        if (action == null) return@handle
        showNewTab = false
        when (action) {
            is TabsHomeAction.OpenTab -> onSelect(action.selection)
            TabsHomeAction.Unlock -> onUnlock()
        }
    }
    val create: (TabsProjectKey) -> Unit = { key -> scope.launch { handle(viewModel.createTab(key)) } }
    val loaded = devices
    val searching = query.isNotBlank()
    val shown = loaded.orEmpty().mapNotNull { it.matching(query) }
    val attention = TabsAttention.collect(loaded.orEmpty())

    Scaffold(bottomBar = footer, containerColor = theme.groupedBackground) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 120.dp,
                    ),
            ) {
                if (loaded == null) {
                    item(key = "loading") { TabsHomeMessage("Loading devices…") }
                }
                if (loaded != null && loaded.isEmpty()) {
                    item(key = "empty") { NoDevices(enabled = idle, onAddDevice = onAddDevice) }
                }
                if (searching && loaded.orEmpty().isNotEmpty() && shown.isEmpty()) {
                    item(key = "no-results") { TabsHomeMessage("No results for “${query.trim()}”") }
                }
                shown.forEach { device ->
                    val connection = device.connection
                    val deviceKey = connection.id.toString()
                    val revealed = deviceKey in revealedDevices
                    item(key = "device:$deviceKey") {
                        TabsDeviceHeader(device) {
                            TabsDeviceMenu(
                                device = device,
                                onProjects = { onOpenConnection(connection) },
                                onRefresh = { handle(viewModel.retry(connection.id)) },
                                onUnlock = onUnlock,
                                onEdit = { onEdit(connection) },
                                onDelete = { deletingId = deviceKey },
                            )
                        }
                    }
                    if (connection.kind == ConnectionKind.SSH) {
                        item(key = "ssh:$deviceKey") {
                            OpenSshTerminalCard(enabled = idle, onOpen = { onOpenConnection(connection) })
                        }
                    } else if (!searching && device.isCurrent && device.tabCount == 0) {
                        item(key = "empty:$deviceKey") {
                            EmptyDeviceTabs(device, idle, { showNewTab = true }, { onOpenConnection(connection) })
                        }
                    }
                    val projects =
                        when {
                            searching -> device.projects
                            device.tabCount == 0 -> emptyList()
                            revealed -> device.projects
                            else -> device.projects.filter { it.tabs.isNotEmpty() }
                        }
                    projects.forEach { project ->
                        val key = TabsProjectKey(connection.id, project.id)
                        val sectionId = "$deviceKey:${project.id}"
                        val expanded = searching || sectionId !in collapsedProjects
                        item(key = "project:$sectionId") {
                            TabsProjectCard(
                                project = project,
                                canCreate = idle && (device.canCreate(project) || device.status == TabsDeviceStatus.LOCKED),
                                creating = creating == key,
                                expanded = expanded,
                                tabsEnabled = idle,
                                onCreate = { create(key) },
                                onToggle = {
                                    collapsedProjects = if (expanded) collapsedProjects + sectionId else collapsedProjects - sectionId
                                },
                                onSelectTab = { tab -> handle(viewModel.select(key, tab.id)) },
                            )
                        }
                    }
                    val idleProjects = device.projects.count { it.tabs.isEmpty() }
                    if (!searching && idleProjects > 0 && device.tabCount > 0) {
                        item(key = "reveal:$deviceKey") {
                            RevealProjectsButton(revealed, idleProjects) {
                                revealedDevices = if (revealed) revealedDevices - deviceKey else revealedDevices + deviceKey
                            }
                        }
                    }
                    item(key = "gap:$deviceKey") { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
            FloatingHomeBar(
                query = query,
                onQueryChange = { query = it },
                enabled = idle,
                canCreate = loaded.orEmpty().isNotEmpty(),
                creating = creating != null,
                onSettings = onSettings,
                onAddDevice = onAddDevice,
                notificationCount = attention.size,
                onNotifications = { showNotifications = true },
                onNewTab = { showNewTab = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding()),
            )
        }
    }
    if (showNewTab) {
        NewTabPicker(
            devices = devices.orEmpty(),
            creating = creating,
            onCreate = create,
            onRetry = { handle(viewModel.retry(it)) },
            onUnlock = { handle(TabsHomeAction.Unlock) },
            onOpenSsh = {
                showNewTab = false
                onOpenConnection(it)
            },
            onAddDevice = {
                showNewTab = false
                onAddDevice()
            },
            onClose = { showNewTab = false },
        )
    }
    if (showNotifications) {
        TabsNotificationsSheet(
            items = attention,
            onOpen = { item ->
                showNotifications = false
                handle(viewModel.select(TabsProjectKey(item.device.connection.id, item.project.id), item.tab.id))
            },
            onClose = { showNotifications = false },
        )
    }
    devices.orEmpty().firstOrNull { it.connection.id.toString() == deletingId }?.connection?.let { connection ->
        ThemedAlertDialog(
            title = "Delete device?",
            message = "Remove ${connection.name} and its saved credentials from this phone?",
            onDismissRequest = { deletingId = null },
            confirm =
                ThemedDialogAction(
                    label = "Delete",
                    onClick = {
                        deletingId = null
                        onDelete(connection)
                    },
                    isDestructive = true,
                ),
            dismiss = listOf(ThemedDialogAction("Cancel", { deletingId = null })),
        )
    }
    error?.let { message ->
        ThemedAlertDialog(
            title = "New tab unavailable",
            message = message,
            onDismissRequest = viewModel::dismissError,
            confirm = ThemedDialogAction("OK", viewModel::dismissError),
        )
    }
}

@Composable
private fun TabsHomeMessage(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        color = LocalAppTheme.current.secondaryForeground,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun NoDevices(
    enabled: Boolean,
    onAddDevice: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("No devices yet", color = theme.foreground, style = MaterialTheme.typography.headlineSmall)
        Text(
            "Add a device to see its projects and open tabs here.",
            color = theme.secondaryForeground,
            style = MaterialTheme.typography.bodyMedium,
        )
        ThemedProminentButton(text = "Add device", onClick = onAddDevice, enabled = enabled)
    }
}
