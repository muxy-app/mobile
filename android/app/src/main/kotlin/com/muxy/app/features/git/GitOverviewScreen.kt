package com.muxy.app.features.git

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.components.ThemedSectionHeader
import com.muxy.app.design.components.themedSection
import com.muxy.app.models.GitDiffKey
import com.muxy.app.models.VcsFile
import com.muxy.app.models.VcsFileStatus

@Composable
internal fun GitOverviewScreen(
    state: GitState,
    pushTitle: String,
    canPush: Boolean,
    onRefresh: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onOpen: (GitRoute) -> Unit,
    onLink: (String) -> Unit,
) {
    PullToRefreshBox(state.isLoadingStatus, onRefresh) {
        ThemedList(PaddingValues(0.dp)) {
            val status = state.status
            if (status == null) {
                if (!state.isLoadingStatus) {
                    item("empty") {
                        ThemedListItem(
                            headline = { Text("No Git Information") },
                            supporting = { Text("Git status is not available for this project.") },
                        )
                    }
                }
                return@ThemedList
            }
            item("branch") {
                ThemedCell(RowPosition.SINGLE) {
                    ThemedListItem(
                        headline = { Text(status.branch) },
                        supporting = {
                            Text(
                                if (status.hasUpstream) "↓ ${status.behindCount} behind    ↑ ${status.aheadCount} ahead" else "No upstream",
                            )
                        },
                        leading = { Icon(painterResource(R.drawable.ic_fork_right), null) },
                    )
                }
            }
            item("actions") {
                ThemedSectionHeader("Actions")
                ThemedCell(
                    RowPosition.FIRST,
                ) { GitActionRow("Pull", R.drawable.ic_arrow_downward, !state.isBusy && status.hasUpstream, onPull) }
                ThemedCell(RowPosition.MIDDLE) { GitActionRow(pushTitle, R.drawable.ic_arrow_upward, !state.isBusy && canPush, onPush) }
                ThemedCell(RowPosition.LAST) {
                    GitActionRow(
                        "Commit ${state.totalChanges}",
                        R.drawable.ic_check,
                        !state.isBusy && state.totalChanges > 0,
                    ) { onOpen(GitRoute.Commit) }
                }
            }
            item("manage") {
                ThemedSectionHeader("Manage")
                ThemedCell(
                    RowPosition.FIRST,
                ) { GitActionRow("Branches", R.drawable.ic_fork_right, !state.isBusy) { onOpen(GitRoute.Branches) } }
                ThemedCell(
                    RowPosition.MIDDLE,
                ) { GitActionRow("Worktrees", R.drawable.ic_stacks, !state.isBusy) { onOpen(GitRoute.Worktrees) } }
                val pr = status.pullRequest
                if (pr == null) {
                    ThemedCell(
                        RowPosition.LAST,
                    ) { GitActionRow("New Pull Request", R.drawable.ic_merge, !state.isBusy) { onOpen(GitRoute.NewPullRequest) } }
                } else {
                    ThemedCell(RowPosition.MIDDLE) {
                        GitActionRow("Pull Request #${pr.number}", R.drawable.ic_merge, !state.isBusy) { onOpen(GitRoute.PullRequest) }
                    }
                    ThemedCell(
                        RowPosition.LAST,
                    ) { GitActionRow("Open Pull Request", R.drawable.ic_language, !state.isBusy) { onLink(pr.url) } }
                }
            }
            if (state.totalChanges == 0) {
                item("clean") {
                    ThemedSectionHeader("Status")
                    ThemedCell(RowPosition.SINGLE) {
                        ThemedListItem(
                            headline = { Text("Working tree clean") },
                            leading = { Icon(painterResource(R.drawable.ic_check), null) },
                        )
                    }
                }
            }
            listOf(true, false).forEach { staged ->
                val files = if (staged) status.stagedFiles else status.changedFiles
                if (files.isNotEmpty()) {
                    themedSection(files, key = { "$staged:${it.path}" }, header = if (staged) "Staged" else "Unstaged") { file ->
                        GitFileRow(
                            file,
                            staged,
                            Modifier.clickable(enabled = !state.isBusy) { onOpen(GitRoute.Diff(GitDiffKey(file.path, staged))) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GitActionRow(
    text: String,
    @DrawableRes icon: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ThemedListItem(
        headline = { Text(text, color = if (enabled) LocalAppTheme.current.foreground else LocalAppTheme.current.secondaryForeground) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        leading = { Icon(painterResource(icon), null) },
    )
}

@Composable
internal fun GitFileRow(
    file: VcsFile,
    staged: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val color =
        when (file.status) {
            VcsFileStatus.ADDED, VcsFileStatus.UNTRACKED -> theme.green
            VcsFileStatus.DELETED, VcsFileStatus.UNMERGED -> theme.red
            else -> theme.yellow
        }
    ThemedListItem(
        headline = { Text(file.path) },
        modifier = modifier,
        supporting = { Text(if (staged) "Staged" else "Unstaged") },
        leading = { Text(file.status.letter, color = color) },
    )
}
