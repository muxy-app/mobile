package com.muxy.app.features.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.TabKindTile
import com.muxy.app.design.components.TabTileColors
import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.models.TabActivity

private val cardShape = RoundedCornerShape(24.dp)
private val outerCorner = 24.dp
private val innerCorner = 4.dp

internal fun segmentShape(
    index: Int,
    count: Int,
): Shape =
    RoundedCornerShape(
        topStart = if (index == 0) outerCorner else innerCorner,
        topEnd = if (index == 0) outerCorner else innerCorner,
        bottomStart = if (index == count - 1) outerCorner else innerCorner,
        bottomEnd = if (index == count - 1) outerCorner else innerCorner,
    )

@Composable
internal fun TabsDeviceHeader(
    device: TabsDevice,
    trailing: @Composable () -> Unit = {},
) {
    val theme = LocalAppTheme.current
    val icon =
        when (device.connection.kind) {
            ConnectionKind.DEVICE, ConnectionKind.SERVER -> R.drawable.ic_desktop_mac
            ConnectionKind.SSH -> R.drawable.ic_terminal
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp).heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(26.dp), tint = theme.secondaryForeground)
        Text(
            device.connection.name,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            color = theme.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (device.isFetching) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = theme.secondaryForeground, strokeWidth = 2.dp)
        }
        statusLabel(device.status)?.let { label ->
            Text(
                label,
                modifier = Modifier.widthIn(max = 120.dp),
                style = MaterialTheme.typography.labelMedium,
                color = theme.secondaryForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

private fun statusLabel(status: TabsDeviceStatus): String? =
    when (status) {
        TabsDeviceStatus.CONNECTING -> "Connecting"
        TabsDeviceStatus.ONLINE -> null
        TabsDeviceStatus.OFFLINE -> "Offline"
        TabsDeviceStatus.DEMO -> "Demo"
        TabsDeviceStatus.SSH -> "SSH"
        TabsDeviceStatus.LOCKED -> "Locked"
    }

@Composable
internal fun TabsDeviceMenu(
    device: TabsDevice,
    onProjects: () -> Unit,
    onRefresh: () -> Unit,
    onUnlock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val theme = LocalAppTheme.current
    val isSsh = device.connection.kind == ConnectionKind.SSH
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_more_horiz),
                contentDescription = "Manage ${device.connection.name}",
                tint = theme.secondaryForeground,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (device.status == TabsDeviceStatus.LOCKED) {
                DropdownMenuItem(text = { Text("Unlock") }, onClick = {
                    expanded = false
                    onUnlock()
                })
            } else if (!isSsh) {
                DropdownMenuItem(text = { Text("Projects") }, onClick = {
                    expanded = false
                    onProjects()
                })
                DropdownMenuItem(text = { Text(if (device.status == TabsDeviceStatus.OFFLINE) "Reconnect" else "Refresh") }, onClick = {
                    expanded = false
                    onRefresh()
                })
            }
            if (device.connection.id != DemoConnection.id) {
                DropdownMenuItem(text = { Text("Edit device") }, onClick = {
                    expanded = false
                    onEdit()
                })
            }
            DropdownMenuItem(text = { Text("Delete device", color = theme.red) }, onClick = {
                expanded = false
                onDelete()
            })
        }
    }
}

@Composable
private fun TabsCard(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(cardShape)
                .background(LocalAppTheme.current.secondaryGroupedBackground),
    ) {
        content()
    }
}

@Composable
internal fun EmptyDeviceTabs(
    device: TabsDevice,
    enabled: Boolean,
    onNewTab: () -> Unit,
    onProjects: () -> Unit,
) {
    val hasProjects = device.projects.isNotEmpty()
    TabsCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (hasProjects) "No open tabs" else "No projects on this device",
                modifier = Modifier.weight(1f),
                color = LocalAppTheme.current.secondaryForeground,
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = if (hasProjects) onNewTab else onProjects, enabled = enabled) {
                Text(if (hasProjects) "New tab" else "Projects")
            }
        }
    }
}

