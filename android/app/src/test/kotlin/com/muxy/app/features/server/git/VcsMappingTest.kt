package com.muxy.app.features.server.git

import com.muxy.app.models.VcsChecksStatus
import com.muxy.app.models.VcsDiffRowKind
import com.muxy.app.models.VcsFileStatus
import com.muxy.app.models.VcsMergeMethod
import com.muxy.app.testing.gitPullRequest
import com.muxy.app.testing.gitStatus
import com.muxy.app.testing.gitSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.muxy_mobile.GitBranch
import uniffi.muxy_mobile.GitChangeKind
import uniffi.muxy_mobile.GitChecks
import uniffi.muxy_mobile.GitDiff
import uniffi.muxy_mobile.GitDiffKind
import uniffi.muxy_mobile.GitDiffRow
import uniffi.muxy_mobile.GitFile
import uniffi.muxy_mobile.GitFileStatus
import uniffi.muxy_mobile.GitLineStat
import uniffi.muxy_mobile.GitMergeMethod

class VcsMappingTest {
    @Test
    fun branchIdentityDistinguishesDetachedAndUnbornRepositories() {
        assertEquals("main", gitStatus().toVcsStatus().branch)
        assertEquals("Detached at 1234567", gitStatus().copy(summary = gitSummary(branch = null)).toVcsStatus().branch)
        val unborn = gitStatus().copy(summary = gitSummary(branch = null, head = null, upstream = null)).toVcsStatus()
        assertEquals("No branch", unborn.branch)
        assertFalse(unborn.hasUpstream)
    }

    @Test
    fun countersClampWithoutOverflow() {
        val status = gitStatus().copy(summary = gitSummary().copy(ahead = ULong.MAX_VALUE, behind = ULong.MAX_VALUE)).toVcsStatus()
        assertEquals(Long.MAX_VALUE, status.aheadCount)
        assertEquals(Long.MAX_VALUE, status.behindCount)
        assertEquals(Long.MAX_VALUE, gitPullRequest().copy(number = ULong.MAX_VALUE).toVcsCreated().number)
    }

    @Test
    fun allFileKindsPreserveBothStagedAndUnstagedRows() {
        val kinds = GitChangeKind.entries
        val files =
            kinds.map { kind ->
                GitFileStatus(
                    GitFile(kind.name, null, kind, kind, null, null),
                    GitLineStat(null, null, false),
                    GitLineStat(null, null, false),
                )
            }
        val status = gitStatus().copy(files = files).toVcsStatus()
        val expected =
            listOf(
                VcsFileStatus.ADDED,
                VcsFileStatus.MODIFIED,
                VcsFileStatus.DELETED,
                VcsFileStatus.RENAMED,
                VcsFileStatus.COPIED,
                VcsFileStatus.TYPE_CHANGED,
                VcsFileStatus.UNTRACKED,
                VcsFileStatus.UNMERGED,
                VcsFileStatus.OTHER,
            )
        assertEquals(expected, status.stagedFiles.map { it.status })
        assertEquals(expected, status.changedFiles.map { it.status })
        assertTrue(status.stagedFiles.none { it.isUntracked })
        assertEquals(listOf("UNTRACKED"), status.changedFiles.filter { it.isUntracked }.map { it.path })
    }

    @Test
    fun branchMappingIncludesCurrentDefaultAndAllLocals() {
        val branches = listOf(GitBranch("main", false, true, true), GitBranch("feature", true, false, false)).toVcsBranches()
        assertEquals("feature", branches.current)
        assertEquals("main", branches.defaultBranch)
        assertEquals(listOf("main", "feature"), branches.locals)
        assertEquals("", emptyList<GitBranch>().toVcsBranches().current)
    }

    @Test
    fun checksHaveFailureThenPendingThenSuccessPrecedenceAndLongTotals() {
        assertEquals(VcsChecksStatus.NONE, GitChecks(0u, 0u, 0u).toVcsChecks().status)
        assertEquals("4/4 passing", GitChecks(4u, 0u, 0u).toVcsChecks().label)
        assertEquals(VcsChecksStatus.PENDING, GitChecks(4u, 0u, 1u).toVcsChecks().status)
        assertEquals(VcsChecksStatus.FAILURE, GitChecks(4u, 1u, 1u).toVcsChecks().status)
        assertEquals(UInt.MAX_VALUE.toLong() * 3, GitChecks(UInt.MAX_VALUE, UInt.MAX_VALUE, UInt.MAX_VALUE).toVcsChecks().total)
    }

    @Test
    fun pullRequestPreservesExpectedHeadDraftAndMergeability() {
        val source = gitPullRequest().copy(draft = true, mergeable = null)
        val result = source.toVcsPullRequest()
        assertEquals("head-oid", result.headOid)
        assertEquals("CLEAN", result.mergeStateStatus)
        assertEquals(null, result.mergeable)
        assertTrue(result.isDraft)
        assertEquals("main", result.baseBranch)
        assertEquals(source.url, result.url)
        assertEquals(42L, result.number)
    }

    @Test
    fun diffPreservesKindsTextNumbersAndBinaryFlags() {
        val rows = GitDiffKind.entries.map { GitDiffRow(it, ULong.MAX_VALUE, 2uL, "old", "new", "display") }
        val diff = GitDiff(rows, ULong.MAX_VALUE, 1uL, true, true).toVcsDiff("file")
        assertEquals(
            listOf(VcsDiffRowKind.HUNK, VcsDiffRowKind.CONTEXT, VcsDiffRowKind.ADDITION, VcsDiffRowKind.DELETION),
            diff.rows.map { it.kind },
        )
        assertTrue(diff.rows.all { it.oldLineNumber == Long.MAX_VALUE && it.newLineNumber == 2L && it.text == "display" })
        assertEquals(Long.MAX_VALUE, diff.additions)
        assertTrue(diff.truncated)
        assertTrue(diff.isBinary)
        assertEquals("file", diff.filePath)
    }

    @Test
    fun mergeMethodsMapOneToOne() {
        assertEquals(
            listOf(GitMergeMethod.MERGE, GitMergeMethod.SQUASH, GitMergeMethod.REBASE),
            VcsMergeMethod.entries.map { it.toSdkMethod() },
        )
    }
}
