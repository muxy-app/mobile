package com.muxy.app.features.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.components.TopBarAction
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewTabPicker(
    devices: List<TabsDevice>,
    creating: TabsProjectKey?,
    onCreate: (TabsProjectKey) -> Unit,
    onRetry: (UUID) -> Unit,
    onUnlock: () -> Unit,
    onOpenSsh: (Connection) -> Unit,
    onAddDevice: () -> Unit,
    onClose: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val busy by rememberUpdatedState(creating != null)
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !busy },
        )
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onClose() },
        sheetState = sheetState,
        sheetGesturesEnabled = !busy,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !busy),
        containerColor = theme.groupedBackground,
        contentColor = theme.foreground,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Row(modifier = Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "New tab",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                )
                TopBarAction(R.drawable.ic_close, "Close", onClose, enabled = !busy)
            }
            Text(
                "Choose a project",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.secondaryForeground,
            )
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (devices.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Add a device to create a tab.",
                            modifier = Modifier.padding(16.dp),
                            color = theme.secondaryForeground,
                        )
                        TextButton(onClick = onAddDevice, enabled = !busy, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("Add device")
                        }
                    }
                }
                devices.forEach { device ->
                    val connection = device.connection
                    item(key = "device:${connection.id}") {
                        TabsDeviceHeader(device) {
                            when (device.status) {
                                TabsDeviceStatus.OFFLINE -> {
                                    TextButton(
                                        onClick = { onRetry(connection.id) },
                                        enabled = !busy,
                                    ) { Text("Reconnect") }
                                }

                                TabsDeviceStatus.LOCKED -> {
                                    TextButton(onClick = onUnlock, enabled = !busy) { Text("Unlock") }
                                }

                                else -> {}
                            }
                        }
                    }
                    if (connection.kind == ConnectionKind.SSH) {
                        item(key = "ssh:${connection.id}") { OpenSshTerminalCard(!busy, { onOpenSsh(connection) }) }
                    } else if (device.isCurrent && device.projects.isEmpty()) {
                        item(key = "empty:${connection.id}") {
                            Text(
                                "No projects. Add a project in Muxy on this device.",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.secondaryForeground,
                            )
                        }
                    }
                    items(device.projects, key = { "project:${connection.id}:${it.id}" }) { project ->
                        val key = TabsProjectKey(connection.id, project.id)
                        NewTabProjectRow(
                            project = project,
                            enabled = !busy && (device.canCreate(project) || device.status == TabsDeviceStatus.LOCKED),
                            creating = creating == key,
                            onCreate = { onCreate(key) },
                        )
                    }
                    item(key = "gap:${connection.id}") { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun NewTabProjectRow(
    project: TabsProject,
    enabled: Boolean,
    creating: Boolean,
    onCreate: () -> Unit,
) {
    val theme = LocalAppTheme.current
    ThemedListItem(
        headline = {
            Text(
                project.name,
                color = if (enabled) theme.foreground else theme.secondaryForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supporting = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val path = project.path
                if (!path.isNullOrBlank()) {
                    Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val detail =
                    when {
                        project.loadFailed -> "Couldn't load tabs"
                        !project.hasLoadedTabs -> "Tabs not loaded"
                        project.tabs.isEmpty() -> "No open tabs"
                        project.tabs.size == 1 -> "1 open tab"
                        else -> "${project.tabs.size} open tabs"
                    }
                Text(detail)
            }
        },
        leading = {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(8.dp).background(tabsProjectColor(project), CircleShape))
            }
        },
        trailing = {
            if (creating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = theme.accent, strokeWidth = 2.dp)
            } else {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (enabled) theme.secondaryForeground else theme.secondaryForeground.copy(alpha = 0.38f),
                )
            }
        },
        modifier =
            Modifier.clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "New tab in ${project.name}",
                onClick = onCreate,
            ),
    )
}
