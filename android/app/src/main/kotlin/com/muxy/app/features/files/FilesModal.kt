package com.muxy.app.features.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.TopBarAction
import com.muxy.app.design.components.TopBarTextAction
import com.muxy.app.features.navigation.NavigationTransitions
import kotlinx.coroutines.launch

private enum class FileExit { BACK, CLOSE, RELOAD }

@Composable
fun FilesModal(
    viewModel: FileManagerViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var prompt by remember(state.contextId) { mutableStateOf<FileNamePrompt?>(null) }
    var deletion by remember(state.contextId) { mutableStateOf<List<String>?>(null) }
    var pendingExit by remember { mutableStateOf<FileExit?>(null) }
    val exit: suspend (FileExit, Boolean) -> Unit = exit@{ action, discard ->
        val current = viewModel.state.value
        if (current.isBusy) return@exit
        if (current.isDirty && !discard) {
            pendingExit = action
            return@exit
        }
        when (action) {
            FileExit.CLOSE -> {
                onClose()
            }

            FileExit.RELOAD -> {
                viewModel.reloadPreview(discard = true)
            }

            FileExit.BACK -> {
                if (current.route == FileRoute.BROWSER && current.currentPath.isEmpty() && !current.selectionMode) {
                    onClose()
                } else {
                    viewModel.goBack()
                }
            }
        }
    }
    val requestExit: (FileExit) -> Unit = { action -> scope.launch { exit(action, false) } }
    Scaffold(
        topBar = {
            MuxyTopAppBar(
                title =
                    when (state.route) {
                        FileRoute.BROWSER -> {
                            "Files"
                        }

                        FileRoute.PREVIEW -> {
                            state.preview
                                ?.entry
                                ?.name
                                .orEmpty()
                        }

                        FileRoute.MOVE -> {
                            "Move"
                        }
                    },
                navigationIcon = { TopBarAction(R.drawable.ic_arrow_back, "Back", { requestExit(FileExit.BACK) }, !state.isBusy) },
                actions = {
                    if (state.route == FileRoute.PREVIEW) {
                        TopBarTextAction(if (state.preview?.wrapsLines == true) "Unwrap" else "Wrap", viewModel::toggleWrap)
                        FilePreviewMenu(
                            state,
                            onRename = { state.preview?.entry?.let { prompt = FileNamePrompt(it.isDirectory, it) } },
                            onMove = { scope.launch { state.preview?.entry?.let { viewModel.startMove(listOf(it.path)) } } },
                            onDelete = { state.preview?.entry?.let { deletion = listOf(it.path) } },
                            onClose = { requestExit(FileExit.CLOSE) },
                        )
                    } else {
                        if (state.route ==
                            FileRoute.BROWSER
                        ) {
                            TopBarTextAction(
                                if (state.selectionMode) "Done" else "Select",
                                viewModel::toggleSelectionMode,
                                !state.isBusy && (state.selectionMode || state.canMutate),
                            )
                        }
                        TopBarAction(
                            R.drawable.ic_refresh,
                            "Refresh",
                            { scope.launch { viewModel.refresh() } },
                            state.isConnected && !state.isBusy,
                        )
                        TopBarAction(R.drawable.ic_close, "Close", { requestExit(FileExit.CLOSE) }, !state.isBusy)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .fillMaxSize(),
        ) {
            if (!state.isConnected) {
                Text("Connection lost", Modifier.padding(horizontal = 16.dp), color = LocalAppTheme.current.yellow)
                Text(state.location.host.reconnectMessage, Modifier.padding(horizontal = 16.dp))
            }
            if (state.hasContextChanged) {
                Text(
                    "The active worktree changed. Discard this draft and return to Files before continuing.",
                    Modifier.padding(16.dp),
                    color = LocalAppTheme.current.yellow,
                )
            }
            state.errorMessage?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = LocalAppTheme.current.red) }
            if (state.isBusy || state.isLoadingMove) LinearProgressIndicator(Modifier.fillMaxWidth())
            NavDisplay(
                backStack = state.routes,
                onBack = { requestExit(FileExit.BACK) },
                transitionSpec = { NavigationTransitions.push() },
                popTransitionSpec = { NavigationTransitions.pop() },
                predictivePopTransitionSpec = { NavigationTransitions.pop() },
                entryProvider =
                    entryProvider {
                        entry<FileRoute> { route ->
                            Surface(Modifier.fillMaxSize()) {
                                when (route) {
                                    FileRoute.BROWSER -> {
                                        FileBrowserScreen(
                                            state,
                                            onRefresh = { scope.launch { viewModel.refresh() } },
                                            onDirectory = { scope.launch { viewModel.goToDirectory(it) } },
                                            onOpen = { scope.launch { viewModel.open(it) } },
                                            onSelect = viewModel::toggleSelection,
                                            onCreate = { prompt = FileNamePrompt(it) },
                                            onRename = {
                                                prompt =
                                                    FileNamePrompt(it.isDirectory, it)
                                            },
                                            onMove = { scope.launch { viewModel.startMove(it) } },
                                            onDelete = { deletion = it },
                                        )
                                    }

                                    FileRoute.PREVIEW -> {
                                        FilePreviewScreen(state, viewModel::updateDraft, viewModel::beginEditing, {
                                            scope.launch { viewModel.save() }
                                        }, { requestExit(FileExit.RELOAD) })
                                    }

                                    FileRoute.MOVE -> {
                                        FileMoveScreen(
                                            state,
                                            { scope.launch { viewModel.goToMoveDirectory(it) } },
                                            { scope.launch { viewModel.moveHere() } },
                                        )
                                    }
                                }
                            }
                        }
                    },
            )
        }
    }
    BackHandler { requestExit(FileExit.BACK) }
    prompt?.let { value ->
        FileNameDialog(value, state, { prompt = null }) { name ->
            scope.launch {
                val succeeded = value.entry?.let { viewModel.rename(it, name) } ?: viewModel.create(name, value.isDirectory)
                if (succeeded) prompt = null
            }
        }
    }
    deletion?.let { paths ->
        AlertDialog(
            onDismissRequest = { if (!state.isBusy) deletion = null },
            title = { Text(state.location.host.deletionTitle) },
            text = {
                Text(
                    state.location.host.deletionMessage(
                        if (paths.size ==
                            1
                        ) {
                            "“${RemoteFilePath.name(paths.first())}”"
                        } else {
                            "${paths.size} items"
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deletion = null
                    scope.launch { viewModel.delete(paths) }
                }, enabled = state.canMutate) { Text(state.location.host.deletionAction) }
            },
            dismissButton = { TextButton(onClick = { deletion = null }, enabled = !state.isBusy) { Text("Cancel") } },
        )
    }
    pendingExit?.let { action ->
        FileUnsavedDialog(
            state.canMutate,
            onSave = {
                pendingExit = null
                scope.launch { if (viewModel.save()) exit(action, false) }
            },
            onDiscard = {
                pendingExit = null
                scope.launch { exit(action, true) }
            },
            onKeep = { pendingExit = null },
        )
    }
}

@Composable
private fun FilePreviewMenu(
    state: FileManagerState,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TopBarTextAction("More", { expanded = true }, !state.isBusy)
        DropdownMenu(expanded, { expanded = false }) {
            val enabled = state.canMutate && state.preview?.isEditing != true
            DropdownMenuItem(text = { Text("Rename") }, onClick = {
                expanded = false
                onRename()
            }, enabled = enabled)
            DropdownMenuItem(text = { Text("Move") }, onClick = {
                expanded = false
                onMove()
            }, enabled = enabled)
            DropdownMenuItem(text = { Text(state.location.host.deletionMenuTitle) }, onClick = {
                expanded = false
                onDelete()
            }, enabled = enabled)
            DropdownMenuItem(text = { Text("Close") }, onClick = {
                expanded = false
                onClose()
            })
        }
    }
}
