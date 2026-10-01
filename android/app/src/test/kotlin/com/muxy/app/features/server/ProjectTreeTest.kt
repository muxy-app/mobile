package com.muxy.app.features.server

import com.muxy.app.testing.serverProject
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectTreeTest {
    private fun names(rows: List<ServerProjectRow>): List<String> = rows.map { (if (it.isNested) "  " else "") + it.project.name }

    @Test
    fun homeComesFirstThenProjectsByName() {
        val rows =
            ProjectTree.rows(
                listOf(
                    serverProject("w", "web"),
                    serverProject("h", "Home", isHome = true),
                    serverProject("a", "api"),
                    serverProject("m", "Muxy"),
                ),
            )
        assertEquals(listOf("Home", "api", "Muxy", "web"), names(rows))
    }

    @Test
    fun worktreesNestUnderTheirParentSortedByName() {
        val rows =
            ProjectTree.rows(
                listOf(
                    serverProject("m", "muxy"),
                    serverProject("wt2", "sdk", parentId = "m", isWorktree = true),
                    serverProject("wt1", "docs", parentId = "m", isWorktree = true),
                    serverProject("z", "zeta"),
                ),
            )
        assertEquals(listOf("muxy", "  docs", "  sdk", "zeta"), names(rows))
    }

    @Test
    fun numbersSortNaturally() {
        val rows = ProjectTree.rows(listOf(serverProject("b", "app10"), serverProject("a", "app2"), serverProject("c", "App3")))
        assertEquals(listOf("app2", "App3", "app10"), names(rows))
    }

    @Test
    fun worktreesWithoutAKnownParentStayVisible() {
        val rows = ProjectTree.rows(listOf(serverProject("wt", "orphan", parentId = "gone", isWorktree = true)))
        assertEquals(listOf("orphan"), names(rows))
    }

    @Test
    fun childrenOfHomeOrOfWorktreesAreNeverHidden() {
        val rows =
            ProjectTree.rows(
                listOf(
                    serverProject("h", "Home", isHome = true),
                    serverProject("c", "under-home", parentId = "h"),
                    serverProject("m", "muxy"),
                    serverProject("wt", "wt", parentId = "m", isWorktree = true),
                    serverProject("g", "grandchild", parentId = "wt", isWorktree = true),
                ),
            )
        assertEquals(setOf("h", "c", "m", "wt", "g"), rows.map { it.project.id }.toSet())
        assertEquals(listOf("Home", "grandchild", "muxy", "  wt", "under-home"), names(rows))
    }
}
