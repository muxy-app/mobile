package com.muxy.app.features.tabs

import com.muxy.app.models.Connection
import com.muxy.app.models.TabActivity
import com.muxy.app.models.TabKind
import com.muxy.app.models.TabTileKind
import java.util.UUID

enum class TabsDeviceStatus {
    CONNECTING,
    ONLINE,
    OFFLINE,
    DEMO,
    SSH,
    LOCKED,
}

data class TabsTab(
    val id: UUID,
    val title: String,
    val kind: TabKind = TabKind.Terminal,
    val detail: String = "",
    val tile: TabTileKind = TabTileKind.of(kind),
    val activity: TabActivity = TabActivity.None,
) {
    fun matches(query: String): Boolean = title.contains(query, ignoreCase = true) || detail.contains(query, ignoreCase = true)
}

data class TabsProject(
    val id: String,
    val name: String,
    val path: String? = null,
    val tabs: List<TabsTab> = emptyList(),
    val hasLoadedTabs: Boolean = false,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
) {
    fun matching(query: String): TabsProject? {
        if (name.contains(query, ignoreCase = true)) return this
        val matched = tabs.filter { it.matches(query) }
        return if (matched.isEmpty()) null else copy(tabs = matched)
    }
}

data class TabsDevice(
    val connection: Connection,
    val status: TabsDeviceStatus = TabsDeviceStatus.CONNECTING,
    val projects: List<TabsProject> = emptyList(),
    val hasLoadedProjects: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null,
) {
    val isOnline: Boolean
        get() = status == TabsDeviceStatus.ONLINE || status == TabsDeviceStatus.DEMO

    val tabCount: Int
        get() = projects.sumOf { it.tabs.size }

    val isFetching: Boolean
        get() = status == TabsDeviceStatus.CONNECTING || (isOnline && (isLoading || !hasLoadedProjects) && message == null)

    val isCurrent: Boolean
        get() = isOnline && hasLoadedProjects && !isLoading && message == null && projects.all { it.hasLoadedTabs }

    fun canCreate(project: TabsProject): Boolean = isOnline && project.hasLoadedTabs && !project.isLoading && !project.loadFailed

    fun matching(query: String): TabsDevice? {
        val needle = query.trim()
        if (needle.isEmpty() || connection.name.contains(needle, ignoreCase = true)) return this
        val matched = projects.mapNotNull { it.matching(needle) }
        return if (matched.isEmpty()) null else copy(projects = matched)
    }
}

data class TabsAttention(
    val device: TabsDevice,
    val project: TabsProject,
    val tab: TabsTab,
) {
    companion object {
        fun collect(devices: List<TabsDevice>): List<TabsAttention> =
            devices
                .flatMap { device ->
                    device.projects.flatMap { project ->
                        project.tabs.filter { it.activity.needsAttention }.map { TabsAttention(device, project, it) }
                    }
                }.sortedByDescending { it.tab.activity.since }
    }
}

data class TabsProjectKey(
    val connectionId: UUID,
    val projectId: String,
)

data class TabsSelection(
    val connection: Connection,
    val project: TabsProject,
    val tabId: UUID,
)
