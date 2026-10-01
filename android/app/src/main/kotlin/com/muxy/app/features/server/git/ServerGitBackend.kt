package com.muxy.app.features.server.git

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.features.git.GitBackend
import com.muxy.app.features.git.GitException
import com.muxy.app.features.server.ServerController
import com.muxy.app.features.server.withGit
import com.muxy.app.models.GitDiffKey
import com.muxy.app.models.VcsMergeMethod
import com.muxy.app.models.VcsPullRequest
import uniffi.muxy_mobile.MobileException

class ServerGitBackend(
    private val projectId: String,
    private val server: ServerController,
) : GitBackend {
    override val canPublishBranch = true

    override suspend fun status() =
        server.withGit(projectId) { repository ->
            try {
                repository.status(includePullRequest = true).toVcsStatus()
            } catch (error: MobileException.Server) {
                val summary = attempt { repository.summary() }
                if (summary.isSuccess && summary.getOrNull() == null) throw GitException.NotRepository()
                throw error
            }
        }

    override suspend fun branches() = server.withGit(projectId) { it.branches().toVcsBranches() }

    override suspend fun diff(
        key: GitDiffKey,
        full: Boolean,
    ) = server.withGit(projectId) {
        it.diff(key.path, key.isStaged, if (full) null else DIFF_LINE_LIMIT).toVcsDiff(key.path)
    }

    override suspend fun commit(
        message: String,
        stageAll: Boolean,
    ) = server.withGit(projectId) { it.commit(message, stageAll) }

    override suspend fun pull() = server.withGit(projectId) { it.pull() }

    override suspend fun push() = server.withGit(projectId) { it.push(setUpstream = false) }

    override suspend fun switchBranch(branch: String) = server.withGit(projectId) { it.switchBranch(branch) }

    override suspend fun createBranch(name: String) = server.withGit(projectId) { it.createBranch(name) }

    override suspend fun createPullRequest(
        title: String,
        body: String,
        baseBranch: String?,
        draft: Boolean,
    ) = server.withGit(projectId) {
        it.createPullRequest(title, body, baseBranch, draft).toVcsCreated()
    }

    override suspend fun mergePullRequest(
        pullRequest: VcsPullRequest,
        method: VcsMergeMethod,
        deleteBranch: Boolean,
    ) = server.withGit(projectId) {
        it.mergePullRequest(pullRequest.number.coerceAtLeast(0).toULong(), method.toSdkMethod(), deleteBranch, pullRequest.headOid)
    }

    companion object {
        const val DIFF_LINE_LIMIT = 800u
    }
}
