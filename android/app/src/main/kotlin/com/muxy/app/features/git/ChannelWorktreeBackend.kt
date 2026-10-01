package com.muxy.app.features.git

import com.muxy.app.core.serialization.uuidString
import com.muxy.app.models.Workspace
import com.muxy.app.models.Worktree
import com.muxy.app.networking.muxy1.ProjectChannel
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.networking.muxy1.protocol.GetWorkspaceParams
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.networking.muxy1.protocol.SelectWorktreeParams
import com.muxy.app.networking.muxy1.protocol.VcsAddWorktreeParams
import com.muxy.app.networking.muxy1.protocol.VcsProjectParams
import com.muxy.app.networking.muxy1.protocol.VcsRemoveWorktreeParams
import com.muxy.app.networking.muxy1.request
import com.muxy.app.persistence.worktrees.WorktreeCache
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.util.UUID

class ChannelWorktreeBackend(
    private val connectionId: UUID,
    private val projectId: UUID,
    private val channel: ProjectChannel,
    private val cache: WorktreeCache,
) : WorktreeBackend {
    override val requiresName = true
    override val changes =
        merge(
            channel.connected.filter { it }.map { Unit },
            channel.events.filter { it.event == EventName.WORKSPACE_CHANGED }.map { Unit },
        )

    override suspend fun cached(): List<WorktreeRow>? = cache.load(connectionId, projectId)?.map { row(it, null) }

    override suspend fun rows(): List<WorktreeRow> {
        val worktrees: List<Worktree> = request(Method.LIST_WORKTREES, VcsProjectParams(projectId.uuidString), ResultType.WORKTREES)
        val workspace: Workspace = request(Method.GET_WORKSPACE, GetWorkspaceParams(projectId.uuidString), ResultType.WORKSPACE)
        if (workspace.projectId != projectId) throw GitException.UnexpectedResponse()
        cache.save(worktrees, connectionId, projectId)
        return worktrees.map { row(it, workspace.worktreeId) }
    }

    override suspend fun open(row: WorktreeRow): String? {
        val result = channel.request(Method.SELECT_WORKTREE, SelectWorktreeParams(projectId.uuidString, row.id))
        if (result.type != ResultType.OK) throw GitException.UnexpectedResponse()
        return null
    }

    override suspend fun create(
        name: String,
        branch: String,
        createsBranch: Boolean,
    ) {
        val worktrees: List<Worktree> =
            request(
                Method.VCS_ADD_WORKTREE,
                VcsAddWorktreeParams(projectId.uuidString, name, branch, createsBranch),
                ResultType.WORKTREES,
            )
        cache.save(worktrees, connectionId, projectId)
    }

    override suspend fun prepareRemoval(row: WorktreeRow): PendingWorktreeRemoval =
        PendingWorktreeRemoval(row, false) {
            val result = channel.request(Method.VCS_REMOVE_WORKTREE, VcsRemoveWorktreeParams(projectId.uuidString, row.id))
            if (result.type != ResultType.OK) throw GitException.UnexpectedResponse()
        }

    private fun row(
        worktree: Worktree,
        activeId: UUID?,
    ): WorktreeRow =
        WorktreeRow(
            id = worktree.id.uuidString,
            name = worktree.name,
            branch = worktree.branch,
            projectId = null,
            isCurrent = worktree.id == activeId,
            isOpenable = true,
            isRemovable = worktree.canBeRemoved,
        )

    private suspend inline fun <reified P, reified R> request(
        method: Method,
        params: P,
        type: String,
    ): R {
        val result = channel.request(method, params)
        if (result.type != type) throw GitException.UnexpectedResponse()
        return result.decode<R>()
    }
}
