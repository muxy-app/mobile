package com.muxy.app.features.tabs

import com.muxy.app.features.server.ProjectModel
import com.muxy.app.features.server.ProjectTree
import com.muxy.app.features.server.ReconnectReason
import com.muxy.app.features.server.ServerController
import com.muxy.app.features.server.ServerPhase
import com.muxy.app.models.Connection
import com.muxy.app.models.DisplayPath
import com.muxy.app.models.TabActivity
import com.muxy.app.models.TabTileKind
import com.muxy.app.networking.server.ServerFailure
import com.muxy.app.networking.server.ServerProject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
internal fun serverTabsCatalog(
    connection: Connection,
    server: ServerController,
): Flow<TabsDevice> {
    val projects =
        server.catalog
            .map { it.projects }
            .distinctUntilChanged()
            .flatMapLatest { projects ->
                if (projects.isEmpty()) return@flatMapLatest flowOf(emptyList())
                combine(
                    ProjectTree.rows(projects).map { row -> projectTabs(row.project, server.projectModel(row.project.id), server) },
                ) { it.toList() }
            }
    return combine(server.phase, server.catalog, projects) { phase, catalog, projects ->
        val message =
            when (phase) {
                is ServerPhase.Failed -> {
                    phase.failure.message(ServerFailure.Context.CONNECTING, connection.name)
                }

                is ServerPhase.Reconnecting -> {
                    when (val reason = phase.reason) {
                        ReconnectReason.ServerRestarting -> "This device is restarting."
                        is ReconnectReason.Lost -> reason.failure.message(ServerFailure.Context.CONNECTING, connection.name)
                    }
                }

                else -> {
                    when {
                        catalog.loadFailed -> "Couldn't refresh projects. Try again."
                        projects.any { it.loadFailed } -> "Couldn't refresh all tabs. Try again."
                        else -> null
                    }
                }
            }
        TabsDevice(
            connection = connection,
            status =
                when (phase) {
                    ServerPhase.Connecting -> TabsDeviceStatus.CONNECTING
                    ServerPhase.Connected -> TabsDeviceStatus.ONLINE
                    ServerPhase.Idle, is ServerPhase.Reconnecting, is ServerPhase.Failed -> TabsDeviceStatus.OFFLINE
                },
            projects = projects,
            hasLoadedProjects = catalog.hasLoaded,
            isLoading =
                catalog.isRefreshing ||
                    (!catalog.hasLoaded && !catalog.loadFailed) ||
                    projects.any { it.isLoading || (!it.hasLoadedTabs && !it.loadFailed) },
            message = message,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun projectTabs(
    project: ServerProject,
    model: ProjectModel,
    server: ServerController,
): Flow<TabsProject> {
    val tabs =
        model.tabs.flatMapLatest { tabs ->
            if (tabs.isEmpty()) return@flatMapLatest flowOf(emptyList())
            combine(
                tabs.map { tab ->
                    combine(tab.title, tab.directory, server.activity) { title, directory, activity ->
                        serverTab(tab.id, title, directory, tab.sessionId?.let(activity::get) ?: TabActivity.None)
                    }
                },
            ) { it.toList() }
        }
    return combine(tabs, model.hasLoadedSessions, model.isRefreshingSessions, model.sessionsLoadFailed) { tabs, loaded, loading, failed ->
        TabsProject(
            id = project.id,
            name = project.name,
            path = project.directory,
            tabs = tabs,
            hasLoadedTabs = loaded,
            isLoading = loading,
            loadFailed = failed,
        )
    }
}

private fun serverTab(
    id: UUID,
    title: String,
    directory: String,
    activity: TabActivity,
): TabsTab {
    val detail =
        if (activity.isAgent) {
            listOfNotNull(
                activity.statusLabel,
                activity.providerName.takeIf { activity.state != TabActivity.State.IDLE },
            ).joinToString(" · ")
        } else {
            DisplayPath.abbreviated(directory)
        }
    return TabsTab(
        id = id,
        title = title,
        detail = detail,
        tile = if (activity.isAgent) TabTileKind.AGENT else TabTileKind.SHELL,
        activity = activity,
    )
}
