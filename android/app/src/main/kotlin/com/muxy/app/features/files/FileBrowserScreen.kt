package com.muxy.app.features.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.components.ThemedSectionFooter
import com.muxy.app.design.components.ThemedTextField
import com.muxy.app.design.components.themedSection
import com.muxy.app.features.projects.ProjectIcon
import com.muxy.app.features.projects.ProjectSymbols
import com.muxy.app.models.RemoteFileEntry

@Composable
internal fun FileBrowserScreen(
    state: FileManagerState,
    onRefresh: () -> Unit,
    onDirectory: (String) -> Unit,
    onOpen: (RemoteFileEntry) -> Unit,
    onSelect: (String) -> Unit,
    onCreate: (Boolean) -> Unit,
    onRename: (RemoteFileEntry) -> Unit,
    onMove: (List<String>) -> Unit,
    onDelete: (List<String>) -> Unit,
) {
    var filter by rememberSaveable(state.currentPath) { mutableStateOf("") }
    val filtered = state.entries.filter { it.name.contains(filter, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        PullToRefreshBox(state.isLoadingDirectory, onRefresh, Modifier.weight(1f)) {
            ThemedList(PaddingValues(0.dp)) {
                item("location") { FileLocationHeader(state.location) }
                item("filter") { ThemedTextField(filter, { filter = it }, "Filter this folder") }
                item("breadcrumbs") { FileBreadcrumbs(state.currentPath, state.location.name, state.canMutate, onDirectory) }
                listOf(true, false).forEach { directories ->
                    val entries = filtered.filter { it.isDirectory == directories }
                    if (entries.isNotEmpty()) {
                        themedSection(
                            entries,
                            key = { "entry:${it.path}" },
                            header = "${if (directories) "Folders" else "Files"} · ${entries.size}",
                        ) { entry ->
                            FileEntryRow(
                                entry,
                                state.canMutate,
                                state.selectionMode,
                                entry.path in state.selectedPaths,
                                { onOpen(entry) },
                                { onSelect(entry.path) },
                            )
                        }
                    }
                }
                if (filtered.isEmpty() && !state.isLoadingDirectory) {
                    item("empty") {
                        ThemedSectionFooter(if (filter.isBlank()) "This folder is empty." else "No matching files.")
                    }
                }
                item("guidance") { ThemedSectionFooter(state.location.host.selectionGuidance) }
            }
        }
        FileBrowserFooter(state, onCreate, onRename, onMove, onDelete)
    }
}

@Composable
private fun FileLocationHeader(location: FileLocation) {
    ThemedCell(RowPosition.SINGLE) {
        ThemedListItem(
            headline = { Text(location.name) },
            supporting = {
                Column {
                    Text(location.host.label)
                    Text(location.path)
                }
            },
            leading = {
                when (val icon = location.icon) {
                    is ProjectIcon.Symbol -> {
                        Icon(
                            painterResource(ProjectSymbols.drawable(icon.name)),
                            null,
                            tint = LocalAppTheme.current.accent,
                        )
                    }

                    is ProjectIcon.Emoji -> {
                        Text(icon.text)
                    }
                }
            },
        )
    }
}

@Composable
private fun FileBrowserFooter(
    state: FileManagerState,
    onCreate: (Boolean) -> Unit,
    onRename: (RemoteFileEntry) -> Unit,
    onMove: (List<String>) -> Unit,
    onDelete: (List<String>) -> Unit,
) {
    var newMenu by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (state.selectionMode) {
            val selected = state.entries.filter { it.path in state.selectedPaths }
            TextButton(onClick = { onMove(selected.map { it.path }) }, enabled = state.canMutate && selected.isNotEmpty()) { Text("Move") }
            TextButton(
                onClick = { selected.singleOrNull()?.let(onRename) },
                enabled = state.canMutate && selected.size == 1,
            ) { Text("Rename") }
            TextButton(onClick = {
                onDelete(selected.map { it.path })
            }, enabled = state.canMutate && selected.isNotEmpty()) { Text(state.location.host.deletionShortTitle) }
            return@Row
        }
        Text(
            "${state.entries.size} items",
            Modifier.weight(1f).padding(vertical = 16.dp),
            color = LocalAppTheme.current.secondaryForeground,
        )
        Column {
            TextButton(onClick = { newMenu = true }, enabled = state.canMutate) { Text("New") }
            DropdownMenu(newMenu, { newMenu = false }) {
                DropdownMenuItem(text = { Text("New file") }, onClick = {
                    newMenu = false
                    onCreate(false)
                })
                DropdownMenuItem(text = { Text("New folder") }, onClick = {
                    newMenu = false
                    onCreate(true)
                })
            }
        }
    }
}

@Composable
internal fun FileMoveScreen(
    state: FileManagerState,
    onDirectory: (String) -> Unit,
    onMove: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ThemedList(PaddingValues(0.dp), Modifier.weight(1f)) {
            item("breadcrumbs") { FileBreadcrumbs(state.movePath, state.location.name, state.canMutate, onDirectory) }
            themedSection(state.moveEntries, key = { "entry:${it.path}" }, header = "Move to folder") { entry ->
                FileEntryRow(
                    entry,
                    state.canMutate &&
                        state.movingPaths.none {
                            RemoteFilePath.contains(entry.path, it)
                        },
                    onOpen = { onDirectory(entry.path) },
                )
            }
        }
        TextButton(onClick = onMove, enabled = state.canMoveHere, modifier = Modifier.fillMaxWidth()) { Text("Move here") }
    }
}
