package com.muxy.app.features.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.components.ThemedSectionHeader
import com.muxy.app.design.components.ThemedTextField
import com.muxy.app.models.VcsStatus

@Composable
internal fun GitFormScreen(
    route: GitRoute,
    drafts: GitDrafts,
    onChange: (GitDrafts) -> Unit,
    busy: Boolean,
    requiresWorktreeName: Boolean,
    status: VcsStatus?,
    onSubmit: () -> Unit,
) {
    ThemedList(PaddingValues(0.dp)) {
        when (route) {
            GitRoute.Commit -> {
                item("message") {
                    ThemedSectionHeader("Commit Message")
                    ThemedCell(RowPosition.SINGLE) {
                        ThemedTextField(drafts.message, {
                            onChange(drafts.copy(message = it))
                        }, "Describe your change", enabled = !busy, singleLine = false, minLines = 3)
                    }
                }
                item("stage") { GitToggle("Stage all changes", drafts.stageAll, !busy) { onChange(drafts.copy(stageAll = it)) } }
                item("submit") {
                    val hasChanges =
                        status?.let { it.stagedFiles.isNotEmpty() || (drafts.stageAll && it.changedFiles.isNotEmpty()) } == true
                    TextButton(onClick = onSubmit, enabled = !busy && drafts.message.isNotBlank() && hasChanges) { Text("Commit") }
                }
                status?.stagedFiles?.forEach { file -> item("staged:${file.path}") { GitFileRow(file, true) } }
                status?.changedFiles?.forEach { file -> item("changed:${file.path}") { GitFileRow(file, false) } }
            }

            GitRoute.NewBranch -> {
                item("branch") { ThemedTextField(drafts.branch, { onChange(drafts.copy(branch = it)) }, "Branch name", enabled = !busy) }
                item("submit") { TextButton(onClick = onSubmit, enabled = !busy && drafts.branch.isNotBlank()) { Text("Create Branch") } }
            }

            GitRoute.NewPullRequest -> {
                item("title") { ThemedTextField(drafts.title, { onChange(drafts.copy(title = it)) }, "Title", enabled = !busy) }
                item("body") {
                    ThemedTextField(
                        drafts.body,
                        { onChange(drafts.copy(body = it)) },
                        "Description",
                        enabled = !busy,
                        singleLine = false,
                        minLines = 4,
                    )
                }
                item("base") {
                    ThemedTextField(
                        drafts.base,
                        { onChange(drafts.copy(base = it)) },
                        "Base branch (${status?.defaultBranch ?: "default"})",
                        enabled = !busy,
                    )
                }
                item("draft") { GitToggle("Draft", drafts.draft, !busy) { onChange(drafts.copy(draft = it)) } }
                item(
                    "submit",
                ) { TextButton(onClick = onSubmit, enabled = !busy && drafts.title.isNotBlank()) { Text("Create Pull Request") } }
            }

            GitRoute.NewWorktree -> {
                if (requiresWorktreeName) {
                    item("name") {
                        ThemedTextField(drafts.worktreeName, { onChange(drafts.copy(worktreeName = it)) }, "Name", enabled = !busy)
                    }
                }
                item(
                    "branch",
                ) { ThemedTextField(drafts.worktreeBranch, { onChange(drafts.copy(worktreeBranch = it)) }, "Branch", enabled = !busy) }
                item("create") { GitToggle("Create branch", drafts.createsBranch, !busy) { onChange(drafts.copy(createsBranch = it)) } }
                item("submit") {
                    TextButton(
                        onClick = onSubmit,
                        enabled =
                            !busy && drafts.worktreeBranch.isNotBlank() && (!requiresWorktreeName || drafts.worktreeName.isNotBlank()),
                    ) { Text("Create Worktree") }
                }
            }

            else -> {}
        }
    }
}

@Composable
internal fun GitToggle(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ThemedListItem(
        headline = { Text(title) },
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
        trailing = { Checkbox(checked, onChange, enabled = enabled) },
    )
}
