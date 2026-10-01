package com.muxy.app.features.server.worktrees

import com.muxy.app.features.git.PendingWorktreeRemoval
import com.muxy.app.features.git.WorktreeBackend
import com.muxy.app.features.git.WorktreeRow
import com.muxy.app.features.server.ServerController
import com.muxy.app.features.server.ServerPhase
import com.muxy.app.features.server.withGit
import com.muxy.app.networking.server.ServerRequestError
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import uniffi.muxy_mobile.GitWorktree
import uniffi.muxy_mobile.MobileException

class ServerWorktreeBackend(
    private val projectId: String,
    private val server: ServerController,
) : WorktreeBackend {
    override val requiresName = false
    override val changes =
        merge(
            server.phase.filter { it == ServerPhase.Connected }.map { Unit },
            server.catalog.filter { server.connection != null }.map { Unit },
        )

    override suspend fun cached(): List<WorktreeRow>? = null

    override suspend fun rows(): List<WorktreeRow> = server.withGit(projectId) { it.worktrees() }.map(::row)

    override suspend fun open(row: WorktreeRow): String {
        row.projectId?.let { return it }
        val project = server.withGit(projectId) { it.registerWorktree(row.id) }
        server.reloadProjects()
        return project.id
    }

    override suspend fun create(
        name: String,
        branch: String,
        createsBranch: Boolean,
    ) {
        server.withGit(projectId) { it.createWorktree(branch, if (createsBranch) "HEAD" else null) }
        server.reloadProjects()
    }

    override suspend fun prepareRemoval(row: WorktreeRow): PendingWorktreeRemoval {
        val worktreeProject = requireNotNull(row.projectId)
        val connection = server.connection
        val expected = server.withGit(worktreeProject) { it.inspectWorktreeRemoval() }
        return PendingWorktreeRemoval(row, expected.dirty, endsTerminals = true) {
            if (server.connection !== connection) throw ServerRequestError.wrapping(MobileException.Disconnected(), server.serverName)
            server.withGit(worktreeProject) { it.removeWorktree(expected) }
            server.reloadProjects()
        }
    }

    fun row(worktree: GitWorktree): WorktreeRow {
        val rootProject = server.project(projectId)?.parentId ?: projectId
        val worktreeProject = if (worktree.primary) rootProject else worktree.registered
        val isCurrent = worktreeProject == projectId
        return WorktreeRow(
            id = worktree.directory,
            name = worktreeProject?.let(server::project)?.name ?: worktree.directory.trimEnd('/').substringAfterLast('/'),
            branch = worktree.branch,
            projectId = worktreeProject,
            isCurrent = isCurrent,
            isOpenable = !worktree.prunable && !worktree.bare,
            isRemovable = !worktree.primary && worktree.registered != null && !worktree.locked && !isCurrent,
            isRegistered = worktreeProject != null,
        )
    }
}
