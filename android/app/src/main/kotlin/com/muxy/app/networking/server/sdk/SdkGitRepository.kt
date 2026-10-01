package com.muxy.app.networking.server.sdk

import com.muxy.app.networking.server.ServerGitRepository
import uniffi.muxy_mobile.GitMergeMethod
import uniffi.muxy_mobile.GitRepository
import uniffi.muxy_mobile.WorktreeRemoval

class SdkGitRepository(
    private val repository: GitRepository,
    private val lanes: SdkLanes,
) : ServerGitRepository {
    override suspend fun summary() = lanes.gitRequest { repository.summary() }

    override suspend fun status(includePullRequest: Boolean) = lanes.gitRequest { repository.status(includePullRequest) }

    override suspend fun branches() = lanes.gitRequest { repository.branches() }

    override suspend fun diff(
        path: String,
        staged: Boolean,
        lineLimit: UInt?,
    ) = lanes.gitRequest { repository.diff(path, staged, lineLimit) }

    override suspend fun commit(
        message: String,
        stageAll: Boolean,
    ) {
        lanes.gitRequest { repository.commit(message, stageAll) }
    }

    override suspend fun pull() = lanes.gitRequest { repository.pull() }

    override suspend fun push(setUpstream: Boolean) = lanes.gitRequest { repository.push(setUpstream) }

    override suspend fun switchBranch(name: String) = lanes.gitRequest { repository.switchBranch(name) }

    override suspend fun createBranch(name: String) = lanes.gitRequest { repository.createBranch(name) }

    override suspend fun createPullRequest(
        title: String,
        body: String,
        baseBranch: String?,
        draft: Boolean,
    ) = lanes.gitRequest { repository.createPullRequest(title, body, baseBranch, draft) }

    override suspend fun mergePullRequest(
        number: ULong,
        method: GitMergeMethod,
        deleteBranch: Boolean,
        expectedHead: String?,
    ) = lanes.gitRequest { repository.mergePullRequest(number, method, deleteBranch, expectedHead) }

    override suspend fun worktrees() = lanes.gitRequest { repository.worktrees() }

    override suspend fun createWorktree(
        branch: String,
        base: String?,
    ) = lanes.gitRequest { repository.createWorktree(branch, base, null) }

    override suspend fun registerWorktree(directory: String) = lanes.gitRequest { repository.registerWorktree(directory) }

    override suspend fun inspectWorktreeRemoval() = lanes.gitRequest { repository.inspectWorktreeRemoval() }

    override suspend fun removeWorktree(expected: WorktreeRemoval) = lanes.gitRequest { repository.removeWorktree(expected) }

    override fun close() = repository.close()
}
