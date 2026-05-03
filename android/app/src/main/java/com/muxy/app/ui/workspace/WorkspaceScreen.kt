package com.muxy.app.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxy.app.model.TabAreaDTO
import com.muxy.app.model.TabDTO
import com.muxy.app.model.TabKindDTO
import com.muxy.app.model.collectAreas
import com.muxy.app.ui.connect.ConnectionViewModel
import com.muxy.app.ui.terminal.TerminalView
import com.muxy.app.ui.theme.MuxyTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(viewModel: ConnectionViewModel) {
    val projects by viewModel.session.projects.collectAsState()
    val activeID by viewModel.session.activeProjectID.collectAsState()
    val workspace by viewModel.session.workspace.collectAsState()
    val theme by viewModel.session.deviceTheme.collectAsState()
    val palette = MuxyTheme.from(theme)
    val scope = rememberCoroutineScope()

    val activeProject = remember(projects, activeID) {
        projects.firstOrNull { it.id == activeID }
    }

    val areas = workspace?.root?.collectAreas().orEmpty()
    val focusedArea = areas.firstOrNull { it.id == workspace?.focusedAreaID } ?: areas.firstOrNull()
    val activeTab = focusedArea?.let { area ->
        area.tabs.firstOrNull { it.id == area.activeTabID } ?: area.tabs.firstOrNull()
    }

    var showBranches by remember { mutableStateOf(false) }
    var showWorktrees by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var pendingClose by remember { mutableStateOf<AreaTab?>(null) }

    val tabsList = remember(workspace) { areas.flatMap { area -> area.tabs.map { AreaTab(area, it) } } }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text(activeProject?.name.orEmpty(), color = palette.foreground) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.session.clearActiveProject() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.foreground,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showBranches = true }) {
                        Icon(Icons.Filled.AccountTree, contentDescription = "Branches", tint = palette.foreground)
                    }
                    IconButton(onClick = { showWorktrees = true }) {
                        Icon(Icons.Filled.Folder, contentDescription = "Worktrees", tint = palette.foreground)
                    }
                    Box {
                        IconButton(onClick = { showTabs = true }) {
                            Icon(Icons.Filled.Tab, contentDescription = "Tabs", tint = palette.foreground)
                        }
                        TabPickerMenu(
                            expanded = showTabs,
                            onDismiss = { showTabs = false },
                            entries = tabsList,
                            activeTabID = activeTab?.id,
                            onTabSelected = { entry ->
                                showTabs = false
                                scope.launch {
                                    viewModel.session.selectTab(activeID!!, entry.area.id, entry.tab.id)
                                }
                            },
                            onTabClose = { entry ->
                                showTabs = false
                                pendingClose = entry
                            },
                            onNewTerminal = {
                                showTabs = false
                                scope.launch { viewModel.session.createTab(activeID!!) }
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    titleContentColor = palette.foreground,
                    navigationIconContentColor = palette.foreground,
                    actionIconContentColor = palette.foreground,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(padding),
        ) {
            val paneID = activeTab?.takeIf { it.kind == TabKindDTO.TERMINAL }?.paneID
            if (paneID != null) {
                TerminalView(paneID = paneID, session = viewModel.session, modifier = Modifier.fillMaxSize())
            } else {
                TabPlaceholder(activeTab, palette.foreground)
            }
        }
    }

    if (showBranches && activeID != null) {
        BranchesSheet(viewModel, activeID!!) { showBranches = false }
    }
    if (showWorktrees && activeID != null) {
        WorktreesSheet(viewModel, activeID!!) { showWorktrees = false }
    }

    pendingClose?.let { entry ->
        val labels = remember(tabsList) { disambiguateLabels(tabsList) }
        val label = remember(tabsList, entry) {
            tabsList.indexOf(entry).takeIf { it >= 0 }?.let { labels[it] } ?: shortTitle(entry.tab.title)
        }
        AlertDialog(
            onDismissRequest = { pendingClose = null },
            title = { Text("Close \"$label\"?") },
            text = { Text("This ends any running process in the tab.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingClose = null
                    scope.launch {
                        viewModel.session.closeTab(activeID!!, entry.area.id, entry.tab.id)
                    }
                }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClose = null }) { Text("Cancel") }
            },
        )
    }
}

private data class AreaTab(val area: TabAreaDTO, val tab: TabDTO)

@Composable
private fun TabPickerMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    entries: List<AreaTab>,
    activeTabID: String?,
    onTabSelected: (AreaTab) -> Unit,
    onTabClose: (AreaTab) -> Unit,
    onNewTerminal: () -> Unit,
) {
    val labels = remember(entries) { disambiguateLabels(entries) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        entries.forEachIndexed { index, entry ->
            DropdownMenuItem(
                text = { Text(labels[index]) },
                leadingIcon = {
                    if (entry.tab.id == activeTabID) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    } else {
                        Icon(iconForKind(entry.tab.kind), contentDescription = null)
                    }
                },
                trailingIcon = {
                    IconButton(onClick = { onTabClose(entry) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close ${labels[index]}",
                        )
                    }
                },
                onClick = { onTabSelected(entry) },
            )
        }
        if (entries.isNotEmpty()) HorizontalDivider()
        DropdownMenuItem(
            text = { Text("New Terminal") },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            onClick = onNewTerminal,
        )
    }
}

private fun shortTitle(title: String): String =
    title.split('/').lastOrNull { it.isNotEmpty() } ?: title

private fun disambiguateLabels(entries: List<AreaTab>): List<String> {
    val shorts = entries.map { shortTitle(it.tab.title) }
    val counts = shorts.groupingBy { it }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return shorts.map { short ->
        if ((counts[short] ?: 0) <= 1) return@map short
        val n = (seen[short] ?: 0) + 1
        seen[short] = n
        "$short ($n)"
    }
}

@Composable
private fun iconForKind(kind: TabKindDTO) = when (kind) {
    TabKindDTO.TERMINAL -> Icons.Filled.Terminal
    TabKindDTO.VCS -> Icons.Filled.AccountTree
    TabKindDTO.EDITOR -> Icons.Filled.Folder
    TabKindDTO.DIFF_VIEWER -> Icons.Filled.Folder
}

@Composable
private fun TabPlaceholder(tab: TabDTO?, foreground: androidx.compose.ui.graphics.Color) {
    if (tab == null) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No tabs", color = foreground.copy(alpha = 0.7f))
            Text(
                "Create a new tab from the menu above.",
                color = foreground.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        return
    }
    val (icon, label) = when (tab.kind) {
        TabKindDTO.TERMINAL -> Icons.Filled.Terminal to (if (tab.paneID == null) "No pane available" else "Terminal — coming in Phase 3")
        TabKindDTO.VCS -> Icons.Filled.AccountTree to "Source Control"
        TabKindDTO.EDITOR -> Icons.Filled.Folder to tab.title
        TabKindDTO.DIFF_VIEWER -> Icons.Filled.Folder to tab.title
    }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = foreground.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
        Text(label, color = foreground.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
    }
}