@Composable
internal fun OpenSshTerminalCard(
    enabled: Boolean,
    onOpen: () -> Unit,
) {
    val theme = LocalAppTheme.current
    TabsCard {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, role = Role.Button, onClick = onOpen)
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_terminal),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = theme.foreground,
            )
            Text("Open SSH terminal", modifier = Modifier.weight(1f), color = theme.foreground, style = MaterialTheme.typography.bodyLarge)
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = theme.secondaryForeground,
            )
        }
    }
}

@Composable
internal fun TabsProjectHeader(
    project: TabsProject,
    enabled: Boolean,
    creating: Boolean,
    expanded: Boolean,
    onCreate: () -> Unit,
    onToggle: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 20.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            project.name,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = theme.secondaryForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onCreate, enabled = enabled) {
            if (creating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = theme.secondaryForeground, strokeWidth = 2.dp)
            } else {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = "New tab in ${project.name}",
                    modifier = Modifier.size(22.dp),
                    tint = theme.secondaryForeground,
                )
            }
        }
        if (project.tabs.isNotEmpty()) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            ) {
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = "${if (expanded) "Collapse" else "Expand"} ${project.name}",
                    modifier = Modifier.size(22.dp).rotate(if (expanded) 0f else -90f),
                    tint = theme.secondaryForeground,
                )
            }
        }
    }
}

@Composable
internal fun TabsTabRow(
    tab: TabsTab,
    shape: Shape,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 1.dp)
                .clip(shape)
                .background(theme.secondaryGroupedBackground)
                .clickable(enabled = enabled, role = Role.Button, onClick = onSelect)
                .padding(horizontal = 16.dp)
                .heightIn(min = 68.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabKindTile(kind = tab.tile, activity = tab.activity)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tab.title,
                color = theme.foreground,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tab.detail.isNotEmpty()) {
                Text(
                    tab.detail,
                    color = activityColor(tab.activity) ?: theme.secondaryForeground,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun activityColor(activity: TabActivity) =
    when (activity.state) {
        TabActivity.State.WAITING -> if (LocalAppTheme.current.isDark) TabTileColors.waiting else LocalAppTheme.current.yellow
        TabActivity.State.FINISHED -> LocalAppTheme.current.accent
        TabActivity.State.IDLE, TabActivity.State.WORKING -> null
    }

@Composable
internal fun RevealProjectsButton(
    revealed: Boolean,
    hiddenCount: Int,
    onToggle: () -> Unit,
) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            if (revealed) "Hide projects without tabs" else "$hiddenCount more ${if (hiddenCount == 1) "project" else "projects"}",
            color = LocalAppTheme.current.secondaryForeground,
        )
    }
}

@Composable
internal fun FloatingHomeBar(
    query: String,
    onQueryChange: (String) -> Unit,
    enabled: Boolean,
    canCreate: Boolean,
    creating: Boolean,
    onSettings: () -> Unit,
    onAddDevice: () -> Unit,
    notificationCount: Int,
    onNotifications: () -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = RoundedCornerShape(28.dp),
            color = theme.secondaryGroupedBackground,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = theme.secondaryForeground,
                )
                Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "Search tabs, projects",
                            color = theme.secondaryForeground,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = theme.foreground),
                        cursorBrush = SolidColor(theme.foreground),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search tabs and projects" },
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear search", tint = theme.secondaryForeground)
                    }
                } else {
                    IconButton(onClick = onAddDevice, enabled = enabled) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = "Add device", tint = theme.foreground)
                    }
                    IconButton(onClick = onSettings, enabled = enabled) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = theme.foreground)
                    }
                    IconButton(onClick = onNotifications, enabled = enabled) {
                        BadgedBox(badge = { if (notificationCount > 0) Badge { Text("$notificationCount") } }) {
                            Icon(
                                painterResource(R.drawable.ic_notifications),
                                contentDescription = if (notificationCount > 0) "Notifications, $notificationCount" else "Notifications",
                                tint = theme.foreground,
                            )
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { if (enabled && canCreate) onNewTab() },
            containerColor = theme.foreground,
            contentColor = theme.groupedBackground,
            modifier =
                Modifier.semantics {
                    contentDescription = "New tab"
                    if (creating) stateDescription = "Creating tab"
                },
        ) {
            if (creating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = theme.groupedBackground, strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = null)
            }
        }
    }
}
