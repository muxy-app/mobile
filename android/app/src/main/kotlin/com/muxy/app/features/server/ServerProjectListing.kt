package com.muxy.app.features.server

import com.muxy.app.features.projectdetail.ProjectTabsStatus
import com.muxy.app.features.projects.ProjectIcon
import com.muxy.app.features.projects.ProjectListItem
import com.muxy.app.features.projects.ProjectListStatus
import com.muxy.app.features.projects.ProjectLogo
import com.muxy.app.features.projects.ProjectSymbols
import com.muxy.app.networking.server.ServerFailure
import com.muxy.app.networking.server.ServerProject

object ServerProjectListing {
    private const val SYMBOL_PREFIX = "sf:"

    fun item(row: ServerProjectRow): ProjectListItem {
        val project = row.project
        return ProjectListItem(
            id = project.id,
            name = project.name,
            path = project.directory,
            icon = icon(project),
            iconColor = project.color,
            logo = project.logo?.let(::ProjectLogo),
            isNested = row.isNested,
        )
    }

    fun status(
        phase: ServerPhase,
        catalog: ProjectCatalog,
        serverName: String,
    ): ProjectListStatus =
        when (phase) {
            ServerPhase.Idle, ServerPhase.Connecting -> ProjectListStatus.Loading
            is ServerPhase.Reconnecting -> reconnectingStatus(phase.reason, serverName)
            is ServerPhase.Failed -> disconnected(phase.failure, serverName)
            ServerPhase.Connected -> connectedStatus(catalog)
        }

    fun tabsStatus(
        phase: ServerPhase,
        hasLoadedSessions: Boolean,
    ): ProjectTabsStatus =
        when (phase) {
            ServerPhase.Idle, ServerPhase.Connecting -> ProjectTabsStatus.LOADING
            is ServerPhase.Reconnecting -> reconnectingTabsStatus(phase.reason)
            is ServerPhase.Failed -> ProjectTabsStatus.DISCONNECTED
            ServerPhase.Connected -> if (hasLoadedSessions) ProjectTabsStatus.READY else ProjectTabsStatus.LOADING
        }

    private fun reconnectingTabsStatus(reason: ReconnectReason): ProjectTabsStatus =
        when (reason) {
            ReconnectReason.ServerRestarting -> ProjectTabsStatus.LOADING
            is ReconnectReason.Lost -> ProjectTabsStatus.DISCONNECTED
        }

    fun icon(project: ServerProject): ProjectIcon {
        val icon = project.icon?.trim().orEmpty()
        if (icon.isEmpty()) return ProjectIcon.Symbol(fallbackSymbol(project))
        if (!icon.startsWith(SYMBOL_PREFIX)) return ProjectIcon.Emoji(icon)
        val name = icon.removePrefix(SYMBOL_PREFIX)
        if (!ProjectSymbols.isKnown(name)) return ProjectIcon.Symbol(fallbackSymbol(project))
        return ProjectIcon.Symbol(name)
    }

    private fun reconnectingStatus(
        reason: ReconnectReason,
        serverName: String,
    ): ProjectListStatus =
        when (reason) {
            ReconnectReason.ServerRestarting -> ProjectListStatus.Loading
            is ReconnectReason.Lost -> disconnected(reason.failure, serverName)
        }

    private fun connectedStatus(catalog: ProjectCatalog): ProjectListStatus {
        if (catalog.loadFailed) return ProjectListStatus.LoadFailed
        if (!catalog.hasLoaded) return ProjectListStatus.Loading
        return ProjectListStatus.Empty
    }

    private fun disconnected(
        failure: ServerFailure,
        serverName: String,
    ): ProjectListStatus = ProjectListStatus.Disconnected(failure.message(ServerFailure.Context.CONNECTING, serverName))

    private fun fallbackSymbol(project: ServerProject): String {
        if (project.isHome) return "house"
        if (project.isWorktree) return "arrow.triangle.branch"
        return "folder"
    }
}
