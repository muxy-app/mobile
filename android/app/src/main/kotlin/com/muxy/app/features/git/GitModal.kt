package com.muxy.app.features.git

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.ui.NavDisplay
import com.muxy.app.R
import com.muxy.app.core.logging.Log
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.TopBarAction
import com.muxy.app.features.navigation.NavigationTransitions
import com.muxy.app.features.navigation.close
import com.muxy.app.features.navigation.open
import kotlinx.coroutines.launch

@Composable
fun GitModal(
    git: GitViewModel,
    worktrees: WorktreesViewModel,
    startsWithWorktrees: Boolean,
    onClose: () -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val state by git.state.collectAsStateWithLifecycle()
    val worktreeState by worktrees.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val backStack =
        rememberSerializable(serializer = NavBackStackSerializer(GitRoute.serializer())) {
            NavBackStack<GitRoute>(if (startsWithWorktrees) GitRoute.Worktrees else GitRoute.Overview)
        }
    var drafts by rememberSerializable(stateSerializer = GitDrafts.serializer()) { mutableStateOf(GitDrafts()) }
    var pendingClose by remember { mutableStateOf<Boolean?>(null) }
    var linkError by remember { mutableStateOf<String?>(null) }
    val route = backStack.last()
    val busy = state.isBusy || worktreeState.isBusy || state.completedForm != null || worktreeState.completedForm != null
    val leave: (Boolean) -> Unit = { closesModal ->
        drafts = drafts.clearing(route)
        if (closesModal || backStack.size == 1) onClose() else backStack.removeLastOrNull()
    }
    val requestLeave: (Boolean) -> Unit = { closesModal ->
        if (!busy) {
            if (drafts.isDirty(route)) pendingClose = closesModal else leave(closesModal)
        }
    }
    val openLink: (String) -> Unit = { linkError = openWebLink(uriHandler, it) }
    val open: (GitRoute) -> Unit = { if (!busy) backStack.open(it) }
    val refresh: () -> Unit = {
        if (!busy) {
            scope.launch {
                when (route) {
                    GitRoute.Worktrees -> worktrees.refresh()
                    GitRoute.Branches -> git.refreshBranches()
                    is GitRoute.Diff -> git.loadDiff(route.key)
                    else -> git.refreshStatus()
                }
            }
        }
    }
    LaunchedEffect(route) {
        when (route) {
            GitRoute.Branches -> git.refreshBranches()
            GitRoute.Worktrees -> worktrees.refresh()
            is GitRoute.Diff -> if (route.key !in state.diffs) git.loadDiff(route.key)
            else -> if (state.status == null) git.refreshStatus()
        }
    }
    LaunchedEffect(worktreeState.revision) {
        if (worktreeState.revision == 0L) return@LaunchedEffect
        git.invalidateDiffs()
        git.refreshStatus()
        git.refreshBranches()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.completedForm, worktreeState.completedForm, lifecycle) {
        lifecycle.withResumed {
            val completions = listOfNotNull(state.completedForm, worktreeState.completedForm)
            for (completion in completions) {
                if (!git.acknowledgeFormCompletion(completion) && !worktrees.acknowledgeFormCompletion(completion)) continue
                drafts = drafts.clearing(completion.route)
                backStack.close(completion.route)
                completion.url?.let(openLink)
            }
        }
    }
    val submit: () -> Unit = {
        scope.launch {
            when (route) {
                GitRoute.Commit -> git.commit(drafts.message, drafts.stageAll)
                GitRoute.NewBranch -> git.createBranch(drafts.branch)
                GitRoute.NewWorktree -> worktrees.create(drafts.worktreeName, drafts.worktreeBranch, drafts.createsBranch)
                GitRoute.NewPullRequest -> git.createPullRequest(drafts.title, drafts.body, drafts.base, drafts.draft)
                else -> Unit
            }
        }
    }
    Scaffold(
        topBar = {
            MuxyTopAppBar(
                title = route.title,
                navigationIcon = { TopBarAction(R.drawable.ic_arrow_back, "Back", { requestLeave(false) }, !busy) },
                actions = {
                    if (!route.isForm) TopBarAction(R.drawable.ic_refresh, "Refresh", refresh, !busy)
                    TopBarAction(R.drawable.ic_close, "Close", { requestLeave(true) }, !busy)
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
            val isWorktree = route == GitRoute.Worktrees || route == GitRoute.NewWorktree
            val error = linkError ?: if (isWorktree) worktreeState.errorMessage else state.errorMessage
            error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = LocalAppTheme.current.red) }
            if (busy || state.isLoadingStatus || state.isLoadingBranches || state.loadingDiffs.isNotEmpty() ||
                worktreeState.isLoading
            ) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            NavDisplay(
                backStack = backStack,
                onBack = { requestLeave(false) },
                transitionSpec = { NavigationTransitions.push() },
                popTransitionSpec = { NavigationTransitions.pop() },
                predictivePopTransitionSpec = { NavigationTransitions.pop() },
                entryProvider = { page ->
                    NavEntry(page) {
                        Surface(Modifier.fillMaxSize()) {
                            when (page) {
                                GitRoute.Overview -> {
                                    GitOverviewScreen(
                                        state,
                                        state.status?.let(git::pushTitle) ?: "Push",
                                        state.status?.let(git::canPush) == true,
                                        refresh,
                                        { scope.launch { git.pull() } },
                                        { scope.launch { git.push() } },
                                        open,
                                        openLink,
                                    )
                                }

                                GitRoute.Branches -> {
                                    GitBranchesScreen(
                                        state,
                                        refresh,
                                        { open(GitRoute.NewBranch) },
                                        { scope.launch { git.switchBranch(it) } },
                                    )
                                }

                                GitRoute.PullRequest -> {
                                    GitPullRequestScreen(state.status?.pullRequest, busy, openLink) { method, deleteBranch ->
                                        state.status?.pullRequest?.let { pr ->
                                            scope.launch { git.mergePullRequest(pr, method, deleteBranch) }
                                        }
                                    }
                                }

                                is GitRoute.Diff -> {
                                    GitDiffScreen(state.diffs[page.key], page.key.isStaged, page.key in state.loadingDiffs) {
                                        scope.launch { git.loadDiff(page.key, full = true) }
                                    }
                                }

                                GitRoute.Worktrees -> {
                                    WorktreesScreen(
                                        worktreeState,
                                        refresh,
                                        { open(GitRoute.NewWorktree) },
                                        { row ->
                                            scope.launch { worktrees.open(row)?.let(onOpenProject) }
                                        },
                                        { scope.launch { worktrees.prepareRemoval(it) } },
                                    )
                                }

                                else -> {
                                    GitFormScreen(page, drafts, { drafts = it }, busy, worktrees.requiresName, state.status, submit)
                                }
                            }
                        }
                    }
                },
            )
        }
    }
    BackHandler { requestLeave(false) }
    pendingClose?.let { closesModal ->
        AlertDialog(
            onDismissRequest = { pendingClose = null },
            title = { Text("Unsaved changes") },
            text = { Text("Discard this draft?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingClose = null
                    leave(closesModal)
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { pendingClose = null }) { Text("Keep editing") } },
        )
    }
    worktreeState.pendingRemoval?.let { pending ->
        WorktreeRemovalDialog(pending, worktrees::cancelRemoval) { scope.launch { worktrees.remove() } }
    }
}

private val GitRoute.isForm: Boolean get() =
    this == GitRoute.Commit || this == GitRoute.NewBranch || this == GitRoute.NewPullRequest ||
        this == GitRoute.NewWorktree

private fun openWebLink(
    handler: UriHandler,
    url: String,
): String? {
    val uri = url.toUri()
    if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() ||
        uri.userInfo != null
    ) {
        return "The pull request link is invalid."
    }
    try {
        handler.openUri(url)
        return null
    } catch (error: Exception) {
        Log.client.error("Opening a pull request link failed: ${error.javaClass.simpleName}")
        return "The pull request link could not be opened."
    }
}
