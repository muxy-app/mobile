package com.muxy.app.features.demo

import com.muxy.app.core.serialization.parseUuid
import com.muxy.app.models.Project
import com.muxy.app.models.Worktree
import com.muxy.app.networking.muxy1.protocol.EmptyResult
import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.networking.muxy1.protocol.EventType
import com.muxy.app.networking.muxy1.protocol.FileChangedEvent
import com.muxy.app.networking.muxy1.protocol.FileDeleteParams
import com.muxy.app.networking.muxy1.protocol.FileMoveParams
import com.muxy.app.networking.muxy1.protocol.FilePathParams
import com.muxy.app.networking.muxy1.protocol.FileReadParams
import com.muxy.app.networking.muxy1.protocol.FileRenameParams
import com.muxy.app.networking.muxy1.protocol.FileWriteParams
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.RawTagged
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.networking.muxy1.protocol.SelectWorktreeParams
import com.muxy.app.networking.muxy1.protocol.VcsAddWorktreeParams
import com.muxy.app.networking.muxy1.protocol.VcsBranchParams
import com.muxy.app.networking.muxy1.protocol.VcsCommitParams
import com.muxy.app.networking.muxy1.protocol.VcsCreateBranchParams
import com.muxy.app.networking.muxy1.protocol.VcsCreatePrParams
import com.muxy.app.networking.muxy1.protocol.VcsGetDiffParams
import com.muxy.app.networking.muxy1.protocol.VcsRemoveWorktreeParams
import kotlinx.serialization.json.JsonElement
import java.util.UUID

internal class DemoProjectTools(
    private val project: Project,
    primaryId: UUID,
    isWeb: Boolean,
) {
    private val files = DemoFileStore(project.name)
    private val git = DemoGitStore(isWeb)
    private val worktrees =
        mutableListOf(Worktree(primaryId, project.name, project.path, git.status.branch, true, false, project.createdAt))

    fun handle(
        method: Method,
        params: JsonElement?,
        activeWorktreeId: UUID?,
    ): DemoReply {
        files.resetChanges()
        val result =
            when (method) {
                Method.FILES_LIST -> {
                    RawTagged.of(ResultType.FILES, files.list(DemoRequest.decode<FilePathParams>(params).path))
                }

                Method.FILES_STAT -> {
                    RawTagged.of(ResultType.FILE_STAT, files.stat(DemoRequest.decode<FilePathParams>(params).path))
                }

                Method.FILES_READ -> {
                    val input = DemoRequest.decode<FileReadParams>(params)
                    RawTagged.of(ResultType.FILE_CONTENT, files.read(input.path, input.encoding))
                }

                Method.FILES_WRITE -> {
                    val input = DemoRequest.decode<FileWriteParams>(params)
                    paths(files.write(input.path, input.contents, input.encoding))
                }

                Method.FILES_MKDIR -> {
                    paths(files.mkdir(DemoRequest.decode<FilePathParams>(params).path))
                }

                Method.FILES_RENAME -> {
                    val input = DemoRequest.decode<FileRenameParams>(params)
                    paths(files.rename(input.path, input.newName))
                }

                Method.FILES_MOVE -> {
                    val input = DemoRequest.decode<FileMoveParams>(params)
                    paths(files.move(input.paths, input.into))
                }

                Method.FILES_DELETE -> {
                    files.delete(DemoRequest.decode<FileDeleteParams>(params).paths)
                    ok()
                }

                Method.VCS_REFRESH -> {
                    RawTagged.of(ResultType.VCS_STATUS, git.status)
                }

                Method.VCS_LIST_BRANCHES -> {
                    RawTagged.of(ResultType.VCS_BRANCHES, git.branches())
                }

                Method.VCS_GET_DIFF -> {
                    RawTagged.of(ResultType.VCS_DIFF, git.diff(DemoRequest.decode<VcsGetDiffParams>(params).filePath))
                }

                Method.VCS_COMMIT -> {
                    val input = DemoRequest.decode<VcsCommitParams>(params)
                    git.commit(input.message, input.stageAll)
                    ok()
                }

                Method.VCS_PULL -> {
                    git.pull()
                    ok()
                }

                Method.VCS_PUSH -> {
                    git.push()
                    ok()
                }

                Method.VCS_SWITCH_BRANCH -> {
                    git.switchBranch(DemoRequest.decode<VcsBranchParams>(params).branch)
                    updateBranch(activeWorktreeId)
                    ok()
                }

                Method.VCS_CREATE_BRANCH -> {
                    git.createBranch(DemoRequest.decode<VcsCreateBranchParams>(params).name)
                    updateBranch(activeWorktreeId)
                    ok()
                }

                Method.VCS_CREATE_PR -> {
                    val input = DemoRequest.decode<VcsCreatePrParams>(params)
                    if (input.title.isBlank()) throw DemoRequest.failure("Enter a pull request title.")
                    RawTagged.of(ResultType.VCS_PR_CREATED, git.createPullRequest(input.baseBranch, input.draft))
                }

                Method.VCS_MERGE_PULL_REQUEST -> {
                    git.mergePullRequest()
                    ok()
                }

                Method.LIST_WORKTREES -> {
                    RawTagged.of(ResultType.WORKTREES, worktrees.toList())
                }

                Method.SELECT_WORKTREE -> {
                    val id = parseUuid(DemoRequest.decode<SelectWorktreeParams>(params).worktreeId)
                    val selected = worktrees.firstOrNull { it.id == id } ?: throw DemoRequest.failure("Worktree not found.")
                    git.switchBranch(selected.branch)
                    ok()
                }

                Method.VCS_ADD_WORKTREE -> {
                    val input = DemoRequest.decode<VcsAddWorktreeParams>(params)
                    if (input.name.isBlank() || input.branch.isBlank()) throw DemoRequest.invalidParams()
                    if (input.createBranch) {
                        val current = git.status.branch
                        git.createBranch(input.branch)
                        git.switchBranch(current)
                    }
                    if (input.branch !in git.branches().locals) throw DemoRequest.failure("Branch not found.")
                    worktrees +=
                        Worktree(
                            UUID.randomUUID(),
                            input.name,
                            "${project.path}-${input.name}",
                            input.branch,
                            false,
                            true,
                            project.createdAt,
                        )
                    RawTagged.of(ResultType.WORKTREES, worktrees.toList())
                }

                Method.VCS_REMOVE_WORKTREE -> {
                    val id = parseUuid(DemoRequest.decode<VcsRemoveWorktreeParams>(params).worktreeId)
                    val row = worktrees.firstOrNull { it.id == id } ?: throw DemoRequest.failure("Worktree not found.")
                    if (!row.canBeRemoved || row.id == activeWorktreeId) throw DemoRequest.failure("This worktree cannot be removed.")
                    worktrees.remove(row)
                    ok()
                }

                else -> {
                    throw DemoRequest.invalidParams()
                }
            }
        val events =
            if (files.changedPaths.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    EventEnvelope(
                        EventName.FILE_CHANGED,
                        RawTagged.of(EventType.FILE_CHANGED, FileChangedEvent(project.id, activeWorktreeId, files.changedPaths, false)),
                    ),
                )
            }
        return DemoReply(result, events)
    }

    private fun updateBranch(activeId: UUID?) {
        val index = worktrees.indexOfFirst { it.id == activeId }
        if (index >= 0) worktrees[index] = worktrees[index].copy(branch = git.status.branch)
    }

    private fun paths(paths: List<String>) = RawTagged.of(ResultType.FILE_PATHS, paths)

    private fun ok() = RawTagged.of(ResultType.OK, EmptyResult())
}
