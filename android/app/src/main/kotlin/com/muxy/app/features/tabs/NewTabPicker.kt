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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.ListRow
import com.muxy.app.design.components.PageMargin
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedBorderedButton
import com.muxy.app.design.components.ThemedBottomSheet
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.features.projects.rememberProjectColor
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.models.DisplayPath
import java.util.UUID

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
    val busy = creating != null
    ThemedBottomSheet(title = "New tab", onClose = onClose, dismissible = !busy) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Text(
                "Choose a project",
                modifier = Modifier.padding(horizontal = PageMargin.horizontal, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.secondaryForeground,
            )
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (devices.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier.padding(horizontal = PageMargin.horizontal, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text("Add a device to create a tab.", color = theme.secondaryForeground)
                            ThemedBorderedButton("Add device", onAddDevice, enabled = !busy)
                        }
                    }
                }
                devices.forEach { device ->
                    val connection = device.connection
                    item(key = "device:${connection.id}") {
                        TabsDeviceHeader(device) {
                            when (device.status) {
                                TabsDeviceStatus.OFFLINE -> {
                                    TextButton(onClick = { onRetry(connection.id) }, enabled = !busy) { Text("Reconnect") }
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
                                modifier = Modifier.padding(horizontal = PageMargin.horizontal, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.secondaryForeground,
                            )
                        }
                    }
                    itemsIndexed(device.projects, key = { _, project -> "project:${connection.id}:${project.id}" }) { index, project ->
                        val key = TabsProjectKey(connection.id, project.id)
                        ThemedCell(
                            RowPosition.of(index, device.projects.size),
                            Modifier.padding(horizontal = PageMargin.horizontal),
                        ) {
                            NewTabProjectRow(
                                project = project,
                                enabled = !busy && (device.canCreate(project) || device.status == TabsDeviceStatus.LOCKED),
                                creating = creating == key,
                                onCreate = { onCreate(key) },
                            )
                        }
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
    val path = project.path?.takeIf { it.isNotBlank() }?.let(DisplayPath::abbreviated)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ListRow.minHeight)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = "New tab in ${project.name}",
                    onClick = onCreate,
                ).semantics { if (creating) stateDescription = "Creating tab" }
                .padding(horizontal = ListRow.horizontalPadding, vertical = ListRow.verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).background(rememberProjectColor(project.id, project.color), CircleShape))
        Text(
            project.name,
            modifier = Modifier.weight(1f),
            color = if (enabled) theme.foreground else theme.secondaryForeground,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (creating) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = theme.secondaryForeground, strokeWidth = 2.dp)
        } else if (path != null) {
            Text(
                path,
                modifier = Modifier.widthIn(max = 160.dp),
                color = theme.secondaryForeground,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
        }
    }
}
