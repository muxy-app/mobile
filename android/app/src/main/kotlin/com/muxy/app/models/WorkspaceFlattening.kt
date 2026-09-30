package com.muxy.app.models

import java.util.UUID

object WorkspaceFlattening {
    fun focusedTabArea(workspace: Workspace): TabArea? {
        val areas = tabAreas(workspace)
        return areas.firstOrNull { it.id == workspace.focusedAreaId } ?: areas.firstOrNull()
    }

    fun tabAreas(workspace: Workspace): List<TabArea> = tabAreas(workspace.root)

    fun areaContaining(
        tabId: UUID,
        workspace: Workspace,
    ): TabArea? = tabAreas(workspace).firstOrNull { area -> area.tabs.any { it.id == tabId } }

    fun mapAreas(
        node: WorkspaceNode,
        transform: (TabArea) -> TabArea,
    ): WorkspaceNode =
        when (node) {
            is WorkspaceNode.Area -> {
                WorkspaceNode.Area(transform(node.area))
            }

            is WorkspaceNode.Split -> {
                WorkspaceNode.Split(
                    node.split.copy(
                        first = mapAreas(node.split.first, transform),
                        second = mapAreas(node.split.second, transform),
                    ),
                )
            }
        }

    fun tabAreas(node: WorkspaceNode): List<TabArea> =
        when (node) {
            is WorkspaceNode.Area -> listOf(node.area)
            is WorkspaceNode.Split -> tabAreas(node.split.first) + tabAreas(node.split.second)
        }
}
