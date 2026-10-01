package com.muxy.app.networking.server

import uniffi.muxy_mobile.GitBranch
import uniffi.muxy_mobile.GitDiff
import uniffi.muxy_mobile.GitMergeMethod
import uniffi.muxy_mobile.GitPullRequest
import uniffi.muxy_mobile.GitStatus
import uniffi.muxy_mobile.GitSummary
import uniffi.muxy_mobile.GitWorktree
import uniffi.muxy_mobile.WorktreeRemoval

interface ServerGitRepository : AutoCloseable {
    suspend fun summary(): GitSummary?

    suspend fun status(includePullRequest: Boolean): GitStatus

    suspend fun branches(): List<GitBranch>

    suspend fun diff(
        path: String,
        staged: Boolean,
        lineLimit: UInt?,
    ): GitDiff

    suspend fun commit(
        message: String,
        stageAll: Boolean,
    )

    suspend fun pull()

    suspend fun push(setUpstream: Boolean)

    suspend fun switchBranch(name: String)

    suspend fun createBranch(name: String)

    suspend fun createPullRequest(
        title: String,
        body: String,
        baseBranch: String?,
        draft: Boolean,
    ): GitPullRequest

    suspend fun mergePullRequest(
        number: ULong,
        method: GitMergeMethod,
        deleteBranch: Boolean,
        expectedHead: String?,
    )

    suspend fun worktrees(): List<GitWorktree>

    suspend fun createWorktree(
        branch: String,
        base: String?,
    ): ServerProject

    suspend fun registerWorktree(directory: String): ServerProject

    suspend fun inspectWorktreeRemoval(): WorktreeRemoval

    suspend fun removeWorktree(expected: WorktreeRemoval)
}
