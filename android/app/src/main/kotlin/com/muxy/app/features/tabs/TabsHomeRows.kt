package com.muxy.app.features.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.PageMargin
import com.muxy.app.design.components.SurfaceCorners
import com.muxy.app.design.components.TabActivityBadge
import com.muxy.app.design.components.TabKindIcon
import com.muxy.app.design.components.TabTileColors
import com.muxy.app.design.components.ThemedCard
import com.muxy.app.design.components.ThemedChevron
import com.muxy.app.design.components.ThemedDropdownMenu
import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.features.projects.rememberProjectColor
import com.muxy.app.models.ConnectionKind
import com.muxy.app.models.TabActivity

@Composable
internal fun TabsDeviceHeader(
    device: TabsDevice,
    trailing: @Composable () -> Unit = {},
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp).heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                device.connection.name,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = theme.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (device.isFetching) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = theme.secondaryForeground, strokeWidth = 2.dp)
            } else {
                DeviceStatus(device.status)
            }
        }
        trailing()
    }
}

@Composable
private fun DeviceStatus(status: TabsDeviceStatus) {
    val theme = LocalAppTheme.current
    val label =
        when (status) {
            TabsDeviceStatus.CONNECTING -> "connecting"
            TabsDeviceStatus.ONLINE -> "online"
            TabsDeviceStatus.OFFLINE -> "offline"
            TabsDeviceStatus.DEMO -> "demo"
            TabsDeviceStatus.SSH -> "SSH"
            TabsDeviceStatus.LOCKED -> "locked"
        }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            TabsDeviceStatus.ONLINE, TabsDeviceStatus.DEMO -> {
                Box(modifier = Modifier.size(7.dp).background(theme.green, CircleShape))
            }

            TabsDeviceStatus.LOCKED -> {
                Box(modifier = Modifier.size(7.dp).background(TabTileColors.waiting, CircleShape))
            }

            TabsDeviceStatus.OFFLINE -> {
                Box(modifier = Modifier.size(7.dp).border(1.5.dp, theme.secondaryForeground, CircleShape))
            }

            TabsDeviceStatus.CONNECTING, TabsDeviceStatus.SSH -> {}
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = theme.secondaryForeground, maxLines = 1)
    }
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
        ThemedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
    ThemedCard(modifier = Modifier.padding(horizontal = PageMargin.horizontal, vertical = 4.dp)) { content() }
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
            ThemedChevron()
        }
    }
}

@Composable
internal fun TabsProjectCard(
    project: TabsProject,
    canCreate: Boolean,
    creating: Boolean,
    expanded: Boolean,
    tabsEnabled: Boolean,
    tabsClosable: Boolean,
    onCreate: () -> Unit,
    onToggle: () -> Unit,
    onSelectTab: (TabsTab) -> Unit,
    onCloseTab: (TabsTab) -> Unit,
) {
    val theme = LocalAppTheme.current
    val showsTabs = expanded && project.tabs.isNotEmpty()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PageMargin.horizontal, vertical = 5.dp)
                .background(theme.secondaryGroupedBackground, RoundedCornerShape(SurfaceCorners.card))
                .padding(
                    start = SurfaceCorners.cardPadding,
                    end = SurfaceCorners.cardPadding,
                    top = 4.dp,
                    bottom = if (showsTabs) SurfaceCorners.cardPadding else 4.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TabsProjectHeader(project, canCreate, creating, expanded, onCreate, onToggle)
        if (showsTabs) {
            project.tabs.forEach { tab ->
                key(tab.id) {
                    TabsTabRow(
                        tab = tab,
                        enabled = tabsEnabled,
                        closable = tabsClosable,
                        onSelect = { onSelectTab(tab) },
                        onClose = { onCloseTab(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabsProjectHeader(
    project: TabsProject,
    enabled: Boolean,
    creating: Boolean,
    expanded: Boolean,
    onCreate: () -> Unit,
    onToggle: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val hasTabs = project.tabs.isNotEmpty()
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(SurfaceCorners.row))
                    .clickable(
                        enabled = hasTabs,
                        onClickLabel = if (expanded) "Collapse" else "Expand",
                        role = Role.Button,
                        onClick = onToggle,
                    ).semantics(mergeDescendants = true) {
                        heading()
                        if (hasTabs) stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }.padding(start = 6.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(8.dp).background(rememberProjectColor(project.id, project.color), CircleShape))
            Text(
                project.name,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium,
                color = theme.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!expanded && hasTabs) {
                Text(
                    if (project.tabs.size == 1) "1 tab" else "${project.tabs.size} tabs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.secondaryForeground,
                    maxLines = 1,
                )
            }
        }
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
        if (hasTabs) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.clearAndSetSemantics {},
            ) {
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp).rotate(if (expanded) 0f else -90f),
                    tint = theme.secondaryForeground,
                )
            }
        }
    }
}

@Composable
private fun TabsTabRow(
    tab: TabsTab,
    enabled: Boolean,
    closable: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val shape = RoundedCornerShape(SurfaceCorners.row)
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(swipe.settledValue) {
        if (swipe.settledValue != SwipeToDismissBoxValue.EndToStart) return@LaunchedEffect
        if (closable) onClose()
        swipe.reset()
    }
    SwipeToDismissBox(
        state = swipe,
        modifier = Modifier.clip(shape),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = closable,
        backgroundContent = {
            if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(theme.red)
                            .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = Color.White)
                }
            }
        },
    ) {
        TabsTabRowContent(tab, enabled, closable, shape, onSelect, onClose)
    }
}

@Composable
private fun TabsTabRowContent(
    tab: TabsTab,
    enabled: Boolean,
    closable: Boolean,
    shape: Shape,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val status = tab.activity.statusLabel.takeIf { tab.activity.state != TabActivity.State.IDLE }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(theme.tertiaryGroupedBackground)
                .clickable(enabled = enabled, role = Role.Button, onClick = onSelect)
                .semantics {
                    if (status != null) stateDescription = status
                    if (closable) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction("Close tab") {
                                    onClose()
                                    true
                                },
                            )
                    }
                }.padding(start = 12.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabKindIcon(kind = tab.tile, activity = tab.activity)
        Text(
            tab.title,
            modifier = Modifier.weight(1f),
            color = theme.foreground,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (status != null) {
            TabActivityBadge(tab.activity)
        } else if (tab.detail.isNotEmpty()) {
            Text(
                tab.detail,
                modifier = Modifier.widthIn(max = 160.dp),
                color = theme.secondaryForeground,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
        }
    }
}

@Composable
internal fun activityColor(activity: TabActivity) =
    when (activity.state) {
        TabActivity.State.WAITING -> if (LocalAppTheme.current.isDark) TabTileColors.waiting else LocalAppTheme.current.yellow
        TabActivity.State.FINISHED -> LocalAppTheme.current.green
        TabActivity.State.IDLE, TabActivity.State.WORKING -> null
    }

@Composable
internal fun RevealProjectsButton(
    revealed: Boolean,
    hiddenCount: Int,
    onToggle: () -> Unit,
) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().padding(horizontal = PageMargin.horizontal, vertical = 4.dp)) {
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
                        BadgedBox(badge = { if (notificationCount > 0) Badge(containerColor = TabTileColors.waiting) }) {
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
            shape = CircleShape,
            containerColor = theme.secondaryGroupedBackground,
            contentColor = theme.foreground,
            modifier =
                Modifier.semantics {
                    contentDescription = "New tab"
                    if (creating) stateDescription = "Creating tab"
                },
        ) {
            if (creating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = theme.foreground, strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = null)
            }
        }
    }
}
