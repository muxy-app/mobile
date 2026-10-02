@file:UseSerializers(UuidSerializer::class)

package com.muxy.app.features.navigation

import androidx.navigation3.runtime.NavKey
import com.muxy.app.core.serialization.UuidSerializer
import com.muxy.app.features.projectdetail.ProjectTool
import com.muxy.app.features.projectdetail.ToolProject
import com.muxy.app.features.server.ServerFocus
import com.muxy.app.networking.muxy1.ConnectionFocus
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.UUID

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Connections : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object Paywall : AppRoute

    @Serializable
    data object AddConnection : AppRoute

    @Serializable
    data class Projects(
        val connectionId: UUID,
    ) : AppRoute

    @Serializable
    data class SshTerminal(
        val connectionId: UUID,
    ) : AppRoute

    @Serializable
    data class ProjectDetail(
        val connectionId: UUID,
        val projectId: UUID,
        val projectName: String,
    ) : AppRoute

    @Serializable
    sealed interface Server : AppRoute {
        val connectionId: UUID
        val serverId: String
    }

    @Serializable
    data class ServerProjects(
        override val connectionId: UUID,
        override val serverId: String,
    ) : Server

    @Serializable
    data class ServerProject(
        override val connectionId: UUID,
        override val serverId: String,
        val projectId: String,
    ) : Server

    @Serializable
    data class ProjectTools(
        val project: ToolProject,
        val tool: ProjectTool,
    ) : AppRoute

    val isModal: Boolean
        get() = this == AddConnection || this == Settings || this == Paywall || this is ProjectTools
}

fun List<AppRoute>.connectionFocus(): ConnectionFocus =
    when (val top = lastOrNull()) {
        is AppRoute.Projects -> {
            ConnectionFocus.Device(top.connectionId)
        }

        is AppRoute.ProjectDetail -> {
            ConnectionFocus.Device(top.connectionId)
        }

        AppRoute.Connections, is AppRoute.Server, is AppRoute.SshTerminal, null -> {
            ConnectionFocus.None
        }

        AppRoute.AddConnection, AppRoute.Settings, AppRoute.Paywall -> {
            ConnectionFocus.Hold
        }

        is AppRoute.ProjectTools -> {
            when (val project = top.project) {
                is ToolProject.Device -> ConnectionFocus.Device(project.connectionId)
                is ToolProject.Server -> ConnectionFocus.None
            }
        }
    }

fun List<AppRoute>.serverFocus(): ServerFocus {
    val server = filterIsInstance<AppRoute.Server>().lastOrNull() ?: return ServerFocus()
    val visible = lastOrNull { !it.isModal } as? AppRoute.ServerProject
    return ServerFocus(server.serverId, visible?.takeIf { it.serverId == server.serverId }?.projectId)
}
