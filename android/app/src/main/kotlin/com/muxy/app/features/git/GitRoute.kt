package com.muxy.app.features.git

import androidx.navigation3.runtime.NavKey
import com.muxy.app.models.GitDiffKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface GitRoute : NavKey {
    @Serializable data object Overview : GitRoute

    @Serializable data object Commit : GitRoute

    @Serializable data object Branches : GitRoute

    @Serializable data object NewBranch : GitRoute

    @Serializable data object PullRequest : GitRoute

    @Serializable data object NewPullRequest : GitRoute

    @Serializable data class Diff(
        val key: GitDiffKey,
    ) : GitRoute

    @Serializable data object Worktrees : GitRoute

    @Serializable data object NewWorktree : GitRoute

    val title: String get() =
        when (this) {
            Overview -> "Git"
            Commit -> "Commit"
            Branches -> "Branches"
            NewBranch -> "New Branch"
            PullRequest -> "Pull Request"
            NewPullRequest -> "New Pull Request"
            is Diff -> key.path.substringAfterLast('/')
            Worktrees -> "Worktrees"
            NewWorktree -> "New Worktree"
        }
}

@Serializable
internal data class GitDrafts(
    val message: String = "",
    val stageAll: Boolean = true,
    val branch: String = "",
    val title: String = "",
    val body: String = "",
    val base: String = "",
    val draft: Boolean = false,
    val worktreeName: String = "",
    val worktreeBranch: String = "",
    val createsBranch: Boolean = true,
) {
    fun isDirty(route: GitRoute): Boolean =
        when (route) {
            GitRoute.Commit -> message.isNotEmpty() || !stageAll
            GitRoute.NewBranch -> branch.isNotEmpty()
            GitRoute.NewPullRequest -> title.isNotEmpty() || body.isNotEmpty() || base.isNotEmpty() || draft
            GitRoute.NewWorktree -> worktreeName.isNotEmpty() || worktreeBranch.isNotEmpty() || !createsBranch
            else -> false
        }

    fun clearing(route: GitRoute): GitDrafts =
        when (route) {
            GitRoute.Commit -> copy(message = "", stageAll = true)
            GitRoute.NewBranch -> copy(branch = "")
            GitRoute.NewPullRequest -> copy(title = "", body = "", base = "", draft = false)
            GitRoute.NewWorktree -> copy(worktreeName = "", worktreeBranch = "", createsBranch = true)
            else -> this
        }
}
