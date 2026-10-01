package com.muxy.app.features.server

import com.muxy.app.core.text.NaturalOrder
import com.muxy.app.networking.server.ServerProject

data class ServerProjectRow(
    val project: ServerProject,
    val isNested: Boolean,
)

object ProjectTree {
    fun rows(
        projects: List<ServerProject>,
        order: Comparator<String> = NaturalOrder(),
    ): List<ServerProjectRow> {
        val parents = projects.filter { !it.isHome && it.parentId == null }.map { it.id }.toSet()
        val children = projects.filter { isChild(it, parents) }.groupBy { it.parentId }
        val home = projects.filter { it.isHome }.map { ServerProjectRow(it, isNested = false) }
        val topLevel = projects.filter { !it.isHome && !isChild(it, parents) }.sortedByName(order)
        return home +
            topLevel.flatMap { project ->
                listOf(ServerProjectRow(project, isNested = false)) +
                    children[project.id].orEmpty().sortedByName(order).map { ServerProjectRow(it, isNested = true) }
            }
    }

    private fun isChild(
        project: ServerProject,
        parents: Set<String>,
    ): Boolean {
        if (project.isHome) return false
        val parentId = project.parentId ?: return false
        return parentId in parents
    }

    private fun List<ServerProject>.sortedByName(order: Comparator<String>): List<ServerProject> = sortedWith(compareBy(order) { it.name })
}
