package com.muxy.app.features.git

import com.muxy.app.core.serialization.uuidString
import com.muxy.app.models.GitDiffKey
import com.muxy.app.models.VcsBranches
import com.muxy.app.models.VcsDiff
import com.muxy.app.models.VcsMergeMethod
import com.muxy.app.models.VcsPrCreated
import com.muxy.app.models.VcsPullRequest
import com.muxy.app.models.VcsStatus
import com.muxy.app.networking.muxy1.ProjectChannel
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.networking.muxy1.protocol.VcsBranchParams
import com.muxy.app.networking.muxy1.protocol.VcsCommitParams
import com.muxy.app.networking.muxy1.protocol.VcsCreateBranchParams
import com.muxy.app.networking.muxy1.protocol.VcsCreatePrParams
import com.muxy.app.networking.muxy1.protocol.VcsGetDiffParams
import com.muxy.app.networking.muxy1.protocol.VcsMergePullRequestParams
import com.muxy.app.networking.muxy1.protocol.VcsProjectParams
import com.muxy.app.networking.muxy1.request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class ChannelGitBackend(
    private val projectId: UUID,
    private val channel: ProjectChannel,
) : GitBackend {
    override val canPublishBranch = false
    private val projectParams get() = VcsProjectParams(projectId.uuidString)

    override suspend fun status(): VcsStatus = request(Method.VCS_REFRESH, projectParams, ResultType.VCS_STATUS)

    override suspend fun branches(): VcsBranches = request(Method.VCS_LIST_BRANCHES, projectParams, ResultType.VCS_BRANCHES)

    override suspend fun diff(
        key: GitDiffKey,
        full: Boolean,
    ): VcsDiff = request(Method.VCS_GET_DIFF, VcsGetDiffParams(projectId.uuidString, key.path, full), ResultType.VCS_DIFF)

    override suspend fun commit(
        message: String,
        stageAll: Boolean,
    ) = change(Method.VCS_COMMIT, VcsCommitParams(projectId.uuidString, message, stageAll))

    override suspend fun pull() = change(Method.VCS_PULL, projectParams)

    override suspend fun push() = change(Method.VCS_PUSH, projectParams)

    override suspend fun switchBranch(branch: String) = change(Method.VCS_SWITCH_BRANCH, VcsBranchParams(projectId.uuidString, branch))

    override suspend fun createBranch(name: String) = change(Method.VCS_CREATE_BRANCH, VcsCreateBranchParams(projectId.uuidString, name))

    override suspend fun createPullRequest(
        title: String,
        body: String,
        baseBranch: String?,
        draft: Boolean,
    ): VcsPrCreated =
        request(Method.VCS_CREATE_PR, VcsCreatePrParams(projectId.uuidString, title, body, baseBranch, draft), ResultType.VCS_PR_CREATED)

    override suspend fun mergePullRequest(
        pullRequest: VcsPullRequest,
        method: VcsMergeMethod,
        deleteBranch: Boolean,
    ) = change(Method.VCS_MERGE_PULL_REQUEST, VcsMergePullRequestParams(projectId.uuidString, pullRequest.number, method, deleteBranch))

    private suspend inline fun <reified P> change(
        method: Method,
        params: P,
    ) {
        if (channel.request(method, params).type != ResultType.OK) throw GitException.UnexpectedResponse()
    }

    private suspend inline fun <reified P, reified R> request(
        method: Method,
        params: P,
        type: String,
    ): R {
        val result = channel.request(method, params)
        if (result.type != type) throw GitException.UnexpectedResponse()
        return withContext(Dispatchers.Default) { result.decode<R>() }
    }
}
