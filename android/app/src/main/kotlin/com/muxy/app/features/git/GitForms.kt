package com.muxy.app.features.git

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedProminentButton
import com.muxy.app.design.components.ThemedSectionHeader
import com.muxy.app.design.components.ThemedTextField
import com.muxy.app.design.components.ThemedToggleRow
import com.muxy.app.design.components.themedSection
import com.muxy.app.models.VcsFile
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
                item("message-header") { ThemedSectionHeader("Commit Message") }
                item("message") {
                    ThemedCell(RowPosition.FIRST) {
                        ThemedTextField(drafts.message, {
                            onChange(drafts.copy(message = it))
                        }, "Describe your change", enabled = !busy, singleLine = false, minLines = 3)
                    }
                }
                item("stage") {
                    ThemedCell(RowPosition.LAST) {
                        ThemedToggleRow("Stage all changes", drafts.stageAll, { onChange(drafts.copy(stageAll = it)) }, enabled = !busy)
                    }
                }
                val hasChanges = status?.let { it.stagedFiles.isNotEmpty() || (drafts.stageAll && it.changedFiles.isNotEmpty()) } == true
                submitButton("Commit", !busy && drafts.message.isNotBlank() && hasChanges, onSubmit)
                val files = status?.stagedFiles.orEmpty().map { it to true } + status?.changedFiles.orEmpty().map { it to false }
                changedFiles(files)
            }

            GitRoute.NewBranch -> {
                item("branch") {
                    ThemedCell(RowPosition.SINGLE) {
                        ThemedTextField(drafts.branch, { onChange(drafts.copy(branch = it)) }, "Branch name", enabled = !busy)
                    }
                }
                submitButton("Create Branch", !busy && drafts.branch.isNotBlank(), onSubmit)
            }

            GitRoute.NewPullRequest -> {
                item("title") {
                    ThemedCell(RowPosition.FIRST) {
                        ThemedTextField(drafts.title, { onChange(drafts.copy(title = it)) }, "Title", enabled = !busy)
                    }
                }
                item("body") {
                    ThemedCell(RowPosition.MIDDLE) {
                        ThemedTextField(
                            drafts.body,
                            { onChange(drafts.copy(body = it)) },
                            "Description",
                            enabled = !busy,
                            singleLine = false,
                            minLines = 4,
                        )
                    }
                }
                item("base") {
                    ThemedCell(RowPosition.MIDDLE) {
                        ThemedTextField(
                            drafts.base,
                            { onChange(drafts.copy(base = it)) },
                            "Base branch (${status?.defaultBranch ?: "default"})",
                            enabled = !busy,
                        )
                    }
                }
                item("draft") {
                    ThemedCell(RowPosition.LAST) {
                        ThemedToggleRow("Draft", drafts.draft, { onChange(drafts.copy(draft = it)) }, enabled = !busy)
                    }
                }
                submitButton("Create Pull Request", !busy && drafts.title.isNotBlank(), onSubmit)
            }

            GitRoute.NewWorktree -> {
                if (requiresWorktreeName) {
                    item("name") {
                        ThemedCell(RowPosition.FIRST) {
                            ThemedTextField(drafts.worktreeName, { onChange(drafts.copy(worktreeName = it)) }, "Name", enabled = !busy)
                        }
                    }
                }
                item("branch") {
                    ThemedCell(if (requiresWorktreeName) RowPosition.MIDDLE else RowPosition.FIRST) {
                        ThemedTextField(drafts.worktreeBranch, { onChange(drafts.copy(worktreeBranch = it)) }, "Branch", enabled = !busy)
                    }
                }
                item("create") {
                    ThemedCell(RowPosition.LAST) {
                        ThemedToggleRow(
                            "Create branch",
                            drafts.createsBranch,
                            { onChange(drafts.copy(createsBranch = it)) },
                            enabled = !busy,
                        )
                    }
                }
                submitButton(
                    "Create Worktree",
                    !busy && drafts.worktreeBranch.isNotBlank() && (!requiresWorktreeName || drafts.worktreeName.isNotBlank()),
                    onSubmit,
                )
            }

            else -> {}
        }
    }
}

private fun LazyListScope.submitButton(
    title: String,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    item("submit") {
        ThemedProminentButton(title, onSubmit, Modifier.fillMaxWidth().padding(top = 20.dp), enabled = enabled)
    }
}

private fun LazyListScope.changedFiles(files: List<Pair<VcsFile, Boolean>>) {
    themedSection(files, key = { (file, staged) -> "$staged:${file.path}" }, header = "Changes") { (file, staged) ->
        GitFileRow(file, staged)
    }
}
