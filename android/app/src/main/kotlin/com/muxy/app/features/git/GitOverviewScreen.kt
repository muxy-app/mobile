package com.muxy.app.features.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.CardRow
import com.muxy.app.design.components.ListRow
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedChevron
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
                        ThemedCell(RowPosition.SINGLE) {
                            CardRow(title = "No Git Information", supporting = "Git status is not available for this project.")
                        }
                    }
                }
                return@ThemedList
            }
            item("branch") {
                ThemedCell(RowPosition.SINGLE) {
                    CardRow(
                        title = status.branch,
                        icon = R.drawable.ic_fork_right,
                        supporting =
                            if (status.hasUpstream) "↓ ${status.behindCount} behind    ↑ ${status.aheadCount} ahead" else "No upstream",
                    )
                }
            }
            item("actions") {
                ThemedSectionHeader("Actions")
                ThemedCell(RowPosition.FIRST) {
                    CardRow("Pull", icon = R.drawable.ic_arrow_downward, enabled = !state.isBusy && status.hasUpstream, onClick = onPull)
                }
                ThemedCell(RowPosition.MIDDLE) {
                    CardRow(pushTitle, icon = R.drawable.ic_arrow_upward, enabled = !state.isBusy && canPush, onClick = onPush)
                }
                ThemedCell(RowPosition.LAST) {
                    CardRow(
                        "Commit",
                        icon = R.drawable.ic_check,
                        value = state.totalChanges.toString(),
                        enabled = !state.isBusy && state.totalChanges > 0,
                        navigates = true,
                        onClick = { onOpen(GitRoute.Commit) },
                    )
                }
            }
            item("manage") {
                ThemedSectionHeader("Manage")
                ThemedCell(RowPosition.FIRST) {
                    CardRow(
                        "Branches",
                        icon = R.drawable.ic_fork_right,
                        enabled = !state.isBusy,
                        navigates = true,
                        onClick = { onOpen(GitRoute.Branches) },
                    )
                }
                ThemedCell(RowPosition.MIDDLE) {
                    CardRow(
                        "Worktrees",
                        icon = R.drawable.ic_stacks,
                        enabled = !state.isBusy,
                        navigates = true,
                        onClick = { onOpen(GitRoute.Worktrees) },
                    )
                }
                val pr = status.pullRequest
                if (pr == null) {
                    ThemedCell(RowPosition.LAST) {
                        CardRow(
                            "New Pull Request",
                            icon = R.drawable.ic_merge,
                            enabled = !state.isBusy,
                            navigates = true,
                            onClick = { onOpen(GitRoute.NewPullRequest) },
                        )
                    }
                } else {
                    ThemedCell(RowPosition.MIDDLE) {
                        CardRow(
                            "Pull Request #${pr.number}",
                            icon = R.drawable.ic_merge,
                            enabled = !state.isBusy,
                            navigates = true,
                            onClick = { onOpen(GitRoute.PullRequest) },
                        )
                    }
                    ThemedCell(RowPosition.LAST) {
                        CardRow("Open Pull Request", icon = R.drawable.ic_language, enabled = !state.isBusy, onClick = { onLink(pr.url) })
                    }
                }
            }
            if (state.totalChanges == 0) {
                item("clean") {
                    ThemedSectionHeader("Status")
                    ThemedCell(RowPosition.SINGLE) {
                        CardRow("Working tree clean", icon = R.drawable.ic_check, iconTint = LocalAppTheme.current.green)
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
                            navigates = true,
                            modifier =
                                Modifier.clickable(enabled = !state.isBusy, role = Role.Button) {
                                    onOpen(GitRoute.Diff(GitDiffKey(file.path, staged)))
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GitFileRow(
    file: VcsFile,
    staged: Boolean,
    modifier: Modifier = Modifier,
    navigates: Boolean = false,
) {
    val theme = LocalAppTheme.current
    val color =
        when (file.status) {
            VcsFileStatus.ADDED, VcsFileStatus.UNTRACKED -> theme.green
            VcsFileStatus.DELETED, VcsFileStatus.UNMERGED -> theme.red
            else -> theme.yellow
        }
    ThemedListItem(
        headline = { Text(file.path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        modifier = modifier,
        supporting = { Text(if (staged) "Staged" else "Unstaged") },
        leading = {
            Text(
                file.status.letter,
                modifier = Modifier.size(ListRow.iconSize),
                color = color,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        },
        trailing = if (navigates) ({ ThemedChevron() }) else null,
    )
}
