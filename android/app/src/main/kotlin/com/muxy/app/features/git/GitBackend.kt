package com.muxy.app.features.git

import com.muxy.app.models.GitDiffKey
import com.muxy.app.models.VcsBranches
import com.muxy.app.models.VcsDiff
import com.muxy.app.models.VcsMergeMethod
import com.muxy.app.models.VcsPrCreated
import com.muxy.app.models.VcsPullRequest
import com.muxy.app.models.VcsStatus

interface GitBackend {
    val canPublishBranch: Boolean

    suspend fun status(): VcsStatus

    suspend fun branches(): VcsBranches

    suspend fun diff(
        key: GitDiffKey,
        full: Boolean,
    ): VcsDiff

    suspend fun commit(
        message: String,
        stageAll: Boolean,
    )

    suspend fun pull()

    suspend fun push()

    suspend fun switchBranch(branch: String)

    suspend fun createBranch(name: String)

    suspend fun createPullRequest(
        title: String,
        body: String,
        baseBranch: String?,
        draft: Boolean,
    ): VcsPrCreated

    suspend fun mergePullRequest(
        pullRequest: VcsPullRequest,
        method: VcsMergeMethod,
        deleteBranch: Boolean,
    )
}

sealed class GitException(
    message: String,
) : Exception(message) {
    class UnexpectedResponse : GitException("The server returned an unexpected Git response.")

    class NotRepository : GitException("This project isn't a Git repository.")
}
