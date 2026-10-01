package com.muxy.app.features.projectdetail

import com.muxy.app.features.files.ChannelFileBackend
import com.muxy.app.features.files.FileException
import com.muxy.app.features.files.FileHost
import com.muxy.app.features.files.FileLocation
import com.muxy.app.features.files.FileManagerViewModel
import com.muxy.app.features.git.ChannelGitBackend
import com.muxy.app.features.git.ChannelWorktreeBackend
import com.muxy.app.features.git.GitViewModel
import com.muxy.app.features.git.WorktreesViewModel
import com.muxy.app.features.projects.ProjectIcon
import com.muxy.app.features.server.ServerDirectory
import com.muxy.app.features.server.ServerProjectListing
import com.muxy.app.features.server.files.ServerFileBackend
import com.muxy.app.features.server.git.ServerGitBackend
import com.muxy.app.features.server.worktrees.ServerWorktreeBackend
import com.muxy.app.networking.muxy1.ConnectionManager
import com.muxy.app.networking.muxy1.ConnectionProjectChannel
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProjectsResult
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.persistence.worktrees.WorktreeCache

class ProjectToolsFactory(
    private val manager: ConnectionManager,
    private val servers: ServerDirectory,
    private val worktreeCache: WorktreeCache,
) {
    fun files(project: ToolProject): FileManagerViewModel =
        when (project) {
            is ToolProject.Device -> {
                val channel = ConnectionProjectChannel(project.connectionId, manager)
                val fallback = FileLocation(project.name, "", ProjectIcon.Symbol("folder"), FileHost.Mac)
                FileManagerViewModel(fallback, ChannelFileBackend(project.projectId, channel)) {
                    val result = channel.request(Method.LIST_PROJECTS, null)
                    if (result.type != ResultType.PROJECTS) throw FileException.UnexpectedResponse()
                    val loaded =
                        result.decode<ProjectsResult>().projects.firstOrNull { it.id == project.projectId }
                            ?: throw FileException.UnexpectedResponse()
                    FileLocation(
                        loaded.name,
                        loaded.path,
                        ProjectIcon.Symbol(loaded.icon),
                        if (loaded.workspaceKind ==
                            "ssh"
                        ) {
                            FileHost.Remote
                        } else {
                            FileHost.Mac
                        },
                    )
                }
            }

            is ToolProject.Server -> {
                val server = servers.controller(project.serverId)
                val location = {
                    val loaded = server.project(project.projectId)
                    FileLocation(
                        loaded?.name ?: "Project",
                        loaded?.directory.orEmpty(),
                        loaded?.let(ServerProjectListing::icon) ?: ProjectIcon.Symbol("folder"),
                        FileHost.Computer(server.serverName),
                    )
                }
                FileManagerViewModel(location(), ServerFileBackend(project.projectId, server)) { location() }
            }
        }

    fun git(project: ToolProject): GitViewModel =
        GitViewModel(
            when (project) {
                is ToolProject.Device -> ChannelGitBackend(project.projectId, ConnectionProjectChannel(project.connectionId, manager))
                is ToolProject.Server -> ServerGitBackend(project.projectId, servers.controller(project.serverId))
            },
        )

    fun worktrees(project: ToolProject): WorktreesViewModel =
        WorktreesViewModel(
            when (project) {
                is ToolProject.Device -> {
                    ChannelWorktreeBackend(
                        project.connectionId,
                        project.projectId,
                        ConnectionProjectChannel(project.connectionId, manager),
                        worktreeCache,
                    )
                }

                is ToolProject.Server -> {
                    ServerWorktreeBackend(project.projectId, servers.controller(project.serverId))
                }
            },
        )
}
