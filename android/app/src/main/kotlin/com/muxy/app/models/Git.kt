@file:UseSerializers(UuidSerializer::class)

package com.muxy.app.models

import com.muxy.app.core.serialization.UuidSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.UUID

@Serializable
enum class VcsFileStatus(
    val letter: String,
) {
    @SerialName("added")
    ADDED("A"),

    @SerialName("modified")
    MODIFIED("M"),

    @SerialName("deleted")
    DELETED("D"),

    @SerialName("renamed")
    RENAMED("R"),

    @SerialName("copied")
    COPIED("C"),

    @SerialName("typeChanged")
    TYPE_CHANGED("T"),

    @SerialName("untracked")
    UNTRACKED("?"),

    @SerialName("unmerged")
    UNMERGED("!"),

    @SerialName("other")
    OTHER("X"),
}

@Serializable
data class VcsFile(
    val path: String,
    val status: VcsFileStatus,
    val isUntracked: Boolean,
)

@Serializable
data class GitDiffKey(
    val path: String,
    val isStaged: Boolean,
)

@Serializable
enum class VcsChecksStatus {
    @SerialName("none")
    NONE,

    @SerialName("pending")
    PENDING,

    @SerialName("success")
    SUCCESS,

    @SerialName("failure")
    FAILURE,
}

@Serializable
data class VcsChecks(
    val status: VcsChecksStatus,
    val passing: Long,
    val failing: Long,
    val pending: Long,
    val total: Long,
) {
    val label: String
        get() =
            when {
                failing > 0 -> "$failing failing"
                pending > 0 -> "$pending pending"
                total > 0 -> "$passing/$total passing"
                else -> status.name.lowercase()
            }
}

@Serializable
data class VcsPullRequest(
    val url: String,
    val number: Long,
    val state: String,
    val isDraft: Boolean,
    val baseBranch: String,
    val mergeable: Boolean? = null,
    val mergeStateStatus: String? = null,
    val checks: VcsChecks? = null,
    val headOid: String? = null,
)

@Serializable
enum class VcsMergeMethod(
    val title: String,
) {
    @SerialName("merge")
    MERGE("Merge"),

    @SerialName("squash")
    SQUASH("Squash"),

    @SerialName("rebase")
    REBASE("Rebase"),
}

@Serializable
data class VcsStatus(
    val branch: String,
    val aheadCount: Long,
    val behindCount: Long,
    val hasUpstream: Boolean,
    val stagedFiles: List<VcsFile>,
    val changedFiles: List<VcsFile>,
    val defaultBranch: String? = null,
    val pullRequest: VcsPullRequest? = null,
)

@Serializable
data class VcsBranches(
    val current: String,
    val locals: List<String>,
    val defaultBranch: String? = null,
)

@Serializable
data class VcsPrCreated(
    val url: String,
    val number: Long,
)

@Serializable
enum class VcsDiffRowKind {
    @SerialName("hunk")
    HUNK,

    @SerialName("context")
    CONTEXT,

    @SerialName("addition")
    ADDITION,

    @SerialName("deletion")
    DELETION,

    @SerialName("collapsed")
    COLLAPSED,
}

@Serializable
data class VcsDiffRow(
    val kind: VcsDiffRowKind,
    val oldLineNumber: Long? = null,
    val newLineNumber: Long? = null,
    val text: String,
)

@Serializable
data class VcsDiff(
    val filePath: String,
    val rows: List<VcsDiffRow>,
    val additions: Long,
    val deletions: Long,
    val truncated: Boolean,
    val isBinary: Boolean,
)

@Serializable
data class Worktree(
    val id: UUID,
    val name: String,
    val path: String,
    val branch: String,
    val isPrimary: Boolean,
    val canBeRemoved: Boolean,
    val createdAt: String,
)
