@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.muxy.app.testing

import com.muxy.app.features.server.ServerController
import com.muxy.app.networking.server.ServerGitRepository
import com.muxy.app.networking.server.ServerProject
import com.muxy.app.networking.server.ServerProjectFiles
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import uniffi.muxy_mobile.FileEntry
import uniffi.muxy_mobile.FileInfo
import uniffi.muxy_mobile.GitBranch
import uniffi.muxy_mobile.GitChecks
import uniffi.muxy_mobile.GitDiff
import uniffi.muxy_mobile.GitMergeMethod
import uniffi.muxy_mobile.GitPullRequest
import uniffi.muxy_mobile.GitStatus
import uniffi.muxy_mobile.GitSummary
import uniffi.muxy_mobile.GitWorktree
import uniffi.muxy_mobile.WorktreeRemoval

fun TestScope.toolServer(connection: FakeServerConnection): ServerController {
    val server =
        ServerController("server-1", FakeServerConnector(listOf(Result.success(connection))), { serverCredential() }, backgroundScope)
    server.setWantsConnection(true)
    runCurrent()
    return server
}

class FakeServerFiles : ServerProjectFiles {
    val calls = mutableListOf<String>()
    var text: suspend () -> String = { "hello" }
    var bytes: suspend () -> ByteArray = { "hello".toByteArray() }
    var info = FileInfo("README.md", "README.md", false, 5uL)
    var closeCount = 0

    override suspend fun list(path: String): List<FileEntry> = listOf(FileEntry(info.name, info.path, info.isDirectory, false))

    override suspend fun stat(path: String): FileInfo = info

    override suspend fun readText(path: String): String {
        calls += "text:$path"
        return text()
    }

    override suspend fun readBytes(path: String): ByteArray {
        calls += "bytes:$path"
        return bytes()
    }

    override suspend fun writeText(
        path: String,
        text: String,
    ): String {
        calls += "write:$path:$text"
        return path
    }

    override suspend fun createDirectory(path: String): String = path

    override suspend fun rename(
        path: String,
        name: String,
    ): String = name

    override suspend fun moveFiles(
        paths: List<String>,
        into: String,
    ): List<String> = paths.map { "$into/$it" }

    override suspend fun deleteFiles(paths: List<String>) {
        calls += "delete:${paths.joinToString()}"
    }

    override suspend fun watch() {
        calls += "watch"
    }

    override suspend fun unwatch() {
        calls += "unwatch"
    }

    override fun close() {
        closeCount += 1
    }
}

fun gitSummary(
    branch: String? = "main",
    head: String? = "1234567890",
    upstream: String? = "origin/main",
) = GitSummary(branch, head, upstream, 2uL, 1uL, 0u, 0u, 0u, 0u, 0u)

fun gitStatus() = GitStatus(gitSummary(), "main", listOf(GitBranch("main", true, true, true)), emptyList(), null)

fun gitPullRequest() =
    GitPullRequest(
        42uL,
        "https://github.com/muxy-app/muxy/pull/42",
        "Changes",
        "demo",
        "feature",
        "head-oid",
        "main",
        "OPEN",
        false,
        null,
        true,
        "clean",
        false,
        GitChecks(4u, 0u, 0u),
    )

fun gitWorktree(
    directory: String = "/work/feature",
    registered: String? = "feature",
) = GitWorktree(directory, "head", "feature", false, false, false, false, false, registered)

class FakeGitRepository : ServerGitRepository {
    val calls = mutableListOf<String>()
    var currentStatus = gitStatus()
    var onStatus: suspend () -> GitStatus = { currentStatus }
    var onSummary: suspend () -> GitSummary? = { currentStatus.summary }
    var onCommit: suspend () -> Unit = {}
    var treeList = listOf(gitWorktree())
    var project = serverProject("feature", "Feature", parentId = "muxy", isWorktree = true)
    var removal = WorktreeRemoval("/work/feature", 1uL, 2uL, true, byteArrayOf(1, 2), "head", "feature")
    var removed: WorktreeRemoval? = null
    var closeCount = 0

    override suspend fun summary(): GitSummary? {
        calls += "summary"
        return onSummary()
    }

    override suspend fun status(includePullRequest: Boolean): GitStatus {
        calls += "status:$includePullRequest"
        return onStatus()
    }

    override suspend fun branches(): List<GitBranch> = currentStatus.branches

    override suspend fun diff(
        path: String,
        staged: Boolean,
        lineLimit: UInt?,
    ): GitDiff {
        calls += "diff:$path:$staged:$lineLimit"
        return GitDiff(emptyList(), 2uL, 1uL, lineLimit != null, false)
    }

    override suspend fun commit(
        message: String,
        stageAll: Boolean,
    ) {
        calls += "commit:$message:$stageAll"
        onCommit()
    }

    override suspend fun pull() {
        calls += "pull"
    }

    override suspend fun push(setUpstream: Boolean) {
        calls += "push:$setUpstream"
    }

    override suspend fun switchBranch(name: String) {
        calls += "switch:$name"
    }

    override suspend fun createBranch(name: String) {
        calls += "create:$name"
    }

    override suspend fun createPullRequest(
        title: String,
        body: String,
        baseBranch: String?,
        draft: Boolean,
    ): GitPullRequest {
        calls += "pr:$title:$body:$baseBranch:$draft"
        return gitPullRequest()
    }

    override suspend fun mergePullRequest(
        number: ULong,
        method: GitMergeMethod,
        deleteBranch: Boolean,
        expectedHead: String?,
    ) {
        calls += "merge:$number:$method:$deleteBranch:$expectedHead"
    }

    override suspend fun worktrees(): List<GitWorktree> = treeList

    override suspend fun createWorktree(
        branch: String,
        base: String?,
    ): ServerProject {
        calls += "worktree:$branch:$base"
        return project
    }

    override suspend fun registerWorktree(directory: String): ServerProject {
        calls += "register:$directory"
        return project
    }

    override suspend fun inspectWorktreeRemoval(): WorktreeRemoval {
        calls += "inspect"
        return removal
    }

    override suspend fun removeWorktree(expected: WorktreeRemoval) {
        calls += "remove"
        removed = expected
    }

    override fun close() {
        closeCount += 1
    }
}
