package com.muxy.app.features.tabs

import com.muxy.app.models.Connection
import com.muxy.app.models.TabActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class TabsCatalogStateTest {
    private fun tab(
        title: String,
        state: TabActivity.State = TabActivity.State.IDLE,
        since: Instant? = null,
    ) = TabsTab(UUID.randomUUID(), title, detail = "~/Projects/muxy", activity = TabActivity(state, "Claude Code", since))

    private fun project(
        name: String,
        tabs: List<TabsTab>,
    ) = TabsProject(name, name, color = null, tabs = tabs, hasLoadedTabs = true)

    private fun device(
        name: String,
        projects: List<TabsProject>,
        status: TabsDeviceStatus = TabsDeviceStatus.ONLINE,
        hasLoadedProjects: Boolean = true,
    ) = TabsDevice(Connection(UUID.randomUUID(), name, "mac.local", 4865), status, projects, hasLoadedProjects)

    @Test
    fun blankQueryAndDeviceNameKeepEverything() {
        val mac = device("Mac Studio", listOf(project("muxy", listOf(tab("zsh"))), project("udesk", emptyList())))

        assertEquals(2, mac.matching("  ")?.projects?.size)
        assertEquals(2, mac.matching("studio")?.projects?.size)
    }

    @Test
    fun tabMatchKeepsOnlyMatchingTabs() {
        val mac =
            device(
                "Mac",
                listOf(project("muxy", listOf(tab("zsh"), tab("pnpm dev"))), project("udesk", listOf(tab("zsh")))),
            )

        val matched = mac.matching("pnpm")

        assertEquals(listOf("muxy"), matched?.projects?.map { it.name })
        assertEquals(
            listOf("pnpm dev"),
            matched
                ?.projects
                ?.single()
                ?.tabs
                ?.map { it.title },
        )
        assertNull(mac.matching("nothing"))
    }

    @Test
    fun fetchingWhileConnectingOrLoading() {
        assertTrue(device("Mac", emptyList(), status = TabsDeviceStatus.CONNECTING).isFetching)
        assertTrue(device("Mac", emptyList(), hasLoadedProjects = false).isFetching)
        assertFalse(device("Mac", emptyList()).isFetching)
        assertFalse(device("Mac", emptyList(), status = TabsDeviceStatus.OFFLINE, hasLoadedProjects = false).isFetching)
    }

    @Test
    fun attentionCollectsWaitingAndFinishedNewestFirst() {
        val older = tab("Review PR", TabActivity.State.WAITING, Instant.ofEpochSecond(100))
        val newer = tab("Fix tests", TabActivity.State.FINISHED, Instant.ofEpochSecond(200))
        val mac = device("Mac", listOf(project("muxy", listOf(older, tab("zsh"), tab("Refactor", TabActivity.State.WORKING), newer))))

        val attention = TabsAttention.collect(listOf(mac))

        assertEquals(listOf("Fix tests", "Review PR"), attention.map { it.tab.title })
        assertEquals("muxy", attention.first().project.name)
    }
}
