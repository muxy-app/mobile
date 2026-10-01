package com.muxy.app.features.server.git

import com.muxy.app.models.VcsBranches
import com.muxy.app.models.VcsChecks
import com.muxy.app.models.VcsChecksStatus
import com.muxy.app.models.VcsDiff
import com.muxy.app.models.VcsDiffRow
import com.muxy.app.models.VcsDiffRowKind
import com.muxy.app.models.VcsFile
import com.muxy.app.models.VcsFileStatus
import com.muxy.app.models.VcsMergeMethod
import com.muxy.app.models.VcsPrCreated
import com.muxy.app.models.VcsPullRequest
import com.muxy.app.models.VcsStatus
import uniffi.muxy_mobile.GitBranch
import uniffi.muxy_mobile.GitChangeKind
import uniffi.muxy_mobile.GitChecks
import uniffi.muxy_mobile.GitDiff
import uniffi.muxy_mobile.GitDiffKind
import uniffi.muxy_mobile.GitMergeMethod
import uniffi.muxy_mobile.GitPullRequest
import uniffi.muxy_mobile.GitStatus

fun GitStatus.toVcsStatus() =
    VcsStatus(
        branch = summary.branch ?: summary.head?.let { "Detached at ${it.take(7)}" } ?: "No branch",
        aheadCount = summary.ahead.clampedLong(),
        behindCount = summary.behind.clampedLong(),
        hasUpstream = summary.upstream != null,
        stagedFiles = files.mapNotNull { status -> status.file.staged?.let { VcsFile(status.file.path, it.toVcsStatus(), false) } },
        changedFiles =
            files.mapNotNull { status ->
                status.file.unstaged?.let { VcsFile(status.file.path, it.toVcsStatus(), it == GitChangeKind.UNTRACKED) }
            },
        defaultBranch = defaultBranch,
        pullRequest = pullRequest?.toVcsPullRequest(),
    )

fun List<GitBranch>.toVcsBranches() =
    VcsBranches(
        current = firstOrNull { it.current }?.name.orEmpty(),
        locals = map { it.name },
        defaultBranch = firstOrNull { it.default }?.name,
    )

fun GitPullRequest.toVcsPullRequest() =
    VcsPullRequest(
        url = url,
        number = number.clampedLong(),
        state = state,
        isDraft = draft,
        baseBranch = baseBranch,
        mergeable = mergeable,
        mergeStateStatus = mergeState.uppercase(),
        checks = checks.toVcsChecks(),
        headOid = headOid,
    )

fun GitPullRequest.toVcsCreated() = VcsPrCreated(url, number.clampedLong())

fun GitChecks.toVcsChecks(): VcsChecks {
    val status =
        when {
            failing > 0u -> VcsChecksStatus.FAILURE
            pending > 0u -> VcsChecksStatus.PENDING
            passing > 0u -> VcsChecksStatus.SUCCESS
            else -> VcsChecksStatus.NONE
        }
    return VcsChecks(status, passing.toLong(), failing.toLong(), pending.toLong(), passing.toLong() + failing.toLong() + pending.toLong())
}

fun GitDiff.toVcsDiff(path: String) =
    VcsDiff(
        path,
        rows.map { VcsDiffRow(it.kind.toVcsKind(), it.oldLineNumber?.clampedLong(), it.newLineNumber?.clampedLong(), it.text) },
        additions.clampedLong(),
        deletions.clampedLong(),
        truncated,
        binary,
    )

fun VcsMergeMethod.toSdkMethod(): GitMergeMethod =
    when (this) {
        VcsMergeMethod.MERGE -> GitMergeMethod.MERGE
        VcsMergeMethod.SQUASH -> GitMergeMethod.SQUASH
        VcsMergeMethod.REBASE -> GitMergeMethod.REBASE
    }

private fun GitChangeKind.toVcsStatus(): VcsFileStatus =
    when (this) {
        GitChangeKind.ADDED -> VcsFileStatus.ADDED
        GitChangeKind.MODIFIED -> VcsFileStatus.MODIFIED
        GitChangeKind.DELETED -> VcsFileStatus.DELETED
        GitChangeKind.RENAMED -> VcsFileStatus.RENAMED
        GitChangeKind.COPIED -> VcsFileStatus.COPIED
        GitChangeKind.TYPE_CHANGED -> VcsFileStatus.TYPE_CHANGED
        GitChangeKind.UNTRACKED -> VcsFileStatus.UNTRACKED
        GitChangeKind.CONFLICTED -> VcsFileStatus.UNMERGED
        GitChangeKind.OTHER -> VcsFileStatus.OTHER
    }

private fun GitDiffKind.toVcsKind(): VcsDiffRowKind =
    when (this) {
        GitDiffKind.HUNK -> VcsDiffRowKind.HUNK
        GitDiffKind.CONTEXT -> VcsDiffRowKind.CONTEXT
        GitDiffKind.ADDITION -> VcsDiffRowKind.ADDITION
        GitDiffKind.DELETION -> VcsDiffRowKind.DELETION
    }

private fun ULong.clampedLong(): Long = coerceAtMost(Long.MAX_VALUE.toULong()).toLong()
