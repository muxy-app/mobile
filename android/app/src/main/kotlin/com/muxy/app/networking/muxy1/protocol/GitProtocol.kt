package com.muxy.app.networking.muxy1.protocol

import com.muxy.app.models.VcsMergeMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VcsProjectParams(
    @SerialName("projectID") val projectId: String,
)

@Serializable
data class SelectWorktreeParams(
    @SerialName("projectID") val projectId: String,
    @SerialName("worktreeID") val worktreeId: String,
)

@Serializable
data class VcsCommitParams(
    @SerialName("projectID") val projectId: String,
    val message: String,
    val stageAll: Boolean,
)

@Serializable
data class VcsBranchParams(
    @SerialName("projectID") val projectId: String,
    val branch: String,
)

@Serializable
data class VcsCreateBranchParams(
    @SerialName("projectID") val projectId: String,
    val name: String,
)

@Serializable
data class VcsCreatePrParams(
    @SerialName("projectID") val projectId: String,
    val title: String,
    val body: String,
    val baseBranch: String?,
    val draft: Boolean,
)

@Serializable
data class VcsMergePullRequestParams(
    @SerialName("projectID") val projectId: String,
    val number: Long,
    val method: VcsMergeMethod,
    val deleteBranch: Boolean,
)

@Serializable
data class VcsAddWorktreeParams(
    @SerialName("projectID") val projectId: String,
    val name: String,
    val branch: String,
    val createBranch: Boolean,
)

@Serializable
data class VcsRemoveWorktreeParams(
    @SerialName("projectID") val projectId: String,
    @SerialName("worktreeID") val worktreeId: String,
)

@Serializable
data class VcsGetDiffParams(
    @SerialName("projectID") val projectId: String,
    val filePath: String,
    val forceFull: Boolean,
)
