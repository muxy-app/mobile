package com.muxy.app.features.demo

import com.muxy.app.models.VcsBranches
import com.muxy.app.models.VcsChecks
import com.muxy.app.models.VcsChecksStatus
import com.muxy.app.models.VcsDiff
import com.muxy.app.models.VcsDiffRow
import com.muxy.app.models.VcsDiffRowKind
import com.muxy.app.models.VcsFile
import com.muxy.app.models.VcsFileStatus
import com.muxy.app.models.VcsPrCreated
import com.muxy.app.models.VcsPullRequest
import com.muxy.app.models.VcsStatus

class DemoGitStore(
    isWeb: Boolean,
) {
    var status =
        VcsStatus(
            branch = if (isWeb) "feature/native-git" else "main",
            aheadCount = if (isWeb) 2 else 0,
            behindCount = 0,
            hasUpstream = true,
            stagedFiles = if (isWeb) emptyList() else listOf(VcsFile("Sources/App.swift", VcsFileStatus.ADDED, false)),
            changedFiles = if (isWeb) emptyList() else listOf(VcsFile("README.md", VcsFileStatus.MODIFIED, false)),
            defaultBranch = "main",
            pullRequest =
                if (isWeb) {
                    VcsPullRequest(
                        "https://github.com/muxy-app/muxy/pull/42",
                        42,
                        "OPEN",
                        false,
                        "main",
                        true,
                        "CLEAN",
                        VcsChecks(VcsChecksStatus.SUCCESS, 4, 0, 0, 4),
                    )
                } else {
                    null
                },
        )
        private set
    private val localBranches = mutableListOf("main", "feature/native-git")

    fun branches() = VcsBranches(status.branch, localBranches.toList(), "main")

    fun commit(
        message: String,
        stageAll: Boolean,
    ) {
        if (message.isBlank()) throw DemoRequest.failure("Enter a commit message.")
        status =
            status.copy(
                stagedFiles = emptyList(),
                changedFiles = if (stageAll) emptyList() else status.changedFiles,
                aheadCount =
                    status.aheadCount + 1,
            )
    }

    fun switchBranch(branch: String) {
        if (branch !in localBranches) throw DemoRequest.failure("Branch not found.")
        status = status.copy(branch = branch)
    }

    fun createBranch(name: String) {
        if (name.isBlank() || name in localBranches) throw DemoRequest.failure("Choose a unique branch name.")
        localBranches += name
        switchBranch(name)
    }

    fun pull() {
        status = status.copy(behindCount = 0)
    }

    fun push() {
        status = status.copy(aheadCount = 0, hasUpstream = true)
    }

    fun createPullRequest(
        base: String?,
        draft: Boolean,
    ): VcsPrCreated {
        val created = VcsPrCreated("https://github.com/muxy-app/muxy/pull/43", 43)
        status = status.copy(pullRequest = VcsPullRequest(created.url, created.number, "OPEN", draft, base ?: "main"))
        return created
    }

    fun mergePullRequest() {
        status = status.copy(pullRequest = status.pullRequest?.copy(state = "MERGED"))
    }

    fun diff(path: String): VcsDiff =
        VcsDiff(
            path,
            listOf(
                VcsDiffRow(VcsDiffRowKind.HUNK, text = "@@ -1,3 +1,4 @@"),
                VcsDiffRow(VcsDiffRowKind.CONTEXT, 1, 1, "import SwiftUI"),
                VcsDiffRow(VcsDiffRowKind.DELETION, 2, null, "Text(\"Hello\")"),
                VcsDiffRow(VcsDiffRowKind.ADDITION, null, 2, "Text(\"Welcome to Muxy\")"),
                VcsDiffRow(VcsDiffRowKind.ADDITION, null, 3, ".font(.headline)"),
                VcsDiffRow(VcsDiffRowKind.CONTEXT, 3, 4, ""),
            ),
            additions = 2,
            deletions = 1,
            truncated = false,
            isBinary = false,
        )
}
